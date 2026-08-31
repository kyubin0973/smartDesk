package com.smartdesk.feature.auth;

/**
 * 이메일 발송 추상화. 현재 구현은 로그만 (LoggingEmailSender).
 * 운영: SMTP/SendGrid/SES 어댑터를 이 인터페이스로 구현해 교체.
 */
public interface EmailSender {
    void send(String to, String subject, String body);
}
