package com.smartdesk.feature.rag;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 단계 2.1: 문서 저장 / 티켓 종료 시 비동기 임베딩 (outbox 대용). 누락은 RagReconcileJob 이 보정. */
@Component
public class RagIndexListener {

    private final IndexingService indexing;

    public RagIndexListener(IndexingService indexing) {
        this.indexing = indexing;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSourceChanged(RagIndexEvents.SourceChanged e) {
        if ("DOCUMENT".equals(e.type())) {
            indexing.indexDocument(e.id());
        } else {
            indexing.indexTicket(e.id());
        }
    }
}
