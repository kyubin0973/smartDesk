-- 단계 4: ShedLock — 다중 인스턴스에서 스케줄 잡이 한 번만 실행되도록 분산 락.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

GRANT SELECT, INSERT, UPDATE, DELETE ON shedlock TO smartdesk_app;
