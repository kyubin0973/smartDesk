package com.smartdesk.feature.ticket.classify;

import com.smartdesk.domain.Category;
import com.smartdesk.repo.CategoryRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * 단계 1.3: ML 서빙(analytics/service)의 POST /classify 호출.
 * queue 라벨 → SmartDesk 카테고리 매핑(설정). 다음 중 하나면 규칙 기반({@code fallback})으로 폴백:
 *   - 서비스 오류·타임아웃
 *   - confidence < mlMinConfidence
 *   - queue 매핑 없음 / 매핑된 카테고리 미존재
 */
public class MlCategorySuggester implements CategorySuggester {

    private static final Logger log = LoggerFactory.getLogger(MlCategorySuggester.class);

    private final RestClient http;
    private final CategoryRepo categories;
    private final CategorySuggester fallback;
    private final ClassificationProperties props;

    public MlCategorySuggester(CategoryRepo categories, CategorySuggester fallback,
                               ClassificationProperties props) {
        this.categories = categories;
        this.fallback = fallback;
        this.props = props;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.getMlTimeoutMs());
        factory.setReadTimeout((int) props.getMlTimeoutMs());
        this.http = RestClient.builder().baseUrl(props.getMlUrl()).requestFactory(factory).build();
    }

    @Override
    public Long suggest(String title, String content) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> res = http.post().uri("/classify")
                    .body(Map.of("subject", title == null ? "" : title,
                                 "body", content == null ? "" : content))
                    .retrieve()
                    .body(Map.class);
            if (res == null) return fallback.suggest(title, content);

            double confidence = res.get("confidence") instanceof Number n ? n.doubleValue() : 0.0;
            String queue = String.valueOf(res.get("queue"));
            if (confidence < props.getMlMinConfidence()) {
                log.debug("[classify-ml] 저신뢰({}) → 폴백. queue={}", confidence, queue);
                return fallback.suggest(title, content);
            }
            String categoryName = props.getQueueMap().get(queue);
            if (categoryName == null) return fallback.suggest(title, content);
            return categories.findByNameIgnoreCase(categoryName)
                    .map(Category::getId)
                    .orElseGet(() -> fallback.suggest(title, content));
        } catch (Exception e) {
            log.warn("[classify-ml] 호출 실패 ({}) → 규칙 기반 폴백", e.toString());
            return fallback.suggest(title, content);
        }
    }

    Duration timeout() {
        return Duration.ofMillis(props.getMlTimeoutMs());
    }
}
