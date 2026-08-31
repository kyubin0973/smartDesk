package com.smartdesk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** C11: 보안·관리 이벤트 감사 로그 (append-only). */
@Entity @Table(name = "audit_log")
@Getter @Setter @NoArgsConstructor
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant at = Instant.now();

    @Column(name = "actor_type")   private String actorType;
    @Column(name = "actor_id")     private Long actorId;
    @Column(name = "actor_email")  private String actorEmail;

    @Column(nullable = false)      private String action;
    @Column(name = "target_type")  private String targetType;
    @Column(name = "target_id")    private Long targetId;
    @Column                        private String detail;
    @Column                        private String ip;
}
