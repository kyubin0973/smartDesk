package com.smartdesk.security;

import com.smartdesk.common.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;

/** 컨트롤러/서비스에서 현재 인증 주체를 얻는 헬퍼. */
public final class CurrentUser {
    private CurrentUser() {}

    public static AuthPrincipal get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) {
            throw ApiException.unauthorized("인증이 필요합니다.");
        }
        return p;
    }

    public static AuthPrincipal requireSiUser() {
        AuthPrincipal p = get();
        if (!p.isSiUser()) throw ApiException.forbidden("SI 직원만 접근할 수 있습니다.");
        return p;
    }

    public static AuthPrincipal requireManager() {
        AuthPrincipal p = get();
        if (!p.isManager()) throw ApiException.forbidden("관리자만 접근할 수 있습니다.");
        return p;
    }
}
