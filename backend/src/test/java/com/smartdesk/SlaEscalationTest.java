package com.smartdesk;

import com.smartdesk.domain.*;
import com.smartdesk.domain.Enums.*;
import com.smartdesk.feature.ticket.SlaMonitorService;
import com.smartdesk.repo.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A2: 미배정 티켓의 SLA 초과가 관리자에게 에스컬레이션되는지.
 * A1: 재스캔 시 알림/이벤트가 중복되지 않는지 (NotificationWriter REQUIRES_NEW + 유니크).
 * NotificationWriter 가 REQUIRES_NEW 로 커밋하므로 @Transactional 롤백을 쓸 수 없음 → 수동 정리.
 */
@SpringBootTest
@ActiveProfiles("test")
class SlaEscalationTest {

    @Autowired SlaMonitorService slaMonitor;
    @Autowired TicketRepo tickets;
    @Autowired NotificationRepo notifications;
    @Autowired TicketEventRepo events;

    private Long ticketId;

    @AfterEach
    void cleanup() {
        if (ticketId != null) {
            notifications.findAll().stream()
                    .filter(n -> ticketId.equals(n.getTicketId()))
                    .forEach(notifications::delete);
            events.findByTicketIdOrderByAtAsc(ticketId).forEach(events::delete);
            tickets.deleteById(ticketId);
        }
    }

    private Ticket newBreachedUnassignedTicket() {
        Ticket t = new Ticket();
        t.setClientId(1L);
        t.setContractId(1L);
        t.setRequesterId(1L);
        t.setCategoryId(2L);           // Access → 보안팀(dept 3), 관리자 없음 → 전체 관리자로 폴백
        t.setAssigneeId(null);         // 미배정
        t.setTitle("SLA 에스컬레이션 테스트");
        t.setContent("x");
        t.setPriority(Priority.HIGH);
        t.setStatus(TicketStatus.RECEIVED);
        t.setSlaDueAt(Instant.now().minus(1, ChronoUnit.HOURS));  // 이미 초과
        return tickets.save(t);
    }

    @Test
    void unassignedBreach_notifiesAManager_andIsIdempotent() {
        ticketId = newBreachedUnassignedTicket().getId();

        int[] first = slaMonitor.scan();
        assertTrue(first[0] >= 1, "초과 티켓 1건 이상 처리");

        List<Notification> mine = notifications.findAll().stream()
                .filter(n -> ticketId.equals(n.getTicketId()) && n.getType() == NotificationType.SLA_BREACHED)
                .toList();
        assertFalse(mine.isEmpty(), "미배정 초과 → 관리자에게 알림이 가야 함");
        assertTrue(mine.stream().allMatch(n -> "USER".equals(n.getRecipientType())));

        long eventsAfterFirst = events.findByTicketIdOrderByAtAsc(ticketId).stream()
                .filter(e -> e.getType() == TicketEventType.SLA_BREACHED).count();
        assertEquals(1, eventsAfterFirst);

        // 재스캔 — 중복 없음
        slaMonitor.scan();
        long notifCount = notifications.findAll().stream()
                .filter(n -> ticketId.equals(n.getTicketId()) && n.getType() == NotificationType.SLA_BREACHED)
                .count();
        long eventCount = events.findByTicketIdOrderByAtAsc(ticketId).stream()
                .filter(e -> e.getType() == TicketEventType.SLA_BREACHED).count();
        assertEquals(mine.size(), notifCount, "재스캔해도 알림 중복 없음");
        assertEquals(1, eventCount, "재스캔해도 이벤트 중복 없음");
    }
}
