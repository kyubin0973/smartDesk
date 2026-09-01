package com.smartdesk.feature.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    @Bean
    public LlmClient llmClient(RagProperties props) {
        RagProperties.Llm llm = props.getLlm();
        if (!"anthropic".equalsIgnoreCase(llm.getProvider())) {
            return disabled("provider=" + llm.getProvider());
        }
        String key = llm.getApiKey();
        if (key == null || key.isBlank()) key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            return disabled("ANTHROPIC_API_KEY 미설정");
        }
        log.info("[rag] LLM 초안 생성 활성 — {} ({})", llm.getModel(), "anthropic");
        return new AnthropicLlmClient(key, llm.getModel(), llm.getMaxTokens());
    }

    private LlmClient disabled(String reason) {
        log.info("[rag] LLM 초안 생성 비활성 ({}) — 검색 컨텍스트만 반환", reason);
        return new LlmClient() {
            public boolean enabled() { return false; }
            public String complete(String system, String user) {
                throw new IllegalStateException("LLM 비활성");
            }
            public String model() { return "disabled"; }
        };
    }
}
