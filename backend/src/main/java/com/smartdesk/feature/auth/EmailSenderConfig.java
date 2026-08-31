package com.smartdesk.feature.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * EmailSender 빈 선택:
 * - spring.mail.host 설정 + JavaMailSender 존재 → SMTP 발송
 * - 그 외 → 로그만 (개발 기본)
 */
@Configuration
class EmailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderConfig.class);

    @Bean
    EmailSender emailSender(ObjectProvider<JavaMailSender> mailProvider,
                            @Value("${spring.mail.host:}") String mailHost,
                            @Value("${smartdesk.mail.from:no-reply@smartdesk.io}") String from) {
        JavaMailSender mail = mailProvider.getIfAvailable();
        if (mail != null && !mailHost.isBlank()) {
            log.info("[email] SMTP 발송 활성화 (host={})", mailHost);
            return new SmtpEmailSender(mail, from);
        }
        log.info("[email] 로그 어댑터 사용 (spring.mail.host 미설정)");
        return (to, subject, body) -> log.info("[email] to={} subject={}\n{}", to, subject, body);
    }
}
