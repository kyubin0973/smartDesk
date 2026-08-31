package com.smartdesk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** REQ-F-012: 상태/담당자/카테고리 변경 이력. REQ-E-003: 담당자 이력 유지 근거. */
@Entity @Table(name = "ticket_history")
@Getter @Setter @NoArgsConstructor
public class TicketHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(nullable = false)
    private String field;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "actor_type")
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public TicketHistory(Long ticketId, String field, String oldValue, String newValue, String actorType, Long actorId) {
        this.ticketId = ticketId;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.actorType = actorType;
        this.actorId = actorId;
    }
}
