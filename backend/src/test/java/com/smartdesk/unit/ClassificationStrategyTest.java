package com.smartdesk.unit;

import com.smartdesk.domain.Category;
import com.smartdesk.feature.ticket.classify.ClassificationProperties;
import com.smartdesk.feature.ticket.classify.MlCategorySuggester;
import com.smartdesk.feature.ticket.classify.RuleBasedCategorySuggester;
import com.smartdesk.repo.CategoryRepo;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 단계 1.3: 자동분류 전략 — 규칙 기반 + ML 폴백. */
class ClassificationStrategyTest {

    private CategoryRepo categoriesWith(String name, long id) {
        CategoryRepo repo = mock(CategoryRepo.class);
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        when(repo.findByNameIgnoreCase(anyString())).thenAnswer(inv ->
                name.equalsIgnoreCase(inv.getArgument(0)) ? Optional.of(c) : Optional.empty());
        return repo;
    }

    @Test
    void ruleBased_matchesKeyword() {
        var rule = new RuleBasedCategorySuggester(categoriesWith("Access", 2L));
        assertEquals(2L, rule.suggest("VPN 접속 안됨", "로그인 계정 인증 실패"));
        assertNull(rule.suggest("점심 메뉴 추천", "김치찌개?"));
    }

    @Test
    void ml_fallsBackToRule_whenServiceUnreachable() {
        var props = new ClassificationProperties();
        props.setMlUrl("http://127.0.0.1:59999");   // 아무도 안 듣는 포트
        props.setMlTimeoutMs(200);
        var repo = categoriesWith("Access", 2L);
        var rule = new RuleBasedCategorySuggester(repo);
        var ml = new MlCategorySuggester(repo, rule, props);

        // 서비스 죽음 → 규칙 기반 결과가 나와야 함
        assertEquals(2L, ml.suggest("VPN 로그인 계정 문제", "인증 안됨"));
    }
}
