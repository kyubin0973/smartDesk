package com.smartdesk.repo;

import com.smartdesk.domain.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentVersionRepo extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersion> findByDocumentIdOrderByVersionDesc(Long documentId);
}
