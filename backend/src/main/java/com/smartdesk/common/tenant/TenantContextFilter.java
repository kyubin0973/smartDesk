package com.smartdesk.common.tenant;

import com.smartdesk.security.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 단계 4: 인증 후 테넌시 컨텍스트 확정. Spring Security 체인 뒤에서 실행되도록 낮은 우선순위.
 * RLS(DB) 는 이 값이 정확해야 동작 — 애플리케이션 레벨 필터의 이중 방어.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
                if (p.isClientUser() && p.clientId() != null) {
                    TenantContext.setClient(p.clientId());
                } else {
                    TenantContext.setSystem();   // SI 직원/관리자
                }
            } else {
                TenantContext.deny();            // 미인증 (로그인 등)
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
