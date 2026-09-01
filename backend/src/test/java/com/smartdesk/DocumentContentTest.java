package com.smartdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 0.5-c: 문서 본문 리치텍스트 저장 시 서버 sanitize. */
class DocumentContentTest extends AbstractIntegrationTest {

    @Test
    void richTextIsSanitizedOnSave() throws Exception {
        String payload = "{\"title\":\"리치텍스트 문서\",\"content\":"
                + "\"<h2>가이드</h2><p><strong>중요</strong></p><script>alert(1)</script>"
                + "<img src=x onerror=alert(1)>\",\"scope\":\"SI_INTERNAL\"}";

        String created = mvc.perform(post("/api/documents").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json").content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        long id = tree(created).get("id").asLong();

        JsonNode doc = tree(mvc.perform(get("/api/documents/" + id).header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        String content = doc.get("content").asText();
        assertTrue(content.contains("<h2>"), "안전한 서식은 유지");
        assertTrue(content.contains("<strong>"));
        assertFalse(content.toLowerCase().contains("<script"), "script 제거");
        assertFalse(content.toLowerCase().contains("onerror"), "이벤트 핸들러 제거");
    }
}
