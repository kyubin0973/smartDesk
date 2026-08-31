package com.smartdesk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C9: 비밀번호 재설정(이메일 토큰) + 로그인 상태 변경. */
class PasswordResetTest extends AbstractIntegrationTest {

    private String forgot(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = tree(body).get("devResetToken");
        return node == null ? null : node.asText();
    }

    private void login(String email, String pw, org.springframework.test.web.servlet.ResultMatcher expect) throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + pw + "\"}"))
                .andExpect(expect);
    }

    @Test
    void forgotPassword_unknownEmail_doesNotLeak() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                        .content("{\"email\":\"nobody@nowhere.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devResetToken").doesNotExist());
    }

    @Test
    void resetFlow_changesPassword_revokesSessions() throws Exception {
        // 기존 세션 (리프레시 토큰) 확보
        String login = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"sec@smartdesk.io\",\"password\":\"Passw0rd!\"}"))
                .andReturn().getResponse().getContentAsString();
        String oldRefresh = tree(login).get("refreshToken").asText();

        String token = forgot("sec@smartdesk.io");
        assertNotNull(token, "dev 프로파일에선 devResetToken 이 와야 함");

        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"BrandNew1!\"}"))
                .andExpect(status().isNoContent());

        login("sec@smartdesk.io", "Passw0rd!", status().isUnauthorized());   // 옛 비번 불가
        login("sec@smartdesk.io", "BrandNew1!", status().isOk());            // 새 비번 가능

        // 재설정 전 리프레시 토큰 폐기됨
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reset_rejectsUsedOrGarbageToken_andShortPassword() throws Exception {
        String token = forgot("app@smartdesk.io");
        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"LongEnough1\"}"))
                .andExpect(status().isNoContent());
        // 같은 토큰 재사용 → 400
        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"LongEnough2\"}"))
                .andExpect(status().isBadRequest());
        // 쓰레기 토큰 → 400
        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                        .content("{\"token\":\"garbage\",\"newPassword\":\"LongEnough3\"}"))
                .andExpect(status().isBadRequest());
        // 짧은 비번 → 400
        String t2 = forgot("app@smartdesk.io");
        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                        .content("{\"token\":\"" + t2 + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newForgotRequest_invalidatesPreviousToken() throws Exception {
        String first = forgot("app@smartdesk.io");
        String second = forgot("app@smartdesk.io");
        assertNotEquals(first, second);

        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                        .content("{\"token\":\"" + first + "\",\"newPassword\":\"LongEnough1\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                        .content("{\"token\":\"" + second + "\",\"newPassword\":\"LongEnough1\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void changePassword_authenticated_checksCurrent() throws Exception {
        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + agentToken)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"BrandNew1!\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + agentToken)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"Passw0rd!\",\"newPassword\":\"BrandNew1!\"}"))
                .andExpect(status().isNoContent());

        login("infra@smartdesk.io", "BrandNew1!", status().isOk());
    }

    @Test
    void changePassword_unauthenticated_is401() throws Exception {
        mvc.perform(post("/api/auth/change-password").contentType("application/json")
                        .content("{\"currentPassword\":\"Passw0rd!\",\"newPassword\":\"BrandNew1!\"}"))
                .andExpect(status().isUnauthorized());
    }
}
