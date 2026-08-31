package com.smartdesk.repo;

import com.smartdesk.domain.RevokedAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface RevokedAccessTokenRepo extends JpaRepository<RevokedAccessToken, String> {
    boolean existsByJti(String jti);

    @Modifying
    @Query("delete from RevokedAccessToken r where r.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
