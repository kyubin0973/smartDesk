package com.smartdesk.repo;

import com.smartdesk.domain.Enums.TicketEventType;
import com.smartdesk.domain.TicketEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TicketEventRepo extends JpaRepository<TicketEvent, Long> {
    List<TicketEvent> findByTicketIdOrderByAtAsc(Long ticketId);
    boolean existsByTicketIdAndType(Long ticketId, TicketEventType type);

    @Query("""
        select e from TicketEvent e
        where (:type is null or e.type = :type)
          and (:ticketId is null or e.ticketId = :ticketId)
          and e.at >= :from and e.at < :to
        order by e.at desc
    """)
    Page<TicketEvent> search(@Param("type") TicketEventType type,
                             @Param("ticketId") Long ticketId,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);
}
