package com.smartdesk.unit;

import com.smartdesk.feature.rag.EmbeddingStore;
import com.smartdesk.feature.rag.TextChunker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 단계 2: 청킹 · 벡터 리터럴 순수 로직. */
class RagUtilTest {

    @Test
    void stripHtml_removesTagsAndEntities() {
        String out = TextChunker.stripHtml("<h2>제목</h2><p>본문 &amp; 링크 <a href=\"x\">click</a></p>");
        assertEquals("제목 본문 & 링크 click", out);
    }

    @Test
    void chunk_splitsLongTextAndKeepsTitlePrefix() {
        String body = "가".repeat(1500);
        List<String> chunks = TextChunker.chunk("정책 문서", body, 600);
        assertTrue(chunks.size() >= 3);
        assertTrue(chunks.get(0).startsWith("정책 문서"));
        assertTrue(chunks.stream().allMatch(c -> c.length() <= 700));
    }

    @Test
    void chunk_emptyBody_returnsTitleOnly() {
        assertEquals(List.of("제목만"), TextChunker.chunk("제목만", "  ", 600));
        assertTrue(TextChunker.chunk("", "", 600).isEmpty());
    }

    @Test
    void sha256_isStable() {
        assertEquals(TextChunker.sha256("abc 가나다"), TextChunker.sha256("abc 가나다"));
        assertNotEquals(TextChunker.sha256("a"), TextChunker.sha256("b"));
    }

    @Test
    void vectorLiteral_format() {
        assertEquals("[0.5,-1.25,0.0]", EmbeddingStore.toVectorLiteral(new float[]{0.5f, -1.25f, 0.0f}));
    }
}
