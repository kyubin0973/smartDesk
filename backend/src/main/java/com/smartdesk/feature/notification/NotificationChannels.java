package com.smartdesk.feature.notification;

import com.smartdesk.domain.Enums.NotificationType;
import com.smartdesk.feature.auth.EmailSender;
import com.smartdesk.repo.AppUserRepo;
import com.smartdesk.repo.ClientUserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 0.5-a: 인앱 알림을 외부 채널로 팬아웃 (이메일 / 슬랙).
 * 채널 발송 실패가 비즈니스 흐름을 막지 않도록 모두 삼킨다.
 */
@Component
public class NotificationChannels {

    private static final Logger log = LoggerFactory.getLogger(NotificationChannels.class);

    private final EmailSender email;
    private final AppUserRepo users;
    private final ClientUserRepo clientUsers;
    private final RestClient http = RestClient.create();

    private final Set<NotificationType> emailTypes;
    private final Set<NotificationType> slackTypes;
    private final String slackWebhook;

    public NotificationChannels(EmailSender email, AppUserRepo users, ClientUserRepo clientUsers,
                                @Value("${smartdesk.notifications.email-types:SLA_BREACHED,SLA_DUE_SOON,TICKET_ASSIGNED}") String emailTypes,
                                @Value("${smartdesk.notifications.slack-types:SLA_BREACHED}") String slackTypes,
                                @Value("${smartdesk.notifications.slack-webhook-url:}") String slackWebhook) {
        this.email = email;
        this.users = users;
        this.clientUsers = clientUsers;
        this.emailTypes = parse(emailTypes);
        this.slackTypes = parse(slackTypes);
        this.slackWebhook = slackWebhook;
    }

    public void fanOut(String recipientType, Long recipientId, NotificationType type,
                       String title, String body, Long ticketId) {
        if (emailTypes.contains(type)) {
            recipientEmail(recipientType, recipientId).ifPresent(addr -> {
                try {
                    email.send(addr, "[SmartDesk] " + title, body);
                } catch (Exception e) {
                    log.warn("[notify-email] 실패 {} : {}", addr, e.toString());
                }
            });
        }
        if (slackTypes.contains(type) && !slackWebhook.isBlank()) {
            try {
                http.post().uri(slackWebhook)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body("{\"text\":" + jsonString("[" + type + "] " + title + "\n" + body) + "}")
                        .retrieve().toBodilessEntity();
            } catch (Exception e) {
                log.warn("[notify-slack] 실패 : {}", e.toString());
            }
        }
    }

    private java.util.Optional<String> recipientEmail(String recipientType, Long id) {
        return "USER".equals(recipientType)
                ? users.findById(id).map(u -> u.getEmail())
                : clientUsers.findById(id).map(c -> c.getEmail());
    }

    private Set<NotificationType> parse(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return NotificationType.valueOf(s); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
