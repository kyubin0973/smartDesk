package com.smartdesk;

import com.smartdesk.domain.Ticket;
import com.smartdesk.feature.triage.TriageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 단계 3.3: 트리아지 회귀 평가셋. 라벨된 시나리오로 카테고리·우선순위 정확도를 지킨다.
 * (test 프로파일은 RAG/LLM 비활성 → 규칙 파이프라인만 평가)
 */
class TriageEvalTest extends AbstractIntegrationTest {

    @Autowired TriageService triage;

    record Case(String title, String content, String expectCategory, String minPriority) {}

    private static final List<Case> CASES = List.of(
            new Case("VPN 로그인이 안 됩니다", "재택에서 사내 VPN 계정 인증이 실패합니다", "Access", "MEDIUM"),
            new Case("SSO 계정 잠김", "비밀번호 5회 오류로 계정이 잠겼습니다 접속 권한 복구 요청", "Access", "MEDIUM"),
            new Case("공유 폴더 용량 부족", "파일 서버 스토리지 quota 초과로 저장이 안 됩니다", "Storage", "MEDIUM"),
            new Case("백업 실패", "야간 백업 스토리지 용량 부족", "Storage", "HIGH"),
            new Case("노트북 교체 요청", "데스크톱 모니터 메모리 부족, 새 장비 필요", "Hardware", "LOW"),
            new Case("라이선스 구매 문의", "소프트웨어 라이선스 구매 견적 요청드립니다", "Purchase", "LOW"),
            new Case("배치 잡 오류", "월마감 배치에서 NPE exception 발생, 배포 후 재현", "Application", "HIGH"),
            new Case("ERP 전체 장애", "ERP 시스템 전체가 먹통입니다 긴급", "Application", "CRITICAL")
    );

    @Test
    void categoryAccuracy_meetsBar() {
        int hit = 0;
        for (Case c : CASES) {
            var r = triage.triage(newTicket(c));
            if (c.expectCategory().equals(r.categoryName())) hit++;
        }
        double acc = (double) hit / CASES.size();
        assertTrue(acc >= 0.75, "카테고리 정확도 " + acc + " (기준 0.75)");
    }

    @Test
    void priorityFloor_respected() {
        int ok = 0;
        for (Case c : CASES) {
            var r = triage.triage(newTicket(c));
            if (severity(r.priority().name()) >= severity(c.minPriority())) ok++;
        }
        assertTrue((double) ok / CASES.size() >= 0.75,
                "우선순위 하한 충족 비율 " + (double) ok / CASES.size());
    }

    private Ticket newTicket(Case c) {
        Ticket t = new Ticket();
        t.setClientId(1L);
        t.setTitle(c.title());
        t.setContent(c.content());
        return t;
    }

    private static int severity(String p) {
        return switch (p) {
            case "LOW" -> 0;
            case "MEDIUM" -> 1;
            case "HIGH" -> 2;
            default -> 3;
        };
    }
}
