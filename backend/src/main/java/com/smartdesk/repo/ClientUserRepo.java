package com.smartdesk.repo;

import com.smartdesk.domain.ClientUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientUserRepo extends JpaRepository<ClientUser, Long> {
    Optional<ClientUser> findByEmail(String email);
    List<ClientUser> findByClientId(Long clientId);
}
