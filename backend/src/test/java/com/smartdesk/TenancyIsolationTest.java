package com.smartdesk;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REQ-N-001: 고객사 간 데이터 격리. */
class TenancyIsolationTest extends AbstractIntegrationTest {

    @Test
    void clientUser_cannotReadOtherClientsTicket() throws Exception {
        // 티켓 1042 는 client 1(A고객사). client 2(B고객사) 담당자로 접근 → 403
        mvc.perform(get("/api/tickets/1042").header("Authorization", "Bearer " + clientBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientUser_cannotReadOtherClientsDashboard() throws Exception {
        mvc.perform(get("/api/dashboard/clients/1").header("Authorization", "Bearer " + clientBToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/clients/2").header("Authorization", "Bearer " + clientBToken))
                .andExpect(status().isOk());
    }

    @Test
    void clientUser_ticketListIsScopedToOwnClient() throws Exception {
        String body = mvc.perform(get("/api/tickets").header("Authorization", "Bearer " + clientBToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // B고객사 담당자는 client 2 티켓만 (1044)
        tree(body).get("content").forEach(t -> {
            if (t.get("clientId").asLong() != 2L) throw new AssertionError("타 고객사 티켓 노출: " + t);
        });
    }

    @Test
    void clientUser_cannotAccessSiOnlyEndpoints() throws Exception {
        mvc.perform(get("/api/clients").header("Authorization", "Bearer " + clientAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_getsJsonUnauthorized() throws Exception {
        mvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void siUser_cannotCreateTicketWithoutValidContract_forNonexistentClient() throws Exception {
        mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json")
                        .content("{\"clientId\":999,\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().is4xxClientError());
    }
}
