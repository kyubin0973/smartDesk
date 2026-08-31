package com.smartdesk.domain;

import com.smartdesk.domain.Enums.DocScope;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity @Table(name = "document")
@Getter @Setter @NoArgsConstructor
public class Document {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** scope=CLIENT_SHARED 이고 단일 고객사일 때. 다중 공유는 document_share 사용. */
    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    /** REQ-F-013: 저장 시 자동 증가. REQ-E-008: 낙관적 잠금 비교 키. */
    @Column(nullable = false)
    private int version = 1;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private DocScope scope = DocScope.SI_INTERNAL;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
