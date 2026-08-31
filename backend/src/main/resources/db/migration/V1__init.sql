-- SmartDesk 초기 스키마 (데이터모델링 산출물 ERD 기준)
-- 문서에 없던 보완 컬럼/테이블은 주석에 [보완] 표기

CREATE TABLE department (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE app_user (                       -- ERD: user (SI 직원). 'user'는 예약어라 app_user 사용
    id            BIGSERIAL PRIMARY KEY,
    department_id BIGINT REFERENCES department(id),
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'AGENT',   -- AGENT(담당자) / MANAGER(관리자)
    active        BOOLEAN      NOT NULL DEFAULT TRUE,       -- [보완] REQ-E-003 퇴사/부서이동
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE client (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE                    -- [보완] 오프보딩 완료 표시
);

CREATE TABLE user_client (                    -- [보완] REQ-F-003 담당 고객사, REQ-F-010 자동배정 후보
    user_id   BIGINT NOT NULL REFERENCES app_user(id),
    client_id BIGINT NOT NULL REFERENCES client(id),
    PRIMARY KEY (user_id, client_id)
);

CREATE TABLE client_user (                    -- 고객사 담당자
    id            BIGSERIAL PRIMARY KEY,
    client_id     BIGINT NOT NULL REFERENCES client(id),
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE,            -- [보완] REQ-E-004 계정 비활성화
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE contract (
    id                  BIGSERIAL PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES client(id),
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    sla_response_min    INT,
    sla_resolution_min  INT,
    maintenance_scope   TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE(계약중) / EXPIRING(만료임박) / ENDED(종료)
);

CREATE TABLE system_asset (                   -- ERD: system. 'system'은 예약어라 system_asset 사용
    id        BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES client(id),
    name      VARCHAR(200) NOT NULL,
    type      VARCHAR(100),
    active    BOOLEAN NOT NULL DEFAULT TRUE                 -- [보완] REQ-E-005 soft delete
);

CREATE TABLE category (
    id     BIGSERIAL PRIMARY KEY,
    name   VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE                     -- [보완] REQ-E-005 soft delete
);

CREATE TABLE category_routing (               -- [보완] REQ-F-010 카테고리 → 처리 부서 매핑 (규칙 기반 라우팅)
    category_id   BIGINT PRIMARY KEY REFERENCES category(id),
    department_id BIGINT NOT NULL REFERENCES department(id)
);

CREATE TABLE ticket (
    id            BIGSERIAL PRIMARY KEY,
    client_id     BIGINT NOT NULL REFERENCES client(id),
    contract_id   BIGINT NOT NULL REFERENCES contract(id),
    system_id     BIGINT REFERENCES system_asset(id),
    category_id   BIGINT REFERENCES category(id),
    requester_id  BIGINT NOT NULL REFERENCES client_user(id),
    assignee_id   BIGINT REFERENCES app_user(id),
    title         VARCHAR(200) NOT NULL,
    content       TEXT NOT NULL,
    priority      VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',     -- LOW / MEDIUM / HIGH / CRITICAL
    status        VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',   -- RECEIVED(접수) / IN_PROGRESS(처리중) / RESOLVED(해결) / CLOSED(종료)
    sla_due_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_client ON ticket(client_id);
CREATE INDEX idx_ticket_status ON ticket(status);
CREATE INDEX idx_ticket_assignee ON ticket(assignee_id);

CREATE TABLE comment (
    id          BIGSERIAL PRIMARY KEY,
    ticket_id   BIGINT NOT NULL REFERENCES ticket(id),
    author_type VARCHAR(20) NOT NULL,                        -- USER / CLIENT_USER
    author_id   BIGINT NOT NULL,
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_comment_ticket ON comment(ticket_id);

CREATE TABLE ticket_history (                  -- [보완] REQ-F-012 상태변경 이력
    id          BIGSERIAL PRIMARY KEY,
    ticket_id   BIGINT NOT NULL REFERENCES ticket(id),
    field       VARCHAR(40) NOT NULL,                        -- status / assignee / category
    old_value   VARCHAR(200),
    new_value   VARCHAR(200),
    actor_type  VARCHAR(20),
    actor_id    BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_history_ticket ON ticket_history(ticket_id);

CREATE TABLE document (
    id          BIGSERIAL PRIMARY KEY,
    client_id   BIGINT REFERENCES client(id),               -- scope=CLIENT_SHARED 일 때만
    category_id BIGINT REFERENCES category(id),
    created_by  BIGINT NOT NULL REFERENCES app_user(id),
    title       VARCHAR(300) NOT NULL,
    content     TEXT NOT NULL,
    version     INT NOT NULL DEFAULT 1,
    scope       VARCHAR(20) NOT NULL DEFAULT 'SI_INTERNAL',  -- SI_INTERNAL / CLIENT_SHARED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE document_share (                  -- [보완] 문서 ↔ 다수 고객사 공유(화면설계서 '특정 고객사 선택(다중)')
    document_id BIGINT NOT NULL REFERENCES document(id),
    client_id   BIGINT NOT NULL REFERENCES client(id),
    PRIMARY KEY (document_id, client_id)
);

CREATE TABLE document_version (                -- [보완] REQ-F-013 버전 이력 / REQ-E-008 낙관적 잠금 근거
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT NOT NULL REFERENCES document(id),
    version      INT NOT NULL,
    title        VARCHAR(300) NOT NULL,
    content      TEXT NOT NULL,
    edited_by    BIGINT NOT NULL REFERENCES app_user(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, version)
);

CREATE TABLE login_attempt (                   -- [보완] REQ-E-009 로그인 5회 실패 → 잠금
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    principal_type VARCHAR(20) NOT NULL,                     -- USER / CLIENT_USER
    fail_count    INT NOT NULL DEFAULT 0,
    locked_until  TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (email, principal_type)
);
