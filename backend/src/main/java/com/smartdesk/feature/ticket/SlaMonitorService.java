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
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * REQ-F-011: SLA 처리 시한 초과 알림 + 임박 경고 로직.
 * 티켓 단위로 처리 — 잡 전체를 하나의 트랜잭션으로 묶지 않는다 (1건 실패가 전체 재알림을 유발하지 않도록).
 * 스케줄 트리거는 SlaMonitorJob (test 프로파일에서 비활성).
 */
@Service
public class SlaMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitorService.class);
    static final Duration DUE_SOON = Duration.ofHours(2);

    private final TicketRepo tickets;
    private final AppUserRepo users;
    private final CategoryRoutingRepo routing;
    private final NotificationService notifications;
    private final TicketEventService events;

    public SlaMonitorService(TicketRepo tickets, AppUserRepo users, CategoryRoutingRepo routing,
                             NotificationService notifications, TicketEventService events) {
        this.tickets = tickets;
        this.users = users;
        this.routing = routing;
        this.notifications = notifications;
        this.events = events;
    }

    /** @return [breached, dueSoon] 처리 건수 */
    public int[] scan() {
        Instant now = Instant.now();
        List<Ticket> atRisk = tickets.findByStatusInAndSlaDueAtIsNotNullAndSlaDueAtBefore(
                List.of(TicketStatus.RECEIVED, TicketStatus.IN_PROGRESS),
                now.plus(DUE_SOON));
        int breached = 0, soon = 0;
        for (Ticket t : atRisk) {
            try {
                if (t.getSlaDueAt().isBefore(now)) { handleBreached(t); breached++; }
                else { handleDueSoon(t); soon++; }
            } catch (Exception e) {
                log.warn("[sla-monitor] 티켓 #{} 처리 중 오류: {}", t.getId(), e.toString());
            }
        }
        if (breached + soon > 0) log.info("[sla-monitor] breached={} dueSoon={}", breached, soon);
        return new int[]{breached, soon};
    }

    private void handleBreached(Ticket t) {
        String body = t.getTitle() + " — 처리 시한을 넘겼습니다.";
        for (Long uid : recipients(t)) {
            notifications.notifyUser(uid, NotificationType.SLA_BREACHED, "SLA 초과: #" + t.getId(), body, t.getId());
        }
        events.recordSystemOnce(t.getId(), TicketEventType.SLA_BREACHED, null, t.getSlaDueAt().toString());
    }

    private void handleDueSoon(Ticket t) {
        String body = t.getTitle() + " — 2시간 내 처리 시한입니다.";
        for (Long uid : recipients(t)) {
            notifications.notifyUser(uid, NotificationType.SLA_DUE_SOON, "SLA 임박: #" + t.getId(), body, t.getId());
        }
    }

    /** 담당자가 있으면 담당자, 없으면 에스컬레이션(카테고리 부서 관리자 → 전체 관리자). */
    private List<Long> recipients(Ticket t) {
        if (t.getAssigneeId() != null) return List.of(t.getAssigneeId());
        Long deptId = t.getCategoryId() == null ? null
                : routing.findById(t.getCategoryId()).map(r -> r.getDepartmentId()).orElse(null);
        List<AppUser> managers = deptId != null
                ? users.findByRoleAndDepartmentIdAndActiveTrue(Role.MANAGER, deptId)
                : List.of();
        if (managers.isEmpty()) managers = users.findByRoleAndActiveTrue(Role.MANAGER);
        return managers.stream().map(AppUser::getId).toList();
    }
}
