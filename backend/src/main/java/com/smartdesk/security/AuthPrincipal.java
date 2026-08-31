package com.smartdesk.security;

import com.smartdesk.domain.Enums.AuthorType;

import java.time.Instant;

/**
 * 인증 주체. SI 직원(USER) 또는 고객사 담당자(CLIENT_USER).
 * clientId 는 CLIENT_USER 일 때만 채워짐 (멀티테넌시 필터 기준, REQ-N-001).
 * jti/expiresAt 은 액세스 토큰에서만 채워짐 (폐기 검사·만료 판정용).
 */
public record AuthPrincipal(AuthorType type, Long id, String email, String role, Long clientId,
                            String jti, Instant expiresAt) {

    public AuthPrincipal(AuthorType type, Long id, String email, String role, Long clientId) {
        this(type, id, email, role, clientId, null, null);
    }

    public boolean isSiUser()      { return type == AuthorType.USER; }
    public boolean isClientUser()  { return type == AuthorType.CLIENT_USER; }
    public boolean isManager()     { return isSiUser() && "MANAGER".equals(role); }
}
