package com.smartdesk.domain;

import com.smartdesk.domain.Enums.ContractStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity @Table(name = "contract")
@Getter @Setter @NoArgsConstructor
public class Contract {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "sla_response_min")
    private Integer slaResponseMin;

    @Column(name = "sla_resolution_min")
    private Integer slaResolutionMin;

    @Column(name = "maintenance_scope")
    private String maintenanceScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status = ContractStatus.ACTIVE;

    /** REQ-E-007: 유효 계약 판단 (오늘이 계약기간 내이고 종료 상태가 아님). */
    public boolean isValidOn(LocalDate date) {
        return status != ContractStatus.ENDED
                && !date.isBefore(startDate)
                && !date.isAfter(endDate);
    }
}
