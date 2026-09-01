package com.smartdesk.feature.triage;

import com.smartdesk.domain.AppUser;
import com.smartdesk.domain.Enums.NotificationType;
import com.smartdesk.domain.Enums.Role;
import com.smartdesk.domain.Enums.TicketEventType;
import com.smartdesk.domain.Enums.TicketStatus;
import com.smartdesk.domain.Ticket;
import com.smartdesk.domain.TicketHistory;
import com.smartdesk.feature.notification.NotificationService;
import com.smartdesk.feature.ticket.TicketEventService;
import com.smartdesk.repo.AppUserRepo;
import com.smartdesk.repo.TicketHistoryRepo;
import com.smartdesk.repo.TicketRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * 단계 3+4: 커밋 후 비동기 정밀 트리아지. 비웹 스레드 → TenantContext SYSTEM →
 * 전 테넌트 유사 티켓·담당자 실적을 활용. 신뢰도 충분하면 담당자 자동 배정.
 */
@Component
public class TriageOnCreateListener {

    private static final Logger log = LoggerFactory.getLogger(TriageOnCreateListener.class);

    private final TriageService triage;
    private final TriageProperties props;
    private final TicketRepo tickets;
    private final TicketHistoryRepo histories;
    private final TicketEventService events;
    private final NotificationService notifications;
    private final AppUserRepo users;

    public TriageOnCreateListener(TriageService triage, TriageProperties props, TicketRepo tickets,
                                  TicketHistoryRepo histories, TicketEventService events,
                                  NotificationService notifications, AppUserRepo users) {
        this.triage = triage;
        this.props = props;
        this.tickets = tickets;
        this.histories = histories;
        this.events = events;
        this.notifications = notifications;
        this.users = users;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketCreated(TicketCreatedEvent e) {
        Ticket t = tickets.findById(e.ticketId()).orElse(null);
        if (t == null || t.getStatus() != TicketStatus.RECEIVED || t.getAssigneeId() != null) return;

        TriageService.TriageResult r;
        try {
            r = triage.triage(t);
        } catch (Exception ex) {
            log.warn("[triage] #{} 자동 트리아지 실패: {}", e.ticketId(), ex.toString());
            return;
        }

        boolean changed = false;
        if (r.categoryId() != null && !r.categoryId().equals(t.getCategoryId())) {
            t.setCategoryId(r.categoryId());
            changed = true;
        }
        if (t.getPriority() != r.priority()) {
            t.setPriority(r.priority());
            changed = true;
        }

        if (!r.escalate() && props.isAutoAssign() && r.suggestedAssigneeId() != null) {
            t.setAssigneeId(r.suggestedAssigneeId());
            t.setStatus(TicketStatus.IN_PROGRESS);
            if (t.getFirstRespondedAt() == null) t.setFirstRespondedAt(Instant.now());
            tickets.save(t);
            histories.save(new TicketHistory(t.getId(), "assignee", null,
                    String.valueOf(r.suggestedAssigneeId()), "SYSTEM", null));
            events.recordSystem(t.getId(), TicketEventType.ASSIGNED, null,
                    String.valueOf(r.suggestedAssigneeId()));
            notifications.notifyUser(r.suggestedAssigneeId(), NotificationType.TICKET_ASSIGNED,
                    "티켓 자동 배정: #" + t.getId(),
                    t.getTitle() + " (트리아지 신뢰도 " + r.confidence() + ")", t.getId());
        } else {
            if (changed) tickets.save(t);
            for (AppUser m : users.findByRoleAndActiveTrue(Role.MANAGER)) {
                notifications.notifyUser(m.getId(), NotificationType.TRIAGE_REVIEW,
                        "트리아지 검토 필요: #" + t.getId(),
                        t.getTitle() + " — 신뢰도 " + r.confidence() + ", 수동 배정 필요", t.getId());
            }
        }
    }
}
