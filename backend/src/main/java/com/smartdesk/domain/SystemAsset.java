package com.smartdesk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ERD의 system (고객사별 자산). 'system' 예약어 회피. */
@Entity @Table(name = "system_asset")
@Getter @Setter @NoArgsConstructor
public class SystemAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(nullable = false)
    private String name;

    private String type;

    /** REQ-E-005: 참조 중이면 삭제 대신 비활성화. */
    @Column(nullable = false)
    private boolean active = true;
}
