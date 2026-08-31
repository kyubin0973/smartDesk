package com.smartdesk.repo;

import com.smartdesk.domain.Document;
import com.smartdesk.domain.Enums.DocScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentRepo extends JpaRepository<Document, Long> {

    List<Document> findByCategoryIdOrderByUpdatedAtDesc(Long categoryId);

    /** B6: SI 직원용 검색 — 전체 로드 대신 scope + 키워드(LIKE) 조건을 쿼리로. */
    @Query("""
        select d from Document d
        where (:scope is null or d.scope = :scope)
          and (:q = '' or lower(d.title) like concat('%', :q, '%') or lower(d.content) like concat('%', :q, '%'))
        order by d.updatedAt desc
    """)
    List<Document> searchAll(@Param("scope") DocScope scope, @Param("q") String q);

    /**
     * B6: 고객사 담당자용 검색 — document_share 조인 (공유는 CLIENT_SHARED 문서에만 생성되므로 scope 조건 불필요).
     */
    @Query("""
        select d from Document d
        join DocumentShare s on s.documentId = d.id
        where s.clientId = :clientId
          and (:q = '' or lower(d.title) like concat('%', :q, '%') or lower(d.content) like concat('%', :q, '%'))
        order by d.updatedAt desc
    """)
    List<Document> searchSharedWith(@Param("clientId") Long clientId, @Param("q") String q);
}
