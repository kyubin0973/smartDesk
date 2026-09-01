package com.smartdesk.feature.analytics;

import com.smartdesk.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 단계 1.1: 분석 대시보드 API (관리자 전용). 데이터 근거는 analytics 스키마(V6). */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        CurrentUser.requireManager();
        return analytics.overview();
    }

    @GetMapping("/resolution-stats")
    public List<Map<String, Object>> resolutionStats() {
        CurrentUser.requireManager();
        return analytics.resolutionStatsByCategory();
    }

    @GetMapping("/heatmap")
    public List<Map<String, Object>> heatmap() {
        CurrentUser.requireManager();
        return analytics.hourlyHeatmap();
    }

    @GetMapping("/assignee-throughput")
    public List<Map<String, Object>> assigneeThroughput() {
        CurrentUser.requireManager();
        return analytics.assigneeThroughput();
    }

    @GetMapping("/sla-recommendation")
    public List<Map<String, Object>> slaRecommendation() {
        CurrentUser.requireManager();
        return analytics.slaRecommendation();
    }

    /** materialized view 수동 갱신 (야간 배치 외 즉시 반영이 필요할 때). */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh() {
        CurrentUser.requireManager();
        analytics.refreshMaterializedViews();
        return ResponseEntity.noContent().build();
    }
}
