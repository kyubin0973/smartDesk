package com.smartdesk.security;

import com.smartdesk.repo.RevokedAccessTokenRepo;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B7: 폐기된 액세스 토큰(jti) 인메모리 집합.
 * 요청마다 DB 조회하던 것을 O(1) 조회로 대체.
 * - 로그아웃 시 즉시 add (+ DB 는 AuthService 가 기록)
 * - 5분마다 DB 에서 재로드 (다중 인스턴스 최종 일관성 + 만료분 정리)
 * 다중 인스턴스에서 다른 노드의 로그아웃은 최대 5분 지연 반영 (액세스 토큰 TTL 1h 대비 허용).
 */
@Component
public class TokenRevocationRegistry {

    private final RevokedAccessTokenRepo repo;
    private volatile Set<String> revoked = ConcurrentHashMap.newKeySet();

    public TokenRevocationRegistry(RevokedAccessTokenRepo repo) {
        this.repo = repo;
    }

    public boolean isRevoked(String jti) {
        return jti != null && revoked.contains(jti);
    }

    public void add(String jti) {
        if (jti != null) revoked.add(jti);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${smartdesk.jwt.revocation-reload-ms:300000}")
    public void reload() {
        Set<String> fresh = ConcurrentHashMap.newKeySet();
        repo.findAll().forEach(t -> fresh.add(t.getJti()));
        this.revoked = fresh;
    }
}
