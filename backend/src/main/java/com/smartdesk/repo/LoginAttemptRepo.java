package com.smartdesk.repo;

import com.smartdesk.domain.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginAttemptRepo extends JpaRepository<LoginAttempt, Long> {
    Optional<LoginAttempt> findByEmailAndPrincipalType(String email, String principalType);
}
