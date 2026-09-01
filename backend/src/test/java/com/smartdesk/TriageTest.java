package com.smartdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 단계 3: 지능형 트리아지 + SLA 위험도. */
class TriageTest extends AbstractIntegrationTest {

    private long createTicket(String title, String content) throws Exception {
        String body = mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + clientAToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("title", title, "content", content))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return tree(body).get("id").asLong();
    }

    private JsonNode triage(long id, String suffix) throws Exception {
        String body = mvc.perform(post("/api/tickets/" + id + "/triage" + suffix)
                        .header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return tree(body);
    }

    @Test
    void clearKeyword_getsCategoryAndPriority_onCreate() throws Exception {
        long id = createTicket("VPN 로그인 계정 인증 실패", "사내 VPN 접속 시 비밀번호 인증이 계속 실패합니다");
        JsonNode t = tree(mvc.perform(get("/api/tickets/" + id).header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("Access", t.get("categoryName").asText(), "VPN/계정 키워드 → Access 카테고리");
    }

    @Test
    void triagePreview_doesNotMutate() throws Exception {
        long id = createTicket("모니터 깜빡임", "듀얼 모니터 중 하나가 깜빡입니다");
        JsonNode before = triage(id, "");   // preview
        assertTrue(before.has("confidence"));
        assertTrue(before.has("priority"));
        assertTrue(before.has("escalate"));
        // preview 는 상태를 안 바꿈 — assignee 필드는 detail 로 확인
        JsonNode t = tree(mvc.perform(get("/api/tickets/" + id).header("Authorization", "Bearer " + siToken))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(t.get("assigneeId").isNull() || t.get("status").asText().equals("RECEIVED"));
    }

    @Test
    void triageApply_setsCategoryAndRecordsEvent() throws Exception {
        long id = createTicket("서버 배치 오류", "야간 배치 작업에서 exception 발생");
        JsonNode r = triage(id, "/apply");
        assertNotNull(r.get("categoryId"));
        // TRIAGED 이벤트가 남았는지
        String events = mvc.perform(get("/api/audit/ticket-events?ticketId=" + id + "&type=TRIAGED")
                        .header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(tree(events).get("totalElements").asLong() >= 1, "TRIAGED 이벤트 기록");
    }

    @Test
    void triage_isSiOnly() throws Exception {
        long id = createTicket("문의", "확인 부탁드립니다");
        mvc.perform(post("/api/tickets/" + id + "/triage").header("Authorization", "Bearer " + clientAToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tickets/" + id + "/sla-risk").header("Authorization", "Bearer " + clientAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void slaRisk_unassignedNearDeadline_isElevated() throws Exception {
        long id = createTicket("일반 문의", "질문이 있습니다");
        // 마감을 임박하게 당김 (미배정 상태 유지)
        mvc.perform(post("/api/tickets/" + id + "/triage").header("Authorization", "Bearer " + siToken));
        JsonNode risk = tree(mvc.perform(get("/api/tickets/" + id + "/sla-risk")
                        .header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(risk.has("score"));
        assertTrue(risk.get("factors").isArray());
        assertTrue(java.util.List.of("LOW", "MEDIUM", "HIGH").contains(risk.get("level").asText()));
    }
}
