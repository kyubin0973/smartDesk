-- 확장(데이터분석·RAG·에이전트) 이전 정지작업: 스키마 보강
-- 리뷰 P0 항목 1~6 반영

-- 1. 티켓 생애주기 타임스탬프 (SLA 준수율·처리시간 분석의 정확한 기준)
ALTER TABLE ticket ADD COLUMN first_responded_at TIMESTAMPTZ;   -- 최초 담당자 응답(처리중 전환) 시각
ALTER TABLE ticket ADD COLUMN resolved_at        TIMESTAMPTZ;   -- 해결 시각
ALTER TABLE ticket ADD COLUMN closed_at          TIMESTAMPTZ;   -- 종료 시각

-- 3. append-only 이벤트 로그 (이벤트 소싱 / 분석 팩트 테이블의 출발점)
CREATE TABLE ticket_event (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT NOT NULL REFERENCES ticket(id),
    type       VARCHAR(40) NOT NULL,   -- CREATED / CATEGORIZED / ASSIGNED / STATUS_CHANGED / COMMENTED / SLA_BREACHED
    from_value VARCHAR(200),
    to_value   VARCHAR(200),
    actor_type VARCHAR(20),            -- USER / CLIENT_USER / SYSTEM
    actor_id   BIGINT,
    at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_event_ticket ON ticket_event(ticket_id);
CREATE INDEX idx_ticket_event_type   ON ticket_event(type, at);

-- 4. 첨부파일 (티켓/코멘트/문서 공용)
CREATE TABLE attachment (
    id               BIGSERIAL PRIMARY KEY,
    owner_type       VARCHAR(20) NOT NULL,   -- TICKET / DOCUMENT
    owner_id         BIGINT NOT NULL,
    filename         VARCHAR(300) NOT NULL,
    content_type     VARCHAR(150),
    size_bytes       BIGINT NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,  -- 로컬 파일 경로 or 오브젝트 스토리지 키
    uploaded_by_type VARCHAR(20),
    uploaded_by_id   BIGINT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attachment_owner ON attachment(owner_type, owner_id);

-- 5. 알림
CREATE TABLE notification (
    id             BIGSERIAL PRIMARY KEY,
    recipient_type VARCHAR(20) NOT NULL,   -- USER / CLIENT_USER
    recipient_id   BIGINT NOT NULL,
    type           VARCHAR(40) NOT NULL,   -- SLA_DUE_SOON / SLA_BREACHED / TICKET_ASSIGNED / TICKET_COMMENTED / TICKET_STATUS
    title          VARCHAR(300) NOT NULL,
    body           TEXT,
    ticket_id      BIGINT,
    read_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_recipient ON notification(recipient_type, recipient_id, read_at);

-- 중복 알림 방지용 (같은 티켓·타입 1회): 부분 유니크
CREATE UNIQUE INDEX uq_notification_once
    ON notification(recipient_type, recipient_id, type, ticket_id)
    WHERE type IN ('SLA_DUE_SOON', 'SLA_BREACHED');

-- 6. 문서 전문검색: tsvector 생성 컬럼 + GIN 인덱스
--    (현재 애플리케이션은 LIKE 검색 유지. 이 컬럼은 2단계 하이브리드 검색 전환 대비)
ALTER TABLE document ADD COLUMN search_tsv tsvector
    GENERATED ALWAYS AS (
        to_tsvector('simple'::regconfig, coalesce(title, '') || ' ' || coalesce(content, ''))
    ) STORED;
CREATE INDEX idx_document_search ON document USING GIN (search_tsv);

-- 보안: 비밀번호를 BCrypt 로 전환 (REQ-N-003 'SHA-256 이상' 충족·강화). 컬럼 크기는 그대로 사용.
-- 데모 시드 계정 재해싱 (평문 'Passw0rd!')
UPDATE app_user    SET password_hash = '$2a$10$r1C0dKZ9VuV0AZmIGmfvdO2GSgv0IFdWdAmUHKvCfK4Ur2CiluGj2';
UPDATE client_user SET password_hash = '$2a$10$r1C0dKZ9VuV0AZmIGmfvdO2GSgv0IFdWdAmUHKvCfK4Ur2CiluGj2';

-- 9. 계약 상태 백필 (스케줄러가 이후 유지)
UPDATE contract SET status = 'ENDED'
    WHERE end_date < CURRENT_DATE;
UPDATE contract SET status = 'EXPIRING'
    WHERE end_date >= CURRENT_DATE AND end_date < CURRENT_DATE + INTERVAL '30 days' AND status <> 'ENDED';

-- 기존 티켓 타임스탬프 백필 (근사): 종결 티켓은 updated_at 을 해결/종료 시각으로
UPDATE ticket SET resolved_at = updated_at WHERE status IN ('RESOLVED', 'CLOSED') AND resolved_at IS NULL;
UPDATE ticket SET closed_at   = updated_at WHERE status = 'CLOSED' AND closed_at IS NULL;
UPDATE ticket SET first_responded_at = updated_at WHERE status <> 'RECEIVED' AND first_responded_at IS NULL;

-- 23. 리프레시 토큰 + 액세스 토큰 폐기(로그아웃 blacklist)
CREATE TABLE refresh_token (
    id             BIGSERIAL PRIMARY KEY,
    principal_type VARCHAR(20) NOT NULL,   -- USER / CLIENT_USER
    principal_id   BIGINT NOT NULL,
    token_hash     VARCHAR(100) NOT NULL UNIQUE,   -- SHA-256 of raw refresh token
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_principal ON refresh_token(principal_type, principal_id);

CREATE TABLE revoked_access_token (
    jti        VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);
