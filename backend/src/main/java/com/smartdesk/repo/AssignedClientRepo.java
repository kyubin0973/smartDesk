package com.smartdesk.repo;

import com.smartdesk.domain.UserClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssignedClientRepo extends JpaRepository<UserClient, UserClient.Key> {
    @Query("select uc.clientId from UserClient uc where uc.userId = :userId")
    List<Long> findClientIdsByUserId(@Param("userId") Long userId);

    @Query("select uc.userId from UserClient uc where uc.clientId = :clientId")
    List<Long> findUserIdsByClientId(@Param("clientId") Long clientId);
}
