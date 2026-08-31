package com.smartdesk;

import com.smartdesk.domain.Enums.TicketStatus;
import com.smartdesk.repo.AppUserRepo;
import com.smartdesk.repo.ClientUserRepo;
import com.smartdesk.repo.TicketRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REQ-E-003 / REQ-E-004: 계정 비활성화 + 열린 티켓 재배정. */
class DeactivationTest extends AbstractIntegrationTest {

    @Autowired AppUserRepo users;
    @Autowired ClientUserRepo clientUsers;
    @Autowired TicketRepo tickets;

    @Test
    void deactivateSiAgent_reassignsOpenTickets_andBlocksLogin_andRevokesTokens() throws Exception {
        // 시드: user 2(infra@) 는 ticket 1042(IN_PROGRESS, client 1) 담당
        assertEquals(2L, tickets.findById(1042L).orElseThrow().getAssigneeId());

        mvc.perform(patch("/api/users/2/deactivate").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk());

        // 비활성화됨
        assertFalse(users.findById(2L).orElseThrow().isActive());
        // 열린 티켓은 다른 담당자로 재배정 (부서 관리자 = user 1)
        Long newAssignee = tickets.findById(1042L).orElseThrow().getAssigneeId();
        assertNotNull(newAssignee);
        assertNotEquals(2L, newAssignee);
        // 로그인 차단
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"infra@smartdesk.io\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivate_requiresManager() throws Exception {
        mvc.perform(patch("/api/users/2/deactivate").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotDeactivateSelf() throws Exception {
        mvc.perform(patch("/api/users/1/deactivate").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivateClientUser_blocksLogin_butKeepsTickets() throws Exception {
        long ticketsBefore = tickets.findByClientId(1L).size();

        mvc.perform(patch("/api/client-users/1/deactivate").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk());

        assertFalse(clientUsers.findById(1L).orElseThrow().isActive());
        assertEquals(ticketsBefore, tickets.findByClientId(1L).size(), "티켓·이력 유지");
        mvc.perform(post("/api/auth/client-login").contentType("application/json")
                        .content("{\"email\":\"user@a-corp.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createSiUser_managerOnly_andRejectsDuplicateEmail() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + agentToken)
                        .contentType("application/json")
                        .content("{\"name\":\"x\",\"email\":\"x@smartdesk.io\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/users").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json")
                        .content("{\"name\":\"신규\",\"email\":\"new-si@smartdesk.io\",\"password\":\"Passw0rd!\",\"role\":\"AGENT\",\"departmentId\":1}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/users").header("Authorization", "Bearer " + siToken)
                        .contentType("application/json")
                        .content("{\"name\":\"중복\",\"email\":\"admin@smartdesk.io\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isConflict());
    }
}
