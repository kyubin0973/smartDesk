package com.smartdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketLifecycleTest extends AbstractIntegrationTest {

    private JsonNode createTicket(String title, String content) throws Exception {
        String body = mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + clientAToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("title", title, "content", content))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return tree(body);
    }

    @Test
    void create_infersPriorityAndSlaDueAndSuggestsCategory() throws Exception {
        JsonNode t = createTicket("VPN 접속 장애로 전체 중단", "로그인 인증 오류가 발생합니다");
        assertEquals("CRITICAL", t.get("priority").asText(), "'장애'/'중단' 키워드 → CRITICAL");
        assertFalse(t.get("slaDueAt").isNull(), "계약 sla_resolution_min 기준 마감시각이 설정되어야 함");
        assertEquals("Access", t.get("categoryName").asText(), "'접속'/'인증' → Access 자동분류");
    }

    @Test
    void statusTransition_rejectsIllegalJump_andStampsTimestamps() throws Exception {
        long id = createTicket("문의", "확인 부탁드립니다").get("id").asLong();

        // RECEIVED → RESOLVED 는 불가
        mvc.perform(put("/api/tickets/" + id + "/status").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json").content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());

        // RECEIVED → IN_PROGRESS → RESOLVED
        mvc.perform(put("/api/tickets/" + id + "/status").header("Authorization", "Bearer " + siToken)
                .contentType("application/json").content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk());
        String body = mvc.perform(put("/api/tickets/" + id + "/status").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json").content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode t = tree(body);
        assertFalse(t.get("firstRespondedAt").isNull());
        assertFalse(t.get("resolvedAt").isNull());
    }

    @Test
    void autoAssign_setsAssigneeAndMovesToInProgress() throws Exception {
        long id = createTicket("ERP 배치 오류", "NullPointerException 으로 실패").get("id").asLong();
        String body = mvc.perform(put("/api/tickets/" + id + "/assignee").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode t = tree(body);
        assertFalse(t.get("assigneeId").isNull(), "자동배정 결과가 있어야 함");
        assertEquals("IN_PROGRESS", t.get("status").asText());
    }

    @Test
    void clientUser_cannotChangeStatus() throws Exception {
        long id = createTicket("문의", "질문 있습니다").get("id").asLong();
        mvc.perform(put("/api/tickets/" + id + "/status").header("Authorization", "Bearer " + clientAToken)
                        .contentType("application/json").content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void relatedDocuments_returnsSameCategoryDocs() throws Exception {
        // 시드: 티켓 1042 = Access(2), 문서 1 = Access(2) SI_INTERNAL
        String body = mvc.perform(get("/api/tickets/1042/related-documents").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(tree(body).size() >= 1);
    }

    @Test
    void relatedDocuments_clientUser_seesOnlySharedDocs() throws Exception {
        // 티켓 1043 = client 1, category 5(Application). 문서 2 = category 5, CLIENT_SHARED→client 1. 문서 1 = Access, SI_INTERNAL.
        String body = mvc.perform(get("/api/tickets/1043/related-documents").header("Authorization", "Bearer " + clientAToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode docs = tree(body);
        assertTrue(docs.size() >= 1);
        docs.forEach(d -> assertEquals("CLIENT_SHARED", d.get("scope").asText(), "고객사 담당자는 공유 문서만"));
    }

    @Test
    void siProxyCreate_requiresValidRequesterOfThatClient() throws Exception {
        // requesterId 누락 → 400
        mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json")
                        .content("{\"clientId\":1,\"title\":\"대리 등록\",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest());

        // 요청자가 다른 고객사 소속 (client_user 2 = client 2) → 400
        mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json")
                        .content("{\"clientId\":1,\"requesterId\":2,\"title\":\"대리 등록\",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest());

        // 올바른 요청자 (client_user 1 = client 1) → 201
        String body = mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json")
                        .content("{\"clientId\":1,\"requesterId\":1,\"title\":\"대리 등록\",\"content\":\"접속 문제\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals(1L, tree(body).get("requesterId").asLong());
    }

    @Test
    void updatePriority_siOnly_andValidatesValue() throws Exception {
        long id = createTicket("문의", "질문").get("id").asLong();

        String body = mvc.perform(put("/api/tickets/" + id + "/priority").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json").content("{\"priority\":\"CRITICAL\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("CRITICAL", tree(body).get("priority").asText());

        mvc.perform(put("/api/tickets/" + id + "/priority").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json").content("{\"priority\":\"URGENT\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/tickets/" + id + "/priority").header("Authorization", "Bearer " + clientAToken)
                        .contentType("application/json").content("{\"priority\":\"LOW\"}"))
                .andExpect(status().isForbidden());
    }
}
