package com.smartdesk.feature.triage;

import com.smartdesk.domain.AppUser;
import com.smartdesk.domain.Ticket;
import com.smartdesk.feature.ticket.AssignmentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 단계 3.1: 배정 후보 스코어링.
 * 점수 = 부하 여유(현재 열린 티켓 적을수록↑) + 해당 카테고리 처리 경험 − 그 카테고리 SLA 위반율.
 * 규칙 기반 후보군(AssignmentService.candidates) 위에 분석 데이터(analytics.ticket_metrics)를 얹는다.
 */
@Service
public class AssigneeScorer {

    private final AssignmentService assignment;
    private final JdbcTemplate jdbc;

    public AssigneeScorer(AssignmentService assignment, JdbcTemplate jdbc) {
        this.assignment = assignment;
        this.jdbc = jdbc;
    }

    public record Scored(long assigneeId, String name, double score, String rationale) {}

    public List<Scored> score(Ticket ticket) {
        List<AppUser> candidates = assignment.candidates(ticket);
        if (candidates.isEmpty()) return List.of();

        Map<Long, CatStat> byUser = categoryStats(ticket.getCategoryId());
        long maxLoad = candidates.stream().mapToLong(u -> assignment.openLoad(u.getId())).max().orElse(0);

        return candidates.stream().map(u -> {
            long load = assignment.openLoad(u.getId());
            double loadScore = maxLoad == 0 ? 1.0 : 1.0 - (double) load / (maxLoad + 1);
            CatStat st = byUser.getOrDefault(u.getId(), CatStat.EMPTY);
            double expScore = Math.min(1.0, st.resolved / 5.0);      // 5건이면 만점
            double penalty = st.breachRate;                          // 0~1
            double score = round(0.5 * loadScore + 0.4 * expScore - 0.3 * penalty);

            String rationale = "부하 " + load + "건"
                    + (st.resolved > 0
                        ? " · 이 카테고리 " + st.resolved + "건 처리(위반율 " + pct(st.breachRate) + ")"
                        : " · 이 카테고리 처리 이력 없음");
            return new Scored(u.getId(), u.getName(), score, rationale);
        }).sorted((a, b) -> Double.compare(b.score(), a.score())).toList();
    }

    private record CatStat(int resolved, double breachRate) {
        static final CatStat EMPTY = new CatStat(0, 0);
    }

    private Map<Long, CatStat> categoryStats(Long categoryId) {
        Map<Long, CatStat> m = new HashMap<>();
        if (categoryId == null) return m;
        jdbc.query("""
            SELECT assignee_id,
                   count(*) FILTER (WHERE resolved_at IS NOT NULL)                      AS resolved,
                   coalesce(avg((sla_met IS FALSE)::int) FILTER (WHERE sla_met IS NOT NULL), 0) AS breach_rate
            FROM analytics.ticket_metrics
            WHERE category_id = ? AND assignee_id IS NOT NULL
            GROUP BY assignee_id
            """, rs -> {
            m.put(rs.getLong("assignee_id"),
                    new CatStat(rs.getInt("resolved"), rs.getDouble("breach_rate")));
        }, categoryId);
        return m;
    }

    private static double round(double d) {
        return Math.max(0, Math.round(d * 1000.0) / 1000.0);
    }

    private static String pct(double d) {
        return Math.round(d * 100) + "%";
    }
}
