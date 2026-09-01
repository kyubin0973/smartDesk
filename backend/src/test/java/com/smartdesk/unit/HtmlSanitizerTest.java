package com.smartdesk.unit;

import com.smartdesk.common.HtmlSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 0.5-c: 리치텍스트 본문 sanitize 정책. */
class HtmlSanitizerTest {

    private final HtmlSanitizer s = new HtmlSanitizer();

    @Test
    void keepsSafeFormatting() {
        String out = s.clean("<h2>제목</h2><p><strong>굵게</strong> 그리고 <em>기울임</em></p><ul><li>항목</li></ul>");
        assertTrue(out.contains("<h2>"));
        assertTrue(out.contains("<strong>"));
        assertTrue(out.contains("<li>"));
    }

    @Test
    void stripsScriptAndHandlers() {
        String out = s.clean("<p onclick=\"steal()\">hi</p><script>alert(1)</script>");
        assertFalse(out.toLowerCase().contains("<script"));
        assertFalse(out.toLowerCase().contains("onclick"));
        assertTrue(out.contains("hi"));
    }

    @Test
    void neutralizesJavascriptUrl() {
        String out = s.clean("<a href=\"javascript:alert(1)\">x</a>");
        assertFalse(out.toLowerCase().contains("javascript:"));
    }

    @Test
    void keepsHttpLinkWithNofollow() {
        String out = s.clean("<a href=\"https://example.com\">docs</a>");
        assertTrue(out.contains("https://example.com"));
        assertTrue(out.contains("nofollow"));
    }

    @Test
    void nullOrBlankBecomesEmpty() {
        assertEquals("", s.clean(null));
        assertEquals("", s.clean("   "));
    }
}
