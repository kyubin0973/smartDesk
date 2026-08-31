package com.smartdesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 활성화. 테스트 프로파일에서는 잡을 끄기 위해 job 클래스에서 조건부 등록. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
