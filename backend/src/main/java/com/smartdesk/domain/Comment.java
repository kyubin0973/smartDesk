package com.smartdesk.domain;

import com.smartdesk.domain.Enums.AuthorType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 다형 관계: author_type 에 따라 author_id 가 app_user / client_user 를 가리킴. */
@Entity @Table(name = "comment")
@Getter @Setter @NoArgsConstructor
public class Comment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false)
    private AuthorType authorType;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
