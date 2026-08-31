-- C11: 감사 로그 (보안·관리 이벤트). 티켓 생애주기 이벤트는 ticket_event 유지.
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_type  VARCHAR(20),          -- USER / CLIENT_USER / ANONYMOUS
    actor_id    BIGINT,
    actor_email VARCHAR(255),
    action      VARCHAR(60) NOT NULL,  -- LOGIN_SUCCESS / LOGIN_FAILURE / LOGOUT / PASSWORD_RESET_REQUESTED /
                                       -- PASSWORD_RESET / PASSWORD_CHANGED / USER_CREATED / USER_DEACTIVATED /
                                       -- CLIENT_USER_CREATED / CLIENT_USER_DEACTIVATED / CONTRACT_OFFBOARDED /
                                       -- DOCUMENT_SCOPE_CHANGED
    target_type VARCHAR(30),          -- USER / CLIENT_USER / CONTRACT / DOCUMENT ...
    target_id   BIGINT,
    detail      VARCHAR(500),
    ip          VARCHAR(64)
);
CREATE INDEX idx_audit_at     ON audit_log(at DESC);
CREATE INDEX idx_audit_action ON audit_log(action, at DESC);
CREATE INDEX idx_audit_actor  ON audit_log(actor_type, actor_id);
