package com.smartdesk.feature.triage;

import com.smartdesk.domain.Category;
import com.smartdesk.domain.Enums.Priority;
import com.smartdesk.domain.Enums.TicketEventType;
import com.smartdesk.domain.Ticket;
import com.smartdesk.feature.rag.RagSearchService;
import com.smartdesk.feature.ticket.PriorityRules;
import com.smartdesk.feature.ticket.TicketEventService;
import com.smartdesk.feature.ticket.classify.CategorySuggester;
import com.smartdesk.repo.CategoryRepo;
import com.smartdesk.repo.TicketRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 단계 3.1: 지능형 트리아지 — 카테고리·우선순위·담당자 제안을 한 번에.
 * 규칙(분류·우선순위) + 단계 2 유사 티켓 + 단계 1 담당자 실적 + (선택) LLM 판단을 종합하고
 * 신뢰도가 낮으면 자동 배정 대신 관리자 검토로 에스컬레이션한다.
 */
@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    private final CategorySuggester categorySuggester;
    private final PriorityRules priorityRules;
    private final AssigneeScorer assigneeScorer;
    private final TriageAdvisor advisor;
    private final RagSearchService rag;
    private final CategoryRepo categories;
    private final TicketRepo tickets;
    private final TicketEventService events;
    private final JdbcTemplate jdbc;
    private final TriageProperties props;

    public TriageService(CategorySuggester categorySuggester, PriorityRules priorityRules,
                         AssigneeScorer assigneeScorer, TriageAdvisor advisor, RagSearchService rag,
                         CategoryRepo categories, TicketRepo tickets, TicketEventService events,
                         JdbcTemplate jdbc, TriageProperties props) {
        this.categorySuggester = categorySuggester;
        this.priorityRules = priorityRules;
        this.assigneeScorer = assigneeScorer;
        this.advisor = advisor;
        this.rag = rag;
        this.categories = categories;
        this.tickets = tickets;
        this.events = events;
        this.jdbc = jdbc;
        this.props = props;
    }

    public record Similar(long ticketId, String title, String priority) {}

    public record TriageResult(Long categoryId, String categoryName,
                               Priority priority, String prioritySource,
                               Long suggestedAssigneeId, String suggestedAssigneeName, String assigneeRationale,
                               double confidence, boolean escalate,
                               List<Similar> similar, String llmNote, boolean llmUsed) {}

    /** 티켓을 트리아지한다. 저장 전(id 없음)이면 유사 티켓 검색만, 이벤트 기록은 생략. */
    public TriageResult triage(Ticket t) {
        long ticketId = t.getId() == null ? 0 : t.getId();

        // 1. 카테고리
        Long categoryId = t.getCategoryId() != null ? t.getCategoryId()
                : categorySuggester.suggest(t.getTitle(), t.getContent());

        // 2. 유사 종료 티켓 (단계 2)
        List<Similar> similar = new ArrayList<>();
        List<Ticket> similarTickets = List.of();
        if (rag.hasTicketIndex()) {
            try {
                var hits = rag.similarClosedTickets(t.getTitle(), t.getContent(), ticketId, 5);
                similarTickets = tickets.findAllById(hits.stream().map(RagSearchService.Result::id).toList());
                Map<Long, Ticket> byId = similarTickets.stream()
                        .collect(Collectors.toMap(Ticket::getId, x -> x));
                for (var h : hits) {
                    Ticket st = byId.get(h.id());
                    if (st != null) similar.add(new Similar(st.getId(), st.getTitle(), st.getPriority().name()));
                }
            } catch (Exception e) {
                log.warn("[triage] 유사 티켓 검색 실패, 무시: {}", e.toString());
            }
        }

        // 카테고리 교차검증: 유사 티켓 다수가 특정 카테고리면 신뢰 가산 / 규칙이 null 이면 대체
        Long similarModalCategory = modal(similarTickets.stream().map(Ticket::getCategoryId).toList());
        boolean categoryAgrees = categoryId != null && categoryId.equals(similarModalCategory);
        if (categoryId == null && similarModalCategory != null) categoryId = similarModalCategory;

        // 3. 우선순위: 규칙 → 유사 티켓 최빈 → LLM 제안 중 가장 높은 심각도
        Priority rulePriority = priorityRules.infer(t.getTitle(), t.getContent());
        Priority similarModalPriority = modalPriority(similarTickets);
        String categoryName = categoryId == null ? null
                : categories.findById(categoryId).map(Category::getName).orElse(null);

        var advice = advisor.advise(t.getTitle(), t.getContent(), categoryName, rulePriority,
                similar.stream().map(Similar::title).toList());

        Priority priority = rulePriority;
        String prioritySource = "RULE";
        if (higher(similarModalPriority, priority)) { priority = similarModalPriority; prioritySource = "SIMILAR"; }
        if (higher(advice.prioritySuggestion(), priority)) { priority = advice.prioritySuggestion(); prioritySource = "LLM"; }

        // 4. 담당자 스코어링 (단계 1 실적 + 부하)
        Ticket forScoring = shallowCopy(t, categoryId);
        var scored = assigneeScorer.score(forScoring);
        AssigneeScorer.Scored top = scored.isEmpty() ? null : scored.get(0);

        // 5. 신뢰도
        double confidence = 0.0;
        if (categoryId != null) confidence += 0.4;
        if (categoryAgrees) confidence += 0.25;
        else if (!similar.isEmpty()) confidence += 0.1;
        if (top != null && top.score() >= 0.5) confidence += 0.35;
        else if (top != null) confidence += 0.15;
        confidence = Math.min(1.0, Math.round(confidence * 100) / 100.0);

        boolean escalate = top == null || confidence < props.getMinConfidence();

        TriageResult result = new TriageResult(categoryId, categoryName, priority, prioritySource,
                top == null ? null : top.assigneeId(), top == null ? null : top.name(),
                top == null ? "배정 후보 없음" : top.rationale(),
                confidence, escalate, similar, advice.note(), advice.used());

        if (ticketId > 0) recordSnapshot(ticketId, result);
        return result;
    }

    private void recordSnapshot(long ticketId, TriageResult r) {
        String rationale = "cat=" + r.categoryName() + " pri=" + r.priority() + "(" + r.prioritySource() + ")"
                + " assignee=" + r.suggestedAssigneeName() + " [" + r.assigneeRationale() + "]"
                + (r.llmNote() != null ? " | LLM: " + r.llmNote() : "");
        try {
            jdbc.update("""
                INSERT INTO triage_snapshot
                    (ticket_id, category_id, priority, suggested_assignee, confidence, escalated, rationale, llm_used)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, ticketId, r.categoryId(), r.priority().name(), r.suggestedAssigneeId(),
                    r.confidence(), r.escalate(), rationale, r.llmUsed());
        } catch (Exception e) {
            log.warn("[triage] 스냅샷 기록 실패: {}", e.toString());
        }
        events.recordSystem(ticketId, TicketEventType.TRIAGED, null,
                "conf=" + r.confidence() + (r.escalate() ? " ESCALATE" : ""));
    }

    // ---------- helpers ----------

    private static Ticket shallowCopy(Ticket t, Long categoryId) {
        Ticket c = new Ticket();
        c.setId(t.getId());
        c.setClientId(t.getClientId());
        c.setCategoryId(categoryId);
        c.setTitle(t.getTitle());
        c.setContent(t.getContent());
        return c;
    }

    private static <T> T modal(List<T> values) {
        Map<T, Long> counts = values.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static Priority modalPriority(List<Ticket> similarTickets) {
        Priority p = modal(similarTickets.stream().map(Ticket::getPriority).toList());
        return p;
    }

    /** a 가 b 보다 심각한가 (null-safe). */
    private static boolean higher(Priority a, Priority b) {
        if (a == null) return false;
        if (b == null) return true;
        return a.ordinal() > b.ordinal();
    }
}
