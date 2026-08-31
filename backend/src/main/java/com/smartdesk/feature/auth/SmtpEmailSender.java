package com.smartdesk.feature.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 0.5-a: 실제 SMTP 발송. EmailSenderConfig 가 spring.mail.host 설정 시 이 구현을 선택한다.
 */
class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mail;
    private final String from;

    SmtpEmailSender(JavaMailSender mail, String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mail.send(msg);
        } catch (Exception e) {
            // 발송 실패가 비즈니스 흐름을 막지 않도록 삼킨다 (재시도 큐는 확장)
            log.warn("[email] 발송 실패 to={} : {}", to, e.toString());
        }
    }
}
