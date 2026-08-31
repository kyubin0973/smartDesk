package com.smartdesk.security;

import com.smartdesk.domain.Enums.AuthorType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    /** application.yml 의 개발용 기본값 — 운영에서 이 값이면 부팅 거부. */
    static final String DEV_DEFAULT_SECRET = "c21hcnRkZXNrLWRldi1zZWNyZXQta2V5LWNoYW5nZS1tZS0zMi1ieXRlcw==";

    private final SecretKey key;
    private final long ttlSeconds;

    public JwtService(@Value("${smartdesk.jwt.secret}") String secret,
                      @Value("${smartdesk.jwt.access-token-ttl-seconds}") long ttlSeconds,
                      Environment env) {
        boolean prodProfile = java.util.List.of(env.getActiveProfiles()).contains("prod");
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            if (prodProfile) {
                throw new IllegalStateException(
                        "JWT_SECRET 환경변수가 설정되지 않았습니다. prod 프로파일에서는 개발용 기본 시크릿을 사용할 수 없습니다.");
            }
            log.warn("개발용 JWT 시크릿을 사용 중입니다. 운영 배포 전 JWT_SECRET 을 반드시 교체하세요.");
        }
        byte[] raw = Base64.getDecoder().decode(secret);
        if (raw.length < 32) throw new IllegalStateException("JWT 시크릿은 Base64 디코딩 시 32바이트 이상이어야 합니다.");
        this.key = Keys.hmacShaKeyFor(raw);
        this.ttlSeconds = ttlSeconds;
    }

    public long ttlSeconds() { return ttlSeconds; }

    public record IssuedToken(String token, String jti, Instant expiresAt) {}

    public IssuedToken issue(AuthPrincipal p) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .subject(String.valueOf(p.id()))
                .claim("typ", p.type().name())
                .claim("email", p.email())
                .claim("role", p.role())
                .claim("clientId", p.clientId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, exp);
    }

    public AuthPrincipal parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Number cid = c.get("clientId", Number.class);
        return new AuthPrincipal(
                AuthorType.valueOf(c.get("typ", String.class)),
                Long.valueOf(c.getSubject()),
                c.get("email", String.class),
                c.get("role", String.class),
                cid == null ? null : cid.longValue(),
                c.getId(),
                c.getExpiration().toInstant());
    }
}
