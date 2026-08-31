# SmartDesk 확장 로드맵

현재 1차 스캐폴드(Spring Boot + Vue, 규칙 기반 자동분류/배정, SLA 타이머)를 기준으로,
요구사항정의서 8장("향후 확장 방향")을 구체화한 단계별 계획입니다.

---

## 단계 0 — 1차 완성도 채우기 (확장 전 정지작업)

| 항목 | 내용 | 현재 상태 |
|---|---|---|
| 알림 | SLA 임박/초과 시 담당자에게 인앱 알림. `SlaMonitorJob` 5분 스캔 | ✅ 인앱 + 로그. 메일/슬랙 어댑터는 TODO |
| 관리자 화면 | SI 직원 생성/비활성화, 고객사 담당자 계정 발급, 우선순위 조정 | ✅ API + 화면. 카테고리/부서 CRUD 는 조회만 |
| 인증 강화 | 리프레시 토큰(회전), 로그아웃 blacklist, BCrypt, IP rate-limit, 비밀번호 재설정(이메일 토큰)·변경 | ✅ (메일 발송은 로그 어댑터 — 운영 시 SMTP 구현) |
| 계약 상태 전이 | `ContractStatusJob` 자동 전이 | ✅ |
| SLA 준수율 정확도 | `resolved_at` 기준 계산 | ✅ |
| 이벤트 로그 | `ticket_event` append-only (분석 기반) | ✅ |
| 첨부파일 | 티켓/문서 업로드 + UI (로컬 디스크). 문서 첨부는 공유 고객사만 열람 | ✅. S3 어댑터는 확장 |
| 감사 로그 | 보안·관리 이벤트 `audit_log` + `/audit` 조회 화면 (REQ-F-014). 티켓 이벤트는 `ticket_event` | ✅ (문서 *열람* 로그는 미포함 — 공개범위 변경만) |
| 관측성 | 구조화 로깅(JSON), Prometheus, 요청 추적 ID(X-Request-Id) | ✅ |
| 배포 | Dockerfile(백·프론트), docker-compose, GitHub Actions CI, prod 프로파일 | ✅ |
| 테스트 | 통합(테넌시·티켓생애·인증·비활성화·첨부·알림·N+1) + 단위 = 45개 | ✅ |
| 승인자 워크플로 | 화면설계서 "승인대기" 지표 | ✅ 관리자=승인자. 해결→`/approve`(종료)·`/reject`(반려). 대시보드 pendingApproval |

---

## 단계 1 — 데이터 분석 기반 마련

**목표:** 규칙 기반 로직(자동분류·SLA)을 실데이터로 검증하고 통계적 근거를 확보.

### 1.1 데이터 파이프라인
- 운영 DB(PostgreSQL) → 분석용 스키마 or 별도 DW로 일 1회 ETL (Airflow / dbt / 단순 cron + SQL)
- 티켓 이벤트를 append-only 팩트 테이블로 적재: `ticket_event(ticket_id, event_type, from, to, actor, at)`
  → 상태별 체류시간, 재오픈율, 담당자별 처리량을 계산 가능하게

### 1.2 분석 과제 (Kaggle IT Ticket 데이터셋 + 자체 데이터)
- **EDA**: 카테고리별 건수/처리시간 분포, 요일·시간대 패턴, 우선순위 vs 실제 처리시간
- **통계 검정**: 카테고리 간 평균 처리시간 차이 (ANOVA), SLA 위반과 요청 시각·시스템의 연관성 (카이제곱)
- **결과 반영**: `contract.sla_resolution_min` 기본값 제안, `category_routing` 부서 매핑 재조정,
  우선순위 자동 산정 규칙(현재 전부 MEDIUM 고정 → 카테고리·키워드 기반)

### 1.3 텍스트 기반 카테고리 분류 모델
- 제목+내용 → 카테고리. 베이스라인: TF-IDF + LogisticRegression / LinearSVC
- 평가: 규칙 기반(`CategorySuggestionService`) 대비 F1, 혼동행렬
- 서빙: 별도 Python 서비스(FastAPI)로 분리, Spring이 HTTP 호출.
  현재 `CategorySuggestionService.suggest()` 한 곳만 교체하면 됨 (인터페이스로 추상화 → 규칙/ML 전략 선택)

**연결점:** REQ-F-009(자동분류), REQ-F-011(SLA)을 실측으로 검증

---

## 단계 2 — AI 서비스: RAG 유사 티켓/문서 추천

**목표:** 지식문서와 과거 해결 티켓을 검색 기반으로 담당자에게 제안 (SCR-TICKET-002 "관련 지식문서" 실동작).

### 2.1 벡터 인덱스
- 임베딩: 한국어 지원 모델 (예: multilingual-e5, KURE, OpenAI text-embedding-3)
- 저장: `pgvector` 확장 (운영 PostgreSQL 재활용, 인프라 최소) → 규모 커지면 Qdrant/Weaviate
- 색인 대상: `document`(공개범위 필터 유지), 종료된 티켓의 제목+내용+해결 코멘트
- 재색인: 문서 저장/티켓 종료 시 이벤트로 비동기 임베딩 (outbox 패턴)

### 2.2 검색·추천
- 티켓 상세 진입 시 `POST /ai/tickets/{id}/related` → 유사 문서 top-k + 유사 과거 티켓 top-k
- **공개범위·테넌시 필터를 벡터 검색 단계에서 강제** (고객사 담당자에겐 CLIENT_SHARED만, REQ-N-001)
- 하이브리드 검색(BM25 + 벡터) → 재순위(reranker)

### 2.3 RAG 응답 초안
- "이 티켓에 대한 1차 답변 초안" 버튼: 검색된 문서를 컨텍스트로 LLM이 답변 초안 생성 → 담당자가 검수 후 코멘트로 게시
- 환각 억제: 출처 문서 인용 강제, 근거 없으면 "관련 문서 없음" 반환

**연결점:** REQ-F-013(지식문서), REQ-F-015(검색)이 의미 검색으로 고도화

---

## 단계 3 — AI 에이전트: 자동 분류·배정·SLA 대응

**목표:** 규칙 기반 라우팅(`AssignmentService`)을 에이전트로 대체/보강.

### 3.1 지능형 트리아지 에이전트
- 신규 티켓 → (분류 모델 + LLM 판단)으로 카테고리·우선순위·긴급도 산정
- 후보 담당자 스코어링: 부서 매핑 + **과거 유사 티켓을 처리한 이력** + 현재 부하 + 근무시간대
- 신뢰도 낮으면 사람에게 에스컬레이션 (완전 자동화 아님, human-in-the-loop)

### 3.2 SLA 위험 예측
- 열린 티켓의 특징(카테고리, 담당자 큐 길이, 과거 유사 건 처리시간) → 위반 확률 예측
- 임계 초과 시 사전 경고 + 재배정 제안

### 3.3 오케스트레이션
- LangChain / LangGraph로 도구 호출 그래프 구성 (분류 → 검색 → 배정 → 초안)
- 관측: 각 단계 입출력·토큰·지연 로깅 (LangSmith 등), 프롬프트 버전 관리
- 평가셋 구축: 과거 티켓 N건에 대한 정답 카테고리/담당자로 회귀 테스트

**연결점:** REQ-F-010(자동배정)이 AI 에이전트로, REQ-E-003(재배정) 자동화

---

## 단계 4 — 플랫폼화 / 배포

| 영역 | 계획 |
|---|---|
| 컨테이너화 | 백엔드·프런트·AI 서비스 각각 Docker 이미지, docker-compose → Helm 차트 |
| K8s 배포 | Deployment + HPA(부하 기반), Ingress, Secret은 External Secrets/Vault |
| CI/CD | GitHub Actions: 테스트 → 이미지 빌드 → 스테이징 배포 → 승인 → 운영 |
| 멀티테넌시 심화 | 현재 애플리케이션 레벨 `client_id` 필터 → PostgreSQL Row-Level Security로 이중 방어 |
| 데이터 격리 감사 | 테넌시 우회 시도 자동 탐지 테스트를 CI에 포함 |
| 확장성 | 티켓 이벤트를 Kafka/SQS로 발행 → 알림·색인·분석 컨슈머 분리 |

---

## 아키텍처 진화 요약

```
[1차 현재]
  Vue SPA ──HTTP──> Spring Boot ──> PostgreSQL
                    (규칙 기반 분류/배정, SLA 계산)

[단계 2~3 목표]
  Vue SPA ──> Spring Boot ──> PostgreSQL (+ pgvector)
                  │
                  ├─HTTP─> ML 분류 서비스 (FastAPI)
                  ├─HTTP─> RAG/에이전트 서비스 (LangChain + LLM)
                  └─event─> Kafka ─> [알림 / 색인 / 분석 DW]
```

## 지금 코드에서 확장을 염두에 둔 지점
- `CategorySuggestionService` / `PriorityRules` — 분류·우선순위 전략을 인터페이스화하면 규칙↔ML 교체가 1곳
- `AssignmentService` — 후보 스코어링 로직이 한 메서드에 모여 있어 에이전트 호출로 대체 용이
- `SlaService` — `business-hours-only` 플래그 + 예측 훅 자리 마련됨
- `NotificationService` — 발송 채널(메일/슬랙) 어댑터 추가 지점 표시됨
- `document.search_tsv` (tsvector + GIN) — 벡터/하이브리드 검색 전환 준비 완료
- `ticket_event` — 이벤트 소싱/분석 팩트 테이블. 상태별 체류시간·재오픈율 계산 가능
- `document` / `ticket`에 `client_id` 직접 보유 — 벡터 검색 필터·RLS 전환이 자연스러움
- `AttachmentController` 의 로컬 스토리지 — S3/GCS 어댑터로 교체 지점
