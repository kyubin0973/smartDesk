package com.smartdesk.feature.audit;

import com.smartdesk.common.ApiException;
import com.smartdesk.common.PageResponse;
import com.smartdesk.domain.AppUser;
import com.smartdesk.domain.AuditLog;
import com.smartdesk.domain.ClientUser;
import com.smartdesk.domain.Enums.TicketEventType;
import com.smartdesk.domain.TicketEvent;
import com.smartdesk.repo.*;
import com.smartdesk.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** C11: 감사 로그 조회 (관리자 전용, REQ-F-014 컴플라이언스). */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogRepo audit;
    private final TicketEventRepo ticketEvents;
    private final TicketRepo tickets;
    private final AppUserRepo users;
    private final ClientUserRepo clientUsers;

    public AuditController(AuditLogRepo audit, TicketEventRepo ticketEvents, TicketRepo tickets,
                          AppUserRepo users, ClientUserRepo clientUsers) {
        this.audit = audit;
        this.ticketEvents = ticketEvents;
        this.tickets = tickets;
        this.users = users;
        this.clientUsers = clientUsers;
    }

    public record AuditRow(Long id, Instant at, String actorType, String actorEmail,
                           String action, String targetType, Long targetId, String detail, String ip) {}
    public record TicketEventRow(Long id, Instant at, Long ticketId, String ticketTitle,
                                 String type, String fromValue, String toValue, String actor) {}

    /** 보안·관리 이벤트 */
    @GetMapping
    public PageResponse<AuditRow> list(@RequestParam(required = false) String action,
                                       @RequestParam(required = false) String actorEmail,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "30") int size) {
        CurrentUser.requireManager();
        var result = audit.search(
                action == null ? "" : action.trim(),
                actorEmail == null ? "" : actorEmail.trim(),
                fromOr(from, Instant.EPOCH), toOr(to), PageRequest.of(page, size));
        return PageResponse.of(result.map(a -> new AuditRow(a.getId(), a.getAt(), a.getActorType(),
                a.getActorEmail(), a.getAction(), a.getTargetType(), a.getTargetId(), a.getDetail(), a.getIp())));
    }

    /** 티켓 생애주기 이벤트 (전체 티켓) */
    @GetMapping("/ticket-events")
    public PageResponse<TicketEventRow> ticketEventList(@RequestParam(required = false) String type,
                                                       @RequestParam(required = false) Long ticketId,
                                                       @RequestParam(required = false) String from,
                                                       @RequestParam(required = false) String to,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "30") int size) {
        CurrentUser.requireManager();
        TicketEventType t = null;
        if (type != null && !type.isBlank()) {
            try { t = TicketEventType.valueOf(type); }
            catch (IllegalArgumentException e) { throw ApiException.badRequest("알 수 없는 이벤트 유형: " + type); }
        }
        var result = ticketEvents.search(t, ticketId, fromOr(from, Instant.EPOCH), toOr(to), PageRequest.of(page, size));

        // 배치: 티켓 제목 + 행위자 이름
        Map<Long, String> titles = new HashMap<>();
        tickets.findAllById(result.getContent().stream().map(TicketEvent::getTicketId).distinct().toList())
                .forEach(tk -> titles.put(tk.getId(), tk.getTitle()));
        Map<Long, String> siNames = new HashMap<>();
        users.findAllById(result.getContent().stream()
                        .filter(e -> "USER".equals(e.getActorType())).map(TicketEvent::getActorId).filter(java.util.Objects::nonNull).distinct().toList())
                .forEach(u -> siNames.put(u.getId(), u.getName()));
        Map<Long, String> cuNames = new HashMap<>();
        clientUsers.findAllById(result.getContent().stream()
                        .filter(e -> "CLIENT_USER".equals(e.getActorType())).map(TicketEvent::getActorId).filter(java.util.Objects::nonNull).distinct().toList())
                .forEach(cu -> cuNames.put(cu.getId(), cu.getName()));

        return PageResponse.of(result.map(e -> {
            String actor = switch (e.getActorType() == null ? "" : e.getActorType()) {
                case "USER" -> siNames.getOrDefault(e.getActorId(), "SI 직원");
                case "CLIENT_USER" -> cuNames.getOrDefault(e.getActorId(), "고객사 담당자");
                default -> "시스템";
            };
            return new TicketEventRow(e.getId(), e.getAt(), e.getTicketId(),
                    titles.getOrDefault(e.getTicketId(), "#" + e.getTicketId()),
                    e.getType().name(), e.getFromValue(), e.getToValue(), actor);
        }));
    }

    private Instant fromOr(String s, Instant fallback) {
        Instant v = parseInstant(s);
        return v != null ? v : fallback;
    }

    private Instant toOr(String s) {
        Instant v = parseInstant(s);
        return v != null ? v : Instant.now().plus(java.time.Duration.ofDays(3650));
    }

    private Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Instant.parse(s); }
        catch (Exception e) { throw ApiException.badRequest("날짜 형식이 올바르지 않습니다 (ISO-8601): " + s); }
    }
}
