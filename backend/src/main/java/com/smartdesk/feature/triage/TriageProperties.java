package com.smartdesk.feature.triage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 단계 3: 트리아지·SLA 예측 설정 (smartdesk.triage.*). */
@ConfigurationProperties(prefix = "smartdesk.triage")
public class TriageProperties {

    /** true 면 신뢰도 충분할 때 담당자까지 자동 배정. false 면 제안만. */
    private boolean autoAssign = true;

    /** 이 신뢰도 미만이면 자동 배정 안 하고 관리자에게 검토 요청. */
    private double minConfidence = 0.7;

    /** LLM 판단(TriageAdvisor) 사용 여부. smartdesk.rag.llm 설정을 공유. */
    private boolean useLlm = true;

    /** SLA 위반 위험 알림 임계치 (0~1). */
    private double slaRiskThreshold = 0.7;

    public boolean isAutoAssign() { return autoAssign; }
    public void setAutoAssign(boolean autoAssign) { this.autoAssign = autoAssign; }
    public double getMinConfidence() { return minConfidence; }
    public void setMinConfidence(double minConfidence) { this.minConfidence = minConfidence; }
    public boolean isUseLlm() { return useLlm; }
    public void setUseLlm(boolean useLlm) { this.useLlm = useLlm; }
    public double getSlaRiskThreshold() { return slaRiskThreshold; }
    public void setSlaRiskThreshold(double slaRiskThreshold) { this.slaRiskThreshold = slaRiskThreshold; }
}
