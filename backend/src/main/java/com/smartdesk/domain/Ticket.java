package com.smartdesk.domain;

import com.smartdesk.domain.Enums.Priority;
import com.smartdesk.domain.Enums.TicketStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity @Table(name = "ticket")
@Getter @Setter @NoArgsConstructor
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "system_id")
    private Long systemId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private TicketStatus status = TicketStatus.RECEIVED;

    /** REQ-F-011: created_at + contract.sla_resolution_min. REQ-E-006: 생성 후 소급 변경 금지. */
    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    /** 최초 응답(처리중 전환) 시각 — SLA 응답 시한·분석용. */
    @Column(name = "first_responded_at")
    private Instant firstRespondedAt;

    /** 해결 시각 — SLA 준수율 계산의 정확한 기준 (updated_at 대신). */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate void touch() { this.updatedAt = Instant.now(); }

    /** SLA 마감 대비 실제 해결 시각으로 준수 여부 판정. 미해결이면 현재 시각 기준. */
    public boolean isSlaMet() {
        if (slaDueAt == null) return true;
        Instant effective = resolvedAt != null ? resolvedAt : Instant.now();
        return !effective.isAfter(slaDueAt);
    }
}
