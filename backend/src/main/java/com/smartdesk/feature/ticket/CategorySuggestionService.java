package com.smartdesk.feature.ticket;

import com.smartdesk.domain.Category;
import com.smartdesk.repo.CategoryRepo;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * REQ-F-009: 카테고리 자동분류(제안).
 * 1차 구현은 키워드 규칙 기반 (요구사항 8장: ML 모델은 2차 확장).
 * 제목+내용에서 카테고리별 키워드 매칭 수가 가장 많은 카테고리를 제안.
 */
@Service
public class CategorySuggestionService {

    private final CategoryRepo categories;

    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();
    static {
        KEYWORDS.put("Hardware",    List.of("노트북", "pc", "데스크톱", "모니터", "메모리", "디스크", "하드", "장비", "서버 장비", "keyboard", "hardware"));
        KEYWORDS.put("Access",      List.of("접속", "권한", "로그인", "vpn", "계정", "인증", "비밀번호", "access", "sso", "mfa"));
        KEYWORDS.put("Storage",     List.of("용량", "저장", "스토리지", "파일 서버", "공유 폴더", "백업", "quota", "storage"));
        KEYWORDS.put("Purchase",    List.of("구매", "발주", "견적", "라이선스 구매", "청구", "purchase", "invoice"));
        KEYWORDS.put("Application", List.of("오류", "에러", "배치", "버그", "500", "npe", "exception", "화면", "기능", "application", "배포"));
    }

    public CategorySuggestionService(CategoryRepo categories) {
        this.categories = categories;
    }

    /** @return 제안 카테고리 id, 매칭 실패 시 null. */
    public Long suggest(String title, String content) {
        String text = ((title == null ? "" : title) + " " + (content == null ? "" : content)).toLowerCase(Locale.ROOT);
        String best = null;
        int bestScore = 0;
        for (var e : KEYWORDS.entrySet()) {
            int score = 0;
            for (String kw : e.getValue()) if (text.contains(kw)) score++;
            if (score > bestScore) { bestScore = score; best = e.getKey(); }
        }
        if (best == null) return null;
        final String match = best;
        return categories.findByNameIgnoreCase(match).map(Category::getId).orElse(null);
    }
}
