-- 단계 2.1: 벡터 검색 (pgvector). 지식문서 + 종료(CLOSED) 티켓을 의미 검색 대상으로 색인.
-- 임베딩: multilingual-e5-small (384차원). analytics/service 의 POST /embed 로 생성.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE embedding (
    id          BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(20)  NOT NULL,           -- DOCUMENT / TICKET
    source_id   BIGINT       NOT NULL,
    chunk_ord   INT          NOT NULL DEFAULT 0,
    content     TEXT         NOT NULL,           -- 청크 원문 (표시·근거 인용용)
    embedding   vector(384)  NOT NULL,
    model       VARCHAR(60)  NOT NULL,
    source_hash VARCHAR(64)  NOT NULL,           -- 원본 변경 감지 → 재색인 스킵
    indexed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (source_type, source_id, chunk_ord)
);

-- 코사인 거리 근사 최근접 (HNSW)
CREATE INDEX idx_embedding_vec ON embedding USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_embedding_src ON embedding (source_type, source_id);

-- 하이브리드 검색의 BM25(전문검색) 쪽 — 티켓용 tsvector (문서엔 V3 의 search_tsv 존재).
ALTER TABLE ticket
    ADD COLUMN search_tsv tsvector
    GENERATED ALWAYS AS (
        to_tsvector('simple'::regconfig, coalesce(title, '') || ' ' || coalesce(content, ''))
    ) STORED;
CREATE INDEX idx_ticket_tsv ON ticket USING gin (search_tsv);
