package com.smartdesk.unit;

import com.smartdesk.domain.Contract;
import com.smartdesk.domain.Enums.Priority;
import com.smartdesk.feature.ticket.PriorityRules;
import com.smartdesk.feature.ticket.SlaService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 순수 단위 테스트 (스프링 컨텍스트 불필요). */
class SlaAndPriorityTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final SlaService sla24h = new SlaService(false, "Asia/Seoul", 9, 18);
    private final SlaService slaBiz = new SlaService(true, "Asia/Seoul", 9, 18);
    private final PriorityRules priority = new PriorityRules();

    private static Contract contract(int min) {
        Contract c = new Contract();
        c.setSlaResolutionMin(min);
        return c;
    }

    @Test
    void computeDueAt_24h_addsResolutionMinutes() {
        Instant created = Instant.parse("2026-08-31T00:00:00Z");
        assertEquals(created.plus(8, ChronoUnit.HOURS), sla24h.computeDueAt(contract(480), created));
    }

    @Test
    void computeDueAt_nullWhenNoResolutionMin() {
        assertNull(sla24h.computeDueAt(new Contract(), Instant.now()));
    }

    @Test
    void slaView_flagsBreach() {
        assertTrue(sla24h.view(Instant.now().minus(30, ChronoUnit.MINUTES)).breached());
        assertFalse(sla24h.view(Instant.now().plus(3, ChronoUnit.HOURS)).breached());
    }

    @Test
    void businessHours_withinSameDay() {
        // 금 10:00 KST + 4h(영업) = 금 14:00 KST
        Instant created = ZonedDateTime.of(2026, 8, 28, 10, 0, 0, 0, KST).toInstant();
        Instant due = slaBiz.computeDueAt(contract(240), created);
        assertEquals(ZonedDateTime.of(2026, 8, 28, 14, 0, 0, 0, KST).toInstant(), due);
    }

    @Test
    void businessHours_rollsOverToNextDay_skippingNight() {
        // 금 16:00 KST + 4h(영업). 금은 2h 남음(18시까지) → 나머지 2h 는 월(다음 영업일) 09:00~11:00
        Instant created = ZonedDateTime.of(2026, 8, 28, 16, 0, 0, 0, KST).toInstant();
        Instant due = slaBiz.computeDueAt(contract(240), created);
        assertEquals(ZonedDateTime.of(2026, 8, 31, 11, 0, 0, 0, KST).toInstant(), due);
    }

    @Test
    void businessHours_startsBeforeOpen_clampsToOpen() {
        // 월 07:00 KST + 1h → 월 10:00 KST (09:00 부터 카운트)
        Instant created = ZonedDateTime.of(2026, 8, 31, 7, 0, 0, 0, KST).toInstant();
        Instant due = slaBiz.computeDueAt(contract(60), created);
        assertEquals(ZonedDateTime.of(2026, 8, 31, 10, 0, 0, 0, KST).toInstant(), due);
    }

    @Test
    void businessHours_createdOnWeekend_startsMonday() {
        // 토 12:00 KST + 3h → 월 12:00 KST
        Instant created = ZonedDateTime.of(2026, 8, 29, 12, 0, 0, 0, KST).toInstant();
        Instant due = slaBiz.computeDueAt(contract(180), created);
        assertEquals(ZonedDateTime.of(2026, 8, 31, 12, 0, 0, 0, KST).toInstant(), due);
    }

    @Test
    void priorityRules_keywords() {
        assertEquals(Priority.CRITICAL, priority.infer("서비스 전체 장애", "중단됨"));
        assertEquals(Priority.HIGH, priority.infer("로그인 오류", "500 에러"));
        assertEquals(Priority.LOW, priority.infer("사용법 문의", "질문 있습니다"));
        assertEquals(Priority.MEDIUM, priority.infer("화면 색상 변경 건", "배경을 파랗게"));
    }
}
