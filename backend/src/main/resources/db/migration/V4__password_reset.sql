-- C9: 비밀번호 재설정 (이메일 토큰)
CREATE TABLE password_reset_token (
    id             BIGSERIAL PRIMARY KEY,
    principal_type VARCHAR(20) NOT NULL,   -- USER / CLIENT_USER
    principal_id   BIGINT NOT NULL,
    token_hash     VARCHAR(100) NOT NULL UNIQUE,  -- SHA-256 of the raw token
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_prt_principal ON password_reset_token(principal_type, principal_id) WHERE used_at IS NULL;
