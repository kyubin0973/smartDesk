package com.smartdesk.feature.user;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.AppUser;
import com.smartdesk.domain.Enums.Role;
import com.smartdesk.feature.ticket.AssignmentService;
import com.smartdesk.repo.*;
import com.smartdesk.security.AuthPrincipal;
import com.smartdesk.security.CurrentUser;
import com.smartdesk.security.PasswordHasher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REQ-F-003 프로필/부서 + SI 직원 관리(관리자). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AppUserRepo users;
    private final DepartmentRepo departments;
    private final ClientRepo clients;
    private final AssignedClientRepo assignedClients;
    private final PasswordHasher hasher;
    private final AssignmentService assignment;
    private final RefreshTokenRepo refreshTokens;
    private final com.smartdesk.feature.audit.AuditService audit;

    public UserController(AppUserRepo users, DepartmentRepo departments, ClientRepo clients,
                          AssignedClientRepo assignedClients, PasswordHasher hasher,
                          AssignmentService assignment, RefreshTokenRepo refreshTokens,
                          com.smartdesk.feature.audit.AuditService audit) {
        this.users = users;
        this.departments = departments;
        this.clients = clients;
        this.audit = audit;
        this.assignedClients = assignedClients;
        this.hasher = hasher;
        this.assignment = assignment;
        this.refreshTokens = refreshTokens;
    }

    public record MeResponse(Long id, String name, String email, String role,
                             Long departmentId, String departmentName, List<ClientBrief> assignedClients) {}
    public record ClientBrief(Long id, String name) {}
    public record UpdateMeRequest(String name, Long departmentId) {}
    public record UserRow(Long id, String name, String email, String role, Long departmentId, boolean active) {}
    public record CreateUserRequest(@NotBlank String name, @Email @NotBlank String email,
                                    @NotBlank String password, String role, Long departmentId,
                                    List<Long> clientIds) {}
    public record DeactivateResult(String message, int reassignedTickets) {}

    @GetMapping("/me")
    public MeResponse me() {
        AuthPrincipal p = CurrentUser.requireSiUser();
        AppUser u = users.findById(p.id()).orElseThrow(() -> ApiException.notFound("사용자"));
        String deptName = u.getDepartmentId() == null ? null
                : departments.findById(u.getDepartmentId()).map(d -> d.getName()).orElse(null);
        List<ClientBrief> assigned = assignedClients.findClientIdsByUserId(u.getId()).stream()
                .map(cid -> clients.findById(cid).map(c -> new ClientBrief(c.getId(), c.getName())).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new MeResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name(),
                u.getDepartmentId(), deptName, assigned);
    }

    @PutMapping("/me")
    @Transactional
    public MeResponse updateMe(@RequestBody UpdateMeRequest req) {
        AuthPrincipal p = CurrentUser.requireSiUser();
        AppUser u = users.findById(p.id()).orElseThrow(() -> ApiException.notFound("사용자"));
        if (req.name() != null && !req.name().isBlank()) u.setName(req.name().trim());
        if (req.departmentId() != null) {
            departments.findById(req.departmentId()).orElseThrow(() -> ApiException.badRequest("존재하지 않는 부서입니다."));
            u.setDepartmentId(req.departmentId());
        }
        users.save(u);
        return me();
    }

    /** 담당자 배정 드롭다운 등. SI 직원만. */
    @GetMapping
    public List<UserRow> list(@RequestParam(required = false) Long departmentId,
                              @RequestParam(defaultValue = "true") boolean activeOnly) {
        CurrentUser.requireSiUser();
        return users.findAll().stream()
                .filter(u -> !activeOnly || u.isActive())
                .filter(u -> departmentId == null || departmentId.equals(u.getDepartmentId()))
                .map(u -> new UserRow(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.getDepartmentId(), u.isActive()))
                .toList();
    }

    /** SI 직원 계정 생성 (관리자). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserRow create(@RequestBody @Valid CreateUserRequest req) {
        CurrentUser.requireManager();
        users.findByEmail(req.email()).ifPresent(x -> { throw ApiException.conflict("EMAIL_TAKEN", "이미 사용 중인 이메일입니다."); });
        AppUser u = new AppUser();
        u.setName(req.name().trim());
        u.setEmail(req.email().trim().toLowerCase());
        u.setPasswordHash(hasher.hash(req.password()));
        u.setRole("MANAGER".equalsIgnoreCase(req.role()) ? Role.MANAGER : Role.AGENT);
        if (req.departmentId() != null) {
            departments.findById(req.departmentId()).orElseThrow(() -> ApiException.badRequest("존재하지 않는 부서입니다."));
            u.setDepartmentId(req.departmentId());
        }
        u = users.save(u);
        if (req.clientIds() != null) {
            for (Long cid : req.clientIds()) {
                clients.findById(cid).orElseThrow(() -> ApiException.badRequest("존재하지 않는 고객사입니다."));
                assignedClients.save(new com.smartdesk.domain.UserClient(u.getId(), cid));
            }
        }
        audit.record("USER_CREATED", "USER", u.getId(), u.getEmail() + " (" + u.getRole() + ")");
        return new UserRow(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.getDepartmentId(), u.isActive());
    }

    /** REQ-E-003: 퇴사/부서이동 → 비활성화 + 열린 티켓 재배정 + 토큰 폐기. */
    @PatchMapping("/{userId}/deactivate")
    @Transactional
    public DeactivateResult deactivate(@PathVariable Long userId) {
        AuthPrincipal p = CurrentUser.requireManager();
        if (p.id().equals(userId)) throw ApiException.badRequest("본인 계정은 비활성화할 수 없습니다.");
        AppUser u = users.findById(userId).orElseThrow(() -> ApiException.notFound("사용자"));
        u.setActive(false);
        users.save(u);
        int reassigned = assignment.reassignOpenTickets(userId);
        refreshTokens.revokeAllFor("USER", userId);
        audit.record("USER_DEACTIVATED", "USER", userId, u.getEmail() + " · 티켓 " + reassigned + "건 재배정");
        return new DeactivateResult("비활성화 완료. 열린 티켓 " + reassigned + "건 재배정.", reassigned);
    }
}
