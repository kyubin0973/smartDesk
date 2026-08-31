package com.smartdesk.domain;

import com.smartdesk.domain.Enums.AttachmentOwnerType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 티켓/문서 첨부파일. 화면설계서 SCR-TICKET-001 '텍스트/이미지 첨부'. */
@Entity @Table(name = "attachment")
@Getter @Setter @NoArgsConstructor
public class Attachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(name = "owner_type", nullable = false)
    private AttachmentOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "uploaded_by_type")
    private String uploadedByType;

    @Column(name = "uploaded_by_id")
    private Long uploadedById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
