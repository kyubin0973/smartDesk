package com.smartdesk;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B5/B6: 목록/이력 엔드포인트의 쿼리 수가 **행 수에 비례해 늘지 않는지** (N+1 회귀 방지).
 * 측정 전 flush/clear 로 대기 중인 INSERT 가 SELECT 앞에서 flush 되며 카운트에 섞이는 것을 방지.
 */
class QueryCountTest extends AbstractIntegrationTest {

    @Autowired EntityManagerFactory emf;
    @PersistenceContext EntityManager em;

    private long measureListQueries() {
        em.flush();
        em.clear();
        Statistics s = emf.unwrap(SessionFactory.class).getStatistics();
        s.setStatisticsEnabled(true);
        s.clear();
        try {
            mvc.perform(get("/api/tickets").param("size", "50").header("Authorization", "Bearer " + siToken))
                    .andExpect(status().isOk());
        } catch (Exception e) { throw new RuntimeException(e); }
        return s.getPrepareStatementCount();
    }

    private void createAssignedTicket(int i) throws Exception {
        long id = tree(mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + clientAToken)
                        .contentType("application/json")
                        .content("{\"title\":\"부하 " + i + " 접속 오류 장애\",\"content\":\"x\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/tickets/" + id + "/assignee").header("Authorization", "Bearer " + siToken)
                .contentType("application/json").content("{}"));
    }

    @Test
    void ticketList_isNotNPlusOne() throws Exception {
        createAssignedTicket(1);
        createAssignedTicket(2);
        long few = measureListQueries();

        for (int i = 3; i <= 12; i++) createAssignedTicket(i);
        long many = measureListQueries();

        assertTrue(many <= few + 1,
                "티켓 10건 더 늘었는데 쿼리 수가 비례 증가 (N+1): " + few + " → " + many);
    }

    @Test
    void commentHistory_isNotNPlusOne() throws Exception {
        long base = measureCommentQueries();
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/tickets/1042/comments").header("Authorization",
                            "Bearer " + (i % 2 == 0 ? siToken : clientAToken))
                    .contentType("application/json").content("{\"content\":\"c" + i + "\"}"));
        }
        long withComments = measureCommentQueries();
        assertTrue(withComments <= base + 1,
                "코멘트 10건 추가 후 쿼리 수 비례 증가 (N+1): " + base + " → " + withComments);
    }

    private long measureCommentQueries() {
        em.flush();
        em.clear();
        Statistics s = emf.unwrap(SessionFactory.class).getStatistics();
        s.setStatisticsEnabled(true);
        s.clear();
        try {
            mvc.perform(get("/api/tickets/1042/comments").header("Authorization", "Bearer " + siToken))
                    .andExpect(status().isOk());
        } catch (Exception e) { throw new RuntimeException(e); }
        return s.getPrepareStatementCount();
    }
}
