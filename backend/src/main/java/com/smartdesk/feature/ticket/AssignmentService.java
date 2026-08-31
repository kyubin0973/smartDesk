package com.smartdesk.feature.ticket;

import com.smartdesk.domain.AppUser;
import com.smartdesk.domain.Enums.Role;
import com.smartdesk.domain.Enums.TicketEventType;
import com.smartdesk.domain.Enums.TicketStatus;
import com.smartdesk.domain.Ticket;
import com.smartdesk.domain.TicketHistory;
import com.smartdesk.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * REQ-F-010: 담당자 자동배정 (규칙 기반 라우팅).
 * 순서: category_routing 의 부서 → 그 부서 소속이면서 해당 고객사 담당(user_client)인 활성 AGENT 중
 *       열린 티켓이 가장 적은 사람 → 없으면 해당 고객사 담당 활성 직원 아무나 → 없으면 부서 관리자.
 * REQ-E-003: 담당자 퇴사/부서이동 시 reassignOpenTickets 로 부서 관리자에게 재배정.
 */
@Service
public class AssignmentService {

    private final CategoryRoutingRepo routing;
    private final AppUserRepo users;
    private final AssignedClientRepo assignedClients;
    private final TicketRepo tickets;
    private final TicketHistoryRepo histories;
    private final TicketEventService eventLog;

    public AssignmentService(CategoryRoutingRepo routing, AppUserRepo users,
                             AssignedClientRepo assignedClients, TicketRepo tickets,
                             TicketHistoryRepo histories, TicketEventService eventLog) {
        this.routing = routing;
        this.users = users;
        this.assignedClients = assignedClients;
        this.tickets = tickets;
        this.histories = histories;
        this.eventLog = eventLog;
    }

    public Optional<Long> autoAssign(Ticket ticket) {
        List<Long> clientUserIds = assignedClients.findUserIdsByClientId(ticket.getClientId());
        Long deptId = ticket.getCategoryId() == null ? null
                : routing.findById(ticket.getCategoryId()).map(r -> r.getDepartmentId()).orElse(null);

        List<AppUser> candidates = users.findAllById(clientUserIds).stream()
                .filter(AppUser::isActive)
                .filter(u -> u.getRole() == Role.AGENT)
                .filter(u -> deptId == null || deptId.equals(u.getDepartmentId()))
                .toList();

        if (candidates.isEmpty() && deptId != null) {
            // 고객사 담당 지정이 없으면 부서 전체 활성 AGENT 로 확장
            candidates = users.findByRoleAndDepartmentIdAndActiveTrue(Role.AGENT, deptId);
        }
        if (candidates.isEmpty()) {
            // 그래도 없으면 고객사 담당 직원 아무나
            candidates = users.findAllById(clientUserIds).stream().filter(AppUser::isActive).toList();
        }
        if (candidates.isEmpty() && deptId != null) {
            return users.findByRoleAndDepartmentIdAndActiveTrue(Role.MANAGER, deptId).stream()
                    .findFirst().map(AppUser::getId);
        }
        return candidates.stream()
                .min(Comparator.comparingLong(u -> openTicketCount(u.getId())))
                .map(AppUser::getId);
    }

    public Optional<Long> reassignTarget(Long leavingUserId) {
        return users.findById(leavingUserId)
                .filter(u -> u.getDepartmentId() != null)
                .flatMap(u -> users.findByRoleAndDepartmentIdAndActiveTrue(Role.MANAGER, u.getDepartmentId())
                        .stream().findFirst())
                .map(AppUser::getId);
    }

    /**
     * REQ-E-003: 담당자 퇴사/부서이동 시 배정된 열린 티켓을 부서 관리자(없으면 자동배정)에게 재배정.
     * 담당자 이력은 ticket_history / ticket_event 에 유지.
     * @return 재배정된 티켓 수
     */
    @Transactional
    public int reassignOpenTickets(Long leavingUserId) {
        Long fallback = reassignTarget(leavingUserId).orElse(null);
        List<Ticket> open = tickets.findByAssigneeIdAndStatusIn(leavingUserId,
                List.of(TicketStatus.RECEIVED, TicketStatus.IN_PROGRESS));
        int n = 0;
        for (Ticket t : open) {
            Long target = fallback != null ? fallback : autoAssignExcluding(t, leavingUserId).orElse(null);
            t.setAssigneeId(target);
            tickets.save(t);
            histories.save(new TicketHistory(t.getId(), "assignee",
                    String.valueOf(leavingUserId), target == null ? null : String.valueOf(target), "SYSTEM", null));
            eventLog.recordSystem(t.getId(), TicketEventType.ASSIGNED,
                    String.valueOf(leavingUserId), target == null ? null : String.valueOf(target));
            n++;
        }
        return n;
    }

    private Optional<Long> autoAssignExcluding(Ticket t, Long excludeUserId) {
        return autoAssign(t).filter(id -> !id.equals(excludeUserId));
    }

    private long openTicketCount(Long userId) {
        return tickets.findByAssigneeIdAndStatusIn(userId,
                List.of(TicketStatus.RECEIVED, TicketStatus.IN_PROGRESS)).size();
    }
}
