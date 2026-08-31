package com.smartdesk.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** 목록 API 표준 응답 (API 명세 보완: 오프셋 페이지네이션). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
