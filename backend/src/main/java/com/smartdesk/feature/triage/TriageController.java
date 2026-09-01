package com.smartdesk.feature.triage;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.Enums.NotificationType;
import com.smartdesk.domain.Enums.TicketEventType;
import com.smartdesk.domain.Enums.TicketStatus;
import com.smartdesk.domain.Ticket;
import com.smartdesk.domain.TicketHistory;
import com.smartdesk.feature.notification.NotificationService;
import com.smartdesk.feature.ticket.TicketEventService;
import com.smartdesk.repo.TicketHistoryRepo;
import com.smartdesk.repo.TicketRepo;
import com.smartdesk.security.AuthPrincipal;
import com.smartdesk.security.CurrentUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** 단계 3: 지능형 트리아지 · SLA 위험도 (SI 담당자 전용). */
@RestController
@RequestMapping("/api/tickets/{ticketId}")
public class TriageController {

    private final TriageService triage;
    private final SlaRiskService slaRisk;
    private final TicketRepo tickets;
    private final TicketHistoryRepo histories;
    private final TicketEventService events;
    private final NotificationService notifications;

    public TriageController(TriageService triage, SlaRiskService slaRisk, TicketRepo tickets,
                            TicketHistoryRepo histories, TicketEventService events,
                            NotificationService notifications) {
        this.triage = triage;
        this.slaRisk = slaRisk;
        this.tickets = tickets;
        this.histories = histories;
        this.events = events;
        this.notifications = notifications;
    }

    /** 트리아지 실행 (미리보기 — 적용하지 않음). */
    @PostMapping("/triage")
    public TriageService.TriageResult preview(@PathVariable Long ticketId) {
        CurrentUser.requireSiUser();
        return triage.triage(ticket(ticketId));
    }

    /** 트리아지 결과를 적용 (카테고리·우선순위·담당자). 에스컬레이션이면 담당자는 건너뜀. */
    @PostMapping("/triage/apply")
    @Transactional
    public TriageService.TriageResult apply(@PathVariable Long ticketId) {
        AuthPrincipal p = CurrentUser.requireSiUser();
        Ticket t = ticket(ticketId);
        var r = triage.triage(t);

        if (r.categoryId() != null && !r.categoryId().equals(t.getCategoryId())) {
            String old = str(t.getCategoryId());
            t.setCategoryId(r.categoryId());
            histories.save(new TicketHistory(ticketId, "category", old, str(r.categoryId()), "USER", p.id()));
            events.record(ticketId, TicketEventType.CATEGORIZED, old, str(r.categoryId()), p);
        }
        if (t.getPriority() != r.priority()) {
            String old = t.getPriority().name();
            t.setPriority(r.priority());
            histories.save(new TicketHistory(ticketId, "priority", old, r.priority().name(), "USER", p.id()));
        }
        if (!r.escalate() && r.suggestedAssigneeId() != null
                && !r.suggestedAssigneeId().equals(t.getAssigneeId())) {
            Long old = t.getAssigneeId();
            t.setAssigneeId(r.suggestedAssigneeId());
            if (t.getStatus() == TicketStatus.RECEIVED) {
                t.setStatus(TicketStatus.IN_PROGRESS);
                if (t.getFirstRespondedAt() == null) t.setFirstRespondedAt(Instant.now());
            }
            histories.save(new TicketHistory(ticketId, "assignee", str(old), str(r.suggestedAssigneeId()), "USER", p.id()));
            events.record(ticketId, TicketEventType.ASSIGNED, str(old), str(r.suggestedAssigneeId()), p);
            notifications.notifyUser(r.suggestedAssigneeId(), NotificationType.TICKET_ASSIGNED,
                    "티켓 배정(트리아지): #" + ticketId, t.getTitle(), ticketId);
        }
        tickets.save(t);
        return r;
    }

    @GetMapping("/sla-risk")
    public SlaRiskService.Risk slaRisk(@PathVariable Long ticketId) {
        CurrentUser.requireSiUser();
        return slaRisk.assess(ticket(ticketId));
    }

    private Ticket ticket(Long id) {
        return tickets.findById(id).orElseThrow(() -> ApiException.notFound("티켓"));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
