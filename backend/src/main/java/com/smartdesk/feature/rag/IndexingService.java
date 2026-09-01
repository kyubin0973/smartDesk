package com.smartdesk.feature.rag;

import com.smartdesk.domain.Comment;
import com.smartdesk.domain.Document;
import com.smartdesk.domain.Enums.TicketStatus;
import com.smartdesk.domain.Ticket;
import com.smartdesk.repo.CommentRepo;
import com.smartdesk.repo.DocumentRepo;
import com.smartdesk.repo.TicketRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 단계 2.1: 지식문서 · 종료 티켓을 embedding 테이블에 색인. */
@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final DocumentRepo documents;
    private final TicketRepo tickets;
    private final CommentRepo comments;
    private final EmbeddingClient embedder;
    private final EmbeddingStore store;
    private final RagProperties props;

    public IndexingService(DocumentRepo documents, TicketRepo tickets, CommentRepo comments,
                           EmbeddingClient embedder, EmbeddingStore store, RagProperties props) {
        this.documents = documents;
        this.tickets = tickets;
        this.comments = comments;
        this.embedder = embedder;
        this.store = store;
        this.props = props;
    }

    public void indexDocument(long documentId) {
        if (!props.isEnabled()) return;
        Document d = documents.findById(documentId).orElse(null);
        if (d == null) { store.deleteSource("DOCUMENT", documentId); return; }
        String body = TextChunker.stripHtml(d.getContent());
        String hash = TextChunker.sha256(safe(d.getTitle()) + " " + body);
        if (store.isFresh("DOCUMENT", documentId, hash)) return;
        index("DOCUMENT", documentId, hash, d.getTitle(), body);
    }

    /** 종료(CLOSED) 티켓만 색인. 제목 + 내용 + 코멘트(해결 과정). */
    public void indexTicket(long ticketId) {
        if (!props.isEnabled()) return;
        Ticket t = tickets.findById(ticketId).orElse(null);
        if (t == null || t.getStatus() != TicketStatus.CLOSED) {
            store.deleteSource("TICKET", ticketId);
            return;
        }
        String body = ticketBody(t);
        String hash = TextChunker.sha256(safe(t.getTitle()) + " " + body);
        if (store.isFresh("TICKET", ticketId, hash)) return;
        index("TICKET", ticketId, hash, t.getTitle(), body);
    }

    /** 전체 재조정 — 누락·변경 원본 색인, 삭제된 원본 정리. @return [문서, 티켓] 색인 건수 */
    public int[] reconcile() {
        if (!props.isEnabled()) return new int[]{0, 0};
        int docs = 0;
        for (Document d : documents.findAll()) {
            String hash = TextChunker.sha256(safe(d.getTitle()) + " " + TextChunker.stripHtml(d.getContent()));
            if (!store.isFresh("DOCUMENT", d.getId(), hash)) { indexDocument(d.getId()); docs++; }
        }
        int tk = 0;
        for (Ticket t : tickets.findByStatus(TicketStatus.CLOSED)) {
            String hash = TextChunker.sha256(safe(t.getTitle()) + " " + ticketBody(t));
            if (!store.isFresh("TICKET", t.getId(), hash)) { indexTicket(t.getId()); tk++; }
        }
        log.info("[rag-index] reconcile — 문서 {}, 티켓 {}", docs, tk);
        return new int[]{docs, tk};
    }

    // ---------- helpers ----------

    private void index(String type, long id, String hash, String title, String body) {
        try {
            List<String> chunks = TextChunker.chunk(title, body, props.getChunkChars());
            if (chunks.isEmpty()) { store.deleteSource(type, id); return; }
            var res = embedder.embedPassages(chunks);
            store.replace(type, id, hash, res.model(), chunks, res.vectors());
            log.debug("[rag-index] {}#{} — {} chunks", type, id, chunks.size());
        } catch (Exception e) {
            log.warn("[rag-index] {}#{} 색인 실패: {}", type, id, e.toString());
        }
    }

    private String ticketBody(Ticket t) {
        StringBuilder sb = new StringBuilder(safe(t.getContent()));
        for (Comment c : comments.findByTicketIdOrderByCreatedAtAsc(t.getId())) {
            sb.append('\n').append(safe(c.getContent()));
        }
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
