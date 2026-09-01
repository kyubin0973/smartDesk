package com.smartdesk.common;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;

/**
 * 0.5-c: 리치텍스트 문서 본문을 저장 전에 sanitize.
 * 허용: 기본 서식(FORMATTING) + 블록/목록/제목(BLOCKS) + 안전한 링크(LINKS, rel=nofollow) + pre/code/hr.
 * 제거: script·style·iframe·on* 핸들러·javascript: URL 등 실행 가능한 모든 요소/속성.
 */
@Component
public class HtmlSanitizer {

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(new HtmlPolicyBuilder()
                    .allowElements("pre", "code", "hr", "s", "del")
                    .toFactory());

    public String clean(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) return "";
        return POLICY.sanitize(rawHtml);
    }
}
