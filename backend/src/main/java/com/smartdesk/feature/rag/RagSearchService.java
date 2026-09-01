package com.smartdesk.feature.rag;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.Document;
import com.smartdesk.domain.Ticket;
import com.smartdesk.repo.DocumentRepo;
import com.smartdesk.repo.TicketRepo;
import com.smartdesk.security.AuthPrincipal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 단계 2.2: 하이브리드 검색 (벡터 + BM25) → RRF 융합 → 테넌시 필터.
 * 티켓 상세의 "유사 문서/티켓 추천" 과 RAG 초안의 컨텍스트 검색에 공용.
 */
@Service
public class RagSearchService {

    private static final int RRF_K = 60;

    private final EmbeddingClient embedder;
    private final EmbeddingStore store;
    private final DocumentRepo documents;
    private final TicketRepo tickets;
    private final RagProperties props;

    public RagSearchService(EmbeddingClient embedder, EmbeddingStore store, DocumentRepo documents,
                            TicketRepo tickets, RagProperties props) {
        this.embedder = embedder;
        this.store = store;
        this.documents = documents;
        this.tickets = tickets;
        this.props = props;
    }

    public record Result(long id, String title, String snippet, double score) {}
    public record Related(List<Result> documents, List<Result> tickets, boolean ragUsed) {}

    public boolean available() {
        return props.isEnabled() && store.count("DOCUMENT") + store.count("TICKET") > 0;
    }

    /** 티켓 기준 유사 문서·티켓 추천. */
    public Related relatedForTicket(AuthPrincipal principal, Ticket t) {
        Long clientId = principal.isClientUser() ? principal.clientId() : null;
        String query = TextChunker.stripHtml((t.getTitle() == null ? "" : t.getTitle()) + ". "
                + (t.getContent() == null ? "" : t.getContent()));
        int k = props.getTopK();

        List<Result> docs;
        List<Result> tix;
        try {
            float[] qvec = embedder.embedQuery(query);
            docs = fuse(
                    store.searchDocuments(qvec, clientId, k * 3),
                    store.bm25Documents(query, clientId, k * 3), k);
            tix = fuse(
                    store.searchTickets(qvec, clientId, t.getId(), k * 3),
                    store.bm25Tickets(query, clientId, t.getId(), k * 3), k);
        } catch (Exception e) {
            throw ApiException.unavailable("RAG_UNAVAILABLE", "임베딩 서비스에 연결할 수 없습니다: " + e.getMessage());
        }
        hydrateDocTitles(docs);
        hydrateTicketTitles(tix);
        return new Related(docs, tix, true);
    }

    /** RAG 초안용 — 문서 청크만, 원문 스니펫 포함. */
    public List<EmbeddingStore.Hit> documentContext(AuthPrincipal principal, String query, int k) {
        Long clientId = principal.isClientUser() ? principal.clientId() : null;
        float[] qvec = embedder.embedQuery(TextChunker.stripHtml(query));
        return fuseHits(
                store.searchDocuments(qvec, clientId, k * 3),
                store.bm25Documents(query, clientId, k * 3), k);
    }

    // ---------- RRF 융합 ----------

    private List<Result> fuse(List<EmbeddingStore.Hit> vector, List<EmbeddingStore.Hit> bm25, int k) {
        return new ArrayList<>(fuseHits(vector, bm25, k).stream()
                .map(h -> new Result(h.sourceId(), null, snippet(h.content()), h.score()))
                .toList());
    }

    private List<EmbeddingStore.Hit> fuseHits(List<EmbeddingStore.Hit> vector,
                                              List<EmbeddingStore.Hit> bm25, int k) {
        Map<Long, Double> rrf = new LinkedHashMap<>();
        Map<Long, EmbeddingStore.Hit> best = new LinkedHashMap<>();
        addRanked(vector, rrf, best);
        addRanked(bm25, rrf, best);
        return rrf.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(k)
                .map(e -> {
                    var h = best.get(e.getKey());
                    return new EmbeddingStore.Hit(h.sourceType(), h.sourceId(), h.chunkOrd(),
                            h.content(), e.getValue());
                })
                .toList();
    }

    private void addRanked(List<EmbeddingStore.Hit> hits, Map<Long, Double> rrf,
                           Map<Long, EmbeddingStore.Hit> best) {
        for (int rank = 0; rank < hits.size(); rank++) {
            EmbeddingStore.Hit h = hits.get(rank);
            rrf.merge(h.sourceId(), 1.0 / (RRF_K + rank + 1), Double::sum);
            best.merge(h.sourceId(), h, (a, b) -> a.score() >= b.score() ? a : b);
        }
    }

    private void hydrateDocTitles(List<Result> results) {
        var byId = new java.util.HashMap<Long, String>();
        documents.findAllById(results.stream().map(Result::id).toList())
                .forEach(d -> byId.put(d.getId(), d.getTitle()));
        replaceTitles(results, byId);
    }

    private void hydrateTicketTitles(List<Result> results) {
        var byId = new java.util.HashMap<Long, String>();
        tickets.findAllById(results.stream().map(Result::id).toList())
                .forEach(t -> byId.put(t.getId(), t.getTitle()));
        replaceTitles(results, byId);
    }

    private void replaceTitles(List<Result> results, Map<Long, String> titles) {
        for (int i = 0; i < results.size(); i++) {
            Result r = results.get(i);
            results.set(i, new Result(r.id(), titles.getOrDefault(r.id(), "#" + r.id()),
                    r.snippet(), round(r.score())));
        }
    }

    private static String snippet(String content) {
        String s = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }

    private static double round(double d) {
        return Math.round(d * 100000.0) / 100000.0;
    }
}
