package com.smartdesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** SecurityConfig 에서 @Bean 으로 등록 (자동 필터 등록 방지). */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final TokenRevocationRegistry revocation;

    public JwtAuthFilter(JwtService jwt, TokenRevocationRegistry revocation) {
        this.jwt = jwt;
        this.revocation = revocation;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AuthPrincipal p = jwt.parse(header.substring(7));
                if (!revocation.isRevoked(p.jti())) {   // B7: 인메모리 O(1) 조회
                    String authority = p.isSiUser() ? "ROLE_" + p.role() : "ROLE_CLIENT_USER";
                    var auth = new UsernamePasswordAuthenticationToken(
                            p, null, List.of(new SimpleGrantedAuthority(authority)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
                // 폐기된 토큰이면 익명으로 → 보호 리소스는 401/403
            } catch (Exception ignored) {
                // 유효하지 않은/만료된 토큰 → 익명 처리
            }
        }
        chain.doFilter(req, res);
    }
}
