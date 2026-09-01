package com.smartdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 단계 1.1: 분석 마트 API (관리자 전용). */
class AnalyticsTest extends AbstractIntegrationTest {

    private JsonNode getJson(String path, String token) throws Exception {
        return tree(mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void managerOnly() throws Exception {
        for (String p : new String[]{"/api/analytics/overview", "/api/analytics/resolution-stats",
                "/api/analytics/assignee-throughput", "/api/analytics/sla-recommendation"}) {
            mvc.perform(get(p).header("Authorization", "Bearer " + agentToken))
                    .andExpect(status().isForbidden());
            mvc.perform(get(p).header("Authorization", "Bearer " + clientAToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void overview_hasHeadlineAndPriorityBreakdown() throws Exception {
        JsonNode r = getJson("/api/analytics/overview", siToken);
        assertTrue(r.get("headline").has("resolved_count"));
        assertTrue(r.get("headline").has("p50_minutes"));
        assertTrue(r.get("byPriority").isArray());
    }

    @Test
    void resolutionStats_isArray() throws Exception {
        assertTrue(getJson("/api/analytics/resolution-stats", siToken).isArray());
        assertTrue(getJson("/api/analytics/heatmap", siToken).isArray());
    }

    @Test
    void assigneeThroughput_isArray() throws Exception {
        assertTrue(getJson("/api/analytics/assignee-throughput", siToken).isArray());
    }

    @Test
    void refresh_materializedView() throws Exception {
        mvc.perform(post("/api/analytics/refresh").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isNoContent());
        // 갱신 후에도 통계 조회가 정상
        assertTrue(getJson("/api/analytics/sla-recommendation", siToken).isArray());
    }
}
