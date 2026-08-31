package com.smartdesk.feature.ticket;

import com.smartdesk.domain.Enums.Priority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * REQ: ticket.priority (Low/Medium/High/Critical) 산정.
 * 1차는 키워드 규칙 기반 (자동배정·SLA 예측의 입력값). 2차에서 ML/모델로 대체 가능.
 */
@Component
public class PriorityRules {

    private static final List<String> CRITICAL = List.of("장애", "다운", "중단", "먹통", "전체", "긴급", "outage", "down", "critical", "p1");
    private static final List<String> HIGH     = List.of("오류", "에러", "실패", "안 됨", "안됨", "지연", "느림", "error", "fail", "500");
    private static final List<String> LOW      = List.of("문의", "질문", "요청", "확인 부탁", "how to", "question");

    public Priority infer(String title, String content) {
        String text = ((title == null ? "" : title) + " " + (content == null ? "" : content)).toLowerCase(Locale.ROOT);
        if (CRITICAL.stream().anyMatch(text::contains)) return Priority.CRITICAL;
        if (HIGH.stream().anyMatch(text::contains)) return Priority.HIGH;
        if (LOW.stream().anyMatch(text::contains)) return Priority.LOW;
        return Priority.MEDIUM;
    }
}
