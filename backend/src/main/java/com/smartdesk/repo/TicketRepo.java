package com.smartdesk.repo;

import com.smartdesk.domain.Enums;
import com.smartdesk.domain.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TicketRepo extends JpaRepository<Ticket, Long> {

    @Query("""
        select t from Ticket t
        where (:clientId is null or t.clientId = :clientId)
          and (:status   is null or t.status   = :status)
          and (:assigneeId is null or t.assigneeId = :assigneeId)
        order by t.createdAt desc
    """)
    Page<Ticket> search(@Param("clientId") Long clientId,
                        @Param("status") Enums.TicketStatus status,
                        @Param("assigneeId") Long assigneeId,
                        Pageable pageable);

    List<Ticket> findByClientId(Long clientId);
    List<Ticket> findByClientIdAndStatusIn(Long clientId, List<Enums.TicketStatus> statuses);
    List<Ticket> findByAssigneeIdAndStatusIn(Long assigneeId, List<Enums.TicketStatus> statuses);
    long countByClientIdAndStatus(Long clientId, Enums.TicketStatus status);
    long countByClientId(Long clientId);
    long countByClientIdAndCreatedAtGreaterThanEqual(Long clientId, Instant from);

    /** 스케줄러: 열린 티켓 중 SLA 마감이 특정 시각 이전(초과 or 임박)인 것. */
    List<Ticket> findByStatusInAndSlaDueAtIsNotNullAndSlaDueAtBefore(
            List<Enums.TicketStatus> statuses, Instant before);

    @Query("select count(t) from Ticket t where t.clientId = :clientId and t.status in :statuses and t.slaDueAt < :now")
    long countSlaBreached(@Param("clientId") Long clientId,
                          @Param("statuses") List<Enums.TicketStatus> statuses,
                          @Param("now") Instant now);
}
