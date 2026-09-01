package com.smartdesk.feature.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** embedding 테이블 (pgvector) 접근. JdbcTemplate + vector 리터럴 캐스팅. */
@Repository
public class EmbeddingStore {

    private final JdbcTemplate jdbc;

    public EmbeddingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Hit(String sourceType, long sourceId, int chunkOrd, String content, double score) {}

    public static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    public boolean isFresh(String sourceType, long sourceId, String hash) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM embedding WHERE source_type = ? AND source_id = ? AND source_hash = ?",
                Integer.class, sourceType, sourceId, hash);
        return n != null && n > 0;
    }

    public void replace(String sourceType, long sourceId, String hash, String model, List<String> chunks,
                        List<float[]> vectors) {
        jdbc.update("DELETE FROM embedding WHERE source_type = ? AND source_id = ?", sourceType, sourceId);
        for (int i = 0; i < chunks.size(); i++) {
            jdbc.update("""
                INSERT INTO embedding (source_type, source_id, chunk_ord, content, embedding, model, source_hash)
                VALUES (?, ?, ?, ?, CAST(? AS vector), ?, ?)
                """, sourceType, sourceId, i, chunks.get(i), toVectorLiteral(vectors.get(i)), model, hash);
        }
    }

    public void deleteSource(String sourceType, long sourceId) {
        jdbc.update("DELETE FROM embedding WHERE source_type = ? AND source_id = ?", sourceType, sourceId);
    }

    public long count(String sourceType) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM embedding WHERE source_type = ?", Long.class, sourceType);
        return n == null ? 0 : n;
    }

    /**
     * 문서 청크 벡터 검색. 테넌시: clientId != null(고객사 담당자)면 공유(CLIENT_SHARED) + 해당 고객사만.
     * score = 1 - cosine_distance.
     */
    public List<Hit> searchDocuments(float[] queryVec, Long clientId, int k) {
        String tenancy = clientId == null ? "" : """
             AND d.scope = 'CLIENT_SHARED'
             AND EXISTS (SELECT 1 FROM document_share s WHERE s.document_id = d.id AND s.client_id = ?)
            """;
        String sql = """
            SELECT e.source_type, e.source_id, e.chunk_ord, e.content,
                   1 - (e.embedding <=> CAST(? AS vector)) AS score
            FROM embedding e
            JOIN document d ON d.id = e.source_id
            WHERE e.source_type = 'DOCUMENT'
            """ + tenancy + """
            ORDER BY e.embedding <=> CAST(? AS vector)
            LIMIT ?
            """;
        String vec = toVectorLiteral(queryVec);
        Object[] args = clientId == null
                ? new Object[]{vec, vec, k}
                : new Object[]{vec, clientId, vec, k};
        return jdbc.query(sql, (rs, i) -> new Hit(rs.getString(1), rs.getLong(2), rs.getInt(3),
                rs.getString(4), rs.getDouble(5)), args);
    }

    /** 종료 티켓 청크 벡터 검색. 테넌시: clientId != null 이면 해당 고객사 티켓만. 질의 티켓 자신 제외. */
    public List<Hit> searchTickets(float[] queryVec, Long clientId, long excludeTicketId, int k) {
        String tenancy = clientId == null ? "" : " AND t.client_id = ? ";
        String sql = """
            SELECT e.source_type, e.source_id, e.chunk_ord, e.content,
                   1 - (e.embedding <=> CAST(? AS vector)) AS score
            FROM embedding e
            JOIN ticket t ON t.id = e.source_id
            WHERE e.source_type = 'TICKET' AND e.source_id <> ?
            """ + tenancy + """
            ORDER BY e.embedding <=> CAST(? AS vector)
            LIMIT ?
            """;
        String vec = toVectorLiteral(queryVec);
        Object[] args = clientId == null
                ? new Object[]{vec, excludeTicketId, vec, k}
                : new Object[]{vec, excludeTicketId, clientId, vec, k};
        return jdbc.query(sql, (rs, i) -> new Hit(rs.getString(1), rs.getLong(2), rs.getInt(3),
                rs.getString(4), rs.getDouble(5)), args);
    }

    /** BM25(전문검색) — 문서. ts_rank 기준 상위. */
    public List<Hit> bm25Documents(String query, Long clientId, int k) {
        String tenancy = clientId == null ? "" : """
             AND d.scope = 'CLIENT_SHARED'
             AND EXISTS (SELECT 1 FROM document_share s WHERE s.document_id = d.id AND s.client_id = ?)
            """;
        String sql = """
            SELECT 'DOCUMENT', d.id, 0, left(coalesce(d.content, ''), 600),
                   ts_rank(d.search_tsv, plainto_tsquery('simple', ?)) AS score
            FROM document d
            WHERE d.search_tsv @@ plainto_tsquery('simple', ?)
            """ + tenancy + """
            ORDER BY score DESC
            LIMIT ?
            """;
        Object[] args = clientId == null
                ? new Object[]{query, query, k}
                : new Object[]{query, query, clientId, k};
        return jdbc.query(sql, (rs, i) -> new Hit(rs.getString(1), rs.getLong(2), rs.getInt(3),
                rs.getString(4), rs.getDouble(5)), args);
    }

    /** BM25 — 종료 티켓. */
    public List<Hit> bm25Tickets(String query, Long clientId, long excludeTicketId, int k) {
        String tenancy = clientId == null ? "" : " AND t.client_id = ? ";
        String sql = """
            SELECT 'TICKET', t.id, 0, left(coalesce(t.content, ''), 600),
                   ts_rank(t.search_tsv, plainto_tsquery('simple', ?)) AS score
            FROM ticket t
            WHERE t.status = 'CLOSED' AND t.id <> ?
              AND t.search_tsv @@ plainto_tsquery('simple', ?)
            """ + tenancy + """
            ORDER BY score DESC
            LIMIT ?
            """;
        Object[] args = clientId == null
                ? new Object[]{query, excludeTicketId, query, k}
                : new Object[]{query, excludeTicketId, query, clientId, k};
        return jdbc.query(sql, (rs, i) -> new Hit(rs.getString(1), rs.getLong(2), rs.getInt(3),
                rs.getString(4), rs.getDouble(5)), args);
    }
}
