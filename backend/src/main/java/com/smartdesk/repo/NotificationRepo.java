package com.smartdesk.repo;

import com.smartdesk.domain.Enums.NotificationType;
import com.smartdesk.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepo extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientTypeAndRecipientIdOrderByCreatedAtDesc(String recipientType, Long recipientId);
    long countByRecipientTypeAndRecipientIdAndReadAtIsNull(String recipientType, Long recipientId);
    boolean existsByRecipientTypeAndRecipientIdAndTypeAndTicketId(
            String recipientType, Long recipientId, NotificationType type, Long ticketId);
}
