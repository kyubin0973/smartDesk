package com.smartdesk;

import com.smartdesk.common.tenant.TenantContext;
import com.smartdesk.support.PgVectorContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 단계 4: Row-Level Security 가 DB 레벨에서 테넌시를 강제하는지.
 * 비트랜잭션 — 커넥션 borrow 시점에 TenantContext 로 세션 변수가 세팅되는 경로를 실제로 탄다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RlsTest extends PgVectorContainer {

    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void reset() {
        TenantContext.clear();
    }

    private long tickets(String where) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM ticket WHERE " + where, Long.class);
        return n == null ? 0 : n;
    }

    @Test
    void clientContext_seesOnlyOwnRows() {
        TenantContext.setClient(1L);
        assertTrue(tickets("client_id = 1") >= 1, "자기 고객사 티켓은 보여야 함");
        assertEquals(0, tickets("client_id = 2"), "다른 고객사 티켓은 RLS 로 차단");
        assertEquals(tickets("client_id = 1"), tickets("1=1"), "전체 조회해도 자기 것만");
    }

    @Test
    void systemContext_seesAll() {
        TenantContext.setSystem();
        assertTrue(tickets("client_id = 1") >= 1);
        assertTrue(tickets("client_id = 2") >= 1);
    }

    @Test
    void denyContext_seesNothing() {
        TenantContext.deny();
        assertEquals(0, tickets("1=1"), "미인증 컨텍스트는 아무 행도 못 봄 (fail-closed)");
    }

    @Test
    void clientCannotInsertForeignClientRow() {
        TenantContext.setClient(1L);
        assertThrows(Exception.class, () -> jdbc.update("""
            INSERT INTO ticket (client_id, contract_id, requester_id, title, content, priority, status, created_at, updated_at)
            VALUES (2, 1, 1, 'x', 'x', 'MEDIUM', 'RECEIVED', now(), now())
            """), "다른 고객사 client_id 로 INSERT 는 WITH CHECK 로 거부");
    }

    @Test
    void document_clientSeesOnlySharedNotSiInternal() {
        TenantContext.setClient(1L);
        // 시드: 문서 1 = SI_INTERNAL, 문서 2 = CLIENT_SHARED → client 1
        Long siInternal = jdbc.queryForObject(
                "SELECT count(*) FROM document WHERE scope = 'SI_INTERNAL'", Long.class);
        assertEquals(0, siInternal, "고객사 담당자는 SI 내부 문서 안 보임");
        Long shared = jdbc.queryForObject(
                "SELECT count(*) FROM document WHERE scope = 'CLIENT_SHARED'", Long.class);
        assertTrue(shared >= 1, "공유 문서는 보임");
    }
}
