package com.smartdesk.repo;

import com.smartdesk.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepo extends JpaRepository<AuditLog, Long> {

    @Query("""
        select a from AuditLog a
        where (:action = '' or a.action = :action)
          and (:actorEmail = '' or lower(a.actorEmail) like lower(concat('%', :actorEmail, '%')))
          and a.at >= :from and a.at < :to
        order by a.at desc
    """)
    Page<AuditLog> search(@Param("action") String action,
                          @Param("actorEmail") String actorEmail,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
