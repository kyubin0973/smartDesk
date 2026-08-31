package com.smartdesk.repo;

import com.smartdesk.domain.DocumentShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentShareRepo extends JpaRepository<DocumentShare, DocumentShare.Key> {
    List<DocumentShare> findByDocumentId(Long documentId);
    List<DocumentShare> findByDocumentIdIn(java.util.Collection<Long> documentIds);
    void deleteByDocumentId(Long documentId);
    List<DocumentShare> findByClientId(Long clientId);
}
