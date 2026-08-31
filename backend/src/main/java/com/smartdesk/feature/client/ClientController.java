package com.smartdesk.feature.client;

import com.smartdesk.common.ApiException;
import com.smartdesk.common.PageResponse;
import com.smartdesk.domain.Client;
import com.smartdesk.domain.Contract;
import com.smartdesk.repo.*;
import com.smartdesk.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

/** REQ-F-004: 고객사 등록/관리. SI 직원만 접근 (REQ-N-002). */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientRepo clients;
    private final ContractRepo contracts;
    private final AssignedClientRepo assigned;
    private final AppUserRepo users;

    public ClientController(ClientRepo clients, ContractRepo contracts, AssignedClientRepo assigned, AppUserRepo users) {
        this.clients = clients;
        this.contracts = contracts;
        this.assigned = assigned;
        this.users = users;
    }

    public record ClientSummary(Long id, String name, boolean active, String contractStatus, List<String> assignees) {}
    public record CreateClientRequest(@NotBlank String name) {}
    public record UpdateClientRequest(String name) {}

    @GetMapping
    public PageResponse<ClientSummary> list(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        CurrentUser.requireSiUser();
        String needle = (q == null) ? "" : q.trim().toLowerCase();
        String wantStatus = (status == null || status.isBlank()) ? null : status.trim().toUpperCase();

        List<ClientSummary> all = clients.findAll(Sort.by("name")).stream()
                .filter(c -> needle.isEmpty() || c.getName().toLowerCase().contains(needle))
                .map(this::toSummary)
                .filter(s -> wantStatus == null || wantStatus.equals(s.contractStatus()))
                .toList();

        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = (int) Math.ceil(all.size() / (double) size);
        return new PageResponse<>(all.subList(from, to), page, size, all.size(), Math.max(totalPages, 1));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ClientSummary create(@RequestBody @jakarta.validation.Valid CreateClientRequest req) {
        CurrentUser.requireSiUser();
        Client c = new Client();
        c.setName(req.name().trim());
        return toSummary(clients.save(c));
    }

    @GetMapping("/{clientId}")
    public ClientSummary detail(@PathVariable Long clientId) {
        CurrentUser.requireSiUser();
        return toSummary(clients.findById(clientId).orElseThrow(() -> ApiException.notFound("고객사")));
    }

    @PutMapping("/{clientId}")
    @Transactional
    public ClientSummary update(@PathVariable Long clientId, @RequestBody UpdateClientRequest req) {
        CurrentUser.requireSiUser();
        Client c = clients.findById(clientId).orElseThrow(() -> ApiException.notFound("고객사"));
        if (req.name() != null && !req.name().isBlank()) c.setName(req.name().trim());
        return toSummary(clients.save(c));
    }

    private ClientSummary toSummary(Client c) {
        // 계약상태는 enum name 으로 통일 (프론트에서 현지화). DashboardController 와 일관.
        String contractStatus = contracts.findByClientId(c.getId()).stream()
                .max(Comparator.comparing(Contract::getEndDate))
                .map(ct -> ct.getStatus().name())
                .orElse("NONE");
        List<String> assignees = assigned.findUserIdsByClientId(c.getId()).stream()
                .map(uid -> users.findById(uid).map(u -> u.getName()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new ClientSummary(c.getId(), c.getName(), c.isActive(), contractStatus, assignees);
    }
}
