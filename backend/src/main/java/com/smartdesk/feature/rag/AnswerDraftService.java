package com.smartdesk.feature.rag;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.Ticket;
import com.smartdesk.repo.DocumentRepo;
import com.smartdesk.repo.TicketRepo;
import com.smartdesk.security.AuthPrincipal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 단계 2.3: 검색된 문서를 컨텍스트로 1차 답변 초안 생성. 출처 인용 강제, 근거 부족 시 명시. */
@Service
public class AnswerDraftService {

    private static final String SYSTEM = """
        당신은 SI 고객사 IT 지원 담당자를 돕는 어시스턴트입니다.
        아래 [참고 문서] 만을 근거로 고객 문의에 대한 1차 답변 초안을 한국어로 작성하세요.
        규칙:
        - 문서에서 뒷받침되는 내용만 쓰고, 근거가 된 문장 옆에 [1], [2] 처럼 문서 번호를 답니다.
        - 참고 문서로 답할 수 없으면 정확히 "관련 문서를 찾지 못했습니다. 담당자 확인이 필요합니다." 라고만 답합니다.
        - 추측·창작 금지. 담당자가 검수 후 게시할 초안임을 감안해 간결하게.
        """;

    private final RagSearchService search;
    private final LlmClient llm;
    private final TicketRepo tickets;
    private final DocumentRepo documents;
    private final RagProperties props;

    public AnswerDraftService(RagSearchService search, LlmClient llm, TicketRepo tickets,
                              DocumentRepo documents, RagProperties props) {
        this.search = search;
        this.llm = llm;
        this.tickets = tickets;
        this.documents = documents;
        this.props = props;
    }

    public record Citation(int n, long documentId, String title, String excerpt) {}
    public record Draft(String draft, boolean grounded, boolean llmUsed, String model,
                        List<Citation> citations) {}

    public Draft forTicket(AuthPrincipal principal, long ticketId) {
        Ticket t = tickets.findById(ticketId).orElseThrow(() -> ApiException.notFound("티켓"));
        if (principal.isClientUser() && !t.getClientId().equals(principal.clientId())) {
            throw ApiException.forbidden("다른 고객사 티켓입니다.");
        }
        if (!search.available()) {
            throw ApiException.unavailable("RAG_NOT_READY", "색인된 문서가 없습니다. 먼저 재색인하세요.");
        }

        String query = TextChunker.stripHtml((t.getTitle() == null ? "" : t.getTitle()) + ". "
                + (t.getContent() == null ? "" : t.getContent()));
        List<EmbeddingStore.Hit> hits = search.documentContext(principal, query, props.getTopK());

        List<Citation> citations = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        int n = 1;
        var titleById = new java.util.HashMap<Long, String>();
        documents.findAllById(hits.stream().map(EmbeddingStore.Hit::sourceId).distinct().toList())
                .forEach(d -> titleById.put(d.getId(), d.getTitle()));
        for (EmbeddingStore.Hit h : hits) {
            String title = titleById.getOrDefault(h.sourceId(), "문서 #" + h.sourceId());
            context.append("[").append(n).append("] ").append(title).append("\n")
                    .append(h.content().replaceAll("\\s+", " ").trim()).append("\n\n");
            citations.add(new Citation(n, h.sourceId(), title, excerpt(h.content())));
            n++;
        }

        if (citations.isEmpty()) {
            return new Draft("관련 문서를 찾지 못했습니다. 담당자 확인이 필요합니다.",
                    false, false, llm.model(), citations);
        }
        if (!llm.enabled()) {
            return new Draft(null, true, false, "disabled", citations);
        }

        String user = "[고객 문의]\n" + query + "\n\n[참고 문서]\n" + context;
        String draft;
        try {
            draft = llm.complete(SYSTEM, user);
        } catch (Exception e) {
            throw ApiException.unavailable("LLM_ERROR", "초안 생성 실패: " + e.getMessage());
        }
        boolean grounded = !draft.contains("관련 문서를 찾지 못했습니다");
        return new Draft(draft, grounded, true, llm.model(), citations);
    }

    private static String excerpt(String s) {
        String c = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return c.length() <= 240 ? c : c.substring(0, 240) + "…";
    }
}
