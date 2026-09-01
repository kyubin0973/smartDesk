package com.smartdesk.feature.ticket.classify;

import com.smartdesk.repo.CategoryRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** 단계 1.3: smartdesk.classification.provider 로 전략 선택. */
@Configuration
@EnableConfigurationProperties(ClassificationProperties.class)
public class ClassificationConfig {

    private static final Logger log = LoggerFactory.getLogger(ClassificationConfig.class);

    @Bean
    @Primary
    public CategorySuggester categorySuggester(RuleBasedCategorySuggester rule,
                                               CategoryRepo categories,
                                               ClassificationProperties props) {
        if ("ml".equalsIgnoreCase(props.getProvider())) {
            log.info("[classify] provider=ml ({}), min-confidence={}, 폴백=rule",
                    props.getMlUrl(), props.getMlMinConfidence());
            return new MlCategorySuggester(categories, rule, props);
        }
        log.info("[classify] provider=rule");
        return rule;
    }
}
