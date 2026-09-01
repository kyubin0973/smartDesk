-- 단계 4: PostgreSQL Row-Level Security — 애플리케이션 레벨 client_id 필터의 이중 방어.
--
-- 마이그레이션은 테이블 소유자로 실행되고, 애플리케이션은 별도 비특권 롤(smartdesk_app)로 SET ROLE 하여
-- RLS 정책의 적용을 받는다 (소유자·슈퍼유저는 RLS 를 우회하므로).
-- 세션 변수:
--   app.is_si = 'true'  → SI 직원·관리자·스케줄러: 전체 접근
--   app.is_si = 'false' → 고객사 담당자: 자기 client_id 행만
--   미설정/미인증 → 아무 행도 안 보임 (fail-closed)

-- ── 1. 애플리케이션 롤 ──────────────────────────────────────────
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartdesk_app') THEN
        CREATE ROLE smartdesk_app NOLOGIN;
    END IF;
END $$;

GRANT smartdesk_app TO CURRENT_USER;   -- 로그인 롤이 SET ROLE smartdesk_app 가능하도록
GRANT USAGE ON SCHEMA public TO smartdesk_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO smartdesk_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO smartdesk_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO smartdesk_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO smartdesk_app;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'analytics') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA analytics TO smartdesk_app';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA analytics TO smartdesk_app';
        EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA analytics TO smartdesk_app';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA analytics GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO smartdesk_app';
        -- REFRESH MATERIALIZED VIEW 권한 (PG 17 MAINTAIN)
        EXECUTE 'GRANT MAINTAIN ON analytics.ticket_resolution_stats TO smartdesk_app';
    END IF;
END $$;

-- ── 2. 컨텍스트 함수 ────────────────────────────────────────────
CREATE OR REPLACE FUNCTION app_current_client_id() RETURNS bigint
    LANGUAGE sql STABLE PARALLEL SAFE AS
$$ SELECT nullif(current_setting('app.client_id', true), '')::bigint $$;

CREATE OR REPLACE FUNCTION app_is_si() RETURNS boolean
    LANGUAGE sql STABLE PARALLEL SAFE AS
$$ SELECT coalesce(current_setting('app.is_si', true), 'false') = 'true' $$;

GRANT EXECUTE ON FUNCTION app_current_client_id(), app_is_si() TO smartdesk_app;

-- ── 3. 정책 ────────────────────────────────────────────────────
DO $$
DECLARE tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['ticket', 'contract', 'system_asset'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format($f$
            CREATE POLICY tenant_isolation ON %I
            USING (app_is_si() OR client_id = app_current_client_id())
            WITH CHECK (app_is_si() OR client_id = app_current_client_id())
        $f$, tbl);
    END LOOP;
END $$;

ALTER TABLE comment ENABLE ROW LEVEL SECURITY;
ALTER TABLE comment FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON comment
    USING (app_is_si() OR EXISTS (
        SELECT 1 FROM ticket t WHERE t.id = comment.ticket_id
        AND t.client_id = app_current_client_id()))
    WITH CHECK (app_is_si() OR EXISTS (
        SELECT 1 FROM ticket t WHERE t.id = comment.ticket_id
        AND t.client_id = app_current_client_id()));

ALTER TABLE document_share ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_share FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_share
    USING (app_is_si() OR client_id = app_current_client_id())
    WITH CHECK (app_is_si() OR client_id = app_current_client_id());

ALTER TABLE document ENABLE ROW LEVEL SECURITY;
ALTER TABLE document FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document
    USING (app_is_si() OR (scope = 'CLIENT_SHARED' AND EXISTS (
        SELECT 1 FROM document_share s WHERE s.document_id = document.id
        AND s.client_id = app_current_client_id())))
    WITH CHECK (app_is_si());
