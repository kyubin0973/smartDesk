package com.smartdesk.feature.rag;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.Ticket;
import com.smartdesk.repo.TicketRepo;
import com.smartdesk.security.AuthPrincipal;
import com.smartdesk.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 단계 2: 유사 문서/티켓 추천 + RAG 답변 초안. */
@RestController
@RequestMapping("/api/ai")
public class RagController {

    private final RagSearchService search;
    private final AnswerDraftService drafts;
    private final IndexingService indexing;
    private final EmbeddingStore store;
    private final TicketRepo tickets;
    private final RagProperties props;
    private final LlmClient llm;

    public RagController(RagSearchService search, AnswerDraftService drafts, IndexingService indexing,
                         EmbeddingStore store, TicketRepo tickets, RagProperties props, LlmClient llm) {
        this.search = search;
        this.drafts = drafts;
        this.indexing = indexing;
        this.store = store;
        this.tickets = tickets;
        this.props = props;
        this.llm = llm;
    }

    /** 유사 문서 + 유사 과거 티켓 (하이브리드 검색, 테넌시 필터). */
    @PostMapping("/tickets/{ticketId}/related")
    public RagSearchService.Related related(@PathVariable Long ticketId) {
        AuthPrincipal p = CurrentUser.get();
        Ticket t = ticket(ticketId, p);
        if (!search.available()) {
            return new RagSearchService.Related(java.util.List.of(), java.util.List.of(), false);
        }
        return search.relatedForTicket(p, t);
    }

    /** RAG 1차 답변 초안 (SI 담당자 전용 — 검수 후 코멘트로 게시). */
    @PostMapping("/tickets/{ticketId}/answer-draft")
    public AnswerDraftService.Draft answerDraft(@PathVariable Long ticketId) {
        AuthPrincipal p = CurrentUser.requireSiUser();
        ticket(ticketId, p);
        return drafts.forTicket(p, ticketId);
    }

    @GetMapping("/rag/status")
    public Map<String, Object> status() {
        CurrentUser.requireManager();
        return Map.of(
                "enabled", props.isEnabled(),
                "documentChunks", store.count("DOCUMENT"),
                "ticketChunks", store.count("TICKET"),
                "draftEnabled", llm.enabled(),
                "draftModel", llm.model());
    }

    @PostMapping("/rag/reindex")
    public ResponseEntity<Map<String, Object>> reindex() {
        CurrentUser.requireManager();
        if (!props.isEnabled()) {
            throw ApiException.unavailable("RAG_DISABLED", "smartdesk.rag.enabled=true 로 활성화하세요.");
        }
        int[] r = indexing.reconcile();
        return ResponseEntity.ok(Map.of("documentsIndexed", r[0], "ticketsIndexed", r[1]));
    }

    private Ticket ticket(Long id, AuthPrincipal p) {
        Ticket t = tickets.findById(id).orElseThrow(() -> ApiException.notFound("티켓"));
        if (p.isClientUser() && !t.getClientId().equals(p.clientId())) {
            throw ApiException.forbidden("다른 고객사 티켓입니다.");
        }
        return t;
    }
}
