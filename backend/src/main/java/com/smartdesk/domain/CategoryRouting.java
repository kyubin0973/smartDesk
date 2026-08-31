package com.smartdesk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** [보완] REQ-F-010: 카테고리 → 처리 부서 매핑. */
@Entity @Table(name = "category_routing")
@Getter @Setter @NoArgsConstructor
public class CategoryRouting {
    @Id @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;
}
