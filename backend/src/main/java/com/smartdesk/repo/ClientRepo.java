package com.smartdesk.repo;

import com.smartdesk.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepo extends JpaRepository<Client, Long> {
}
