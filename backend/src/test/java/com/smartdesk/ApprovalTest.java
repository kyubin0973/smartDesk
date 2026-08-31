package com.smartdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C8: 승인자(관리자) 워크플로 — 해결 → (승인) 종료 / (반려) 처리중. */
class ApprovalTest extends AbstractIntegrationTest {

    /** 새 티켓을 담당자 2번에게 배정하고 RESOLVED 까지 진행. */
    private long resolvedTicket() throws Exception {
        long id = tree(mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + clientAToken)
                        .contentType("application/json")
                        .content("{\"title\":\"승인 테스트 접속 오류\",\"content\":\"x\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        putOk(id, "assignee", "{\"assigneeId\":2}");
        putOk(id, "status", "{\"status\":\"IN_PROGRESS\"}");
        putOk(id, "status", "{\"status\":\"RESOLVED\"}");
        return id;
    }

    private void putOk(long id, String sub, String body) throws Exception {
        mvc.perform(put("/api/tickets/" + id + "/" + sub).header("Authorization", "Bearer " + siToken)
                .contentType("application/json").content(body)).andExpect(status().isOk());
    }

    @Test
    void manager_approves_resolvedTicket_closesIt() throws Exception {
        long id = resolvedTicket();
        String body = mvc.perform(post("/api/tickets/" + id + "/approve").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode t = tree(body);
        assertEquals("CLOSED", t.get("status").asText());
        assertFalse(t.get("closedAt").isNull());
    }

    @Test
    void agent_cannotApprove() throws Exception {
        long id = resolvedTicket();
        mvc.perform(post("/api/tickets/" + id + "/approve").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotApprove_ticketNotResolved() throws Exception {
        long id = tree(mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + clientAToken)
                        .contentType("application/json").content("{\"title\":\"미해결\",\"content\":\"x\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/tickets/" + id + "/approve").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isConflict());
    }

    @Test
    void directResolvedToClosed_viaStatus_isBlocked() throws Exception {
        long id = resolvedTicket();
        mvc.perform(put("/api/tickets/" + id + "/status").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json").content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void manager_rejects_reopensToInProgress_withReasonComment() throws Exception {
        long id = resolvedTicket();
        String body = mvc.perform(post("/api/tickets/" + id + "/reject").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json").content("{\"reason\":\"로그 첨부 누락\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("IN_PROGRESS", tree(body).get("status").asText());
        assertTrue(tree(body).get("resolvedAt").isNull());

        String thread = mvc.perform(get("/api/tickets/" + id + "/comments").header("Authorization", "Bearer " + siToken))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        boolean hasRejectComment = false;
        for (JsonNode c : tree(thread).get("comments")) {
            if (c.get("content").asText().equals("[반려] 로그 첨부 누락")) hasRejectComment = true;
        }
        assertTrue(hasRejectComment, "반려 사유 코멘트가 남아야 함");
    }

    @Test
    void reject_requiresReason() throws Exception {
        long id = resolvedTicket();
        mvc.perform(post("/api/tickets/" + id + "/reject").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dashboard_pendingApproval_countsResolved() throws Exception {
        String before = mvc.perform(get("/api/dashboard/clients/1").header("Authorization", "Bearer " + siToken))
                .andReturn().getResponse().getContentAsString();
        long p0 = tree(before).get("pendingApproval").asLong();

        resolvedTicket();

        String after = mvc.perform(get("/api/dashboard/clients/1").header("Authorization", "Bearer " + siToken))
                .andReturn().getResponse().getContentAsString();
        assertEquals(p0 + 1, tree(after).get("pendingApproval").asLong());
    }
}
