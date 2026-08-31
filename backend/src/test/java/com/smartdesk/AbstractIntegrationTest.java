package com.smartdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 테스트 베이스. 로컬 PostgreSQL 의 smartdesk_test DB + Flyway 시드 사용.
 * 각 테스트는 트랜잭션 롤백되어 격리됨.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    protected String siToken;        // admin@smartdesk.io (MANAGER)
    protected String agentToken;     // infra@smartdesk.io (AGENT)
    protected String clientAToken;   // user@a-corp.com (client 1)
    protected String clientBToken;   // user@b-corp.com (client 2)

    @BeforeEach
    void authenticate() throws Exception {
        siToken = login("/api/auth/login", "admin@smartdesk.io");
        agentToken = login("/api/auth/login", "infra@smartdesk.io");
        clientAToken = login("/api/auth/client-login", "user@a-corp.com");
        clientBToken = login("/api/auth/client-login", "user@b-corp.com");
    }

    protected String login(String path, String email) throws Exception {
        String res = mvc.perform(post(path)
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("accessToken").asText();
    }

    protected JsonNode tree(String body) throws Exception {
        return json.readTree(body);
    }
}
