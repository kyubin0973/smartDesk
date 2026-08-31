package com.smartdesk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 로그아웃된 액세스 토큰의 jti. 만료 시각 이후엔 정리 대상. */
@Entity @Table(name = "revoked_access_token")
@Getter @Setter @NoArgsConstructor
public class RevokedAccessToken {
    @Id
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public RevokedAccessToken(String jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }
}
