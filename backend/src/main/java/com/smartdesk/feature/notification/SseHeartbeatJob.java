package com.smartdesk.feature.notification;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 0.5-b: SSE keep-alive 스케줄 트리거. 로직은 SseHub. (test 프로파일에서 비활성) */
@Component
@Profile("!test")
public class SseHeartbeatJob {

    private final SseHub hub;

    public SseHeartbeatJob(SseHub hub) {
        this.hub = hub;
    }

    @Scheduled(fixedRateString = "${smartdesk.notifications.sse-heartbeat-ms:25000}")
    public void run() {
        hub.heartbeat();
    }
}
