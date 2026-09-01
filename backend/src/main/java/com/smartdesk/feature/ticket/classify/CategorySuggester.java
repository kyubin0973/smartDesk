package com.smartdesk.feature.ticket.classify;

/**
 * REQ-F-009 자동분류 전략. 단계 1.3: 규칙 기반 ↔ ML 을 설정으로 교체.
 * 구현: {@link RuleBasedCategorySuggester}, {@link MlCategorySuggester}(폴백 포함).
 */
public interface CategorySuggester {

    /** @return 제안 카테고리 id, 판단 불가 시 null. */
    Long suggest(String title, String content);
}
