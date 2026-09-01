package com.smartdesk.feature.ticket;

import org.springframework.context.annotation.Profile;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** SLA 모니터의 스케줄 트리거. 로직은 SlaMonitorService. (test 프로파일에서 비활성) */
@Component
@Profile("!test")
public class SlaMonitorJob {

    private final SlaMonitorService service;

    public SlaMonitorJob(SlaMonitorService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${smartdesk.sla.monitor-interval-ms:300000}")
    @SchedulerLock(name = "sla-monitor", lockAtMostFor = "PT9M", lockAtLeastFor = "PT10S")
    public void run() {
        service.scan();
    }
}
