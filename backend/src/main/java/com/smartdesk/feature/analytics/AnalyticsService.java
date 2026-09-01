package com.smartdesk.feature.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 단계 1.1: 분석 마트 조회 + materialized view 갱신.
 * 뷰는 analytics 스키마(V6). JPA 엔티티 대신 JdbcTemplate — 읽기 전용 집계라 매핑이 단순.
 */
@Service
public class AnalyticsService {

    private final JdbcTemplate jdbc;

    public AnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 전체 요약: 해결 건수, 처리시간 p50/p90, SLA 위반율, 재오픈율, 우선순위별 처리시간. */
    public Map<String, Object> overview() {
        Map<String, Object> headline = jdbc.queryForMap("""
            SELECT
                count(*) FILTER (WHERE resolution_minutes IS NOT NULL)                       AS resolved_count,
                count(*)                                                                     AS total_count,
                round(percentile_cont(0.5) WITHIN GROUP (ORDER BY resolution_minutes)::numeric, 1) AS p50_minutes,
                round(percentile_cont(0.9) WITHIN GROUP (ORDER BY resolution_minutes)::numeric, 1) AS p90_minutes,
                round(avg((sla_met IS FALSE)::int)::numeric, 4)                              AS sla_breach_rate,
                round(avg((reopen_count > 0)::int)::numeric, 4)                              AS reopen_rate
            FROM analytics.ticket_metrics
            """);

        List<Map<String, Object>> byPriority = jdbc.queryForList("""
            SELECT priority,
                   count(*)                                                                        AS ticket_count,
                   round(percentile_cont(0.5) WITHIN GROUP (ORDER BY resolution_minutes)::numeric, 1) AS p50_minutes,
                   round(avg((sla_met IS FALSE)::int)::numeric, 4)                                  AS sla_breach_rate
            FROM analytics.ticket_metrics
            GROUP BY priority
            ORDER BY CASE priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END
            """);

        return Map.of("headline", headline, "byPriority", byPriority);
    }

    /** 카테고리별 해결시간 통계 (요일·시간대 집계를 카테고리로 롤업). */
    public List<Map<String, Object>> resolutionStatsByCategory() {
        return jdbc.queryForList("""
            SELECT c.id                           AS category_id,
                   COALESCE(c.name, '(미분류)')   AS category_name,
                   sum(s.resolved_count)          AS resolved_count,
                   round(avg(s.p50_minutes)::numeric, 1) AS p50_minutes,
                   round(avg(s.p90_minutes)::numeric, 1) AS p90_minutes,
                   round(sum(s.resolved_count * s.sla_breach_rate)
                         / NULLIF(sum(s.resolved_count), 0), 4)  AS sla_breach_rate
            FROM analytics.ticket_resolution_stats s
            LEFT JOIN category c ON c.id = s.category_id
            GROUP BY c.id, c.name
            ORDER BY resolved_count DESC
            """);
    }

    /** 요일 × 시간대 요청량 히트맵 (전체 카테고리 합). */
    public List<Map<String, Object>> hourlyHeatmap() {
        return jdbc.queryForList("""
            SELECT created_dow, created_hour, count(*) AS ticket_count
            FROM analytics.ticket_metrics
            GROUP BY created_dow, created_hour
            ORDER BY created_dow, created_hour
            """);
    }

    public List<Map<String, Object>> assigneeThroughput() {
        return jdbc.queryForList("""
            SELECT assignee_id, name, resolved_count, open_load,
                   round(p50_minutes::numeric, 1) AS p50_minutes,
                   round(avg_minutes::numeric, 1) AS avg_minutes,
                   round(sla_breach_rate::numeric, 4) AS sla_breach_rate
            FROM analytics.assignee_throughput
            WHERE resolved_count > 0 OR open_load > 0
            ORDER BY resolved_count DESC
            """);
    }

    /** contract.sla_resolution_min 권장값: 카테고리별 p90 (분) 올림. */
    public List<Map<String, Object>> slaRecommendation() {
        return jdbc.queryForList("""
            SELECT c.id AS category_id,
                   COALESCE(c.name, '(미분류)') AS category_name,
                   sum(s.resolved_count) AS sample_size,
                   ceil(avg(s.p90_minutes))::int AS recommended_sla_minutes
            FROM analytics.ticket_resolution_stats s
            LEFT JOIN category c ON c.id = s.category_id
            GROUP BY c.id, c.name
            HAVING sum(s.resolved_count) >= 1
            ORDER BY recommended_sla_minutes DESC
            """);
    }

    public void refreshMaterializedViews() {
        jdbc.execute("REFRESH MATERIALIZED VIEW analytics.ticket_resolution_stats");
    }
}
