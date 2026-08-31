package com.smartdesk.domain;

import com.smartdesk.domain.Enums.TicketEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** append-only 티켓 이벤트 로그. 상태별 체류시간·재오픈율 등 분석의 원천. */
@Entity @Table(name = "ticket_event")
@Getter @Setter @NoArgsConstructor
public class TicketEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private TicketEventType type;

    @Column(name = "from_value")
    private String fromValue;

    @Column(name = "to_value")
    private String toValue;

    @Column(name = "actor_type")
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false)
    private Instant at = Instant.now();

    public TicketEvent(Long ticketId, TicketEventType type, String fromValue, String toValue, String actorType, Long actorId) {
        this.ticketId = ticketId;
        this.type = type;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.actorType = actorType;
        this.actorId = actorId;
    }
}
