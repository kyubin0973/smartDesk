package com.smartdesk.feature.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 개발용 EmailSender: 실제로 보내지 않고 로그에 남긴다. 운영에선 EmailSender 빈을 별도로 등록하면 이 빈은 비활성. */
@Configuration
class EmailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderConfig.class);

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender loggingEmailSender() {
        return (to, subject, body) -> log.info("[email] to={} subject={}\n{}", to, subject, body);
    }
}
