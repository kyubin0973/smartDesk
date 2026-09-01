package com.smartdesk.feature.document;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.*;
import com.smartdesk.domain.Enums.DocScope;
import com.smartdesk.repo.*;
import com.smartdesk.security.AuthPrincipal;
import com.smartdesk.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** REQ-F-013 ~ REQ-F-015. 공개범위(scope) 기반 접근 제어. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepo documents;
    private final DocumentVersionRepo versions;
    private final DocumentShareRepo shares;
    private final CategoryRepo categories;
    private final com.smartdesk.feature.audit.AuditService audit;
    private final com.smartdesk.common.HtmlSanitizer htmlSanitizer;
    private final org.springframework.context.ApplicationEventPublisher events;

    public DocumentController(DocumentRepo documents, DocumentVersionRepo versions,
                              DocumentShareRepo shares, CategoryRepo categories,
                              com.smartdesk.feature.audit.AuditService audit,
                              com.smartdesk.common.HtmlSanitizer htmlSanitizer,
                              org.springframework.context.ApplicationEventPublisher events) {
        this.documents = documents;
        this.versions = versions;
        this.shares = shares;
        this.categories = categories;
        this.audit = audit;
        this.htmlSanitizer = htmlSanitizer;
        this.events = events;
    }

    public record DocRow(Long id, String title, int version, String scope, String categoryName,
                         List<Long> sharedClientIds, Instant updatedAt) {}
    public record DocDetail(Long id, String title, String content, int version, String scope,
                            Long categoryId, List<Long> sharedClientIds, Instant createdAt, Instant updatedAt) {}

    public record CreateDocRequest(@NotBlank String title, @NotBlank String content, Long categoryId,
                                   String scope, List<Long> clientIds) {}
    /** REQ-E-008: expectedVersion 이 현재 버전과 다르면 저장 거부. */
    public record UpdateDocRequest(@NotBlank String title, @NotBlank String content, Long categoryId,
                                   String scope, List<Long> clientIds, Integer expectedVersion) {}
    public record ScopeRequest(String scope, List<Long> clientIds) {}

    /** REQ-F-015: 키워드 검색 (공개범위 내에서만). B6: 조건을 쿼리로 내림 (전체 로드 제거). */
    @GetMapping
    public List<DocRow> search(@RequestParam(required = false) String q,
                               @RequestParam(required = false) String scope) {
        AuthPrincipal p = CurrentUser.get();
        String needle = q == null ? "" : q.toLowerCase(Locale.ROOT).trim();

        List<Document> found = p.isClientUser()
                ? documents.searchSharedWith(p.clientId(), needle)
                : documents.searchAll(
                        (scope == null || scope.isBlank()) ? null : parseScope(scope), needle);

        // B6: 카테고리명·공유고객사를 배치 조회 (행별 쿼리 제거)
        Map<Long, String> catNames = new java.util.HashMap<>();
        categories.findAllById(found.stream().map(Document::getCategoryId)
                .filter(java.util.Objects::nonNull).distinct().toList())
                .forEach(c -> catNames.put(c.getId(), c.getName()));

        Map<Long, List<Long>> sharedByDoc = new java.util.HashMap<>();
        shares.findByDocumentIdIn(found.stream().map(Document::getId).toList())
                .forEach(s -> sharedByDoc.computeIfAbsent(s.getDocumentId(), k -> new java.util.ArrayList<>()).add(s.getClientId()));

        return found.stream()
                .map(d -> new DocRow(d.getId(), d.getTitle(), d.getVersion(), d.getScope().name(),
                        catNames.get(d.getCategoryId()),
                        sharedByDoc.getOrDefault(d.getId(), List.of()), d.getUpdatedAt()))
                .toList();
    }

    @GetMapping("/{documentId}")
    public DocDetail detail(@PathVariable Long documentId) {
        AuthPrincipal p = CurrentUser.get();
        Document d = documents.findById(documentId).orElseThrow(() -> ApiException.notFound("문서"));
        if (!canRead(p, d)) throw ApiException.forbidden("열람 권한이 없는 문서입니다.");
        // 0.5-e: 고객사 담당자의 공유 문서 열람만 감사 (SI 는 노이즈)
        if (p.isClientUser()) {
            audit.record("DOCUMENT_VIEWED", "DOCUMENT", documentId, d.getTitle());
        }
        return toDetail(d);
    }

    @GetMapping("/{documentId}/versions")
    public List<DocumentVersionView> history(@PathVariable Long documentId) {
        AuthPrincipal p = CurrentUser.requireSiUser();
        Document d = documents.findById(documentId).orElseThrow(() -> ApiException.notFound("문서"));
        if (!canRead(p, d)) throw ApiException.forbidden("열람 권한이 없는 문서입니다.");
        return versions.findByDocumentIdOrderByVersionDesc(documentId).stream()
                .map(v -> new DocumentVersionView(v.getVersion(), v.getTitle(), v.getCreatedAt()))
                .toList();
    }
    public record DocumentVersionView(int version, String title, Instant createdAt) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public DocDetail create(@RequestBody @jakarta.validation.Valid CreateDocRequest req) {
        AuthPrincipal p = CurrentUser.requireSiUser();
        Document d = new Document();
        d.setCreatedBy(p.id());
        d.setTitle(req.title().trim());
        d.setContent(htmlSanitizer.clean(req.content()));
        d.setCategoryId(req.categoryId());
        d.setScope(parseScope(req.scope()));
        d.setVersion(1);
        Instant now = Instant.now();
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        d = documents.save(d);
        applyShares(d, d.getScope(), req.clientIds());
        versions.save(new DocumentVersion(d.getId(), 1, d.getTitle(), d.getContent(), p.id()));
        events.publishEvent(com.smartdesk.feature.rag.RagIndexEvents.SourceChanged.document(d.getId()));
        return toDetail(d);
    }

    /** REQ-F-013: 저장 시 버전 자동 증가. REQ-E-008: 낙관적 잠금. */
    @PutMapping("/{documentId}")
    @Transactional
    public DocDetail update(@PathVariable Long documentId, @RequestBody @jakarta.validation.Valid UpdateDocRequest req) {
        AuthPrincipal p = CurrentUser.requireSiUser();
        Document d = documents.findById(documentId).orElseThrow(() -> ApiException.notFound("문서"));
        if (req.expectedVersion() != null && req.expectedVersion() != d.getVersion()) {
            throw ApiException.conflict("VERSION_CONFLICT",
                    "다른 사용자가 먼저 수정했습니다. 최신 버전(v" + d.getVersion() + ")을 다시 불러오세요.");
        }
        d.setTitle(req.title().trim());
        d.setContent(htmlSanitizer.clean(req.content()));
        if (req.categoryId() != null) d.setCategoryId(req.categoryId());
        if (req.scope() != null) {
            d.setScope(parseScope(req.scope()));
            applyShares(d, d.getScope(), req.clientIds());
        }
        d.setVersion(d.getVersion() + 1);
        d.setUpdatedAt(Instant.now());
        d = documents.save(d);
        versions.save(new DocumentVersion(d.getId(), d.getVersion(), d.getTitle(), d.getContent(), p.id()));
        events.publishEvent(com.smartdesk.feature.rag.RagIndexEvents.SourceChanged.document(d.getId()));
        return toDetail(d);
    }

    /** REQ-F-014: 공개범위 변경. */
    @PutMapping("/{documentId}/scope")
    @Transactional
    public DocDetail updateScope(@PathVariable Long documentId, @RequestBody ScopeRequest req) {
        CurrentUser.requireSiUser();
        Document d = documents.findById(documentId).orElseThrow(() -> ApiException.notFound("문서"));
        String oldScope = d.getScope().name();
        DocScope scope = parseScope(req.scope());
        d.setScope(scope);
        d.setUpdatedAt(Instant.now());
        applyShares(d, scope, req.clientIds());
        audit.record("DOCUMENT_SCOPE_CHANGED", "DOCUMENT", documentId,
                oldScope + " → " + scope.name() + " (공유: " + sharedIds(documentId) + ")");
        return toDetail(documents.save(d));
    }

    // ---------- helpers ----------

    private void applyShares(Document d, DocScope scope, List<Long> clientIds) {
        shares.deleteByDocumentId(d.getId());
        d.setClientId(null);
        if (scope == DocScope.CLIENT_SHARED && clientIds != null && !clientIds.isEmpty()) {
            for (Long cid : clientIds) shares.save(new DocumentShare(d.getId(), cid));
            if (clientIds.size() == 1) d.setClientId(clientIds.get(0));
        }
        documents.save(d);
    }

    private boolean canRead(AuthPrincipal p, Document d) {
        if (p.isSiUser()) return true; // SI 직원은 내부/공유 문서 모두 열람
        if (d.getScope() != DocScope.CLIENT_SHARED) return false;
        Set<Long> sharedTo = shares.findByDocumentId(d.getId()).stream()
                .map(DocumentShare::getClientId).collect(Collectors.toSet());
        return sharedTo.contains(p.clientId());
    }

    private DocScope parseScope(String s) {
        if (s == null || s.isBlank()) return DocScope.SI_INTERNAL;
        try {
            return DocScope.valueOf(normalizeScope(s));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("공개범위 값이 올바르지 않습니다: " + s);
        }
    }

    private String normalizeScope(String s) {
        return switch (s) {
            case "SI_INTERNAL", "SI내부", "SI 내부" -> "SI_INTERNAL";
            case "CLIENT_SHARED", "고객사공유", "고객사 공유" -> "CLIENT_SHARED";
            default -> s;
        };
    }

    private List<Long> sharedIds(Long docId) {
        return shares.findByDocumentId(docId).stream().map(DocumentShare::getClientId).toList();
    }

    private DocDetail toDetail(Document d) {
        return new DocDetail(d.getId(), d.getTitle(), d.getContent(), d.getVersion(), d.getScope().name(),
                d.getCategoryId(), sharedIds(d.getId()), d.getCreatedAt(), d.getUpdatedAt());
    }
}
