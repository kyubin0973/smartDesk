package com.smartdesk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdesk.security.JwtAuthFilter;
import com.smartdesk.security.JwtService;
import com.smartdesk.security.TokenRevocationRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Configuration
public class SecurityConfig {

    @Bean
    JwtAuthFilter jwtAuthFilter(JwtService jwtService, TokenRevocationRegistry revocation) {
        return new JwtAuthFilter(jwtService, revocation);
    }

    /** Spring Boot 의 서블릿 필터 자동 등록 비활성화 → SecurityFilterChain 안에서만 실행. */
    @Bean
    FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter, ObjectMapper mapper,
                                    CorsConfigurationSource corsSource) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsSource))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                // health/prometheus 는 내부망(k8s probe, prometheus 스크레이프)에서 접근 — 운영에선 네트워크로 제한 권장
                .requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                // 나머지는 인증 필요. 세부 권한은 컨트롤러/서비스에서 CurrentUser 로 검증 (RBAC, REQ-N-002)
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> writeError(res, mapper, 401, "UNAUTHORIZED", "인증이 필요합니다.", req.getRequestURI()))
                .accessDeniedHandler((req, res, ex) -> writeError(res, mapper, 403, "FORBIDDEN", "권한이 없습니다.", req.getRequestURI())))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(HttpServletResponse res, ObjectMapper mapper, int status, String code, String msg, String path) {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        try {
            mapper.writeValue(res.getWriter(), Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", status, "code", code, "message", msg, "path", path));
        } catch (Exception ignored) { }
    }

    /**
     * 허용 Origin 패턴. 기본은 개발 편의를 위해 localhost 전 포트.
     * 운영: SMARTDESK_CORS_ALLOWED_ORIGINS=https://desk.example.com 로 지정.
     */
    @Bean
    CorsConfigurationSource corsSource(
            @org.springframework.beans.factory.annotation.Value(
                    "${smartdesk.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}") List<String> allowedOrigins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(allowedOrigins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("X-Request-Id"));
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
