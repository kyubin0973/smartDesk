package com.smartdesk.feature.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 0.5-b: 알림 실시간 푸시. 수신자별 SseEmitter 를 들고 있다가 새 알림이 생기면 "poke" 한다.
 * 페이로드는 최소한(신호만) — 프런트가 신호를 받으면 /api/notifications 를 다시 불러 정확한 목록·미읽음 수를 얻는다.
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<String, List<SseEmitter>> byRecipient = new ConcurrentHashMap<>();

    public SseEmitter register(String recipientType, Long recipientId) {
        String key = key(recipientType, recipientId);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        byRecipient.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(e -> remove(key, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(key, emitter);
        }
        return emitter;
    }

    /** 해당 수신자의 모든 연결에 새 알림 신호를 보낸다. */
    public void poke(String recipientType, Long recipientId) {
        List<SseEmitter> list = byRecipient.get(key(recipientType, recipientId));
        if (list == null) return;
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name("notification").data("new"));
            } catch (Exception ex) {
                remove(key(recipientType, recipientId), e);
            }
        }
    }

    /** 프록시·로드밸런서가 유휴 연결을 끊지 않도록 주기적 코멘트 전송 + 죽은 연결 정리. (SseHeartbeatJob 이 호출) */
    public void heartbeat() {
        byRecipient.forEach((key, list) -> {
            for (SseEmitter e : list) {
                try {
                    e.send(SseEmitter.event().comment("ping"));
                } catch (Exception ex) {
                    remove(key, e);
                }
            }
        });
    }

    public int connectionCount() {
        return byRecipient.values().stream().mapToInt(List::size).sum();
    }

    private void remove(String key, SseEmitter emitter) {
        List<SseEmitter> list = byRecipient.get(key);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) byRecipient.remove(key, list);
        }
    }

    private String key(String type, Long id) {
        return type + ":" + id;
    }
}
