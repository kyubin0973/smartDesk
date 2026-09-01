package com.smartdesk.feature.ticket;

import com.smartdesk.domain.AppUser;
import com.smartdesk.domain.Enums.NotificationType;
import com.smartdesk.domain.Enums.Role;
import com.smartdesk.domain.Enums.TicketEventType;
import com.smartdesk.domain.Enums.TicketStatus;
import com.smartdesk.domain.Ticket;
import com.smartdesk.feature.notification.NotificationService;
import com.smartdesk.repo.AppUserRepo;
import com.smartdesk.repo.CategoryRoutingRepo;
import com.smartdesk.repo.TicketRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * REQ-F-011: SLA 처리 시한 초과 알림 + 임박 경고 로직.
 * 티켓 단위로 처리 — 잡 전체를 하나의 트랜잭션으로 묶지 않는다.
 *
 * 0.5-h 다단계 에스컬레이션: 초과 시간이 길어질수록 알림 대상이 넓어진다.
 * 알림 중복은 notification 의 (recipient, type, ticket) 유니크 인덱스로 자연 방지되므로
 * 별도 "에스컬레이션 레벨" 컬럼 없이, 매 스캔에서 레벨에 맞는 대상 집합을 계산해 보낸다.
 */
@Service
public class SlaMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitorService.class);
    static final Duration DUE_SOON = Duration.ofHours(2);
    static final Duration RISK_HORIZON = Duration.ofHours(8);   // 단계 3.2: 위반 예측 관찰 창

    private final TicketRepo tickets;
    private final AppUserRepo users;
    private final CategoryRoutingRepo routing;
    private final NotificationService notifications;
    private final TicketEventService events;
    private final com.smartdesk.feature.triage.SlaRiskService slaRisk;
    private final com.smartdesk.feature.triage.TriageProperties triageProps;
    private final Duration l2After;
    private final Duration l3After;

    public SlaMonitorService(TicketRepo tickets, AppUserRepo users, CategoryRoutingRepo routing,
                             NotificationService notifications, TicketEventService events,
                             com.smartdesk.feature.triage.SlaRiskService slaRisk,
                             com.smartdesk.feature.triage.TriageProperties triageProps,
                             @Value("${smartdesk.sla.escalation-l2-minutes:60}") long l2Minutes,
                             @Value("${smartdesk.sla.escalation-l3-minutes:240}") long l3Minutes) {
        this.tickets = tickets;
        this.users = users;
        this.routing = routing;
        this.notifications = notifications;
        this.events = events;
        this.slaRisk = slaRisk;
        this.triageProps = triageProps;
        this.l2After = Duration.ofMinutes(l2Minutes);
        this.l3After = Duration.ofMinutes(l3Minutes);
    }

    /** @return [breached, dueSoon, atRisk] 처리 건수 */
    public int[] scan() {
        Instant now = Instant.now();
        List<Ticket> horizon = tickets.findByStatusInAndSlaDueAtIsNotNullAndSlaDueAtBefore(
                List.of(TicketStatus.RECEIVED, TicketStatus.IN_PROGRESS),
                now.plus(RISK_HORIZON));
        int breached = 0, soon = 0, risky = 0;
        for (Ticket t : horizon) {
            try {
                if (t.getSlaDueAt().isBefore(now)) { handleBreached(t, now); breached++; }
                else if (t.getSlaDueAt().isBefore(now.plus(DUE_SOON))) { handleDueSoon(t); soon++; }
                else if (handleAtRisk(t)) { risky++; }
            } catch (Exception e) {
                log.warn("[sla-monitor] 티켓 #{} 처리 중 오류: {}", t.getId(), e.toString());
            }
        }
        if (breached + soon + risky > 0) {
            log.info("[sla-monitor] breached={} dueSoon={} atRisk={}", breached, soon, risky);
        }
        return new int[]{breached, soon, risky};
    }

    /** 단계 3.2: 아직 임박 전이지만 위반 위험이 높은 티켓 → 담당자·관리자에게 사전 경고. */
    private boolean handleAtRisk(Ticket t) {
        var risk = slaRisk.assess(t);
        if (risk.score() < triageProps.getSlaRiskThreshold()) return false;
        String body = t.getTitle() + " — SLA 위반 위험 " + Math.round(risk.score() * 100) + "%"
                + (risk.factors().isEmpty() ? "" : " (" + String.join(", ", risk.factors()) + ")")
                + (risk.suggestReassign() ? " · 재배정 검토 권장" : "");
        for (Long uid : risk.suggestReassign() ? breachRecipients(t, 2) : baseRecipients(t)) {
            notifications.notifyUser(uid, NotificationType.SLA_AT_RISK,
                    "SLA 위험: #" + t.getId(), body, t.getId());
        }
        return true;
    }

    private void handleBreached(Ticket t, Instant now) {
        Duration overdue = Duration.between(t.getSlaDueAt(), now);
        int level = overdue.compareTo(l3After) >= 0 ? 3 : overdue.compareTo(l2After) >= 0 ? 2 : 1;
        String suffix = level >= 2 ? " (에스컬레이션 L" + level + ", " + overdue.toHours() + "시간 초과)" : "";
        String body = t.getTitle() + " — 처리 시한을 넘겼습니다." + suffix;
        for (Long uid : breachRecipients(t, level)) {
            notifications.notifyUser(uid, NotificationType.SLA_BREACHED, "SLA 초과: #" + t.getId(), body, t.getId());
        }
        events.recordSystemOnce(t.getId(), TicketEventType.SLA_BREACHED, null, t.getSlaDueAt().toString());
    }

    private void handleDueSoon(Ticket t) {
        String body = t.getTitle() + " — 2시간 내 처리 시한입니다.";
        for (Long uid : baseRecipients(t)) {
            notifications.notifyUser(uid, NotificationType.SLA_DUE_SOON, "SLA 임박: #" + t.getId(), body, t.getId());
        }
    }

    /** L1: 담당자(없으면 부서 관리자). L2: + 부서 관리자. L3: + 전체 관리자. */
    private Set<Long> breachRecipients(Ticket t, int level) {
        Set<Long> ids = new LinkedHashSet<>(baseRecipients(t));
        if (level >= 2) deptManagers(t).forEach(u -> ids.add(u.getId()));
        if (level >= 3) users.findByRoleAndActiveTrue(Role.MANAGER).forEach(u -> ids.add(u.getId()));
        return ids;
    }

    private List<Long> baseRecipients(Ticket t) {
        if (t.getAssigneeId() != null) return List.of(t.getAssigneeId());
        List<AppUser> m = deptManagers(t);
        if (m.isEmpty()) m = users.findByRoleAndActiveTrue(Role.MANAGER);
        return m.stream().map(AppUser::getId).toList();
    }

    private List<AppUser> deptManagers(Ticket t) {
        Long deptId = t.getCategoryId() == null ? null
                : routing.findById(t.getCategoryId()).map(r -> r.getDepartmentId()).orElse(null);
        return deptId != null ? users.findByRoleAndDepartmentIdAndActiveTrue(Role.MANAGER, deptId) : List.of();
    }
}
