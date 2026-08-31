package com.smartdesk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdesk.domain.Enums.NotificationType;
import com.smartdesk.domain.Notification;
import com.smartdesk.repo.NotificationRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 알림 API. NotificationWriter 가 REQUIRES_NEW 커밋이므로 비트랜잭션 + 수동 정리. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired NotificationRepo repo;

    private final List<Long> created = new ArrayList<>();
    private String siToken;   // admin@smartdesk.io = USER id 1

    @BeforeEach
    void setup() throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"admin@smartdesk.io\",\"password\":\"Passw0rd!\"}"))
                .andReturn().getResponse().getContentAsString();
        siToken = json.readTree(res).get("accessToken").asText();

        created.add(save("USER", 1L, "알림 A"));
        created.add(save("USER", 1L, "알림 B"));
        created.add(save("USER", 2L, "다른 사람 알림")); // 노출되면 안 됨
    }

    @AfterEach
    void cleanup() {
        created.forEach(repo::deleteById);
        created.clear();
    }

    private Long save(String type, Long id, String title) {
        Notification n = new Notification();
        n.setRecipientType(type);
        n.setRecipientId(id);
        n.setType(NotificationType.TICKET_COMMENTED);
        n.setTitle(title);
        return repo.save(n).getId();
    }

    @Test
    void list_scopedToCaller_withUnreadCount() throws Exception {
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread", org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.items[*].title", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.not("다른 사람 알림"))));
    }

    @Test
    void markRead_and_markAllRead() throws Exception {
        Long first = created.get(0);
        mvc.perform(patch("/api/notifications/" + first + "/read").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertNotNull(repo.findById(first).orElseThrow().getReadAt());

        mvc.perform(patch("/api/notifications/read-all").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                repo.countByRecipientTypeAndRecipientIdAndReadAtIsNull("USER", 1L));
    }

    @Test
    void cannotMarkOthersNotification() throws Exception {
        Long othersId = created.get(2); // recipient = USER 2
        mvc.perform(patch("/api/notifications/" + othersId + "/read").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isForbidden());
    }
}
