package com.smartdesk.unit;

import com.smartdesk.domain.AppUser;
import com.smartdesk.domain.ClientUser;
import com.smartdesk.domain.Enums.NotificationType;
import com.smartdesk.feature.auth.EmailSender;
import com.smartdesk.feature.notification.NotificationChannels;
import com.smartdesk.repo.AppUserRepo;
import com.smartdesk.repo.ClientUserRepo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 0.5-a: 알림 채널 팬아웃 로직 (순수 단위). */
class NotificationChannelsTest {

    record Sent(String to, String subject, String body) {}

    private final List<Sent> emails = new CopyOnWriteArrayList<>();
    private final EmailSender capture = (to, subject, body) -> emails.add(new Sent(to, subject, body));

    private NotificationChannels channels(String emailTypes, String slackTypes, String webhook) {
        AppUserRepo users = mock(AppUserRepo.class);
        ClientUserRepo clientUsers = mock(ClientUserRepo.class);
        AppUser u = new AppUser();
        u.setId(2L); u.setEmail("agent@smartdesk.io");
        when(users.findById(2L)).thenReturn(Optional.of(u));
        ClientUser cu = new ClientUser();
        cu.setId(1L); cu.setEmail("client@a-corp.com");
        when(clientUsers.findById(1L)).thenReturn(Optional.of(cu));
        return new NotificationChannels(capture, users, clientUsers, emailTypes, slackTypes, webhook);
    }

    @Test
    void slaBreach_sendsEmailToRecipient() {
        channels("SLA_BREACHED,TICKET_ASSIGNED", "SLA_BREACHED", "")
                .fanOut("USER", 2L, NotificationType.SLA_BREACHED, "SLA 초과: #1", "본문", 1L);
        assertEquals(1, emails.size());
        assertEquals("agent@smartdesk.io", emails.get(0).to());
        assertTrue(emails.get(0).subject().startsWith("[SmartDesk] "));
    }

    @Test
    void commentNotification_isNotEmailed_byDefault() {
        channels("SLA_BREACHED,SLA_DUE_SOON,TICKET_ASSIGNED", "SLA_BREACHED", "")
                .fanOut("CLIENT_USER", 1L, NotificationType.TICKET_COMMENTED, "새 코멘트", "본문", 1L);
        assertTrue(emails.isEmpty());
    }

    @Test
    void configuredTypesAreParsed_unknownIgnored() {
        // "NONSENSE" 는 무시, TICKET_ASSIGNED 만 유효
        channels("TICKET_ASSIGNED,NONSENSE", "", "")
                .fanOut("CLIENT_USER", 1L, NotificationType.TICKET_ASSIGNED, "배정", "본문", 1L);
        assertEquals(1, emails.size());
    }

    @Test
    void slackSkippedWhenNoWebhook() {
        // 웹훅 미설정 → 예외 없이 무시 (이메일만)
        assertDoesNotThrow(() ->
                channels("", "SLA_BREACHED", "").fanOut("USER", 2L, NotificationType.SLA_BREACHED, "t", "b", 1L));
        assertTrue(emails.isEmpty());
    }
}
