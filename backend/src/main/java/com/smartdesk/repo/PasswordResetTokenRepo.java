package com.smartdesk.repo;

import com.smartdesk.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepo extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** 새 요청 시 이전 미사용 토큰을 무효화. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now " +
           "where t.principalType = :type and t.principalId = :id and t.usedAt is null")
    void invalidateActive(@Param("type") String type, @Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
