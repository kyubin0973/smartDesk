package com.smartdesk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/** REQ-F-014: 문서 ↔ 다수 고객사 공유. */
@Entity @Table(name = "document_share")
@IdClass(DocumentShare.Key.class)
@Getter @Setter @NoArgsConstructor
public class DocumentShare {
    @Id @Column(name = "document_id")
    private Long documentId;

    @Id @Column(name = "client_id")
    private Long clientId;

    public DocumentShare(Long documentId, Long clientId) {
        this.documentId = documentId;
        this.clientId = clientId;
    }

    public static class Key implements Serializable {
        private Long documentId;
        private Long clientId;
        public Key() {}
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return Objects.equals(documentId, k.documentId) && Objects.equals(clientId, k.clientId);
        }
        @Override public int hashCode() { return Objects.hash(documentId, clientId); }
    }
}
