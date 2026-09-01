package com.smartdesk.feature.ticket;

import com.smartdesk.common.ApiException;
import com.smartdesk.common.PageResponse;
import com.smartdesk.domain.*;
import com.smartdesk.domain.Enums.AuthorType;
import com.smartdesk.domain.Enums.NotificationType;
import com.smartdesk.domain.Enums.TicketEventType;
import com.smartdesk.domain.Enums.TicketStatus;
import com.smartdesk.feature.notification.NotificationService;
import com.smartdesk.repo.*;
import com.smartdesk.security.AuthPrincipal;
import com.smartdesk.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** REQ-F-008 ~ REQ-F-012. 멀티테넌시 격리(REQ-N-001)는 client_id 기준 필터링. */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketRepo tickets;
    private final ContractRepo contracts;
    private final CommentRepo comments;
    private final TicketHistoryRepo histories;
    private final CategoryRepo categories;
    private final SystemAssetRepo systems;
    private final AppUserRepo users;
    private final ClientUserRepo clientUsers;
    private final DocumentRepo documents;
    private final DocumentShareRepo documentShares;
    private final SlaService sla;
    private final com.smartdesk.feature.ticket.classify.CategorySuggester suggestion;
    private final AssignmentService assignment;
    private final PriorityRules priorityRules;
    private final TicketEventService eventLog;
    private final NotificationService notifications;
    private final org.springframework.context.ApplicationEventPublisher events;

    public TicketController(TicketRepo tickets, ContractRepo contracts, CommentRepo comments,
                            TicketHistoryRepo histories, CategoryRepo categories, SystemAssetRepo systems,
                            AppUserRepo users, ClientUserRepo clientUsers, DocumentRepo documents,
                            DocumentShareRepo documentShares, SlaService sla, com.smartdesk.feature.ticket.classify.CategorySuggester suggestion,
                            AssignmentService assignment, PriorityRules priorityRules,
                            TicketEventService eventLog, NotificationService notifications,
                            org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
        this.tickets = tickets;
        this.contracts = contracts;
        this.comments = comments;
        this.histories = histories;
        this.categories = categories;
        this.systems = systems;
        this.users = users;
        this.clientUsers = clientUsers;
        this.documents = documents;
        this.documentShares = documentShares;
        this.sla = sla;
        this.suggestion = suggestion;
        this.assignment = assignment;
        this.priorityRules = priorityRules;
        this.eventLog = eventLog;
        this.notifications = notifications;
    }

    // ---------- DTO ----------
    public record TicketRow(Long id, String title, String status, String priority, Long clientId,
                            String assigneeName, Instant slaDueAt, long slaRemainingMinutes, boolean slaBreached,
                            Instant createdAt) {}

    public record TicketDetail(Long id, String title, String content, String status, String priority,
                               Long clientId, Long contractId, Long systemId, String systemName,
                               Long categoryId, String categoryName, Long suggestedCategoryId,
                               Long requesterId, String requesterName,
                               Long assigneeId, String assigneeName,
                               Instant slaDueAt, long slaRemainingMinutes, boolean slaBreached,
                               Instant firstRespondedAt, Instant resolvedAt, Instant closedAt,
                               Instant createdAt, Instant updatedAt) {}

    /** requesterId 는 SI 직원이 대리 등록할 때만 사용 (해당 고객사의 활성 담당자여야 함). 고객사 담당자 본인 등록 시엔 무시. */
    public record CreateTicketRequest(Long clientId, Long requesterId, Long systemId,
                                      @NotBlank String title, @NotBlank String content) {}
    public record CategoryUpdateRequest(Long categoryId) {}
    public record AssigneeUpdateRequest(Long assigneeId) {}
    public record PriorityUpdateRequest(String priority) {}
    public record StatusUpdateRequest(String status) {}
    public record CommentRequest(@NotBlank String content) {}
    public record CommentView(Long id, String authorType, String authorName, String content, Instant createdAt) {}
    public record HistoryView(Long id, String field, String oldValue, String newValue, Instant createdAt) {}
    public record RelatedDoc(Long id, String title, int version, String scope) {}

    // ---------- Endpoints ----------

    @GetMapping
    public PageResponse<TicketRow> list(@RequestParam(required = false) Long clientId,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Long assigneeId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        AuthPrincipal p = CurrentUser.get();
        Long effectiveClientId = clientId;
        if (p.isClientUser()) {
            if (clientId != null && !clientId.equals(p.clientId())) throw ApiException.forbidden("다른 고객사 티켓은 조회할 수 없습니다.");
            effectiveClientId = p.clientId();
        }
        var result = tickets.search(effectiveClientId, parseStatus(status), assigneeId, PageRequest.of(page, size));

        // B5: 담당자 이름을 배치 조회 (행별 findById 제거)
        Map<Long, String> assigneeNames = names(result.getContent().stream()
                .map(Ticket::getAssigneeId).filter(java.util.Objects::nonNull).distinct().toList());

        return PageResponse.of(result.map(t -> {
            var v = sla.view(t.getSlaDueAt());
            return new TicketRow(t.getId(), t.getTitle(), t.getStatus().name(), t.getPriority().name(), t.getClientId(),
                    assigneeNames.get(t.getAssigneeId()), t.getSlaDueAt(), v.remainingMinutes(), v.breached(), t.getCreatedAt());
        }));
    }

    private Map<Long, String> names(List<Long> userIds) {
        Map<Long, String> m = new java.util.HashMap<>();  // HashMap.get(null) → null (Map.of() 는 NPE)
        if (!userIds.isEmpty()) users.findAllById(userIds).forEach(u -> m.put(u.getId(), u.getName()));
        return m;
    }

    /** REQ-F-008 + REQ-E-007: 유효 계약 없으면 차단. REQ-F-009: 카테고리 자동 제안. 우선순위 자동 산정. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TicketDetail create(@RequestBody @jakarta.validation.Valid CreateTicketRequest req) {
        AuthPrincipal p = CurrentUser.get();
        Long clientId;
        Long requesterId;
        if (p.isClientUser()) {
            clientId = p.clientId();
            requesterId = p.id();
        } else {
            // SI 직원 대리 등록: 요청자를 명시해야 함 (임의 지정 금지, C12)
            if (req.clientId() == null) throw ApiException.badRequest("clientId 가 필요합니다.");
            if (req.requesterId() == null) throw ApiException.badRequest("requesterId(대리 등록할 고객사 담당자)가 필요합니다.");
            clientId = req.clientId();
            ClientUser requester = clientUsers.findById(req.requesterId())
                    .filter(ClientUser::isActive)
                    .orElseThrow(() -> ApiException.badRequest("존재하지 않거나 비활성 상태인 담당자입니다."));
            if (!requester.getClientId().equals(clientId)) {
                throw ApiException.badRequest("요청자가 해당 고객사 소속이 아닙니다.");
            }
            requesterId = requester.getId();
        }

        Contract contract = contracts.findByClientIdAndStatusNot(clientId, Enums.ContractStatus.ENDED).stream()
                .filter(c -> c.isValidOn(LocalDate.now()))
                .min(Comparator.comparing(Contract::getEndDate))
                .orElseThrow(() -> ApiException.conflict("NO_VALID_CONTRACT", "유효한 계약이 없어 등록할 수 없습니다."));

        if (req.systemId() != null) {
            SystemAsset s = systems.findById(req.systemId()).orElseThrow(() -> ApiException.badRequest("존재하지 않는 시스템입니다."));
            if (!s.getClientId().equals(clientId) || !s.isActive()) throw ApiException.badRequest("선택할 수 없는 시스템입니다.");
        }

        Ticket t = new Ticket();
        t.setClientId(clientId);
        t.setContractId(contract.getId());
        t.setSystemId(req.systemId());
        t.setRequesterId(requesterId);
        t.setTitle(req.title().trim());
        t.setContent(req.content());
        t.setCategoryId(suggestion.suggest(req.title(), req.content()));
        t.setPriority(priorityRules.infer(req.title(), req.content()));
        Instant now = Instant.now();
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        t.setSlaDueAt(sla.computeDueAt(contract, now));
        t = tickets.save(t);

        eventLog.record(t.getId(), TicketEventType.CREATED, null, t.getPriority().name(), p);
        return toDetail(t);
    }

    @GetMapping("/{ticketId}")
    public TicketDetail detail(@PathVariable Long ticketId) {
        return toDetail(requireVisible(ticketId));
    }

    /** REQ-F-009: 자동분류 결과 확정/수정. */
    @PutMapping("/{ticketId}/category")
    @Transactional
    public TicketDetail updateCategory(@PathVariable Long ticketId, @RequestBody CategoryUpdateRequest req) {
        AuthPrincipal p = CurrentUser.requireSiUser();
        Ticket t = requireVisible(ticketId);
        Long old = t.getCategoryId();
        if (req.categoryId() != null) {
            categories.findById(req.categoryId()).filter(Category::isActive)
                    .orElseThrow(() -> ApiException.badRequest("선택할 수 없는 카테고리입니다."));
        }
        t.setCategoryId(req.categoryId());
        tickets.save(t);
        histories.save(new TicketHistory(ticketId, "category", str(old), str(req.categoryId()), "USER", p.id()));
        eventLog.record(ticketId, TicketEventType.CATEGORIZED, str(old), str(req.categoryId()), p);
        return toDetail(t);
    }

    /** REQ: 우선순위 수동 조정. */
    @PutMapping("/{ticketId}/priority")
    @Transactional
    public TicketDetail updatePriority(@PathVariable Long ticketId, @RequestBody PriorityUpdateRequest req) {
        AuthPrincipal p = CurrentUser.requireSiUser();
        Ticket t = requireVisible(ticketId);
        Enums.Priority next;
        try { next = Enums.Priority.valueOf(req.priority()); }
        catch (Exception e) { throw ApiException.badRequest("유효하지 않은 우선순위입니다."); }
        String old = t.getPriority().name();
        t.setPriority(next);
        tickets.save(t);
        histories.save(new TicketHistory(ticketId, "priority", old, next.name(), "USER", p.id()));
        return toDetail(t);
    }

    /** REQ-F-010: 담당자 배정/재배정. assigneeId 미지정 시 자동배정. */
    @PutMapping("/{ticketId}/assignee")
    @Transactional
    public TicketDetail updateAssignee(@PathVariable Long ticketId, @RequestBody(required = false) AssigneeUpdateRequest req) {
        AuthPrincipal p = CurrentUser.requireSiUser();
        Ticket t = requireVisible(ticketId);
        Long old = t.getAssigneeId();
        Long target = (req != null && req.assigneeId() != null)
                ? req.assigneeId()
                : assignment.autoAssign(t).orElse(null);
        if (target != null) {
            users.findById(target).filter(AppUser::isActive)
                    .orElseThrow(() -> ApiException.badRequest("배정할 수 없는 담당자입니다."));
        }
        t.setAssigneeId(target);
        if (t.getStatus() == TicketStatus.RECEIVED && target != null) {
            t.setStatus(TicketStatus.IN_PROGRESS);
            if (t.getFirstRespondedAt() == null) t.setFirstRespondedAt(Instant.now());
        }
        tickets.save(t);
        histories.save(new TicketHistory(ticketId, "assignee", str(old), str(target), "USER", p.id()));
        eventLog.record(ticketId, TicketEventType.ASSIGNED, str(old), str(target), p);
        if (target != null && !target.equals(old)) {
            notifications.notifyUser(target, NotificationType.TICKET_ASSIGNED,
                    "티켓 배정: #" + t.getId(), t.getTitle(), t.getId());
        }
        return toDetail(t);
    }

    /** REQ-F-011: SLA 잔여/마감 조회. */
    @GetMapping("/{ticketId}/sla")
    public SlaService.SlaView slaView(@PathVariable Long ticketId) {
        return sla.view(requireVisible(ticketId).getSlaDueAt());
    }

    /** REQ-F-011 상태 전이. REQ-E-001: 계약 만료 후에도 이미 열린 티켓의 처리(상태 변경)는 허용. */
    @PutMapping("/{ticketId}/status")
    @Transactional
    public TicketDetail updateStatus(@PathVariable Long ticketId, @RequestBody @jakarta.validation.Valid StatusUpdateRequest req) {
        AuthPrincipal p = CurrentUser.requireSiUser();
        Ticket t = requireVisible(ticketId);
        TicketStatus next = parseStatus(req.status());
        if (next == null) throw ApiException.badRequest("유효하지 않은 상태입니다.");
        TicketStatus old = t.getStatus();
        if (!old.canTransitionTo(next)) {
            throw ApiException.conflict("INVALID_TRANSITION", old + " → " + next + " 상태 전이는 허용되지 않습니다.");
        }
        // C8: 해결 → 종료 는 승인자(관리자)의 /approve 로만. 담당자가 임의 종료 못 함.
        if (old == TicketStatus.RESOLVED && next == TicketStatus.CLOSED) {
            throw ApiException.conflict("APPROVAL_REQUIRED", "해결된 티켓의 종료는 승인이 필요합니다. /approve 를 사용하세요.");
        }
        Instant now = Instant.now();
        t.setStatus(next);
        switch (next) {
            case IN_PROGRESS -> { if (t.getFirstRespondedAt() == null) t.setFirstRespondedAt(now); }
            case RESOLVED    -> t.setResolvedAt(now);
            case CLOSED      -> { if (t.getResolvedAt() == null) t.setResolvedAt(now); t.setClosedAt(now); }
            case RECEIVED    -> { /* 재오픈: 해결/종료 시각 초기화 */ t.setResolvedAt(null); t.setClosedAt(null); }
        }
        tickets.save(t);
        histories.save(new TicketHistory(ticketId, "status", old.name(), next.name(), "USER", p.id()));
        eventLog.record(ticketId, TicketEventType.STATUS_CHANGED, old.name(), next.name(), p);
        // 요청자에게 상태 변경 알림
        notifications.notifyClientUser(t.getRequesterId(), NotificationType.TICKET_STATUS,
                "티켓 상태 변경: #" + t.getId(), t.getTitle() + " → " + next.name(), t.getId());
        events.publishEvent(com.smartdesk.feature.rag.RagIndexEvents.SourceChanged.ticket(ticketId));
        return toDetail(t);
    }

    // ---------- C8: 승인자(관리자) 워크플로 ----------
    public record RejectRequest(@NotBlank String reason) {}

    /** 승인: 해결 → 종료. 관리자(승인자)만. SLA 준수 여부도 이벤트에 남김. */
    @PostMapping("/{ticketId}/approve")
    @Transactional
    public TicketDetail approve(@PathVariable Long ticketId) {
        AuthPrincipal p = CurrentUser.requireManager();
        Ticket t = requireVisible(ticketId);
        if (t.getStatus() != TicketStatus.RESOLVED) {
            throw ApiException.conflict("NOT_PENDING_APPROVAL", "해결(RESOLVED) 상태의 티켓만 승인할 수 있습니다.");
        }
        Instant now = Instant.now();
        t.setStatus(TicketStatus.CLOSED);
        t.setClosedAt(now);
        tickets.save(t);
        histories.save(new TicketHistory(ticketId, "status", "RESOLVED", "CLOSED", "USER", p.id()));
        eventLog.record(ticketId, TicketEventType.APPROVED,
                t.isSlaMet() ? "SLA_MET" : "SLA_BREACHED", "CLOSED", p);
        if (t.getAssigneeId() != null) {
            notifications.notifyUser(t.getAssigneeId(), NotificationType.TICKET_STATUS,
                    "승인됨: #" + t.getId(), t.getTitle() + " 처리가 승인되어 종료되었습니다.", t.getId());
        }
        notifications.notifyClientUser(t.getRequesterId(), NotificationType.TICKET_STATUS,
                "티켓 종료: #" + t.getId(), t.getTitle(), t.getId());
        events.publishEvent(com.smartdesk.feature.rag.RagIndexEvents.SourceChanged.ticket(ticketId));
        return toDetail(t);
    }

    /** 반려: 해결 → 처리중. 관리자만. 사유는 코멘트로 남김. */
    @PostMapping("/{ticketId}/reject")
    @Transactional
    public TicketDetail reject(@PathVariable Long ticketId, @RequestBody @jakarta.validation.Valid RejectRequest req) {
        AuthPrincipal p = CurrentUser.requireManager();
        Ticket t = requireVisible(ticketId);
        if (t.getStatus() != TicketStatus.RESOLVED) {
            throw ApiException.conflict("NOT_PENDING_APPROVAL", "해결(RESOLVED) 상태의 티켓만 반려할 수 있습니다.");
        }
        t.setStatus(TicketStatus.IN_PROGRESS);
        t.setResolvedAt(null);
        tickets.save(t);

        Comment c = new Comment();
        c.setTicketId(ticketId);
        c.setAuthorType(AuthorType.USER);
        c.setAuthorId(p.id());
        c.setContent("[반려] " + req.reason());
        comments.save(c);

        histories.save(new TicketHistory(ticketId, "status", "RESOLVED", "IN_PROGRESS", "USER", p.id()));
        eventLog.record(ticketId, TicketEventType.REJECTED, "RESOLVED", "IN_PROGRESS", p);
        if (t.getAssigneeId() != null) {
            notifications.notifyUser(t.getAssigneeId(), NotificationType.TICKET_STATUS,
                    "반려됨: #" + t.getId(), req.reason(), t.getId());
        }
        return toDetail(t);
    }

    /** REQ-F-012: 코멘트 + 상태변경 이력. */
    @GetMapping("/{ticketId}/comments")
    public Map<String, Object> commentHistory(@PathVariable Long ticketId) {
        requireVisible(ticketId);
        List<Comment> raw = comments.findByTicketIdOrderByCreatedAtAsc(ticketId);

        // B5: 작성자 이름 배치 조회
        Map<Long, String> siNames = names(raw.stream()
                .filter(c -> c.getAuthorType() == AuthorType.USER).map(Comment::getAuthorId).distinct().toList());
        Map<Long, String> clientNames = new java.util.HashMap<>();
        clientUsers.findAllById(raw.stream()
                .filter(c -> c.getAuthorType() == AuthorType.CLIENT_USER).map(Comment::getAuthorId).distinct().toList())
                .forEach(cu -> clientNames.put(cu.getId(), cu.getName()));

        List<CommentView> cs = raw.stream().map(c -> {
            String name = c.getAuthorType() == AuthorType.USER
                    ? siNames.getOrDefault(c.getAuthorId(), "SI 직원")
                    : clientNames.getOrDefault(c.getAuthorId(), "고객사 담당자");
            return new CommentView(c.getId(), c.getAuthorType().name(), name, c.getContent(), c.getCreatedAt());
        }).toList();

        List<HistoryView> hs = histories.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(h -> new HistoryView(h.getId(), h.getField(), h.getOldValue(), h.getNewValue(), h.getCreatedAt()))
                .toList();
        return Map.of("comments", cs, "history", hs);
    }

    @PostMapping("/{ticketId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CommentView addComment(@PathVariable Long ticketId, @RequestBody @jakarta.validation.Valid CommentRequest req) {
        AuthPrincipal p = CurrentUser.get();
        Ticket t = requireVisible(ticketId);
        Comment c = new Comment();
        c.setTicketId(ticketId);
        c.setAuthorType(p.isSiUser() ? AuthorType.USER : AuthorType.CLIENT_USER);
        c.setAuthorId(p.id());
        c.setContent(req.content());
        c = comments.save(c);
        eventLog.record(ticketId, TicketEventType.COMMENTED, null, null, p);
        // 상대편에게 알림
        if (p.isSiUser()) {
            notifications.notifyClientUser(t.getRequesterId(), NotificationType.TICKET_COMMENTED,
                    "새 코멘트: #" + t.getId(), req.content(), t.getId());
        } else if (t.getAssigneeId() != null) {
            notifications.notifyUser(t.getAssigneeId(), NotificationType.TICKET_COMMENTED,
                    "새 코멘트: #" + t.getId(), req.content(), t.getId());
        }
        return toCommentView(c);
    }

    /** 화면설계서 SCR-TICKET-002 ⑤: 동일 카테고리 관련 지식문서 (공개범위 내). RAG(2단계) 규칙 기반 전신. */
    @GetMapping("/{ticketId}/related-documents")
    public List<RelatedDoc> relatedDocuments(@PathVariable Long ticketId) {
        Ticket t = requireVisible(ticketId);
        AuthPrincipal p = CurrentUser.get();
        if (t.getCategoryId() == null) return List.of();
        var sharedDocIds = p.isClientUser()
                ? documentShares.findByClientId(p.clientId()).stream().map(DocumentShare::getDocumentId).toList()
                : null;
        // B6: 전체 문서 로드 대신 카테고리 조건을 쿼리로 내림
        return documents.findByCategoryIdOrderByUpdatedAtDesc(t.getCategoryId()).stream()
                .filter(d -> {
                    if (p.isSiUser()) return true;
                    return d.getScope() == Enums.DocScope.CLIENT_SHARED && sharedDocIds.contains(d.getId());
                })
                .limit(5)
                .map(d -> new RelatedDoc(d.getId(), d.getTitle(), d.getVersion(), d.getScope().name()))
                .toList();
    }

    // ---------- helpers ----------

    private Ticket requireVisible(Long ticketId) {
        Ticket t = tickets.findById(ticketId).orElseThrow(() -> ApiException.notFound("티켓"));
        AuthPrincipal p = CurrentUser.get();
        if (p.isClientUser() && !t.getClientId().equals(p.clientId())) {
            throw ApiException.forbidden("다른 고객사 티켓에는 접근할 수 없습니다.");
        }
        return t;
    }

    private TicketStatus parseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        return switch (s) {
            case "RECEIVED", "접수" -> TicketStatus.RECEIVED;
            case "IN_PROGRESS", "처리중" -> TicketStatus.IN_PROGRESS;
            case "RESOLVED", "해결" -> TicketStatus.RESOLVED;
            case "CLOSED", "종료" -> TicketStatus.CLOSED;
            default -> null;
        };
    }

    private String str(Long v) { return v == null ? null : String.valueOf(v); }
    private String userName(Long id) { return id == null ? null : users.findById(id).map(AppUser::getName).orElse(null); }

    private TicketDetail toDetail(Ticket t) {
        var v = sla.view(t.getSlaDueAt());
        String systemName = t.getSystemId() == null ? null : systems.findById(t.getSystemId()).map(SystemAsset::getName).orElse(null);
        String categoryName = t.getCategoryId() == null ? null : categories.findById(t.getCategoryId()).map(Category::getName).orElse(null);
        Long suggested = t.getCategoryId() == null ? suggestion.suggest(t.getTitle(), t.getContent()) : null;
        String requesterName = clientUsers.findById(t.getRequesterId()).map(ClientUser::getName).orElse(null);
        return new TicketDetail(t.getId(), t.getTitle(), t.getContent(), t.getStatus().name(), t.getPriority().name(),
                t.getClientId(), t.getContractId(), t.getSystemId(), systemName,
                t.getCategoryId(), categoryName, suggested,
                t.getRequesterId(), requesterName,
                t.getAssigneeId(), userName(t.getAssigneeId()),
                t.getSlaDueAt(), v.remainingMinutes(), v.breached(),
                t.getFirstRespondedAt(), t.getResolvedAt(), t.getClosedAt(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    private CommentView toCommentView(Comment c) {
        String name = c.getAuthorType() == AuthorType.USER
                ? users.findById(c.getAuthorId()).map(AppUser::getName).orElse("SI 직원")
                : clientUsers.findById(c.getAuthorId()).map(ClientUser::getName).orElse("고객사 담당자");
        return new CommentView(c.getId(), c.getAuthorType().name(), name, c.getContent(), c.getCreatedAt());
    }
}
