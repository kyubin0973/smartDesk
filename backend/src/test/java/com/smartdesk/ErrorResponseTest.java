package com.smartdesk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorResponseTest extends AbstractIntegrationTest {

    @Test
    void unmappedApiPath_returns404NotFound_notInternalError() throws Exception {
        String body = mvc.perform(get("/api/analytics/summary")
                        .header("Authorization", "Bearer " + siToken))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertEquals("NOT_FOUND", tree(body).get("code").asText());
        assertEquals(404, tree(body).get("status").asInt());
    }
}
