package com.smartdesk.feature.triage;

/** 신규 티켓 생성 → 커밋 후 비동기 정밀 트리아지 트리거. */
public record TicketCreatedEvent(long ticketId) {}
