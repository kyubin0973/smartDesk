-- 단계 3: 지능형 트리아지 · SLA 위반 예측.
-- SLA_AT_RISK 알림도 티켓당 1회로 제한 (uq_notification_once 확장).

DROP INDEX IF EXISTS uq_notification_once;
CREATE UNIQUE INDEX uq_notification_once
    ON notification (recipient_type, recipient_id, type, ticket_id)
    WHERE type IN ('SLA_DUE_SOON', 'SLA_BREACHED', 'SLA_AT_RISK');

-- 트리아지 감사용: 티켓별 최근 트리아지 스냅샷 (이벤트 로그로도 남지만 조회 편의).
CREATE TABLE triage_snapshot (
    id                  BIGSERIAL PRIMARY KEY,
    ticket_id           BIGINT NOT NULL REFERENCES ticket(id),
    category_id         BIGINT,
    priority            VARCHAR(10),
    suggested_assignee  BIGINT,
    confidence          NUMERIC(4, 3) NOT NULL,
    escalated           BOOLEAN NOT NULL DEFAULT FALSE,
    rationale           TEXT,
    llm_used            BOOLEAN NOT NULL DEFAULT FALSE,
    at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_triage_snapshot_ticket ON triage_snapshot (ticket_id, at DESC);
