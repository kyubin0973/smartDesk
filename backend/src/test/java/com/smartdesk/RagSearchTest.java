package com.smartdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartdesk.domain.Document;
import com.smartdesk.domain.DocumentShare;
import com.smartdesk.domain.Enums.DocScope;
import com.smartdesk.domain.Enums.TicketStatus;
import com.smartdesk.domain.Ticket;
import com.smartdesk.feature.rag.EmbeddingClient;
import com.smartdesk.feature.rag.EmbeddingStore;
import com.smartdesk.feature.rag.IndexingService;
import com.smartdesk.repo.DocumentRepo;
import com.smartdesk.repo.DocumentShareRepo;
import com.smartdesk.repo.TicketRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 단계 2.2: 하이브리드 검색 + 테넌시 필터. 임베딩은 결정적 가짜(BoW)로 대체. */
@TestPropertySource(properties = {"smartdesk.rag.enabled=true"})
class RagSearchTest extends AbstractIntegrationTest {

    @MockBean EmbeddingClient embedder;
    @Autowired IndexingService indexing;
    @Autowired EmbeddingStore store;
    @Autowired DocumentRepo documents;
    @Autowired DocumentShareRepo shares;
    @Autowired TicketRepo tickets;

    /** 단어 해시 → 384차원 BoW, 정규화. 비슷한 텍스트 → 비슷한 벡터. */
    static float[] fakeVec(String text) {
        float[] v = new float[384];
        for (String w : text.toLowerCase().split("\\W+")) {
            if (w.isBlank()) continue;
            v[Math.abs(w.hashCode()) % 384] += 1f;
        }
        double norm = 0;
        for (float f : v) norm += f * f;
        norm = Math.sqrt(norm) + 1e-9;
        for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
        return v;
    }

    private Long vpnDocId, deployDocId, vpnTicketId, queryTicketId;

    @BeforeEach
    void index() {
        when(embedder.embedQuery(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> fakeVec(inv.getArgument(0)));
        when(embedder.embedPassages(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return new EmbeddingClient.EmbedResult("fake", 384,
                    texts.stream().map(RagSearchTest::fakeVec).toList());
        });

        vpnDocId = doc("VPN 접속 오류 해결 가이드", "사내 VPN 로그인 계정 인증 실패 시 비밀번호 재설정과 MFA 재등록으로 해결한다",
                DocScope.CLIENT_SHARED, 1L);
        deployDocId = doc("서버 배포 절차", "블루그린 배포와 롤백 순서를 정의한다", DocScope.SI_INTERNAL, null);
        vpnTicketId = closedTicket(1L, "VPN 연결이 계속 끊깁니다", "재택 근무 중 VPN 접속 계정 인증 오류 반복");
        queryTicketId = openTicket(1L, "VPN 로그인 계정 문제", "VPN 접속 시 인증 실패");

        indexing.indexDocument(vpnDocId);
        indexing.indexDocument(deployDocId);
        indexing.indexTicket(vpnTicketId);
    }

    private Long doc(String title, String content, DocScope scope, Long shareWithClient) {
        Document d = new Document();
        d.setCreatedBy(1L);
        d.setTitle(title);
        d.setContent(content);
        d.setScope(scope);
        d.setVersion(1);
        d.setCreatedAt(Instant.now());
        d.setUpdatedAt(Instant.now());
        d = documents.save(d);
        if (shareWithClient != null) shares.save(new DocumentShare(d.getId(), shareWithClient));
        return d.getId();
    }

    private Long closedTicket(long clientId, String title, String content) {
        Long id = openTicket(clientId, title, content);
        Ticket t = tickets.findById(id).orElseThrow();
        t.setStatus(TicketStatus.CLOSED);
        t.setResolvedAt(Instant.now());
        t.setClosedAt(Instant.now());
        return tickets.save(t).getId();
    }

    private Long openTicket(long clientId, String title, String content) {
        Ticket t = new Ticket();
        t.setClientId(clientId);
        t.setContractId(1L);
        t.setRequesterId(clientId == 1L ? 1L : 2L);
        t.setTitle(title);
        t.setContent(content);
        t.setStatus(TicketStatus.RECEIVED);
        t.setCreatedAt(Instant.now());
        return tickets.save(t).getId();
    }

    private JsonNode related(String token, Long ticketId) throws Exception {
        String body = mvc.perform(post("/api/ai/tickets/" + ticketId + "/related")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return tree(body);
    }

    @Test
    void si_getsSimilarDocumentAndClosedTicket() throws Exception {
        JsonNode r = related(siToken, queryTicketId);
        assertTrue(r.get("ragUsed").asBoolean());
        List<Long> docIds = ids(r.get("documents"));
        List<Long> tixIds = ids(r.get("tickets"));
        assertTrue(docIds.contains(vpnDocId), "VPN 문서가 상위에 있어야 함");
        assertTrue(tixIds.contains(vpnTicketId), "유사 종료 티켓이 있어야 함");
        assertFalse(tixIds.contains(queryTicketId), "질의 티켓 자신은 제외");
    }

    @Test
    void clientUser_seesOnlySharedDoc_notSiInternal() throws Exception {
        JsonNode r = related(clientAToken, queryTicketId);
        List<Long> docIds = ids(r.get("documents"));
        assertTrue(docIds.contains(vpnDocId), "공유받은 문서는 보임");
        assertFalse(docIds.contains(deployDocId), "SI 내부 문서는 안 보임 (REQ-N-001)");
    }

    @Test
    void otherClientUser_cannotQueryForeignTicket() throws Exception {
        mvc.perform(post("/api/ai/tickets/" + queryTicketId + "/related")
                        .header("Authorization", "Bearer " + clientBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void answerDraft_llmDisabled_returnsCitationsOnly() throws Exception {
        // provider=none (기본) → 초안 텍스트 없이 근거 문서만
        String body = mvc.perform(post("/api/ai/tickets/" + queryTicketId + "/answer-draft")
                        .header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode r = tree(body);
        assertFalse(r.get("llmUsed").asBoolean());
        assertTrue(r.get("citations").size() >= 1);
        assertTrue(r.get("citations").get(0).has("documentId"));
    }

    @Test
    void answerDraft_clientUserForbidden() throws Exception {
        mvc.perform(post("/api/ai/tickets/" + queryTicketId + "/answer-draft")
                        .header("Authorization", "Bearer " + clientAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void answerDraft_embedServiceDown_returns503_not500() throws Exception {
        when(embedder.embedQuery(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("connection refused"));
        mvc.perform(post("/api/ai/tickets/" + queryTicketId + "/answer-draft")
                        .header("Authorization", "Bearer " + siToken))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void reindex_replacesChunks_whenContentChanges() {
        var d = documents.findById(vpnDocId).orElseThrow();
        d.setContent("완전히 다른 내용으로 교체 " + "가".repeat(1500));
        d.setUpdatedAt(Instant.now());
        documents.save(d);

        long before = store.count("DOCUMENT");
        indexing.indexDocument(vpnDocId);          // 재색인 (락 + DELETE + INSERT)
        long after = store.count("DOCUMENT");
        assertNotEquals(before, after, "긴 새 내용 → 청크 수가 달라져야 함");
        // 다시 호출해도 멱등 (해시 동일 → no-op)
        indexing.indexDocument(vpnDocId);
        assertEquals(after, store.count("DOCUMENT"));
    }

    private static List<Long> ids(JsonNode arr) {
        return java.util.stream.StreamSupport.stream(arr.spliterator(), false)
                .map(n -> n.get("id").asLong()).toList();
    }
}
