package com.smartdesk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** REQ-E-009: 로그인 5회 연속 실패 시 임시 잠금 + 캡차 요구. */
@Entity @Table(name = "login_attempt")
@Getter @Setter @NoArgsConstructor
public class LoginAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "principal_type", nullable = false)
    private String principalType;

    @Column(name = "fail_count", nullable = false)
    private int failCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
