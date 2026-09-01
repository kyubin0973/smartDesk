package com.smartdesk.feature.rag;

import org.springframework.context.annotation.Profile;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 단계 2.1: 누락·변경 원본 재색인 (이벤트 유실 대비). test 프로파일 비활성. */
@Component
@Profile("!test")
public class RagReconcileJob {

    private final IndexingService indexing;
    private final RagProperties props;

    public RagReconcileJob(IndexingService indexing, RagProperties props) {
        this.indexing = indexing;
        this.props = props;
    }

    @Scheduled(cron = "${smartdesk.rag.reconcile-cron:0 */10 * * * *}")
    @SchedulerLock(name = "rag-reconcile", lockAtMostFor = "PT9M", lockAtLeastFor = "PT10S")
    public void run() {
        if (props.isEnabled()) indexing.reconcile();
    }
}
