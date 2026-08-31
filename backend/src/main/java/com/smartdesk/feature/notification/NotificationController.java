package com.smartdesk.feature.notification;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.Notification;
import com.smartdesk.repo.NotificationRepo;
import com.smartdesk.security.AuthPrincipal;
import com.smartdesk.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepo repo;

    public NotificationController(NotificationRepo repo) {
        this.repo = repo;
    }

    public record NotificationView(Long id, String type, String title, String body, Long ticketId,
                                   boolean read, Instant createdAt) {}

    @GetMapping
    public Map<String, Object> list() {
        AuthPrincipal p = CurrentUser.get();
        String rt = p.type().name();
        List<NotificationView> items = repo.findByRecipientTypeAndRecipientIdOrderByCreatedAtDesc(rt, p.id()).stream()
                .limit(50)
                .map(n -> new NotificationView(n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
                        n.getTicketId(), n.getReadAt() != null, n.getCreatedAt()))
                .toList();
        long unread = repo.countByRecipientTypeAndRecipientIdAndReadAtIsNull(rt, p.id());
        return Map.of("items", items, "unread", unread);
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        AuthPrincipal p = CurrentUser.get();
        Notification n = repo.findById(id).orElseThrow(() -> ApiException.notFound("알림"));
        if (!n.getRecipientType().equals(p.type().name()) || !n.getRecipientId().equals(p.id())) {
            throw ApiException.forbidden("본인 알림이 아닙니다.");
        }
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            repo.save(n);
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @Transactional
    public ResponseEntity<Void> markAllRead() {
        AuthPrincipal p = CurrentUser.get();
        repo.findByRecipientTypeAndRecipientIdOrderByCreatedAtDesc(p.type().name(), p.id()).forEach(n -> {
            if (n.getReadAt() == null) { n.setReadAt(Instant.now()); repo.save(n); }
        });
        return ResponseEntity.noContent().build();
    }
}
