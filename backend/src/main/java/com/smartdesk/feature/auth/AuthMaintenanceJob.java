package com.smartdesk.feature.auth;

import com.smartdesk.repo.PasswordResetTokenRepo;
import com.smartdesk.repo.RefreshTokenRepo;
import com.smartdesk.repo.RevokedAccessTokenRepo;
import org.springframework.context.annotation.Profile;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 만료된 리프레시 토큰·폐기 토큰·재설정 토큰 정리. */
@Component
@Profile("!test")
public class AuthMaintenanceJob {

    private final RefreshTokenRepo refreshTokens;
    private final RevokedAccessTokenRepo revokedTokens;
    private final PasswordResetTokenRepo resetTokens;

    public AuthMaintenanceJob(RefreshTokenRepo refreshTokens, RevokedAccessTokenRepo revokedTokens,
                              PasswordResetTokenRepo resetTokens) {
        this.refreshTokens = refreshTokens;
        this.revokedTokens = revokedTokens;
        this.resetTokens = resetTokens;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @SchedulerLock(name = "auth-maintenance", lockAtMostFor = "PT10M")
    @Transactional
    public void purge() {
        Instant now = Instant.now();
        refreshTokens.deleteExpired(now);
        revokedTokens.deleteExpired(now);
        resetTokens.deleteExpired(now);
    }
}
