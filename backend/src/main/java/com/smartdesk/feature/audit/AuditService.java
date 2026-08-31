package com.smartdesk.feature.audit;

import com.smartdesk.domain.AuditLog;
import com.smartdesk.repo.AuditLogRepo;
import com.smartdesk.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** C11: 보안·관리 이벤트 감사 로그 기록. */
@Service
public class AuditService {

    private final AuditLogRepo repo;

    public AuditService(AuditLogRepo repo) {
        this.repo = repo;
    }

    /** 현재 인증 주체·요청 IP 를 자동으로 채워 기록. */
    public void record(String action, String targetType, Long targetId, String detail) {
        AuthPrincipal p = currentPrincipal();
        AuditLog a = new AuditLog();
        if (p != null) {
            a.setActorType(p.type().name());
            a.setActorId(p.id());
            a.setActorEmail(p.email());
        } else {
            a.setActorType("ANONYMOUS");
        }
        a.setAction(action);
        a.setTargetType(targetType);
        a.setTargetId(targetId);
        a.setDetail(truncate(detail));
        a.setIp(currentIp());
        repo.save(a);
    }

    /**
     * 인증 이벤트 (로그인 성공/실패, 로그아웃, 비밀번호). 독립 트랜잭션 — 실패해서 롤백되는
     * 비즈니스 트랜잭션 안에서 호출돼도 감사 기록은 남는다 (예: LOGIN_FAILURE).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuth(String action, String actorType, Long actorId, String actorEmail, String ip, String detail) {
        AuditLog a = new AuditLog();
        a.setActorType(actorType);
        a.setActorId(actorId);
        a.setActorEmail(actorEmail);
        a.setAction(action);
        a.setDetail(truncate(detail));
        a.setIp(ip);
        repo.save(a);
    }

    private AuthPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) ? p : null;
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
            return req.getRemoteAddr();
        }
        return null;
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
