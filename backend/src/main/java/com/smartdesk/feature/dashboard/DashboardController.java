package com.smartdesk.feature.dashboard;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.Contract;
import com.smartdesk.domain.Enums.TicketStatus;
import com.smartdesk.domain.Ticket;
import com.smartdesk.repo.*;
import com.smartdesk.security.AuthPrincipal;
import com.smartdesk.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

/** REQ-F-016: 고객사별 티켓 현황 + SLA 준수율. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final TicketRepo tickets;
    private final ContractRepo contracts;

    public DashboardController(TicketRepo tickets, ContractRepo contracts) {
        this.tickets = tickets;
        this.contracts = contracts;
    }

    public record DashboardResponse(Long clientId, String contractStatus,
                                    long totalTickets, long thisMonthTickets,
                                    long received, long inProgress, long resolved, long closed,
                                    long pendingApproval,   // 승인대기 = 해결(RESOLVED) 상태 (화면설계서 SCR-MAIN-001)
                                    long slaBreached, double slaComplianceRate,
                                    List<Row> recentTickets) {}
    public record Row(Long id, String title, String status, Long slaRemainingMinutes, boolean slaBreached) {}

    @GetMapping("/clients/{clientId}")
    public DashboardResponse forClient(@PathVariable Long clientId) {
        AuthPrincipal p = CurrentUser.get();
        if (p.isClientUser() && !p.clientId().equals(clientId)) {
            throw ApiException.forbidden("다른 고객사 대시보드는 조회할 수 없습니다."); // REQ-N-001
        }

        long total = tickets.countByClientId(clientId);
        long received = tickets.countByClientIdAndStatus(clientId, TicketStatus.RECEIVED);
        long inProgress = tickets.countByClientIdAndStatus(clientId, TicketStatus.IN_PROGRESS);
        long resolved = tickets.countByClientIdAndStatus(clientId, TicketStatus.RESOLVED);
        long closed = tickets.countByClientIdAndStatus(clientId, TicketStatus.CLOSED);

        long breached = tickets.countSlaBreached(clientId,
                List.of(TicketStatus.RECEIVED, TicketStatus.IN_PROGRESS), Instant.now());

        // 준수율: 종결(해결+종료) 티켓 중 resolved_at 이 마감시각 내인 비율. 데이터 없으면 100%.
        List<Ticket> done = tickets.findByClientIdAndStatusIn(clientId, List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED));
        long onTime = done.stream().filter(Ticket::isSlaMet).count();
        double compliance = done.isEmpty() ? 100.0 : Math.round(onTime * 10000.0 / done.size()) / 100.0;

        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long thisMonth = tickets.countByClientIdAndCreatedAtGreaterThanEqual(clientId, monthStart);

        String contractStatus = contracts.findByClientId(clientId).stream()
                .max(Comparator.comparing(Contract::getEndDate))
                .map(c -> c.getStatus().name()).orElse("NONE");

        List<Row> recent = tickets.findByClientId(clientId).stream()
                .sorted(Comparator.comparing(Ticket::getCreatedAt).reversed())
                .limit(5)
                .map(t -> {
                    Long remaining = t.getSlaDueAt() == null ? null
                            : Duration.between(Instant.now(), t.getSlaDueAt()).toMinutes();
                    return new Row(t.getId(), t.getTitle(), t.getStatus().name(), remaining,
                            remaining != null && remaining < 0);
                })
                .toList();

        return new DashboardResponse(clientId, contractStatus, total, thisMonth,
                received, inProgress, resolved, closed, resolved, breached, compliance, recent);
    }
}
