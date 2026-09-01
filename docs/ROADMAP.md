# SmartDesk 확장 로드맵

요구사항정의서 8장("향후 확장 방향": SI 플랫폼 → 데이터분석 → AI 서비스 → K8s 배포)을
현재 코드베이스 기준으로 구체화한 계획입니다.

- **현재:** Spring Boot 3.3 + Vue 3 + Python(analytics) · 규칙/ML 자동분류 · pgvector 하이브리드 검색 + RAG 추천 · **지능형 트리아지 + SLA 위반 예측** · SLA 다단계 에스컬레이션 · 승인자 워크플로 · 감사 로그 + CSV/리포트 · 실시간 알림(SSE) · 리치텍스트 문서 · 운영 분석 대시보드 · 마이그레이션 V1~V8 · 백엔드 테스트 113개 + 파이썬 4개
- **단계 0.5 (빠른 개선) — 완료 ✅** (a~h 아래 표)
- **단계 1 (데이터 분석) — 완료 ✅** (파이프라인 + EDA/검정 + TF-IDF 분류 모델 + FastAPI 서빙 + 폴백)
- **단계 2 (RAG 추천) — 완료 ✅** (2.1 pgvector 색인, 2.2 하이브리드 검색 + 테넌시 필터, 2.3 답변 초안)
- **단계 3 (AI 에이전트) — 완료 ✅** (3.1 지능형 트리아지, 3.2 SLA 위반 예측, 3.3 파이프라인 오케스트레이션 + 회귀 평가셋)
- **단계 4 (플랫폼화) — 핵심 완료 ✅** (RLS 테넌시 이중방어, S3 스토리지 어댑터, ShedLock 분산락, 배포 준비). K8s/Kafka/OTLP 는 배포 규모 확정 후
- 마이그레이션 V1~V10 · 백엔드 테스트 120개 + 파이썬 4개

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

## 단계 0.5 — 빠른 개선 — 완료 ✅ (a~h)

| # | 작업 | 상태 | 근거 |
|---|---|---|---|
| a | **알림 채널 어댑터** — `EmailSender`(SMTP) + Slack Webhook. 유형별 `email-types`/`slack-types` 설정 | ✅ | REQ-F-011 초과 알림, 현재 인앱만 |
| e | **문서 열람 감사** — CLIENT_SHARED 문서 조회 시 `audit_log` 기록 (`DOCUMENT_VIEWED`, SI 제외) | ✅ | REQ-F-014 컴플라이언스 |
| g | **세션 관리 화면** — "내 로그인 세션" 목록 + 개별/전체 로그아웃 (`refresh_token` 활용) | ✅ `/auth/sessions`, `SessionsCard` | 보안, 이미 데이터는 있음 |
| h | **SLA 다단계 에스컬레이션** — 초과 시간별 L1 담당자 → L2 부서관리자 → L3 전체관리자 | ✅ `escalation-l2/l3-minutes` | 현재 담당자→관리자 1단계 |
| f | **Testcontainers** — `jdbc:tc:` URL 로 PostgreSQL 17 컨테이너 자동 기동, CI 에서 postgres 서비스 제거 | ✅ TC 1.21.4 | 개발 편의, CI 안정성 |
| c | **리치텍스트 에디터** — 문서 본문 `textarea` → TipTap. 저장 시 서버 `HtmlSanitizer`(OWASP) 로 허용 태그만 | ✅ `RichTextEditor.vue` | 화면설계서 SCR-DOC-002 "리치텍스트 입력" |
| b | **실시간 알림** — SSE `/api/notifications/stream` (fetch 스트리밍 + 백오프 재연결), 폴링은 120초 백스톱 | ✅ `SseHub` | UX, 서버 부하 |
| d | **감사 리포트 내보내기** — `/audit` + 티켓이벤트 CSV export, `/reports/sla` SLA 준수율 리포트 화면(고객사·카테고리별) + CSV | ✅ `ReportController`, `SlaReportView.vue` | REQ 리포트 대분류, 감사 대응 |

---

## 단계 1 — 데이터 분석 기반 마련 — 완료 ✅

**목표:** 규칙 기반 로직(자동분류·SLA)을 실데이터로 검증하고 통계적 근거 확보.

### 1.1 데이터 파이프라인 ✅ — `V6__analytics.sql`, `feature/analytics/*`, `/analytics` 화면
- `analytics` 스키마: `ticket_metrics` 뷰(처리시간·최초응답·SLA준수·재오픈·요청 요일·시간대),
  `ticket_resolution_stats` materialized view(카테고리×요일×시간대 p50/p90/위반율), `assignee_throughput` 뷰
- `AnalyticsRefreshJob` 야간 REFRESH. `AnalyticsController` 관리자 API + Vue 대시보드(히트맵·SLA 권장값)
- `external_ticket` 테이블 — 외부 데이터셋 적재 대상

### 1.2 분석 · 통계 검정 ✅ — `analytics/` (Python), `reports/*.md`
- 데이터셋: **Tobi-Bueck/customer-support-tickets** (HF, CC-BY-NC-4.0). Kaggle "Customer IT Support" 동일 데이터의 저자 미러 → 인증 불필요. EN 11,923행, 큐 10종
- EDA: 큐·유형·우선순위 분포, 큐×우선순위/유형 교차표, 본문 길이
- 검정: 큐 ⟂ 우선순위 카이제곱(V=0.28, 종속), 큐 ⟂ 유형(V=0.20, 종속), 큐별 본문길이 Kruskal(차이 有)
  · 운영 DB 검정(카테고리별 처리시간 ANOVA)은 표본 축적 후 자동 실행
- 결과 반영 훅: `/analytics/sla-recommendation` (카테고리별 p90 → `contract.sla_resolution_min` 권장값)

### 1.3 텍스트 분류 모델 ✅ — `analytics/model/`, `analytics/service/` (FastAPI), `feature/ticket/classify/`
- TF-IDF(word 1–2gram + char_wb 3–5gram) + LinearSVC(calibrated). 홀드아웃 macro-F1:
  규칙 0.18 → LogReg 0.43 → **LinearSVC 0.49 (+31%p, 목표 +10%p 초과)**
- 잘 잡는 큐: Billing/Outages (F1 0.57~0.81). 약한 큐: IT↔Technical↔Customer Service (의미 중첩) → 단계 2 임베딩으로 개선
- 서빙: FastAPI `POST /classify` {subject, body} → {queue, confidence}. `analytics/service/Dockerfile`, compose `ml` 서비스
- Spring: `CategorySuggester` 인터페이스 → `RuleBasedCategorySuggester` / `MlCategorySuggester`.
  `smartdesk.classification.provider=rule|ml`, `queue-map`(모델 라벨 → SmartDesk 카테고리), `ml-min-confidence`.
  ML 오류·저신뢰·매핑없음 → 규칙 기반 폴백 (런타임 검증)

**한계·다음:** 외부 데이터셋 라벨 체계가 SmartDesk 카테고리와 달라 `queue-map` 으로 근사. 운영에서
자체 티켓 라벨이 쌓이면 재학습하면 매핑 불필요. 처리시간 기반 검정은 운영 데이터 축적 후.

---

## 단계 2 — AI 서비스: RAG 유사 티켓/문서 추천 — 완료 ✅

**목표:** 지식문서와 과거 해결 티켓을 의미 검색으로 담당자에게 제안.

### 2.1 벡터 인덱스 ✅ — `V7__vector_search.sql`, `feature/rag/*`, `analytics/service /embed`
- 임베딩: **multilingual-e5-small** (384차원, 한국어). analytics/service 의 `POST /embed` (시작 시 warmup)
- 저장: **pgvector** `embedding` 테이블 + HNSW 인덱스. `ticket.search_tsv` 추가 (문서엔 V3 의 `search_tsv`)
- 색인 대상: 지식문서 전체, 종료(CLOSED) 티켓의 제목+내용+코멘트. `source_hash` 로 변경분만 재색인
- 트리거: `RagIndexListener`(`@TransactionalEventListener` AFTER_COMMIT) + `RagReconcileJob`(10분) 보정
- Testcontainers 이미지를 `pgvector/pgvector:pg17` 로 전환 (`PgVectorContainer`)

### 2.2 하이브리드 검색 ✅ — `EmbeddingStore`, `RagSearchService`
- `POST /api/ai/tickets/{id}/related` → 유사 문서 top-k + 유사 종료 티켓 top-k
- **테넌시 필터를 SQL WHERE 절에서 강제**: 고객사 담당자 = 공유 문서(`document_share`) + 자사 티켓만 (REQ-N-001, 런타임 검증)
- 하이브리드: 벡터(`<=>` 코사인) + BM25(`ts_rank`) → **RRF 융합**. cross-encoder 재순위는 향후
- `TicketDetailView`: RAG 있으면 의미 검색 결과, 없으면 카테고리 매칭 폴백

### 2.3 RAG 답변 초안 ✅ (LLM 연동은 키 설정 시) — `AnswerDraftService`, `LlmClient`
- `POST /api/ai/tickets/{id}/answer-draft` (SI 담당자 전용): 검색 문서를 번호 매긴 컨텍스트로 LLM 초안
- 환각 억제: `[n]` 출처 인용 강제, 근거 부족 시 "관련 문서를 찾지 못했습니다" 고정 문구
- `LlmClient` = `AnthropicLlmClient`(anthropic-java, 기본 `claude-opus-5`) / disabled. `provider=none`(기본)이면 근거 문서만 반환
- `TicketDetailView` "1차 답변 초안" 버튼 + "코멘트에 넣기". 관리자 `/analytics` 에 RAG 색인 상태·재색인

**활성화:** `smartdesk.rag.enabled=true` + analytics/service 기동. 초안은 `RAG_LLM_PROVIDER=anthropic` + `ANTHROPIC_API_KEY`.

**한계·다음:** e5-small 은 짧은 한국어에서 절대 유사도가 압축돼 있어 랭킹 위주로 사용 (재순위 모델로 개선 여지). 동시 색인은 `pg_advisory_xact_lock` 으로 직렬화(다중 인스턴스 안전) — 다만 이벤트 유실 대비는 여전히 10분 재조정에 의존, 대량이면 outbox 테이블 권장.

**연결점:** REQ-F-013(지식문서)·REQ-F-015(검색) 의미 검색화

---

## 단계 3 — AI 에이전트: 트리아지·배정·SLA 예측 — 완료 ✅

**목표:** 규칙 기반(`AssignmentService`·`PriorityRules`·`CategorySuggester`)을 통합 트리아지로 보강. human-in-the-loop.

### 3.1 지능형 트리아지 ✅ — `feature/triage/`
- `TriageService`: 분류(단계1 `CategorySuggester`) + 유사 종료티켓(단계2) + 담당자 실적(`analytics.ticket_metrics`) + (선택)`TriageAdvisor`(LLM) 종합
  → 카테고리 / 우선순위(RULE·SIMILAR·LLM 중 최고 심각도) / 담당자 제안 + 신뢰도(0~1)
- `AssigneeScorer`: `0.5·부하여유 + 0.4·카테고리경험 − 0.3·SLA위반율` 로 후보 스코어링
- 신규 티켓 자동 트리아지(`TicketController.create`): 카테고리·우선순위 적용, `신뢰도 ≥ min-confidence` 면 담당자까지 자동 배정 + `IN_PROGRESS`, 아니면 관리자에게 `TRIAGE_REVIEW` 알림
- `POST /api/tickets/{id}/triage`(미리보기)·`/triage/apply`, `TicketDetailView` "AI 트리아지" 카드. `TRIAGED` 이벤트 + `triage_snapshot`(V8)

### 3.2 SLA 위반 예측 ✅ — `SlaRiskService`
- 휴리스틱(구조는 ML 교체 가능): 경과율 + 담당자 부하 + 카테고리 p90 vs 잔여시간 + 재오픈 → 위험도 0~1
- `SlaMonitor` 관찰창 2h → **8h** 확대. 임박 전이라도 HIGH 위험이면 `SLA_AT_RISK` 사전 경고 (+ `suggestReassign` 시 관리자까지)
- `GET /api/tickets/{id}/sla-risk`

### 3.3 오케스트레이션 ✅ (경량)
- LangGraph 대신 `TriageService` 파이프라인이 오케스트레이션 (분류 → 유사검색 → 스코어링 → LLM 판단 → 결정). 분기가 단순해 그래프 프레임워크 불필요 — 복잡해지면 도입
- `TriageEvalTest`: 라벨된 8 시나리오로 카테고리·우선순위 정확도 회귀 방어 (기준 0.75)
- LangSmith 관측은 미도입 — `TriageService` 가 결정·입력을 `TRIAGED` 이벤트 + 로그로 남김

**한계·다음:** SLA 예측은 휴리스틱 (운영 데이터 축적 후 학습 모델). 근무시간대 스코어링·LLM 배정 판단은 미구현.

**연결점:** REQ-F-009·010(자동분류·배정) AI화, REQ-E-003(재배정) 사전 제안

---

## 단계 4 — 플랫폼화 / 배포

| 영역 | 상태 |
|---|---|
| 멀티테넌시 심화 | ✅ **PostgreSQL RLS** — `V9`: `smartdesk_app` 비특권 롤 + `FORCE RLS` + `tenant_isolation` 정책(ticket·contract·system_asset·comment·document·document_share). `TenantContext`/`TenantContextFilter`/`RlsDataSource`(문 생성 직전 세션변수 동기화). 크로스테넌트 접근 → 404. `RlsTest` |
| 스토리지 | ✅ `BlobStorage` 어댑터 — `LocalDiskStorage` / `S3BlobStorage`(awssdk v2, endpoint override 로 MinIO 호환). `smartdesk.storage.type`. MIME 화이트리스트. `S3BlobStorageTest`(localstack). 바이러스 스캔 훅은 향후 |
| 스케줄러 다중화 | ✅ **ShedLock**(`V10`) — 잡별 `@SchedulerLock` 으로 다중 인스턴스 1회 실행 |
| 배포 준비 | ✅ `docker-compose.prod.yml` + `docs/DEPLOY.md` + `DemoAccountGuard`(prod 데모 계정 비활성화) |
| K8s | ⬜ Deployment + HPA, Ingress, External Secrets, Helm 차트 — 배포 규모 확정 후 |
| CI/CD | ⬜ 이미지 레지스트리 push + 스테이징 자동 배포 (현재는 빌드까지) |
| 이벤트 버스 | ⬜ `ticket_event` / outbox → Kafka 발행 → 알림·색인·분석 컨슈머 분리 |
| 관측성 심화 | ⬜ OpenTelemetry 분산 추적, Grafana 대시보드 |

---

## 6개월 제안 순서

1. ~~**0.5** 전체 (a~h)~~ — 완료
2. ~~**단계 1** 전체 (파이프라인 → EDA → 분류 모델)~~ — 완료
3. ~~**단계 2** 전체 (pgvector 색인 + 하이브리드 검색 + RAG 초안)~~ — 완료
4. ~~**단계 3** (지능형 트리아지 + SLA 위반 예측)~~ — 완료
5. ~~**단계 4 핵심** (RLS, S3 어댑터, ShedLock, 배포 준비)~~ — 완료
6. **단계 4 잔여** (K8s/Helm, 이미지 CD, Kafka 이벤트 버스, OTLP) — 실제 배포 규모 확정 후

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
