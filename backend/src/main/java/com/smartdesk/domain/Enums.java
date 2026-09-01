package com.smartdesk.domain;

/** 도메인 공통 열거형. API/화면 표기와 매핑은 README 및 프론트 라벨 참고. */
public final class Enums {
    private Enums() {}

    public enum Role { AGENT, MANAGER }                       // 담당자 / 관리자 (REQ-N-002)

    public enum ContractStatus { ACTIVE, EXPIRING, ENDED }    // 계약중 / 만료임박 / 종료

    public enum TicketStatus {
        RECEIVED, IN_PROGRESS, RESOLVED, CLOSED;              // 접수 / 처리중 / 해결 / 종료
        public boolean isOpen() { return this == RECEIVED || this == IN_PROGRESS; }

        /** 허용되는 다음 상태 (접수→처리중→해결→종료, 되돌리기 일부 허용). */
        public boolean canTransitionTo(TicketStatus next) {
            if (this == next) return true;
            return switch (this) {
                case RECEIVED    -> next == IN_PROGRESS;
                case IN_PROGRESS -> next == RESOLVED || next == RECEIVED;
                case RESOLVED    -> next == CLOSED || next == IN_PROGRESS;   // 재오픈 허용
                case CLOSED      -> next == IN_PROGRESS;                     // 재오픈만
            };
        }
    }

    public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }

    public enum DocScope { SI_INTERNAL, CLIENT_SHARED }

    public enum AuthorType { USER, CLIENT_USER }

    /** 티켓 이벤트 로그 유형 (분석·이벤트 소싱). */
    public enum TicketEventType {
        CREATED, CATEGORIZED, ASSIGNED, STATUS_CHANGED, COMMENTED, SLA_BREACHED,
        APPROVED,   // 승인자(관리자)가 해결 → 종료 승인
        REJECTED,   // 승인자가 반려 → 처리중으로 되돌림
        TRIAGED     // 단계 3: 지능형 트리아지 결과 기록
    }

    /** 알림 유형. */
    public enum NotificationType {
        SLA_DUE_SOON, SLA_BREACHED, SLA_AT_RISK, TICKET_ASSIGNED, TICKET_COMMENTED, TICKET_STATUS,
        TRIAGE_REVIEW   // 단계 3: 트리아지 신뢰도 낮음 → 관리자 수동 검토 요청
    }

    public enum AttachmentOwnerType { TICKET, DOCUMENT }
}
