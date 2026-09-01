package com.smartdesk.feature.ticket.classify;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** 단계 1.3: 자동분류 전략 설정 (smartdesk.classification.*). */
@ConfigurationProperties(prefix = "smartdesk.classification")
public class ClassificationProperties {

    /** rule | ml. ml 은 실패·저신뢰 시 rule 로 폴백. */
    private String provider = "rule";

    /** ML 서빙 URL (analytics/service). */
    private String mlUrl = "http://localhost:8000";

    /** 이 신뢰도 미만이면 ML 결과를 버리고 규칙 기반으로 폴백. */
    private double mlMinConfidence = 0.55;

    /** ML 호출 타임아웃(ms). 초과 시 폴백. */
    private long mlTimeoutMs = 800;

    /** ML 모델의 queue 라벨 → SmartDesk 카테고리명 매핑. 없는 라벨은 폴백. */
    private Map<String, String> queueMap = new LinkedHashMap<>();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getMlUrl() { return mlUrl; }
    public void setMlUrl(String mlUrl) { this.mlUrl = mlUrl; }
    public double getMlMinConfidence() { return mlMinConfidence; }
    public void setMlMinConfidence(double mlMinConfidence) { this.mlMinConfidence = mlMinConfidence; }
    public long getMlTimeoutMs() { return mlTimeoutMs; }
    public void setMlTimeoutMs(long mlTimeoutMs) { this.mlTimeoutMs = mlTimeoutMs; }
    public Map<String, String> getQueueMap() { return queueMap; }
    public void setQueueMap(Map<String, String> queueMap) { this.queueMap = queueMap; }
}
