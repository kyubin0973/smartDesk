package com.smartdesk.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 해시. REQ-N-003("SHA-256 이상 단방향")을 BCrypt 로 충족·강화.
 * BCrypt 는 솔트를 해시에 내장하고 work factor 로 무차별 대입을 늦춘다.
 */
@Component
public class PasswordHasher {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(10);

    public String hash(String raw) {
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String hash) {
        return hash != null && encoder.matches(raw, hash);
    }
}
