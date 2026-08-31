package com.smartdesk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** REQ-F-013: 문서 버전 스냅샷. */
@Entity @Table(name = "document_version")
@Getter @Setter @NoArgsConstructor
public class DocumentVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    @Column(name = "edited_by", nullable = false)
    private Long editedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public DocumentVersion(Long documentId, int version, String title, String content, Long editedBy) {
        this.documentId = documentId;
        this.version = version;
        this.title = title;
        this.content = content;
        this.editedBy = editedBy;
    }
}
