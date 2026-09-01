package com.smartdesk.feature.triage;

import com.smartdesk.domain.Ticket;
import com.smartdesk.feature.ticket.AssignmentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 단계 3.2: 열린 티켓의 SLA 위반 위험도 (0~1) 추정.
 * 운영 데이터가 얇은 동안은 휴리스틱 — 경과율·담당자 부하·카테고리 p90 대비 잔여시간·재오픈 여부.
 * PriorityRules 처럼 나중에 학습 모델로 교체 가능한 구조.
 */
@Service
public class SlaRiskService {

    private final AssignmentService assignment;
    private final JdbcTemplate jdbc;

    public SlaRiskService(AssignmentService assignment, JdbcTemplate jdbc) {
        this.assignment = assignment;
        this.jdbc = jdbc;
    }

    public record Risk(double score, String level, List<String> factors, boolean suggestReassign) {}

    public Risk assess(Ticket t) {
        Instant now = Instant.now();
        List<String> factors = new ArrayList<>();
        double score = 0;

        // 1. 경과율: 생성~마감 중 얼마나 지났나
        if (t.getSlaDueAt() != null) {
            double total = Math.max(1, Duration.between(t.getCreatedAt(), t.getSlaDueAt()).toMinutes());
            double elapsed = Duration.between(t.getCreatedAt(), now).toMinutes();
            double frac = elapsed / total;
            if (frac >= 0.9) { score += 0.45; factors.add("마감 임박 (경과 " + pct(frac) + ")"); }
            else if (frac >= 0.7) { score += 0.25; factors.add("경과 " + pct(frac)); }
        }

        // 2. 미배정
        if (t.getAssigneeId() == null) {
            score += 0.25;
            factors.add("담당자 미배정");
        } else {
            long load = assignment.openLoad(t.getAssigneeId());
            if (load >= 8) { score += 0.2; factors.add("담당자 부하 높음 (" + load + "건)"); }
            else if (load >= 5) { score += 0.1; factors.add("담당자 부하 " + load + "건"); }
        }

        // 3. 카테고리 난이도: p90 처리시간이 잔여시간보다 길면 위험
        if (t.getCategoryId() != null && t.getSlaDueAt() != null) {
            Double p90 = categoryP90Minutes(t.getCategoryId());
            long remaining = Duration.between(now, t.getSlaDueAt()).toMinutes();
            if (p90 != null && remaining > 0 && p90 > remaining) {
                score += 0.25;
                factors.add("이 카테고리 통상 처리시간(p90 " + Math.round(p90) + "분) > 잔여 " + remaining + "분");
            }
        }

        // 4. 재오픈 이력
        Integer reopens = reopenCount(t.getId());
        if (reopens != null && reopens > 0) {
            score += 0.15;
            factors.add("재오픈 " + reopens + "회");
        }

        score = Math.min(1.0, Math.round(score * 100) / 100.0);
        String level = score >= 0.7 ? "HIGH" : score >= 0.4 ? "MEDIUM" : "LOW";
        boolean suggestReassign = score >= 0.7
                && (t.getAssigneeId() == null || assignment.openLoad(t.getAssigneeId()) >= 5);
        return new Risk(score, level, factors, suggestReassign);
    }

    private Double categoryP90Minutes(Long categoryId) {
        return jdbc.query("""
            SELECT percentile_cont(0.9) WITHIN GROUP (ORDER BY resolution_minutes) AS p90
            FROM analytics.ticket_metrics
            WHERE category_id = ? AND resolution_minutes IS NOT NULL
            """, rs -> rs.next() ? (Double) rs.getObject("p90") : null, categoryId);
    }

    private Integer reopenCount(Long ticketId) {
        if (ticketId == null) return 0;
        return jdbc.query("SELECT reopen_count FROM analytics.ticket_metrics WHERE ticket_id = ?",
                rs -> rs.next() ? rs.getInt("reopen_count") : 0, ticketId);
    }

    private static String pct(double d) {
        return Math.round(d * 100) + "%";
    }
}
