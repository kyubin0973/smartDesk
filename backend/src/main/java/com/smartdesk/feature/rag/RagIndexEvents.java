package com.smartdesk.feature.rag;

/** 색인 대상 원본이 바뀌었음을 알리는 도메인 이벤트. */
public final class RagIndexEvents {

    private RagIndexEvents() {}

    public record SourceChanged(String type, long id) {
        public static SourceChanged document(long id) { return new SourceChanged("DOCUMENT", id); }
        public static SourceChanged ticket(long id) { return new SourceChanged("TICKET", id); }
    }
}
