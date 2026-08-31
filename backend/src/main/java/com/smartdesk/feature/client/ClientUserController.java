package com.smartdesk.feature.client;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.ClientUser;
import com.smartdesk.repo.*;
import com.smartdesk.security.CurrentUser;
import com.smartdesk.security.PasswordHasher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REQ-F-006 온보딩: 고객사 담당자 계정 발급.
 * REQ-E-004: 계정 비활성화.
 */
@RestController
@RequestMapping("/api")
public class ClientUserController {

    private final ClientUserRepo clientUsers;
    private final ClientRepo clients;
    private final RefreshTokenRepo refreshTokens;
    private final PasswordHasher hasher;
    private final com.smartdesk.feature.audit.AuditService audit;

    public ClientUserController(ClientUserRepo clientUsers, ClientRepo clients,
                                RefreshTokenRepo refreshTokens, PasswordHasher hasher,
                                com.smartdesk.feature.audit.AuditService audit) {
        this.clientUsers = clientUsers;
        this.clients = clients;
        this.refreshTokens = refreshTokens;
        this.hasher = hasher;
        this.audit = audit;
    }

    public record ClientUserRow(Long id, Long clientId, String name, String email, boolean active) {}
    public record CreateClientUserRequest(@NotBlank String name, @Email @NotBlank String email, @NotBlank String password) {}

    @GetMapping("/clients/{clientId}/users")
    public List<ClientUserRow> list(@PathVariable Long clientId) {
        CurrentUser.requireSiUser();
        return clientUsers.findByClientId(clientId).stream()
                .map(cu -> new ClientUserRow(cu.getId(), cu.getClientId(), cu.getName(), cu.getEmail(), cu.isActive()))
                .toList();
    }

    @PostMapping("/clients/{clientId}/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ClientUserRow create(@PathVariable Long clientId, @RequestBody @Valid CreateClientUserRequest req) {
        CurrentUser.requireManager();
        clients.findById(clientId).orElseThrow(() -> ApiException.notFound("고객사"));
        clientUsers.findByEmail(req.email()).ifPresent(x -> {
            throw ApiException.conflict("EMAIL_TAKEN", "이미 사용 중인 이메일입니다.");
        });
        ClientUser cu = new ClientUser();
        cu.setClientId(clientId);
        cu.setName(req.name().trim());
        cu.setEmail(req.email().trim().toLowerCase());
        cu.setPasswordHash(hasher.hash(req.password()));
        cu = clientUsers.save(cu);
        audit.record("CLIENT_USER_CREATED", "CLIENT_USER", cu.getId(), cu.getEmail() + " · 고객사 " + clientId);
        return new ClientUserRow(cu.getId(), cu.getClientId(), cu.getName(), cu.getEmail(), cu.isActive());
    }

    /** REQ-E-004: 기존 티켓/이력은 유지, 로그인만 차단. */
    @PatchMapping("/client-users/{id}/deactivate")
    @Transactional
    public ClientUserRow deactivate(@PathVariable Long id) {
        CurrentUser.requireManager();
        ClientUser cu = clientUsers.findById(id).orElseThrow(() -> ApiException.notFound("담당자"));
        cu.setActive(false);
        clientUsers.save(cu);
        refreshTokens.revokeAllFor("CLIENT_USER", id);
        audit.record("CLIENT_USER_DEACTIVATED", "CLIENT_USER", id, cu.getEmail());
        return new ClientUserRow(cu.getId(), cu.getClientId(), cu.getName(), cu.getEmail(), cu.isActive());
    }
}
