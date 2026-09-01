package com.smartdesk.feature.auth;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.RefreshToken;
import com.smartdesk.repo.RefreshTokenRepo;
import com.smartdesk.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** REQ-F-001 / REQ-F-002 / REQ-E-009 / C9(비밀번호 재설정·변경). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final PasswordService passwords;
    private final RefreshTokenRepo refreshTokens;

    public AuthController(AuthService auth, PasswordService passwords, RefreshTokenRepo refreshTokens) {
        this.auth = auth;
        this.passwords = passwords;
        this.refreshTokens = refreshTokens;
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record ForgotPasswordRequest(@Email @NotBlank String email, String principalType) {}
    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    @PostMapping("/login")
    public AuthService.Tokens login(@RequestBody @Valid LoginRequest req, HttpServletRequest http) {
        return auth.loginSiUser(req.email(), req.password(), clientIp(http));
    }

    @PostMapping("/client-login")
    public AuthService.Tokens clientLogin(@RequestBody @Valid LoginRequest req, HttpServletRequest http) {
        return auth.loginClientUser(req.email(), req.password(), clientIp(http));
    }

    @PostMapping("/refresh")
    public AuthService.Tokens refresh(@RequestBody @Valid RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest req) {
        var principal = safePrincipal();
        auth.logout(principal, req == null ? null : req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** C9: 재설정 메일 발송 요청. 계정 존재 여부를 노출하지 않기 위해 항상 200. */
    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@RequestBody @Valid ForgotPasswordRequest req) {
        var devToken = passwords.requestReset(req.email().trim().toLowerCase(), req.principalType());
        Map<String, Object> res = new HashMap<>();
        res.put("message", "등록된 계정이면 재설정 메일을 보냈습니다.");
        devToken.ifPresent(t -> res.put("devResetToken", t)); // 운영(prod)에선 미노출
        return res;
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest req) {
        passwords.reset(req.token(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** 로그인 상태에서 비밀번호 변경. (인증 필요 — 코드에서 강제) */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody @Valid ChangePasswordRequest req) {
        passwords.change(CurrentUser.get(), req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    // 0.5-g: 세션(리프레시 토큰) 관리
    public record SessionView(Long id, Instant createdAt, Instant expiresAt, boolean revoked) {}

    @GetMapping("/sessions")
    public List<SessionView> sessions() {
        var p = CurrentUser.get();
        return refreshTokens.findByPrincipalTypeAndPrincipalIdOrderByCreatedAtDesc(p.type().name(), p.id()).stream()
                .map(rt -> new SessionView(rt.getId(), rt.getCreatedAt(), rt.getExpiresAt(), rt.isRevoked()))
                .toList();
    }

    @DeleteMapping("/sessions/{id}")
    @Transactional
    public ResponseEntity<Void> revokeSession(@PathVariable Long id) {
        var p = CurrentUser.get();
        RefreshToken rt = refreshTokens.findById(id).orElseThrow(() -> ApiException.notFound("세션"));
        if (!rt.getPrincipalType().equals(p.type().name()) || !rt.getPrincipalId().equals(p.id())) {
            throw ApiException.forbidden("본인 세션이 아닙니다.");
        }
        rt.setRevoked(true);
        refreshTokens.save(rt);
        return ResponseEntity.noContent().build();
    }

    /** 모든 세션 로그아웃 (다른 기기 포함). 현재 액세스 토큰도 폐기. */
    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeAllSessions(@RequestBody(required = false) RefreshRequest req) {
        var p = CurrentUser.get();
        auth.revokeAllSessions(p);
        return ResponseEntity.noContent().build();
    }

    private com.smartdesk.security.AuthPrincipal safePrincipal() {
        try { return CurrentUser.get(); } catch (Exception e) { return null; }
    }

    private String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}
