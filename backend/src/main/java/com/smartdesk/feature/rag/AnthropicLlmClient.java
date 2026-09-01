package com.smartdesk.feature.rag;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;

/** Anthropic Messages API 기반 초안 생성. 키는 설정값 또는 ANTHROPIC_API_KEY 환경변수. */
public class AnthropicLlmClient implements LlmClient {

    private final AnthropicClient client;
    private final String model;
    private final int maxTokens;

    public AnthropicLlmClient(String apiKey, String model, int maxTokens) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String complete(String system, String user) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens((long) maxTokens)
                .system(system)
                .addUserMessage(user)
                .build();
        Message res = client.messages().create(params);
        StringBuilder sb = new StringBuilder();
        res.content().forEach(block -> block.text().ifPresent(t -> sb.append(t.text())));
        return sb.toString().trim();
    }

    @Override
    public String model() {
        return model;
    }
}
