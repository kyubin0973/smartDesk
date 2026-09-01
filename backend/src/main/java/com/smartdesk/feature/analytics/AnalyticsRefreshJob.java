package com.smartdesk.feature.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 단계 1.1: 분석 materialized view 야간 갱신. (test 프로파일에서 비활성) */
@Component
@Profile("!test")
public class AnalyticsRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRefreshJob.class);

    private final AnalyticsService analytics;

    public AnalyticsRefreshJob(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @Scheduled(cron = "${smartdesk.analytics.refresh-cron:0 20 0 * * *}")   // 매일 00:20 UTC
    public void refresh() {
        long t0 = System.currentTimeMillis();
        analytics.refreshMaterializedViews();
        log.info("[analytics] ticket_resolution_stats 갱신 완료 ({}ms)", System.currentTimeMillis() - t0);
    }
}
