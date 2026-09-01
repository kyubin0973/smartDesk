package com.smartdesk.feature.contract;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 계약 상태 전이의 스케줄/부팅 트리거. 로직은 ContractStatusService.
 * REQ-E-001(만료 후 열린 티켓 처리)·티켓 등록 차단 판정이 계약 상태에 의존하므로 매일 갱신.
 * (test 프로파일에서 비활성)
 */
@Component
@Profile("!test")
public class ContractStatusJob {

    private final ContractStatusService service;

    public ContractStatusJob(ContractStatusService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        service.applyTransitions();
    }

    /** 매일 00:10 (UTC). */
    @Scheduled(cron = "${smartdesk.contract.status-cron:0 10 0 * * *}", zone = "UTC")
    @SchedulerLock(name = "contract-status", lockAtMostFor = "PT10M")
    public void refresh() {
        service.applyTransitions();
    }
}
