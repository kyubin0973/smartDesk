package com.smartdesk.feature.triage;

import com.smartdesk.domain.Enums.Priority;
import com.smartdesk.feature.rag.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 단계 3.1: LLM 트리아지 판단 (선택). 규칙 파이프라인이 뽑은 후보를 검토하고
 * 긴급도 코멘트 + 우선순위 조정 제안만 낸다. 결정권은 규칙/사람에게.
 */
@Service
public class TriageAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TriageAdvisor.class);

    private static final String SYSTEM = """
        너는 SI IT 지원팀의 트리아지 보조다. 아래 신규 티켓과 규칙 엔진이 뽑은 후보를 검토해라.
        한 줄 요약 + 우선순위 재검토 의견만 낸다. 형식(정확히 두 줄):
        PRIORITY: <LOW|MEDIUM|HIGH|CRITICAL 또는 KEEP>
        NOTE: <한 문장 긴급도/리스크 코멘트>
        """;

    private final LlmClient llm;
    private final TriageProperties props;

    public TriageAdvisor(LlmClient llm, TriageProperties props) {
        this.llm = llm;
        this.props = props;
    }

    public record Advice(Priority prioritySuggestion, String note, boolean used) {}

    public Advice advise(String title, String content, String category, Priority rulePriority,
                         List<String> similarTitles) {
        if (!props.isUseLlm() || !llm.enabled()) return new Advice(null, null, false);
        String user = "제목: " + n(title) + "\n내용: " + n(content)
                + "\n규칙 후보 — 카테고리: " + n(category) + ", 우선순위: " + rulePriority
                + "\n유사 과거 티켓: " + (similarTitles.isEmpty() ? "없음" : String.join(" / ", similarTitles));
        try {
            String out = llm.complete(SYSTEM, user);
            Priority pri = parsePriority(out);
            String note = parseNote(out);
            return new Advice(pri, note, true);
        } catch (Exception e) {
            log.warn("[triage-advisor] LLM 실패, 무시: {}", e.toString());
            return new Advice(null, null, false);
        }
    }

    private Priority parsePriority(String out) {
        for (String line : out.split("\n")) {
            String l = line.trim().toUpperCase(Locale.ROOT);
            if (l.startsWith("PRIORITY:")) {
                String v = l.substring("PRIORITY:".length()).trim();
                if (v.startsWith("KEEP")) return null;
                for (Priority p : Priority.values()) if (v.startsWith(p.name())) return p;
            }
        }
        return null;
    }

    private String parseNote(String out) {
        for (String line : out.split("\n")) {
            String t = line.trim();
            if (t.toUpperCase(Locale.ROOT).startsWith("NOTE:")) return t.substring(5).trim();
        }
        return out.strip().lines().findFirst().orElse("").trim();
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
