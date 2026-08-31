package com.smartdesk.feature.auth;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.*;
import com.smartdesk.feature.audit.AuditService;
import com.smartdesk.repo.*;
import com.smartdesk.security.AuthPrincipal;
import com.smartdesk.security.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * C9: 비밀번호 재설정(이메일 토큰) + 로그인 상태에서 변경.
 * 토큰은 원문을 메일로만 보내고 DB 엔 SHA-256 해시만 저장. 재설정 시 해당 계정의 리프레시 토큰 전부 폐기.
 */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    private final PasswordResetTokenRepo tokens;
    private final AppUserRepo users;
    private final ClientUserRepo clientUsers;
    private final RefreshTokenRepo refreshTokens;
    private final PasswordHasher hasher;
    private final EmailSender email;
    private final AuditService audit;
    private final SecureRandom rnd = new SecureRandom();

    private final String resetUrlBase;
    private final Duration ttl;
    private final int minLength;
    private final boolean exposeToken;

    public PasswordService(PasswordResetTokenRepo tokens, AppUserRepo users, ClientUserRepo clientUsers,
                           RefreshTokenRepo refreshTokens, PasswordHasher hasher, EmailSender email, AuditService audit,
                           @Value("${smartdesk.password.reset-url-base:http://localhost:5173/reset-password}") String resetUrlBase,
                           @Value("${smartdesk.password.reset-ttl-minutes:30}") long ttlMinutes,
                           @Value("${smartdesk.password.min-length:8}") int minLength,
                           @Value("${smartdesk.password.expose-reset-token:true}") boolean exposeToken) {
        this.tokens = tokens;
        this.users = users;
        this.clientUsers = clientUsers;
        this.refreshTokens = refreshTokens;
        this.hasher = hasher;
        this.email = email;
        this.audit = audit;
        this.resetUrlBase = resetUrlBase;
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.minLength = minLength;
        this.exposeToken = exposeToken;
    }

    /** 재설정 요청. 계정 존재 여부를 노출하지 않기 위해 항상 정상 흐름. dev 에선 devToken 반환. */
    @Transactional
    public Optional<String> requestReset(String email, String principalType) {
        String type = "CLIENT_USER".equalsIgnoreCase(principalType) ? "CLIENT_USER" : "USER";
        Long principalId = "USER".equals(type)
                ? users.findByEmail(email).filter(AppUser::isActive).map(AppUser::getId).orElse(null)
                : clientUsers.findByEmail(email).filter(ClientUser::isActive).map(ClientUser::getId).orElse(null);
        if (principalId == null) {
            log.info("[password-reset] 존재하지 않거나 비활성 계정에 대한 요청: {} ({})", email, type);
            return Optional.empty();
        }

        tokens.invalidateActive(type, principalId, Instant.now());   // 이전 미사용 토큰 무효화

        byte[] rb = new byte[32];
        rnd.nextBytes(rb);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(rb);

        PasswordResetToken prt = new PasswordResetToken();
        prt.setPrincipalType(type);
        prt.setPrincipalId(principalId);
        prt.setTokenHash(sha256(raw));
        prt.setExpiresAt(Instant.now().plus(ttl));
        tokens.save(prt);

        String link = resetUrlBase + "?token=" + raw;
        this.email.send(email, "[SmartDesk] 비밀번호 재설정",
                "아래 링크에서 새 비밀번호를 설정하세요 (" + ttl.toMinutes() + "분 유효):\n" + link);
        audit.recordAuth("PASSWORD_RESET_REQUESTED", type, principalId, email, null, null);

        return exposeToken ? Optional.of(raw) : Optional.empty();
    }

    /** 토큰으로 재설정. */
    @Transactional
    public void reset(String rawToken, String newPassword) {
        validatePolicy(newPassword);
        PasswordResetToken prt = tokens.findByTokenHash(sha256(rawToken))
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> ApiException.badRequest("유효하지 않거나 만료된 재설정 토큰입니다."));

        applyNewPassword(prt.getPrincipalType(), prt.getPrincipalId(), newPassword);
        prt.setUsedAt(Instant.now());
        tokens.save(prt);
        refreshTokens.revokeAllFor(prt.getPrincipalType(), prt.getPrincipalId());
        audit.recordAuth("PASSWORD_RESET", prt.getPrincipalType(), prt.getPrincipalId(), null, null, null);
        log.info("[password-reset] 완료: {}#{}", prt.getPrincipalType(), prt.getPrincipalId());
    }

    /** 로그인 상태에서 현재 비밀번호 확인 후 변경. */
    @Transactional
    public void change(AuthPrincipal p, String currentPassword, String newPassword) {
        validatePolicy(newPassword);
        String type = p.type().name();
        String currentHash = "USER".equals(type)
                ? users.findById(p.id()).map(AppUser::getPasswordHash).orElseThrow()
                : clientUsers.findById(p.id()).map(ClientUser::getPasswordHash).orElseThrow();
        if (!hasher.matches(currentPassword, currentHash)) {
            throw ApiException.badRequest("현재 비밀번호가 올바르지 않습니다.");
        }
        applyNewPassword(type, p.id(), newPassword);
        refreshTokens.revokeAllFor(type, p.id());
        audit.record("PASSWORD_CHANGED", type, p.id(), null);
    }

    private void applyNewPassword(String type, Long id, String newPassword) {
        String hash = hasher.hash(newPassword);
        if ("USER".equals(type)) {
            AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("사용자"));
            u.setPasswordHash(hash);
            users.save(u);
        } else {
            ClientUser cu = clientUsers.findById(id).orElseThrow(() -> ApiException.notFound("담당자"));
            cu.setPasswordHash(hash);
            clientUsers.save(cu);
        }
    }

    private void validatePolicy(String pw) {
        if (pw == null || pw.length() < minLength) {
            throw ApiException.badRequest("비밀번호는 " + minLength + "자 이상이어야 합니다.");
        }
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
