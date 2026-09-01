-- 단계 1.1: 분석 스키마 — 운영 테이블(ticket, ticket_event)에서 파생 지표를 계산.
-- 뷰는 항상 최신, 무거운 집계는 materialized view + 야간 REFRESH (AnalyticsRefreshJob).

CREATE SCHEMA IF NOT EXISTS analytics;

-- ────────────────────────────────────────────────────────────────
-- 1. 티켓별 지표 — 처리시간 · 최초응답 · SLA 준수 · 재오픈 횟수 · 요청 시각 패턴
-- ────────────────────────────────────────────────────────────────
CREATE VIEW analytics.ticket_metrics AS
SELECT
    t.id                                                              AS ticket_id,
    t.client_id,
    t.category_id,
    t.assignee_id,
    t.priority,
    t.status,
    t.created_at,
    t.first_responded_at,
    t.resolved_at,
    t.sla_due_at,
    EXTRACT(EPOCH FROM (t.first_responded_at - t.created_at)) / 60.0   AS first_response_minutes,
    EXTRACT(EPOCH FROM (t.resolved_at        - t.created_at)) / 60.0   AS resolution_minutes,
    CASE WHEN t.resolved_at IS NOT NULL AND t.sla_due_at IS NOT NULL
         THEN (t.resolved_at <= t.sla_due_at)
    END                                                               AS sla_met,
    EXTRACT(DOW  FROM (t.created_at AT TIME ZONE 'Asia/Seoul'))::int   AS created_dow,   -- 0=일요일
    EXTRACT(HOUR FROM (t.created_at AT TIME ZONE 'Asia/Seoul'))::int   AS created_hour,
    COALESCE(re.reopen_count, 0)                                       AS reopen_count
FROM ticket t
LEFT JOIN (
    SELECT ticket_id, count(*) AS reopen_count
    FROM ticket_event
    WHERE (type = 'STATUS_CHANGED'
             AND from_value IN ('RESOLVED', 'CLOSED')
             AND to_value   IN ('IN_PROGRESS', 'RECEIVED'))
       OR type = 'REJECTED'
    GROUP BY ticket_id
) re ON re.ticket_id = t.id;

COMMENT ON VIEW analytics.ticket_metrics IS
    '티켓 1건 = 1행. 처리시간(분)/최초응답/SLA 준수/재오픈/요청 요일·시간대(Asia/Seoul).';

-- ────────────────────────────────────────────────────────────────
-- 2. 카테고리 × 요일 × 시간대 해결시간 통계 (파생 마트, 야간 REFRESH)
--    contract.sla_resolution_min 권장값·라우팅 재조정의 근거 데이터
-- ────────────────────────────────────────────────────────────────
CREATE MATERIALIZED VIEW analytics.ticket_resolution_stats AS
SELECT
    category_id,
    created_dow,
    created_hour,
    count(*)                                                          AS resolved_count,
    percentile_cont(0.5) WITHIN GROUP (ORDER BY resolution_minutes)   AS p50_minutes,
    percentile_cont(0.9) WITHIN GROUP (ORDER BY resolution_minutes)   AS p90_minutes,
    avg(resolution_minutes)                                           AS avg_minutes,
    avg((sla_met IS FALSE)::int)::numeric(6, 4)                       AS sla_breach_rate
FROM analytics.ticket_metrics
WHERE resolution_minutes IS NOT NULL
GROUP BY category_id, created_dow, created_hour
WITH DATA;

-- ────────────────────────────────────────────────────────────────
-- 3. 담당자별 처리량 · 처리시간 · 현재 부하
-- ────────────────────────────────────────────────────────────────
CREATE VIEW analytics.assignee_throughput AS
SELECT
    u.id                                                                        AS assignee_id,
    u.name,
    u.department_id,
    count(m.ticket_id) FILTER (WHERE m.resolved_at IS NOT NULL)                  AS resolved_count,
    count(m.ticket_id) FILTER (WHERE m.status IN ('RECEIVED', 'IN_PROGRESS'))    AS open_load,
    percentile_cont(0.5) WITHIN GROUP (ORDER BY m.resolution_minutes)           AS p50_minutes,
    avg(m.resolution_minutes)                                                    AS avg_minutes,
    avg((m.sla_met IS FALSE)::int) FILTER (WHERE m.sla_met IS NOT NULL)          AS sla_breach_rate
FROM app_user u
LEFT JOIN analytics.ticket_metrics m ON m.assignee_id = u.id
GROUP BY u.id, u.name, u.department_id;

-- ────────────────────────────────────────────────────────────────
-- 4. 외부 티켓 데이터셋 (EDA · 분류 모델 학습용)
--    출처: Tobi-Bueck/customer-support-tickets (HF, CC-BY-NC-4.0) — Kaggle "Customer IT Support" 동일 데이터
--    적재: analytics/scripts/load_to_db.py
-- ────────────────────────────────────────────────────────────────
CREATE TABLE analytics.external_ticket (
    id        BIGSERIAL PRIMARY KEY,
    source    VARCHAR(60) NOT NULL DEFAULT 'tobi-bueck/customer-support-tickets',
    subject   TEXT,
    body      TEXT,
    answer    TEXT,
    type      VARCHAR(40),     -- Incident / Request / Problem / Change
    queue     VARCHAR(80),     -- 처리 큐 = 라우팅 대상(카테고리 대응)
    priority  VARCHAR(20),     -- low / medium / high
    language  VARCHAR(8),
    tags      TEXT[],
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_external_ticket_queue ON analytics.external_ticket (queue);
CREATE INDEX idx_external_ticket_lang  ON analytics.external_ticket (language);
