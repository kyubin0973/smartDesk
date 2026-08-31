package com.smartdesk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity @Table(name = "password_reset_token")
@Getter @Setter @NoArgsConstructor
public class PasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "principal_type", nullable = false)
    private String principalType;   // USER / CLIENT_USER

    @Column(name = "principal_id", nullable = false)
    private Long principalId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isUsable() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }
}
