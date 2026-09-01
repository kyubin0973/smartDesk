package com.smartdesk.feature.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 단계 2: 벡터 검색 · RAG 설정 (smartdesk.rag.*). */
@ConfigurationProperties(prefix = "smartdesk.rag")
public class RagProperties {

    /** false 면 인덱싱·검색·초안 전부 비활성 (임베딩 서비스 없이 기존 카테고리 매칭만). */
    private boolean enabled = false;

    /** 임베딩 서비스 (analytics/service) 베이스 URL. */
    private String embedUrl = "http://localhost:8000";

    /** 임베딩 서비스 호출 타임아웃(ms). */
    private long embedTimeoutMs = 20000;

    /** 검색 결과 상한 (문서·티켓 각각). */
    private int topK = 5;

    /** 청크 최대 길이(문자). */
    private int chunkChars = 600;

    /** 재조정 스케줄 (stale 원본 재색인). */
    private String reconcileCron = "0 */10 * * * *";

    private final Llm llm = new Llm();

    public static class Llm {
        /** none | anthropic. none 이면 answer-draft 는 검색 컨텍스트만 반환. */
        private String provider = "none";
        private String model = "claude-sonnet-5";
        private int maxTokens = 1500;
        /** 미설정 시 ANTHROPIC_API_KEY 환경변수 사용. */
        private String apiKey = "";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEmbedUrl() { return embedUrl; }
    public void setEmbedUrl(String embedUrl) { this.embedUrl = embedUrl; }
    public long getEmbedTimeoutMs() { return embedTimeoutMs; }
    public void setEmbedTimeoutMs(long embedTimeoutMs) { this.embedTimeoutMs = embedTimeoutMs; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public int getChunkChars() { return chunkChars; }
    public void setChunkChars(int chunkChars) { this.chunkChars = chunkChars; }
    public String getReconcileCron() { return reconcileCron; }
    public void setReconcileCron(String reconcileCron) { this.reconcileCron = reconcileCron; }
    public Llm getLlm() { return llm; }
}
