package com.smartdesk.feature.auth;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.*;
import com.smartdesk.domain.Enums.AuthorType;
import com.smartdesk.feature.audit.AuditService;
import com.smartdesk.repo.*;
import com.smartdesk.security.AuthPrincipal;
import com.smartdesk.security.JwtService;
import com.smartdesk.security.PasswordHasher;
import com.smartdesk.security.TokenRevocationRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final int MAX_EMAIL_FAILS = 5;
    private static final int MAX_IP_FAILS = 20;
    private static final long LOCK_MINUTES = 15;
    private static final long REFRESH_TTL_DAYS = 7;

    private final AppUserRepo users;
    private final ClientUserRepo clientUsers;
    private final LoginAttemptRepo attempts;
    private final RefreshTokenRepo refreshTokens;
    private final RevokedAccessTokenRepo revokedTokens;
    private final TokenRevocationRegistry revocationRegistry;
    private final PasswordHasher hasher;
    private final JwtService jwt;
    private final AuditService audit;
    private final SecureRandom rnd = new SecureRandom();

    public AuthService(AppUserRepo users, ClientUserRepo clientUsers, LoginAttemptRepo attempts,
                       RefreshTokenRepo refreshTokens, RevokedAccessTokenRepo revokedTokens,
                       TokenRevocationRegistry revocationRegistry, PasswordHasher hasher, JwtService jwt,
                       AuditService audit) {
        this.users = users;
        this.clientUsers = clientUsers;
        this.attempts = attempts;
        this.refreshTokens = refreshTokens;
        this.revokedTokens = revokedTokens;
        this.revocationRegistry = revocationRegistry;
        this.hasher = hasher;
        this.jwt = jwt;
        this.audit = audit;
    }

    public record Tokens(String accessToken, String refreshToken, String tokenType, long expiresIn, Object principal) {}
    public record PrincipalView(Long id, String name, String email, String role, Long clientId) {}

    // @Transactional 없음: 실패 시 throw 로 login_attempt 증가·감사 기록이 롤백되면 안 됨.
    // recordFail / clearFail / audit / issue 는 각자 트랜잭션 경계를 가진다.
    public Tokens loginSiUser(String email, String password, String ip) {
        guard(ip, "IP");
        guard(email, "USER");
        AppUser u = users.findByEmail(email).filter(AppUser::isActive).orElse(null);
        if (u == null || !hasher.matches(password, u.getPasswordHash())) {
            recordFail(email, "USER"); recordFail(ip, "IP");
            audit.recordAuth("LOGIN_FAILURE", "ANONYMOUS", null, email, ip, "SI 로그인 실패");
            throw ApiException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        clearFail(email, "USER");
        audit.recordAuth("LOGIN_SUCCESS", "USER", u.getId(), email, ip, null);
        var p = new AuthPrincipal(AuthorType.USER, u.getId(), u.getEmail(), u.getRole().name(), null);
        return issue(p, new PrincipalView(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), null), "USER", u.getId());
    }

    public Tokens loginClientUser(String email, String password, String ip) {
        guard(ip, "IP");
        guard(email, "CLIENT_USER");
        ClientUser cu = clientUsers.findByEmail(email).filter(ClientUser::isActive).orElse(null);
        if (cu == null || !hasher.matches(password, cu.getPasswordHash())) {
            recordFail(email, "CLIENT_USER"); recordFail(ip, "IP");
            audit.recordAuth("LOGIN_FAILURE", "ANONYMOUS", null, email, ip, "고객사 로그인 실패");
            throw ApiException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        clearFail(email, "CLIENT_USER");
        audit.recordAuth("LOGIN_SUCCESS", "CLIENT_USER", cu.getId(), email, ip, null);
        var p = new AuthPrincipal(AuthorType.CLIENT_USER, cu.getId(), cu.getEmail(), "CLIENT_USER", cu.getClientId());
        return issue(p, new PrincipalView(cu.getId(), cu.getName(), cu.getEmail(), "CLIENT_USER", cu.getClientId()),
                "CLIENT_USER", cu.getId());
    }

    @Transactional
    public Tokens refresh(String rawRefresh) {
        RefreshToken rt = refreshTokens.findByTokenHash(sha256(rawRefresh))
                .filter(RefreshToken::isUsable)
                .orElseThrow(() -> ApiException.unauthorized("리프레시 토큰이 유효하지 않습니다."));
        // 원자적 회전: 동시 요청 중 하나만 승자 (revokeIfActive == 1)
        if (refreshTokens.revokeIfActive(rt.getId()) != 1) {
            throw ApiException.unauthorized("리프레시 토큰이 이미 사용되었습니다.");
        }

        Object[] resolved = resolvePrincipal(rt.getPrincipalType(), rt.getPrincipalId());
        AuthPrincipal p = (AuthPrincipal) resolved[0];
        PrincipalView view = (PrincipalView) resolved[1];
        return issue(p, view, rt.getPrincipalType(), rt.getPrincipalId());
    }

    /** 0.5-g: 모든 세션 종료 (다른 기기 포함) + 현재 액세스 토큰 폐기. */
    @Transactional
    public void revokeAllSessions(AuthPrincipal current) {
        refreshTokens.revokeAllFor(current.type().name(), current.id());
        if (current.jti() != null && current.expiresAt() != null) {
            revokedTokens.save(new RevokedAccessToken(current.jti(), current.expiresAt()));
            revocationRegistry.add(current.jti());
        }
        audit.recordAuth("SESSIONS_REVOKED_ALL", current.type().name(), current.id(), current.email(), null, null);
    }

    @Transactional
    public void logout(AuthPrincipal current, String rawRefresh) {
        if (current != null) {
            audit.recordAuth("LOGOUT", current.type().name(), current.id(), current.email(), null, null);
        }
        if (current != null && current.jti() != null && current.expiresAt() != null) {
            revokedTokens.save(new RevokedAccessToken(current.jti(), current.expiresAt()));
            revocationRegistry.add(current.jti());   // 즉시 반영 (다른 인스턴스는 다음 reload 때)
        }
        if (rawRefresh != null && !rawRefresh.isBlank()) {
            refreshTokens.findByTokenHash(sha256(rawRefresh)).ifPresent(rt -> {
                rt.setRevoked(true);
                refreshTokens.save(rt);
            });
        }
    }

    // ---------- helpers ----------

    private Tokens issue(AuthPrincipal p, PrincipalView view, String principalType, Long principalId) {
        JwtService.IssuedToken access = jwt.issue(p);

        byte[] rb = new byte[32];
        rnd.nextBytes(rb);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(rb);
        RefreshToken rt = new RefreshToken();
        rt.setPrincipalType(principalType);
        rt.setPrincipalId(principalId);
        rt.setTokenHash(sha256(rawRefresh));
        rt.setExpiresAt(Instant.now().plus(java.time.Duration.ofDays(REFRESH_TTL_DAYS)));
        refreshTokens.save(rt);

        return new Tokens(access.token(), rawRefresh, "Bearer", jwt.ttlSeconds(), view);
    }

    private Object[] resolvePrincipal(String type, Long id) {
        if ("USER".equals(type)) {
            AppUser u = users.findById(id).filter(AppUser::isActive)
                    .orElseThrow(() -> ApiException.unauthorized("비활성 계정입니다."));
            return new Object[]{
                    new AuthPrincipal(AuthorType.USER, u.getId(), u.getEmail(), u.getRole().name(), null),
                    new PrincipalView(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), null)};
        }
        ClientUser cu = clientUsers.findById(id).filter(ClientUser::isActive)
                .orElseThrow(() -> ApiException.unauthorized("비활성 계정입니다."));
        return new Object[]{
                new AuthPrincipal(AuthorType.CLIENT_USER, cu.getId(), cu.getEmail(), "CLIENT_USER", cu.getClientId()),
                new PrincipalView(cu.getId(), cu.getName(), cu.getEmail(), "CLIENT_USER", cu.getClientId())};
    }

    private void guard(String key, String type) {
        if (key == null) return;
        attempts.findByEmailAndPrincipalType(key, type)
                .filter(LoginAttempt::isLocked)
                .ifPresent(a -> {
                    String msg = "IP".equals(type)
                            ? "이 네트워크에서 로그인 시도가 너무 많습니다. 잠시 후 다시 시도하세요."
                            : "로그인 시도가 5회 이상 실패하여 계정이 잠겼습니다. 15분 후 캡차 인증과 함께 다시 시도하세요.";
                    throw ApiException.conflict("LOCKED", msg);
                });
    }

    private void recordFail(String key, String type) {
        if (key == null) return;
        LoginAttempt a = attempts.findByEmailAndPrincipalType(key, type).orElseGet(() -> {
            LoginAttempt n = new LoginAttempt();
            n.setEmail(key);
            n.setPrincipalType(type);
            return n;
        });
        // 잠금이 이미 만료됐으면 카운터 리셋 후 다시 카운트
        if (a.getLockedUntil() != null && !a.isLocked()) {
            a.setFailCount(0);
            a.setLockedUntil(null);
        }
        a.setFailCount(a.getFailCount() + 1);
        a.setUpdatedAt(Instant.now());
        int limit = "IP".equals(type) ? MAX_IP_FAILS : MAX_EMAIL_FAILS;
        if (a.getFailCount() >= limit) {
            a.setLockedUntil(Instant.now().plusSeconds(LOCK_MINUTES * 60));
        }
        attempts.save(a);
    }

    private void clearFail(String key, String type) {
        attempts.findByEmailAndPrincipalType(key, type).ifPresent(a -> {
            a.setFailCount(0);
            a.setLockedUntil(null);
            a.setUpdatedAt(Instant.now());
            attempts.save(a);
        });
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
