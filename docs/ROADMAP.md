# SmartDesk 확장 로드맵

요구사항정의서 8장("향후 확장 방향": SI 플랫폼 → 데이터분석 → AI 서비스 → K8s 배포)을
현재 코드베이스 기준으로 구체화한 계획입니다.

- **현재:** Spring Boot 3.3 + Vue 3 · 규칙 기반 자동분류/배정 · SLA 타이머(영업시간) · 승인자 워크플로 · 감사 로그 · 마이그레이션 V1~V5 · 테스트 65개
- **다음 큰 모듈:** 단계 1(데이터 분석) — 규칙 기반 로직을 실데이터로 검증

---

## 진행 현황

**단계 0 (확장 전 정지작업) — 완료 ✅**

| 항목 | 상태 |
|---|---|
| 인증 강화 (리프레시 회전·폐기·BCrypt·IP rate-limit·비밀번호 재설정) | ✅ 메일은 로그 어댑터 |
| 알림 (`SlaMonitor` 5분 스캔, 미배정 티켓 관리자 에스컬레이션) | ✅ 채널 어댑터는 0.5 |
| 승인자 워크플로 (관리자=승인자, `/approve`·`/reject`, 대시보드 pendingApproval) | ✅ |
| 계약 상태 자동 전이 · SLA 준수율 `resolved_at` 기준 · 우선순위 규칙 산정 | ✅ |
| 이벤트 로그(`ticket_event`) · 감사 로그(`audit_log` + `/audit` 화면) | ✅ |
| 첨부파일 (티켓/문서, UI 포함, 문서는 공유 고객사만 열람) | ✅ 로컬 디스크 |
| 관측성 (X-Request-Id · Prometheus · JSON 로그) · 계정 비활성화+재배정 | ✅ |
| 배포 (Dockerfile · compose `--profile app` · GitHub Actions CI · prod 프로파일) | ✅ |
| N+1 제거(`QueryCountTest`) · 멀티테넌시 격리 · 예외처리 REQ-E-001~010 | ✅ |

---

## 단계 0.5 — 빠른 개선 (각 1~3일, 단계 1 병행 가능)

| # | 작업 | 상태 | 근거 |
|---|---|---|---|
| a | **알림 채널 어댑터** — `EmailSender`(SMTP) + Slack Webhook. 유형별 `email-types`/`slack-types` 설정 | ✅ | REQ-F-011 초과 알림, 현재 인앱만 |
| e | **문서 열람 감사** — CLIENT_SHARED 문서 조회 시 `audit_log` 기록 (`DOCUMENT_VIEWED`, SI 제외) | ✅ | REQ-F-014 컴플라이언스 |
| g | **세션 관리 화면** — "내 로그인 세션" 목록 + 개별/전체 로그아웃 (`refresh_token` 활용) | ✅ `/auth/sessions`, `SessionsCard` | 보안, 이미 데이터는 있음 |
| h | **SLA 다단계 에스컬레이션** — 초과 시간별 L1 담당자 → L2 부서관리자 → L3 전체관리자 | ✅ `escalation-l2/l3-minutes` | 현재 담당자→관리자 1단계 |
| f | **Testcontainers** — 통합 테스트가 로컬 PostgreSQL 의존 → 컨테이너 자동 기동 | 다음 | 개발 편의, CI 안정성 |
| c | **리치텍스트 에디터** — 문서 본문 `textarea` → TipTap/Toast UI (HTML 저장, 서버 sanitize) | 다음 | 화면설계서 SCR-DOC-002 "리치텍스트 입력" |
| b | **실시간 알림** — 폴링(30초) → SSE `/api/notifications/stream` | 대기 | UX, 서버 부하 |
| d | **감사 리포트 내보내기** — `/audit` CSV/PDF export, SLA 준수율 리포트 화면 | 대기 | REQ 리포트 대분류, 감사 대응 |

---

## 단계 1 — 데이터 분석 기반 마련

**목표:** 규칙 기반 로직(자동분류·SLA·배정)을 실데이터로 검증하고 통계적 근거 확보. **다음 캡스톤 모듈.**

### 1.1 데이터 파이프라인
- 운영 DB → 분석 스키마/DW 로 일 1회 ETL (dbt 또는 cron + SQL). 시작은 `ticket` + `ticket_event` 만
- `ticket_event` 는 이미 append-only 로 쌓이는 중 → 상태별 체류시간·재오픈율·담당자별 처리량을 SQL 로 바로 계산 가능
- 파생 마트: `ticket_resolution_stats(category_id, dow, hour, p50_minutes, p90_minutes, sla_breach_rate)`

### 1.2 분석 과제 (Kaggle IT Ticket 데이터셋 + 자체 데이터)
- **EDA**: 카테고리별 처리시간 분포, 요일·시간대 패턴, 우선순위 vs 실제 처리시간, 재오픈율
- **통계 검정**: 카테고리 간 평균 처리시간 차이(ANOVA), SLA 위반과 요청 시각·시스템의 연관성(카이제곱), 담당자별 처리시간 차이
- **결과 반영**:
  - `contract.sla_resolution_min` 권장값 산출 (카테고리별 p90 기반)
  - `category_routing` 부서 매핑을 실제 처리 이력으로 재조정
  - `PriorityRules` 키워드 가중치 튜닝

### 1.3 텍스트 기반 카테고리 분류 모델
- 제목+내용 → 카테고리. 베이스라인: TF-IDF + LinearSVC / LogisticRegression
- 평가: 규칙 기반(`CategorySuggestionService`) 대비 macro-F1, 혼동행렬. 목표: 규칙 대비 +10%p
- 서빙: 별도 Python 서비스(FastAPI). Spring 은 `CategorySuggestionService` 를 인터페이스화 → `RuleBasedSuggestion` / `MlSuggestion(HttpClient)` 전략 선택. 서비스 다운 시 규칙으로 폴백

**연결점:** REQ-F-009(자동분류)·REQ-F-011(SLA)을 실측으로 검증

---

## 단계 2 — AI 서비스: RAG 유사 티켓/문서 추천

**목표:** 지식문서와 과거 해결 티켓을 의미 검색으로 담당자에게 제안. `GET /tickets/{id}/related-documents`(현재 카테고리 매칭)를 벡터 검색으로 고도화.

### 2.1 벡터 인덱스
- 임베딩: 한국어 지원 모델 (multilingual-e5, KURE, 또는 OpenAI text-embedding-3-small)
- 저장: **`pgvector`** (운영 PostgreSQL 재활용). `document.search_tsv`(이미 존재)와 함께 하이브리드
- 색인 대상: `document`(공개범위 유지), 종료(CLOSED) 티켓의 제목+내용+해결 코멘트
- 재색인: 문서 저장 / 티켓 종료 시 outbox 이벤트 → 비동기 임베딩 워커

### 2.2 검색·추천
- `POST /api/ai/tickets/{id}/related` → 유사 문서 top-k + 유사 과거 티켓 top-k
- **테넌시·공개범위 필터를 벡터 검색 WHERE 절에서 강제** (고객사 담당자 = 공유 문서만, REQ-N-001) — 현재 `AttachmentController.assertCanAccess` / `DocumentRepo.searchSharedWith` 와 동일 원칙
- 하이브리드: BM25(tsvector) + 벡터 → 재순위(cross-encoder reranker)

### 2.3 RAG 답변 초안
- 티켓 상세에 "1차 답변 초안" 버튼: 검색된 문서를 컨텍스트로 LLM 초안 생성 → 담당자 검수 후 코멘트 게시
- 환각 억제: 출처 문서 인용 강제, 근거 부족 시 "관련 문서 없음"

**연결점:** REQ-F-013(지식문서)·REQ-F-015(검색) 의미 검색화

---

## 단계 3 — AI 에이전트: 트리아지·배정·SLA 예측

**목표:** 규칙 기반(`AssignmentService`·`PriorityRules`·`CategorySuggestionService`)을 에이전트로 대체/보강. human-in-the-loop.

### 3.1 지능형 트리아지
- 신규 티켓 → (1.3 분류 모델 + LLM 판단)으로 카테고리·우선순위·긴급도 산정
- 담당자 스코어링: `AssignmentService` 의 현재 로직(부서 매핑 + 부하) + **과거 유사 티켓 처리 이력** + 근무시간대
- 신뢰도 낮으면 사람에게 에스컬레이션

### 3.2 SLA 위반 예측
- 열린 티켓 특징(카테고리, 담당자 큐 길이, 유사 건 처리시간 통계 from 단계 1) → 위반 확률
- `SlaMonitorJob` 에 예측 훅 추가: 임계 초과 시 사전 경고 + 재배정 제안

### 3.3 오케스트레이션
- LangGraph 로 도구 호출 그래프 (분류 → 검색 → 배정 → 초안)
- 관측: 단계별 입출력·토큰·지연 로깅(LangSmith), 프롬프트 버전 관리
- 회귀 테스트: 과거 티켓 N건의 정답 카테고리/담당자로 평가셋

**연결점:** REQ-F-010(자동배정) AI화, REQ-E-003(재배정) 자동화

---

## 단계 4 — 플랫폼화 / 배포

| 영역 | 계획 |
|---|---|
| K8s | Deployment + HPA, Ingress, Secret 은 External Secrets/Vault. Helm 차트 |
| CI/CD | 현 GitHub Actions 확장: 테스트 → 이미지 → 스테이징 자동 배포 → 승인 → 운영 |
| 멀티테넌시 심화 | 애플리케이션 레벨 `client_id` 필터 → **PostgreSQL Row-Level Security** 이중 방어. 테넌시 우회 탐지 테스트를 CI 게이트로 |
| 스토리지 | `AttachmentController` 로컬 디스크 → S3/GCS 어댑터 (다중 인스턴스 필수). MIME 화이트리스트 + 바이러스 스캔 훅 |
| 이벤트 버스 | `ticket_event` / outbox → Kafka/SQS 발행 → 알림·색인·분석 컨슈머 분리 |
| 관측성 심화 | 분산 추적(OpenTelemetry), 대시보드(Grafana), 알림 규칙 |

---

## 6개월 제안 순서

1. **0.5-a,c,f** (알림 어댑터, 리치텍스트, Testcontainers) — 2주
2. **단계 1** 전체 (파이프라인 → EDA → 분류 모델) — 6~8주
3. **단계 2.1~2.2** (pgvector 색인 + 하이브리드 검색) — 4주
4. **단계 2.3 + 단계 3.1** (RAG 초안 + 트리아지) — 4주
5. **단계 4** (RLS, S3, K8s) — 지속

---

## 아키텍처 진화

```
[현재]
  Vue SPA ──HTTP──> Spring Boot ──> PostgreSQL
                    (규칙 기반 분류/배정, SLA, 감사)

[단계 2~3]
  Vue SPA ──> Spring Boot ──> PostgreSQL (+ pgvector)
                  │
                  ├─HTTP─> ML 분류 서비스 (FastAPI)
                  ├─HTTP─> RAG/에이전트 서비스 (LangGraph + LLM)
                  └─event─> Kafka ─> [알림 / 색인 / 분석 DW]
```

## 지금 코드에서 확장을 염두에 둔 지점

| 코드 | 확장 방향 |
|---|---|
| `CategorySuggestionService` / `PriorityRules` | 인터페이스화 → 규칙↔ML 전략 교체 1곳 |
| `AssignmentService` | 후보 스코어링이 한 메서드 → 에이전트 호출로 대체 용이 |
| `SlaService` | `business-hours-only` 플래그 + 예측 훅 자리 |
| `NotificationService` / `NotificationWriter` | 발송 채널(메일/슬랙) 어댑터 추가 지점 (REQUIRES_NEW 격리 완료) |
| `document.search_tsv` (tsvector + GIN) | 벡터/하이브리드 검색 전환 준비됨 |
| `ticket_event` (append-only) | 이벤트 소싱/분석 팩트 테이블. 체류시간·재오픈율 계산 가능 |
| `audit_log` | 컴플라이언스 리포트·이상 탐지의 원천 |
| `document`/`ticket` 의 `client_id` 직접 보유 | 벡터 검색 필터·RLS 전환 자연스러움 |
| `AttachmentController` 로컬 스토리지 | S3/GCS 어댑터로 교체 지점 |
| `DocumentRepo.searchSharedWith` | 테넌시 필터를 쿼리 레벨에서 강제하는 패턴 — 벡터 검색에 동일 적용 |
