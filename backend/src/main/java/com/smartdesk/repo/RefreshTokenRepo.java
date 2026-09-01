package com.smartdesk.repo;

import com.smartdesk.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepo extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByPrincipalTypeAndPrincipalIdOrderByCreatedAtDesc(String principalType, Long principalId);

    /** 원자적 회전: 아직 유효할 때만 폐기. @return 영향 행 수 (1이면 이 요청이 회전 승자). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken r set r.revoked = true where r.id = :id and r.revoked = false")
    int revokeIfActive(@Param("id") Long id);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.principalType = :type and r.principalId = :id and r.revoked = false")
    void revokeAllFor(@Param("type") String type, @Param("id") Long id);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
