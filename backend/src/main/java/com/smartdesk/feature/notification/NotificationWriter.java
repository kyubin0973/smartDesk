package com.smartdesk.feature.notification;

import com.smartdesk.domain.Enums.NotificationType;
import com.smartdesk.domain.Notification;
import com.smartdesk.repo.NotificationRepo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 저장을 **독립 트랜잭션(REQUIRES_NEW)** 으로 분리.
 * 예외/롤백은 이 트랜잭션 경계 안에서만 발생 → 호출자(예: SlaMonitorJob 전체 스캔) 트랜잭션은 안전.
 * 예외 처리는 경계 밖(NotificationService)에서 한다.
 */
@Component
class NotificationWriter {

    private final NotificationRepo repo;

    NotificationWriter(NotificationRepo repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persist(String recipientType, Long recipientId, NotificationType type,
                 String title, String body, Long ticketId) {
        if ((type == NotificationType.SLA_DUE_SOON || type == NotificationType.SLA_BREACHED)
                && ticketId != null
                && repo.existsByRecipientTypeAndRecipientIdAndTypeAndTicketId(recipientType, recipientId, type, ticketId)) {
            return;
        }
        Notification n = new Notification();
        n.setRecipientType(recipientType);
        n.setRecipientId(recipientId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setTicketId(ticketId);
        repo.save(n); // 동시성 중복 시 DataIntegrityViolationException → 이 트랜잭션만 롤백
    }
}
