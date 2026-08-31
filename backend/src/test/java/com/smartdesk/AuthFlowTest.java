package com.smartdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowTest extends AbstractIntegrationTest {

    @Test
    void refresh_rotatesAndIssuesNewAccessToken() throws Exception {
        String login = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"app@smartdesk.io\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refresh = tree(login).get("refreshToken").asText();

        String r1 = mvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNotNull(tree(r1).get("accessToken").asText());

        // 회전됨 — 같은 리프레시 토큰 재사용 불가
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesAccessToken() throws Exception {
        String login = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"sec@smartdesk.io\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode t = tree(login);
        String access = t.get("accessToken").asText();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access)
                .contentType("application/json").content("{\"refreshToken\":\"" + t.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isNoContent());

        // 폐기된 액세스 토큰 → 401
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fiveFailedLogins_locksAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                            .content("{\"email\":\"app@smartdesk.io\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        // 6번째 — 잠금
        String body = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"app@smartdesk.io\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertEquals("LOCKED", tree(body).get("code").asText());
    }

    @Test
    void wrongTab_siEmailOnClientLogin_fails() throws Exception {
        mvc.perform(post("/api/auth/client-login").contentType("application/json")
                        .content("{\"email\":\"admin@smartdesk.io\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isUnauthorized());
    }
}
