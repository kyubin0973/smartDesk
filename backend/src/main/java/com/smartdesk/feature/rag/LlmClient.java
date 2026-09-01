package com.smartdesk.feature.rag;

/** 단계 2.3: RAG 답변 초안 생성용 LLM 추상화. */
public interface LlmClient {

    /** false 면 초안 생성 비활성 — 컨트롤러는 검색 컨텍스트만 반환. */
    boolean enabled();

    /** 초안 텍스트. enabled()==false 면 호출 금지. */
    String complete(String system, String user);

    /** 사용 모델 식별자 (UI 표시용). */
    String model();
}
