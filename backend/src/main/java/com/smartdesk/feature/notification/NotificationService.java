package com.smartdesk.feature.notification;

import com.smartdesk.domain.Enums.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;

/**
 * 알림 생성 + 발송.
 * - 인앱: NotificationWriter 의 REQUIRES_NEW 트랜잭션 (중복 예외는 여기서 삼킴)
 * - 외부 채널(이메일/슬랙): 인앱 저장 성공 시 NotificationChannels 로 팬아웃 (0.5-a)
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationWriter writer;
    private final NotificationChannels channels;

    public NotificationService(NotificationWriter writer, NotificationChannels channels) {
        this.writer = writer;
        this.channels = channels;
    }

    public void notifyUser(Long userId, NotificationType type, String title, String body, Long ticketId) {
        create("USER", userId, type, title, body, ticketId);
    }

    public void notifyClientUser(Long clientUserId, NotificationType type, String title, String body, Long ticketId) {
        create("CLIENT_USER", clientUserId, type, title, body, ticketId);
    }

    private void create(String recipientType, Long recipientId, NotificationType type,
                        String title, String body, Long ticketId) {
        if (recipientId == null) return;
        boolean persisted = false;
        try {
            persisted = writer.persist(recipientType, recipientId, type, title, body, ticketId);
            log.info("[notify] {}#{} <- {} : {}", recipientType, recipientId, type, title);
        } catch (DataIntegrityViolationException | UnexpectedRollbackException dup) {
            // 동시성으로 인한 중복 삽입 — 무시 (REQUIRES_NEW 트랜잭션만 롤백됨)
        }
        if (persisted) {
            channels.fanOut(recipientType, recipientId, type, title, body, ticketId);
        }
    }
}
