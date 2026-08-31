package com.smartdesk.feature.contract;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.Contract;
import com.smartdesk.domain.Enums.ContractStatus;
import com.smartdesk.domain.Enums.TicketStatus;
import com.smartdesk.domain.SystemAsset;
import com.smartdesk.repo.*;
import com.smartdesk.security.CurrentUser;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** REQ-F-005 / REQ-F-006. 온보딩·오프보딩 절차 포함. */
@RestController
@RequestMapping("/api")
public class ContractController {

    private final ContractRepo contracts;
    private final ClientRepo clients;
    private final SystemAssetRepo systems;
    private final TicketRepo tickets;
    private final com.smartdesk.feature.audit.AuditService audit;

    public ContractController(ContractRepo contracts, ClientRepo clients, SystemAssetRepo systems, TicketRepo tickets,
                              com.smartdesk.feature.audit.AuditService audit) {
        this.contracts = contracts;
        this.clients = clients;
        this.systems = systems;
        this.tickets = tickets;
        this.audit = audit;
    }

    public record ContractView(Long id, Long clientId, LocalDate startDate, LocalDate endDate,
                               Integer slaResponseMin, Integer slaResolutionMin,
                               String maintenanceScope, String status, boolean validNow) {}

    public record UpsertContractRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            Integer slaResponseMin,
            Integer slaResolutionMin,
            String maintenanceScope) {}

    public record OnboardingRequest(List<SystemInput> systems, List<String> checklist) {}
    public record SystemInput(String name, String type) {}
    public record OnboardingResult(String message, int systemsCreated) {}

    @GetMapping("/clients/{clientId}/contracts")
    public List<ContractView> list(@PathVariable Long clientId) {
        CurrentUser.requireSiUser();
        return contracts.findByClientId(clientId).stream().map(this::toView).toList();
    }

    @PostMapping("/clients/{clientId}/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ContractView create(@PathVariable Long clientId, @RequestBody @jakarta.validation.Valid UpsertContractRequest req) {
        CurrentUser.requireSiUser();
        clients.findById(clientId).orElseThrow(() -> ApiException.notFound("고객사"));
        if (req.endDate().isBefore(req.startDate())) throw ApiException.badRequest("종료일이 시작일보다 빠를 수 없습니다.");
        Contract c = new Contract();
        c.setClientId(clientId);
        apply(c, req);
        c.setStatus(deriveStatus(c));
        return toView(contracts.save(c));
    }

    /** REQ-E-006: 이미 발급된 열린 티켓의 sla_due_at 은 소급 변경하지 않음 (여기서 티켓을 건드리지 않음). */
    @PutMapping("/contracts/{contractId}")
    @Transactional
    public ContractView update(@PathVariable Long contractId, @RequestBody @jakarta.validation.Valid UpsertContractRequest req) {
        CurrentUser.requireSiUser();
        Contract c = contracts.findById(contractId).orElseThrow(() -> ApiException.notFound("계약"));
        if (req.endDate().isBefore(req.startDate())) throw ApiException.badRequest("종료일이 시작일보다 빠를 수 없습니다.");
        apply(c, req);
        c.setStatus(deriveStatus(c));
        return toView(contracts.save(c));
    }

    /** REQ-F-006: 신규 계약 시 시스템·계정 초기설정. */
    @PostMapping("/contracts/{contractId}/onboarding")
    @Transactional
    public OnboardingResult onboarding(@PathVariable Long contractId, @RequestBody(required = false) OnboardingRequest req) {
        CurrentUser.requireSiUser();
        Contract c = contracts.findById(contractId).orElseThrow(() -> ApiException.notFound("계약"));
        int created = 0;
        if (req != null && req.systems() != null) {
            for (SystemInput s : req.systems()) {
                if (s.name() == null || s.name().isBlank()) continue;
                SystemAsset a = new SystemAsset();
                a.setClientId(c.getClientId());
                a.setName(s.name().trim());
                a.setType(s.type());
                systems.save(a);
                created++;
            }
        }
        c.setStatus(deriveStatus(c));
        contracts.save(c);
        // NOTE: 고객사 담당자 계정 초기 발급은 별도 관리자 절차(스텁). README '보완 결정' 참고.
        return new OnboardingResult("온보딩이 완료되었습니다.", created);
    }

    /** REQ-F-006 + REQ-E-002: 미해결 티켓이 있으면 차단. 완료 시 계약 종료 + 자산 비활성화. */
    @PostMapping("/contracts/{contractId}/offboarding")
    @Transactional
    public OnboardingResult offboarding(@PathVariable Long contractId) {
        CurrentUser.requireSiUser();
        Contract c = contracts.findById(contractId).orElseThrow(() -> ApiException.notFound("계약"));
        List<com.smartdesk.domain.Ticket> open = tickets.findByClientIdAndStatusIn(
                c.getClientId(), List.of(TicketStatus.RECEIVED, TicketStatus.IN_PROGRESS));
        if (!open.isEmpty()) {
            throw ApiException.conflict("UNRESOLVED_TICKETS",
                    "해결되지 않은 티켓 " + open.size() + "건이 있습니다. 전건 종료 후 다시 시도하세요.");
        }
        c.setStatus(ContractStatus.ENDED);
        contracts.save(c);
        systems.findByClientIdAndActiveTrue(c.getClientId()).forEach(s -> { s.setActive(false); systems.save(s); });
        // 데이터 반환·파기 배치는 스텁 (README 참고)
        audit.record("CONTRACT_OFFBOARDED", "CONTRACT", c.getId(), "고객사 " + c.getClientId());
        return new OnboardingResult("오프보딩이 완료되었습니다. 신규 티켓 접수가 차단됩니다.", 0);
    }

    private void apply(Contract c, UpsertContractRequest r) {
        c.setStartDate(r.startDate());
        c.setEndDate(r.endDate());
        c.setSlaResponseMin(r.slaResponseMin());
        c.setSlaResolutionMin(r.slaResolutionMin());
        c.setMaintenanceScope(r.maintenanceScope());
    }

    private ContractStatus deriveStatus(Contract c) {
        LocalDate today = LocalDate.now();
        if (today.isAfter(c.getEndDate())) return ContractStatus.ENDED;
        if (!today.isBefore(c.getEndDate().minusDays(30))) return ContractStatus.EXPIRING;
        return ContractStatus.ACTIVE;
    }

    private ContractView toView(Contract c) {
        return new ContractView(c.getId(), c.getClientId(), c.getStartDate(), c.getEndDate(),
                c.getSlaResponseMin(), c.getSlaResolutionMin(), c.getMaintenanceScope(),
                c.getStatus().name(), c.isValidOn(LocalDate.now()));
    }
}
