package com.smartdesk.feature.auth;

import com.smartdesk.repo.AppUserRepo;
import com.smartdesk.repo.ClientUserRepo;
import com.smartdesk.security.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 단계 4: 운영에서 데모 시드 계정 처리.
 * - 데모 담당자/고객사 계정(agent·client): 시드 기본 비밀번호면 비활성화.
 * - admin@smartdesk.io: SMARTDESK_ADMIN_PASSWORD 가 있으면 그 값으로 재해싱, 없으면 유지 + 경고.
 * 운영자가 이미 비밀번호를 바꿨으면(해시 다름) 건드리지 않는다.
 */
@Component
@Profile("prod")
public class DemoAccountGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountGuard.class);
    private static final String SEED_HASH = "$2a$10$r1C0dKZ9VuV0AZmIGmfvdO2GSgv0IFdWdAmUHKvCfK4Ur2CiluGj2";
    private static final String ADMIN_EMAIL = "admin@smartdesk.io";
    private static final Set<String> DEMO_AGENTS = Set.of(
            "infra@smartdesk.io", "app@smartdesk.io", "sec@smartdesk.io");
    private static final Set<String> DEMO_CLIENTS = Set.of("user@a-corp.com", "user@b-corp.com");

    private final AppUserRepo users;
    private final ClientUserRepo clientUsers;
    private final PasswordHasher hasher;
    private final String adminPassword;

    public DemoAccountGuard(AppUserRepo users, ClientUserRepo clientUsers, PasswordHasher hasher,
                            @Value("${SMARTDESK_ADMIN_PASSWORD:}") String adminPassword) {
        this.users = users;
        this.clientUsers = clientUsers;
        this.hasher = hasher;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int disabled = 0;
        for (String email : DEMO_AGENTS) {
            var u = users.findByEmail(email).orElse(null);
            if (u != null && u.isActive() && SEED_HASH.equals(u.getPasswordHash())) {
                u.setActive(false);
                users.save(u);
                disabled++;
            }
        }
        for (String email : DEMO_CLIENTS) {
            var cu = clientUsers.findByEmail(email).orElse(null);
            if (cu != null && cu.isActive() && SEED_HASH.equals(cu.getPasswordHash())) {
                cu.setActive(false);
                clientUsers.save(cu);
                disabled++;
            }
        }
        if (disabled > 0) log.warn("[demo-guard] 데모 계정 {}개 비활성화.", disabled);

        var admin = users.findByEmail(ADMIN_EMAIL).orElse(null);
        if (admin != null && SEED_HASH.equals(admin.getPasswordHash())) {
            if (!adminPassword.isBlank()) {
                admin.setPasswordHash(hasher.hash(adminPassword));
                users.save(admin);
                log.warn("[demo-guard] admin@smartdesk.io 비밀번호를 SMARTDESK_ADMIN_PASSWORD 로 재설정했습니다.");
            } else {
                log.error("[demo-guard] ⚠ admin@smartdesk.io 가 아직 기본 비밀번호(Passw0rd!)입니다. "
                        + "즉시 변경하거나 SMARTDESK_ADMIN_PASSWORD 를 설정하세요.");
            }
        }
    }
}
