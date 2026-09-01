package com.smartdesk.common.tenant;

/**
 * 단계 4: 요청별 테넌시 컨텍스트. RlsDataSource 가 문(statement) 생성 직전 이 값으로
 * Postgres 세션 변수(app.client_id / app.is_si)를 세팅 → RLS 정책이 강제한다.
 *
 * 모든 HTTP 요청은 TenantContextFilter 를 지나며 CLIENT/SYSTEM/DENY 중 하나로 세팅된다.
 * 미설정(스케줄러·@Async·부팅·테스트 스레드)은 SYSTEM(전체 접근).
 */
public final class TenantContext {

    public record Ctx(long clientId, boolean si) {}

    /** 관리자·SI·스케줄러·마이그레이션 — 전체 접근. */
    public static final Ctx SYSTEM = new Ctx(-1, true);
    /** 인증 안 됨 / 알 수 없음 — 아무것도 못 봄. */
    public static final Ctx DENY = new Ctx(-1, false);

    private static final ThreadLocal<Ctx> HOLDER = new ThreadLocal<>();

    private TenantContext() {}

    public static void setClient(long clientId) {
        HOLDER.set(new Ctx(clientId, false));
    }

    public static void setSystem() {
        HOLDER.set(SYSTEM);
    }

    public static void deny() {
        HOLDER.set(DENY);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Ctx current() {
        Ctx c = HOLDER.get();
        return c != null ? c : SYSTEM;
    }

    /** 명시적으로 전체 권한으로 블록 실행 (예: 시스템 배치가 특정 스레드에서 도는 경우). */
    public static void runAsSystem(Runnable body) {
        Ctx prev = HOLDER.get();
        HOLDER.set(SYSTEM);
        try {
            body.run();
        } finally {
            if (prev != null) HOLDER.set(prev);
            else HOLDER.remove();
        }
    }
}
