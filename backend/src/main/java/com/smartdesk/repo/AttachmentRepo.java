package com.smartdesk.repo;

import com.smartdesk.domain.Attachment;
import com.smartdesk.domain.Enums.AttachmentOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepo extends JpaRepository<Attachment, Long> {
    List<Attachment> findByOwnerTypeAndOwnerId(AttachmentOwnerType ownerType, Long ownerId);
}
