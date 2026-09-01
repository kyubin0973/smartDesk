package com.smartdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 0.5-g: 로그인 세션(리프레시 토큰) 목록·개별 종료. */
class SessionManagementTest extends AbstractIntegrationTest {

    private JsonNode sessions(String token) throws Exception {
        String body = mvc.perform(get("/api/auth/sessions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tree(body);
    }

    @Test
    void listsOwnSessions() throws Exception {
        JsonNode list = sessions(siToken);
        assertTrue(list.isArray());
        assertTrue(list.size() >= 1, "로그인으로 최소 1개 세션 존재");
        list.forEach(s -> assertFalse(s.get("revoked").asBoolean()));
    }

    @Test
    void revokeOwnSession_marksRevoked() throws Exception {
        long id = sessions(siToken).get(0).get("id").asLong();
        mvc.perform(delete("/api/auth/sessions/" + id).header("Authorization", "Bearer " + siToken))
                .andExpect(status().isNoContent());

        boolean stillActive = false;
        for (JsonNode s : sessions(siToken)) {
            if (s.get("id").asLong() == id) stillActive = !s.get("revoked").asBoolean();
        }
        assertFalse(stillActive, "종료한 세션은 revoked 여야 함");
    }

    @Test
    void cannotRevokeOthersSession() throws Exception {
        long othersId = sessions(clientAToken).get(0).get("id").asLong();
        mvc.perform(delete("/api/auth/sessions/" + othersId).header("Authorization", "Bearer " + siToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownSession_is404() throws Exception {
        mvc.perform(delete("/api/auth/sessions/999999").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isNotFound());
    }
}
