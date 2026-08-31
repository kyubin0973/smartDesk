package com.smartdesk.repo;

import com.smartdesk.domain.TicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketHistoryRepo extends JpaRepository<TicketHistory, Long> {
    List<TicketHistory> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
