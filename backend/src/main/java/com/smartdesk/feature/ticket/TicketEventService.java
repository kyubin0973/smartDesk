package com.smartdesk.feature.ticket;

import com.smartdesk.domain.Enums.TicketEventType;
import com.smartdesk.domain.TicketEvent;
import com.smartdesk.repo.TicketEventRepo;
import com.smartdesk.security.AuthPrincipal;
import org.springframework.stereotype.Service;

/** 티켓 이벤트 로그 기록 (append-only). */
@Service
public class TicketEventService {

    private final TicketEventRepo events;

    public TicketEventService(TicketEventRepo events) {
        this.events = events;
    }

    public void record(Long ticketId, TicketEventType type, String from, String to, AuthPrincipal actor) {
        String actorType = actor == null ? "SYSTEM" : actor.type().name();
        Long actorId = actor == null ? null : actor.id();
        events.save(new TicketEvent(ticketId, type, from, to, actorType, actorId));
    }

    public void recordSystem(Long ticketId, TicketEventType type, String from, String to) {
        events.save(new TicketEvent(ticketId, type, from, to, "SYSTEM", null));
    }

    /** 티켓·유형당 1회만 기록 (예: SLA_BREACHED — 스캔마다 중복 방지). */
    public void recordSystemOnce(Long ticketId, TicketEventType type, String from, String to) {
        if (!events.existsByTicketIdAndType(ticketId, type)) {
            events.save(new TicketEvent(ticketId, type, from, to, "SYSTEM", null));
        }
    }
}
