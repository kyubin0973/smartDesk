package com.smartdesk.feature.rag;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** HTML 제거 + 문자 길이 기반 청킹. 각 청크 앞에 제목을 붙여 맥락 유지. */
public final class TextChunker {

    private TextChunker() {}

    public static String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static List<String> chunk(String title, String body, int maxChars) {
        String text = stripHtml(body);
        String prefix = (title == null || title.isBlank()) ? "" : title.trim() + "\n";
        List<String> chunks = new ArrayList<>();
        if (text.isEmpty()) {
            if (!prefix.isBlank()) chunks.add(prefix.trim());
            return chunks;
        }
        int budget = Math.max(120, maxChars - prefix.length());
        for (int start = 0; start < text.length(); ) {
            int end = Math.min(text.length(), start + budget);
            if (end < text.length()) {
                int brk = text.lastIndexOf(' ', end);
                if (brk > start + budget / 2) end = brk;
            }
            chunks.add(prefix + text.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
