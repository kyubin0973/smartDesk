package com.smartdesk.domain;

import com.smartdesk.domain.Enums.NotificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity @Table(name = "notification")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_type", nullable = false)
    private String recipientType;   // USER / CLIENT_USER

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column
    private String body;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
