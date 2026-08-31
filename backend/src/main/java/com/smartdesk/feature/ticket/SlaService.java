package com.smartdesk.feature.ticket;

import com.smartdesk.domain.Contract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;

/**
 * REQ-F-011: 계약 sla_resolution_min 기준 처리 마감시각 계산. 모든 시각 UTC 저장 (REQ-E-010).
 * - business-hours-only=false: 경과시간 단순 가산 (24h)
 * - business-hours-only=true : 영업시간(기본 평일 09-18, Asia/Seoul)만 카운트 (C13)
 */
@Service
public class SlaService {

    private final boolean businessHoursOnly;
    private final ZoneId zone;
    private final LocalTime dayStart;
    private final LocalTime dayEnd;

    public SlaService(@Value("${smartdesk.sla.business-hours-only:false}") boolean businessHoursOnly,
                      @Value("${smartdesk.sla.business-zone:Asia/Seoul}") String zone,
                      @Value("${smartdesk.sla.business-day-start:9}") int startHour,
                      @Value("${smartdesk.sla.business-day-end:18}") int endHour) {
        this.businessHoursOnly = businessHoursOnly;
        this.zone = ZoneId.of(zone);
        this.dayStart = LocalTime.of(startHour, 0);
        this.dayEnd = LocalTime.of(endHour, 0);
        if (!dayStart.isBefore(dayEnd)) {
            throw new IllegalStateException("smartdesk.sla.business-day-start < business-day-end 이어야 합니다.");
        }
    }

    public Instant computeDueAt(Contract contract, Instant createdAt) {
        Integer minutes = contract.getSlaResolutionMin();
        if (minutes == null || minutes <= 0) return null;
        return businessHoursOnly
                ? addBusinessMinutes(createdAt, minutes)
                : createdAt.plus(Duration.ofMinutes(minutes));
    }

    /** from 에서 영업시간 기준 minutes 만큼 경과한 시각. */
    Instant addBusinessMinutes(Instant from, long minutes) {
        ZonedDateTime cursor = from.atZone(zone);
        long remaining = minutes;
        while (remaining > 0) {
            cursor = advanceToBusinessTime(cursor);
            ZonedDateTime endOfToday = cursor.toLocalDate().atTime(dayEnd).atZone(zone);
            long availToday = Duration.between(cursor, endOfToday).toMinutes();
            if (availToday <= 0) {
                cursor = nextDayStart(cursor);
                continue;
            }
            long take = Math.min(remaining, availToday);
            cursor = cursor.plusMinutes(take);
            remaining -= take;
        }
        return cursor.toInstant();
    }

    private ZonedDateTime advanceToBusinessTime(ZonedDateTime t) {
        while (true) {
            DayOfWeek d = t.getDayOfWeek();
            if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) {
                t = nextDayStart(t);
                continue;
            }
            LocalTime lt = t.toLocalTime();
            if (lt.isBefore(dayStart)) {
                t = t.toLocalDate().atTime(dayStart).atZone(zone);
                continue;
            }
            if (!lt.isBefore(dayEnd)) {
                t = nextDayStart(t);
                continue;
            }
            return t;
        }
    }

    private ZonedDateTime nextDayStart(ZonedDateTime t) {
        return t.toLocalDate().plusDays(1).atTime(dayStart).atZone(zone);
    }

    public record SlaView(Instant dueAt, long remainingMinutes, boolean breached) {}

    public SlaView view(Instant dueAt) {
        if (dueAt == null) return new SlaView(null, 0, false);
        long remaining = Duration.between(Instant.now(), dueAt).toMinutes();
        return new SlaView(dueAt, remaining, remaining < 0);
    }
}
