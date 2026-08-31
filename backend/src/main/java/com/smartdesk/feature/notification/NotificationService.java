package com.smartdesk.feature.notification;

import com.smartdesk.domain.Enums.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;

/**
 * 알림 생성 + 발송. 현재는 인앱 저장 + 로그.
 * 저장은 NotificationWriter 의 REQUIRES_NEW 트랜잭션에서 수행하고,
 * 동시성 중복 예외는 여기(경계 밖)에서 삼킨다 → 호출자 트랜잭션 안전.
 * 확장: 이메일/슬랙 어댑터를 여기에 추가.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationWriter writer;

    public NotificationService(NotificationWriter writer) {
        this.writer = writer;
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
        try {
            writer.persist(recipientType, recipientId, type, title, body, ticketId);
            log.info("[notify] {}#{} <- {} : {}", recipientType, recipientId, type, title);
            // TODO(확장): 이메일/슬랙 발송 어댑터 호출
        } catch (DataIntegrityViolationException | UnexpectedRollbackException dup) {
            // 동시성으로 인한 중복 삽입 — 무시 (REQUIRES_NEW 트랜잭션만 롤백됨)
        }
    }
}
