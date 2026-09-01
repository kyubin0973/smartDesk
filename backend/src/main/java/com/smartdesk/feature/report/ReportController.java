package com.smartdesk.feature.report;

import com.smartdesk.common.ApiException;
import com.smartdesk.common.Csv;
import com.smartdesk.domain.Enums.TicketStatus;
import com.smartdesk.domain.Ticket;
import com.smartdesk.repo.CategoryRepo;
import com.smartdesk.repo.ClientRepo;
import com.smartdesk.repo.TicketRepo;
import com.smartdesk.security.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 0.5-d: SLA 준수율 리포트 (관리자 전용, REQ-F-016 리포트). */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final List<TicketStatus> DONE = List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneOffset.UTC);

    private final TicketRepo tickets;
    private final CategoryRepo categories;
    private final ClientRepo clients;

    public ReportController(TicketRepo tickets, CategoryRepo categories, ClientRepo clients) {
        this.tickets = tickets;
        this.categories = categories;
        this.clients = clients;
    }

    public record Group(Long id, String name, int total, int met, int breached, double complianceRate) {}
    public record SlaReport(Instant from, Instant to, int total, int met, int breached,
                            double complianceRate, List<Group> byClient, List<Group> byCategory) {}

    @GetMapping("/sla")
    public SlaReport sla(@RequestParam(required = false) String from,
                         @RequestParam(required = false) String to) {
        CurrentUser.requireManager();
        Instant fromT = parse(from, Instant.now().minus(Duration.ofDays(90)));
        Instant toT = parse(to, Instant.now().plus(Duration.ofDays(1)));
        if (!fromT.isBefore(toT)) throw ApiException.badRequest("from 은 to 보다 앞서야 합니다.");

        List<Ticket> done = tickets.findByStatusInAndCreatedAtRange(DONE, fromT, toT);
        int met = (int) done.stream().filter(Ticket::isSlaMet).count();

        Map<Long, String> catNames = names(categories.findAll(), c -> c.getId(), c -> c.getName());
        Map<Long, String> clientNames = names(clients.findAll(), c -> c.getId(), c -> c.getName());

        return new SlaReport(fromT, toT, done.size(), met, done.size() - met,
                rate(met, done.size()),
                groupBy(done, Ticket::getClientId, clientNames, "(미지정)"),
                groupBy(done, Ticket::getCategoryId, catNames, "(미분류)"));
    }

    @GetMapping("/sla/export")
    public ResponseEntity<byte[]> exportSla(@RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to) {
        SlaReport r = sla(from, to);
        List<List<Object>> body = new ArrayList<>();
        body.add(List.<Object>of("전체", "전체", r.total(), r.met(), r.breached(), pct(r.complianceRate())));
        for (Group g : r.byClient()) {
            body.add(List.<Object>of("고객사", g.name(), g.total(), g.met(), g.breached(), pct(g.complianceRate())));
        }
        for (Group g : r.byCategory()) {
            body.add(List.<Object>of("카테고리", g.name(), g.total(), g.met(), g.breached(), pct(g.complianceRate())));
        }
        String csv = Csv.of(List.of("구분", "이름", "종결건수", "SLA준수", "SLA위반", "준수율(%)"), body);
        String filename = "sla-report-" + TS.format(Instant.now()).replace(":", "").replace(" ", "_") + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- helpers ----------

    private List<Group> groupBy(List<Ticket> done, Function<Ticket, Long> keyFn,
                                Map<Long, String> nameLookup, String nullLabel) {
        Map<Long, List<Ticket>> grouped = done.stream()
                .collect(Collectors.groupingBy(t -> keyFn.apply(t) == null ? -1L : keyFn.apply(t)));
        List<Group> out = new ArrayList<>();
        grouped.forEach((id, list) -> {
            int met = (int) list.stream().filter(Ticket::isSlaMet).count();
            String name = id == -1L ? nullLabel : nameLookup.getOrDefault(id, "#" + id);
            out.add(new Group(id == -1L ? null : id, name, list.size(), met, list.size() - met,
                    rate(met, list.size())));
        });
        out.sort((a, b) -> Integer.compare(b.total(), a.total()));
        return out;
    }

    private <T> Map<Long, String> names(List<T> all, Function<T, Long> id, Function<T, String> name) {
        Map<Long, String> m = new HashMap<>();
        all.forEach(x -> m.put(id.apply(x), name.apply(x)));
        return m;
    }

    private static double rate(int met, int total) {
        return total == 0 ? 100.0 : Math.round(met * 10000.0 / total) / 100.0;
    }

    private static String pct(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    private Instant parse(String s, Instant fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            throw ApiException.badRequest("날짜 형식이 올바르지 않습니다 (ISO-8601): " + s);
        }
    }
}
