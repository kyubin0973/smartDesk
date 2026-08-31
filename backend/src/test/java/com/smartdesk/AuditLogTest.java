package com.smartdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C11: 감사 로그 조회 (관리자 전용). */
class AuditLogTest extends AbstractIntegrationTest {

    private JsonNode audit(String query) throws Exception {
        String body = mvc.perform(get("/api/audit" + query).header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return tree(body);
    }

    @Test
    void managerOnly() throws Exception {
        mvc.perform(get("/api/audit").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit/ticket-events").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").header("Authorization", "Bearer " + clientAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginEventsAreRecorded() throws Exception {
        // @BeforeEach 의 로그인들이 이미 LOGIN_SUCCESS 를 남김 (REQUIRES_NEW 로 커밋됨)
        JsonNode res = audit("?action=LOGIN_SUCCESS&size=5");
        assertTrue(res.get("totalElements").asLong() >= 1);
        res.get("content").forEach(r -> assertEquals("LOGIN_SUCCESS", r.get("action").asText()));
    }

    @Test
    void loginFailure_isRecordedEvenThoughRequestFails() throws Exception {
        long before = audit("?action=LOGIN_FAILURE").get("totalElements").asLong();
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"admin@smartdesk.io\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
        long after = audit("?action=LOGIN_FAILURE").get("totalElements").asLong();
        assertEquals(before + 1, after, "실패한 로그인도 감사에 남아야 함");
    }

    @Test
    void adminAction_createUser_isAudited() throws Exception {
        long before = audit("?action=USER_CREATED").get("totalElements").asLong();
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json")
                        .content("{\"name\":\"감사대상\",\"email\":\"audit-target@smartdesk.io\",\"password\":\"Passw0rd!\",\"role\":\"AGENT\",\"departmentId\":1}"))
                .andExpect(status().isCreated());
        long after = audit("?action=USER_CREATED").get("totalElements").asLong();
        assertEquals(before + 1, after);
        assertEquals("audit-target@smartdesk.io (AGENT)", audit("?action=USER_CREATED&size=1").get("content").get(0).get("detail").asText());
    }

    @Test
    void ticketEvents_listWithActorAndTitle() throws Exception {
        // CREATED 이벤트 하나 생성
        mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + clientAToken)
                        .contentType("application/json")
                        .content("{\"title\":\"감사 이벤트 테스트\",\"content\":\"x\"}"))
                .andExpect(status().isCreated());

        String body = mvc.perform(get("/api/audit/ticket-events?type=CREATED&size=5")
                        .header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode res = tree(body);
        assertTrue(res.get("totalElements").asLong() >= 1);
        JsonNode row = res.get("content").get(0);
        assertEquals("CREATED", row.get("type").asText());
        assertNotNull(row.get("ticketTitle").asText());
        assertNotNull(row.get("actor").asText());
    }

    @Test
    void documentView_byClientUser_isAudited_notForSi() throws Exception {
        long before = audit("?action=DOCUMENT_VIEWED").get("totalElements").asLong();
        // 문서 2 = CLIENT_SHARED → client 1
        mvc.perform(get("/api/documents/2").header("Authorization", "Bearer " + clientAToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/documents/2").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk());
        assertEquals(before + 1, audit("?action=DOCUMENT_VIEWED").get("totalElements").asLong(),
                "고객사 담당자 열람만 감사 (SI 는 제외)");
    }

    @Test
    void badDate_is400() throws Exception {
        mvc.perform(get("/api/audit?from=not-a-date").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isBadRequest());
    }
}
