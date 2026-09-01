# SmartDesk — SI 고객사 IT 지원 플랫폼

SI(System Integration) 기업이 여러 고객사의 IT 지원을 **하나의 콘솔**에서 처리하는 멀티테넌트 헬프데스크.
티켓 · SLA · 지식관리를 코어로 두고, 그 위에 **데이터 분석 → 의미 검색(RAG) → AI 트리아지**를 단계적으로 얹었습니다.

| | |
|---|---|
| **스택** | Java 21 · Spring Boot 3.3 · PostgreSQL 17(+pgvector) · Vue 3 · Python(FastAPI · scikit-learn) |
| **규모** | 엔티티 23 · REST 엔드포인트 79 · 마이그레이션 V1–V10 · 테스트 121(백엔드) + 4(Python) |
| **원본** | 요구사항정의서 · ERD · API 명세서 · 화면설계서(직접 작성, 울산 2반 김규빈)를 기반으로 설계·구현 |
| **데모** | `배포 URL 기입` · 계정 `admin@smartdesk.io` / `Passw0rd!` (아래 [데모 계정](#데모-계정)) |

> 이 문서는 **문제 → 아키텍처 → 트레이드오프** 순입니다. 실행 방법은 [4장](#4-실행), 산출물↔코드 매핑과 전체 결정 로그는 [6장 부록](#6-부록).

---

## 1. 문제

SI 기업은 계약을 맺은 여러 고객사의 IT 인프라를 대신 운영·지원한다. 지원 조직이 실제로 겪는 문제:

| 문제 | 구체적 상황 |
|---|---|
| **테넌트 격리** | 한 콘솔에서 A사·B사 티켓을 함께 다루지만, A사 담당자에게 B사 데이터가 한 건이라도 새면 계약 위반이다. 쿼리 하나에서 `client_id` 조건을 빠뜨린 버그가 사고가 된다. |
| **SLA 준수** | 계약마다 해결 시한(SLA)이 다르다. 초과가 임박한 티켓을 사람이 눈으로 좇으면 놓친다. |
| **지식 파편화** | 같은 장애를 과거에 해결한 티켓·문서가 있는데, 담당자가 그 존재를 모른 채 처음부터 다시 조사한다. |
| **수작업 분류·배정** | 접수 티켓의 카테고리·우선순위·담당자를 매번 사람이 판단한다. 편차가 크고 느리다. |
| **감사 추적** | "누가 언제 이 고객사 계정을 만들었는가"에 답할 수 있어야 한다(컴플라이언스). |

원본 산출물은 위 문제의 **기능 요건**까지 정의한다. 이 저장소는 거기서 출발해
**비기능 요건(격리·성능·운영)** 과 **지능화(분석·검색·트리아지)** 를 설계·구현한 결과다.

---

## 2. 아키텍처

### 2.1 구성

```
  Vue 3 SPA  ──HTTP──►  Spring Boot REST API  ──JDBC──►  PostgreSQL 17
  (Pinia/Router,          + 내장 스케줄러                 ├─ 도메인 테이블 · Row-Level Security
   nginx가 정적 서빙        (ShedLock 분산락)              ├─ pgvector 임베딩 인덱스 (HNSW)
   + /api 프록시)                │                        └─ analytics 스키마 (뷰 / MView)
                                 │
                          HTTP · 옵트인 · 실패 시 규칙 폴백
                                 ▼
                     Python analytics/ (FastAPI)
                       ├─ POST /classify   TF-IDF + LinearSVC
                       └─ POST /embed      multilingual-e5-small
```

- **모놀리식 백엔드가 기본.** ML 서비스는 별도 프로세스지만 **선택적 의존성** — 죽어도 규칙 기반으로 폴백해 코어 기능은 유지된다.
- 프론트는 nginx가 정적 서빙 + `/api` 리버스 프록시. **단일 오리진이라 CORS가 필요 없다.**
- 스케줄러(SLA 모니터·벡터 재색인·계약 상태 전이 등)는 백엔드에 내장, **ShedLock** 으로 다중 인스턴스에서도 잡당 1회만 실행.

### 2.2 패키지 구조 — feature 단위

```
com.smartdesk
├─ security/           JWT · 인증 필터 · 토큰 폐기 레지스트리 · CurrentUser
├─ common/             GlobalExceptionHandler · PageResponse · HtmlSanitizer
│  └─ tenant/          TenantContext · TenantContextFilter · RlsDataSource
├─ domain/             JPA 엔티티 (23)
├─ config/             SchedulingConfig(ShedLock) · Storage · Rag · Classification
└─ feature/
   ├─ auth/ user/ client/ contract/ system/     기본 SI 도메인
   ├─ ticket/          티켓 · 상태전이 · SLA 모니터 · 우선순위 규칙 · 분류 전략
   ├─ document/        지식문서 · 버전 · 공유 · 첨부
   ├─ notification/    인앱 · SSE · 메일/Slack 채널 어댑터
   ├─ analytics/       운영 분석 마트 API (단계 1)
   ├─ rag/             임베딩 · 색인 · 하이브리드 검색 · 답변 초안 (단계 2)
   ├─ triage/          TriageService · AssigneeScorer · SlaRiskService (단계 3)
   └─ audit/ report/   감사 로그 · CSV/SLA 리포트
```

### 2.3 핵심 플로우

**인증** — JWT 액세스(1h · HS256) + 리프레시(7d). 리프레시는 **일회성 회전**: 동시 요청은 `revokeIfActive`로 원자적으로 하나만 승자, 소진된 토큰 재사용은 401. 로그아웃 시 액세스 토큰의 `jti`를 **인메모리 블랙리스트**에 등록 — 인증 경로는 DB 조회 없이 O(1), 5분마다 재동기화(다중 인스턴스 최종 일관성). 로그인 실패는 이메일·IP 단위로 rate-limit.

**AI 트리아지** — 티켓 생성 → `TriageService`가 네 신호를 종합한다:
`[규칙/ML 분류]` + `[유사 종료 티켓 검색]` + `[담당자 실적 스코어링]` + `[(선택) LLM 판단]`
→ 카테고리 · 우선순위 · 담당자 후보 · **신뢰도(0~1)**. 신뢰도 ≥ 임계값이면 자동 배정 + `IN_PROGRESS`, 아니면 관리자에게 검토 요청(**human-in-the-loop**). 결정과 입력은 `ticket_event`에 남는다.

**RAG 검색** — 지식문서 + 종료 티켓을 청킹·임베딩해 pgvector에 저장(저장/종료 이벤트 트리거 + 10분 재조정 잡). 질의 시 **벡터 유사도 + BM25 → RRF 융합**. **테넌트 필터를 SQL WHERE 절에 강제** — 애플리케이션 코드가 빠뜨려도 격리가 유지된다.

### 2.4 데이터 모델에서 의도한 것

| 설계 | 이유 |
|---|---|
| `ticket` · `document`가 `client_id`를 직접 보유 | RLS 정책과 벡터 검색 필터를 단일 컬럼으로 |
| `ticket_event` append-only | 이벤트 소싱 기반 분석(체류시간·재오픈율) + 감사 |
| `audit_log` 분리 · `REQUIRES_NEW` 트랜잭션 | 실패한(롤백된) 요청도 기록되어야 함 |
| `document_version` 스냅샷 | 낙관적 잠금(REQ-E-008) + 이력 |
| 모든 시각 UTC 저장, 표시 변환은 프론트 | REQ-E-010 |

---

## 3. 트레이드오프 / 설계 결정

산출물에 명시가 없던 지점에서 내린 결정과, **고려한 대안**.

### 3.1 테넌트 격리 — 애플리케이션 필터 vs DB RLS

- **선택**: 둘 다 (심층 방어). 앱 레벨 `CurrentUser` 가드 **+** PostgreSQL Row-Level Security(`V9`).
- **대안 ⓐ 앱 필터만** — 코드 리뷰에 100% 의존. 새 쿼리 하나가 `where client_id = ?`를 빠뜨리면 유출.
- **대안 ⓑ RLS만** — 모든 접근이 DB 왕복, 앱이 조기에 403을 줄 수 없음. 배치/스케줄러 컨텍스트 처리가 까다로움.
- **비용**: 커넥션마다 세션변수(`app.current_client`)를 문 실행 직전 동기화하는 `RlsDataSource`, 마이그레이션은 소유자 롤·앱 커넥션은 비특권 롤(`smartdesk_app`)로 분리 → 운영 DB 프로비저닝에 `CREATEROLE` 필요.
- **부수효과**: 크로스테넌트 접근이 403이 아니라 **404**(행 자체가 안 보임). 리소스 존재 여부도 숨기므로 정보 노출 관점에선 오히려 낫다.
- 적용 테이블은 `ticket · contract · system_asset · comment · document · document_share`. 로그인 흐름·횡단 테이블(`embedding`·`notification`·`audit_log` 등)은 앱 레벨 필터 유지.

### 3.2 자동분류 — 규칙 vs ML

- **선택**: 인터페이스(`CategorySuggester`) 뒤에 규칙·ML 두 구현. 기본은 규칙, `provider=ml`로 전환, **ML 오류·저신뢰·매핑 없음 시 규칙으로 폴백**.
- **왜**: 콜드 스타트(운영 라벨 0건)에서 ML은 외부 데이터셋으로 학습할 수밖에 없다. 그 라벨 체계(큐 10종)가 SmartDesk 카테고리(5종)와 안 맞아 `queue-map`으로 근사 — 홀드아웃 macro-F1 규칙 0.18 → **LinearSVC 0.49**.
- **트레이드오프**: ML을 코어에 인라인하지 않아 지연·장애가 전파되지 않지만, 별도 서비스 운영 부담이 생긴다. 운영 티켓 라벨이 쌓이면 재학습 → `queue-map` 제거.

### 3.3 실시간 알림 — 폴링 vs SSE vs WebSocket

- **선택**: **SSE**(`fetch` 스트리밍 + 지수 백오프 재연결), 폴링은 120초 백스톱으로 축소.
- **WebSocket 아님**: 서버→클라이언트 단방향이면 충분. WS는 스티키 세션·프록시 설정 등 인프라 비용만 추가.
- **`EventSource` 아님**: 표준 EventSource는 `Authorization` 헤더를 실을 수 없어 fetch 스트리밍으로 직접 구현.

### 3.4 트리아지 오케스트레이션 — LangGraph vs 순수 코드

- **선택**: `TriageService`의 순차 파이프라인(분류 → 유사검색 → 스코어링 → LLM 판단 → 결정).
- **왜**: 분기가 단순(신뢰도 임계값 하나)해 그래프 프레임워크의 상태관리가 과하다. 회귀는 `TriageEvalTest`(라벨 8종, 정확도 ≥ 0.75)로 방어. 분기가 복잡해지면 도입.

### 3.5 LLM — 필수 vs 옵트인

- **선택**: **전 기능이 LLM 없이 동작.** `ANTHROPIC_API_KEY`가 있을 때만 답변 초안·트리아지 판단에 가산.
- **왜**: 데모·테스트·오프라인에서 돌아가야 하고, 비용·레이턴시·환각이 코어 경로에 들어오면 안 된다. 답변 초안은 `[n]` 출처 인용을 강제하고 근거 부족 시 고정 문구를 반환.

### 3.6 그 외 결정 (요약)

| 지점 | 결정 | 대안 대비 |
|---|---|---|
| 비밀번호 해시 | **BCrypt**(cost 10) | 요건은 "SHA-256 이상"이었으나 솔트·work factor가 필요 |
| 목록 페이지네이션 | 오프셋(`page`/`size`) | 커서보다 단순, 관리 콘솔 규모엔 충분 |
| 첨부 저장 | `BlobStorage` 어댑터(로컬 디스크 / S3) | 단일 인스턴스는 로컬, 스케일아웃 시 코드 변경 없이 S3 |
| N+1 | 배치 조회(`findAllById`) + `QueryCountTest` 회귀 방어 | fetch join은 페이지네이션과 충돌 |
| 전문검색 | `tsvector` + GIN → 이후 벡터 하이브리드 | 외부 검색엔진(ES) 도입은 운영 규모 미달 |
| 승인 워크플로 | 관리자 = 승인자, `RESOLVED→CLOSED`는 `/approve`로만 | 별도 승인자 롤은 조직 규모 대비 과설계 |
| 오류 응답 | `{timestamp, status, code, message, path}` 통일, 500은 세부 숨기고 `ref` 로그 | — |

전체 39개 항목: [§6.2 결정 로그](#62-결정-로그-39).

---

## 4. 실행

### 사전 준비

| 도구 | 버전 | 비고 |
|---|---|---|
| JDK | **21** | 상위 JDK는 Lombok과 충돌. `JAVA_HOME`을 21로 지정 |
| Maven | 3.9+ | |
| Node | 20+ | |
| Docker | — | PostgreSQL·테스트 컨테이너용 |

### 가장 빠른 길 — 전체 스택 컨테이너

```bash
docker compose --profile app up --build                 # db + backend + frontend
docker compose --profile app --profile ml up --build     # + AI 서빙(:8000)
# frontend :5173(nginx) · backend :8080 · db :5432 · ml :8000
```

### 로컬 개발

```bash
# 1) DB — Docker 또는 로컬 PostgreSQL 17 (+ pgvector 확장)
docker compose up -d db

# 2) 백엔드  → http://localhost:8080  (Flyway가 스키마 + 데모 시드 자동 적용)
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run

# 3) 프론트  → http://localhost:5173  (/api → 8080 프록시)
cd frontend && npm install && npm run dev

# 4) (선택) 분석·ML
cd analytics && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
make train && make serve       # FastAPI :8000 — POST /classify, /embed
```

AI 연동을 켜려면: `CLASSIFICATION_PROVIDER=ml RAG_ENABLED=true [ANTHROPIC_API_KEY=...]`

### 데모 계정

비밀번호 공통 `Passw0rd!` (prod 프로파일에선 첫 부팅에 자동 비활성화)

| 구분 | 이메일 | 역할 |
|---|---|---|
| SI 관리자 | `admin@smartdesk.io` | MANAGER |
| SI 담당자 | `infra@ · app@ · sec@smartdesk.io` | AGENT |
| 고객사 담당자 | `user@a-corp.com` · `user@b-corp.com` | CLIENT_USER |

---

## 5. 테스트

```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test     # 121개
```

DB는 **Testcontainers**가 `pgvector/pgvector:pg17` + `localstack`(S3) 컨테이너를 자동 기동.
각 통합 테스트는 트랜잭션 롤백으로 격리. 스케줄러는 `test` 프로파일에서 비활성.

| 테스트 | 검증 |
|---|---|
| `TenancyIsolationTest` · `RlsTest` | 고객사 간 격리 — 앱 레벨(404) + RLS 정책(자기 행만·WITH CHECK로 타사 INSERT 차단) |
| `RagSearchTest` | 하이브리드 검색(실제 pgvector) — 유사 문서·티켓, 고객사는 공유 문서만, 타사 티켓 차단 |
| `TriageTest` · `TriageEvalTest` | 자동 트리아지(카테고리·우선순위), preview 무변경, apply + 이벤트, 회귀 평가셋 ≥ 0.75 |
| `AuthFlowTest` | 리프레시 회전, 로그아웃 폐기, 5회 실패 잠금 |
| `S3BlobStorageTest` | S3 어댑터 왕복(localstack) — put/get/exists/delete, prefix, 없는 키 404 |
| `SlaEscalationTest` | 미배정 티켓 SLA 초과 → 다단계 에스컬레이션, 재스캔 시 멱등 |
| `ApprovalTest` · `PasswordResetTest` · `AuditLogTest` | 승인/반려, 재설정 토큰 1회성·세션 폐기, 실패 로그인도 감사 기록 |
| `QueryCountTest` | N+1 회귀 방어 — 데이터가 늘어도 쿼리 수 불변 |
| `DeactivationTest` · `AttachmentTest` · `NotificationApiTest` · `ContractStatusServiceTest` · `TicketLifecycleTest` · `AnalyticsTest` · `SlaAndPriorityTest`(단위) · `ContextLoadTest` | 각 도메인 규칙 |

CI: [.github/workflows/ci.yml](.github/workflows/ci.yml) — PR/main push 시 backend `mvn verify` + frontend `lint`/`build` + 이미지 빌드.

---

## 6. 부록

### 6.1 산출물 ↔ 코드 매핑

<details>
<summary><b>화면 (화면설계서)</b></summary>

| 화면 ID | 라우트 | 파일 |
|---|---|---|
| SCR-AUTH-001 | `/login` | `views/LoginView.vue` |
| SCR-MAIN-001 | `/` | `views/SiDashboardView.vue` |
| SCR-CLIENT-001 | `/portal` | `views/ClientPortalView.vue` |
| SCR-TICKET-001 | `/portal/tickets/new` | `views/TicketNewView.vue` |
| SCR-TICKET-002 | `/tickets/:id` | `views/TicketDetailView.vue` |
| SCR-CLIENT-LIST-001 | `/clients` | `views/ClientListView.vue` |
| SCR-CONTRACT-001 | `/clients/:id/contract` | `views/ContractDetailView.vue` |
| SCR-DOC-001 | `/docs` | `views/DocListView.vue` |
| SCR-DOC-002 | `/docs/new`, `/docs/:id/edit` | `views/DocEditView.vue` |
| (문서 상세 — 고객사 열람) | `/docs/:id` | `views/DocDetailView.vue` |
| SCR-PROFILE-001 | `/profile` | `views/ProfileView.vue` |
| (비밀번호 재설정) | `/forgot-password`, `/reset-password` | `views/ForgotPasswordView.vue`, `ResetPasswordView.vue` |
| (SLA 준수율 리포트 — 관리자) | `/reports/sla` | `views/SlaReportView.vue` |
| (감사 로그 — 관리자) | `/audit` | `views/AuditLogView.vue` |

</details>

<details>
<summary><b>API (API 명세서) — 모든 경로에 <code>/api</code> 프리픽스</b></summary>

| 명세서 경로 | 구현 경로 | 컨트롤러 |
|---|---|---|
| `POST /auth/login`, `/auth/client-login`, `/auth/logout` | `POST /api/auth/*` (+ `/api/auth/refresh`) | `feature/auth/AuthController` |
| `GET/PUT /users/me` | `/api/users/me` | `feature/user/UserController` |
| `GET/POST/PUT /clients...` | `/api/clients...` | `feature/client/ClientController` |
| `GET/POST/PUT /clients/{id}/contracts`, `/contracts/{id}`, `/onboarding`, `/offboarding` | `/api/...` | `feature/contract/ContractController` |
| `GET/POST/DELETE /clients/{id}/systems`, `/systems/{id}` | `/api/...` | `feature/system/SystemController` |
| `GET/POST/PUT /tickets`, `/tickets/{id}/category\|assignee\|priority\|sla\|status\|comments\|related-documents\|approve\|reject` | `/api/tickets...` | `feature/ticket/TicketController` |
| `GET/POST/PUT /documents`, `/documents/{id}/scope\|versions` | `/api/documents...` | `feature/document/DocumentController` |
| `GET /dashboard/clients/{id}` | `/api/dashboard/clients/{id}` | `feature/dashboard/DashboardController` |

**확장하며 추가한 엔드포인트**

| 엔드포인트 | 설명 |
|---|---|
| `POST /api/auth/refresh` | 리프레시 토큰으로 액세스 토큰 재발급(회전) |
| `POST /api/auth/forgot-password`, `/reset-password`, `/change-password` | 비밀번호 재설정(이메일 토큰)·변경 |
| `GET /api/users`, `POST /api/users`, `PATCH /api/users/{id}/deactivate` | SI 직원 목록/생성/비활성화(+열린 티켓 재배정) |
| `GET/POST /api/clients/{id}/users`, `PATCH /api/client-users/{id}/deactivate` | 고객사 담당자 계정 발급/비활성화 |
| `GET/PATCH /api/notifications`, `/{id}/read`, `/read-all` | 인앱 알림 |
| `GET/POST/GET/DELETE /api/attachments` | 티켓/문서 첨부파일 |
| `GET /api/tickets/{id}/related-documents` | 동일 카테고리 지식문서(RAG 전신) |
| `PUT /api/tickets/{id}/priority` | 우선순위 수동 조정 |
| `GET /api/audit`, `/api/audit/ticket-events` (+ `/export`) | 감사 로그 조회·CSV |
| `GET /api/auth/sessions`, `DELETE /api/auth/sessions/{id}`, `DELETE /api/auth/sessions` | 로그인 세션 목록·종료 |
| `GET /api/notifications/stream` | 실시간 알림 SSE |
| `GET /api/reports/sla` (+ `/export`) | SLA 준수율 리포트 |
| `GET /api/analytics/*` | 운영 분석 마트 |
| `POST /api/ai/tickets/{id}/related` · `/answer-draft` | 유사 문서·티켓 추천 · RAG 답변 초안 |
| `POST /api/tickets/{id}/triage` · `/triage/apply` · `GET /sla-risk` | 지능형 트리아지 · SLA 위험도 |
| `GET /api/ai/rag/status` · `POST /api/ai/rag/reindex` | 벡터 색인 상태·재색인 |

</details>

<details>
<summary><b>데이터 (ERD)</b></summary>

`backend/src/main/resources/db/migration/V1__init.sql`이 ERD 테이블 정의서를 반영.
`user` → `app_user`, `system` → `system_asset` (SQL 예약어 회피).

</details>

### 6.2 결정 로그 (39)

산출물은 요구사항 ID로 잘 교차 연결돼 있어 스캐폴딩은 가능했지만, 아래는 명세에 없어
**합리적 기본값으로 구현**했다. 실제 확정값이 정해지면 교체 지점.

| # | 공백 | 적용한 결정 | 위치 |
|---|---|---|---|
| 1 | 인증 방식 미지정 | JWT 액세스(HS256, 1h) + 리프레시(7d, 원자적 회전) + 로그아웃 시 액세스 토큰 폐기(jti blacklist) | `security/JwtService`, `feature/auth/AuthService` |
| 2 | API 응답 스키마 없음 | 컨트롤러별 record DTO. 오류는 `{timestamp,status,code,message,path}` 통일. 500은 세부 숨기고 ref id 로그 | `common/GlobalExceptionHandler` |
| 3 | 페이지네이션 규격 없음 | 오프셋 `?page=&size=`, 응답 `PageResponse{content,page,size,totalElements,totalPages}` | `common/PageResponse` |
| 4 | 엔드포인트별 RBAC 규칙 없음 | SI 직원 / 관리자 / 고객사 격리를 `CurrentUser` 헬퍼로 강제(미인증 401, 권한없음 403). 단계 4에서 PostgreSQL RLS 이중 방어 | `security/CurrentUser`, `common/tenant/*` |
| 5 | `client_user` 계정 생성 API 없음 | `POST /api/clients/{id}/users` 추가(관리자, 온보딩 화면) | `feature/client/ClientUserController` |
| 6 | category / department 조회 API 누락 | `GET /api/categories`, `/api/departments`, `/api/users` 추가 | `feature/meta/MetaController` |
| 7 | 카테고리 자동분류 알고리즘 미정 | 키워드 규칙 제안 + 단계 1 TF-IDF 분류 모델(FastAPI). `provider=ml` 시 ML, 실패·저신뢰 시 규칙 폴백 | `feature/ticket/classify/*`, `analytics/` |
| 8 | 담당자 자동배정 규칙 미정 | `category_routing` + `user_client` 후보 → 단계 3 `AssigneeScorer`가 부하·실적·SLA 위반율로 스코어링 | `AssignmentService`, `feature/triage/*` |
| 9 | SLA 계산식 미정 | 기본 `sla_due_at = created_at + sla_resolution_min`. `business-hours-only=true` 시 영업시간만 카운트 | `SlaService` |
| 10 | SLA 초과 알림 수단 미정 | `SlaMonitorJob`(5분) → 티켓 단위 처리, 경과 시간별 다단계 에스컬레이션(L1→L2→L3). `NotificationWriter` REQUIRES_NEW + 유니크로 중복 방지 | `feature/ticket/SlaMonitorService` |
| 11 | 문서 버전 이력 테이블 없음 | `document_version` 스냅샷 + `GET /api/documents/{id}/versions` | `document_version` |
| 12 | 낙관적 잠금 version 파라미터 없음 | `PUT /documents/{id}` 바디 `expectedVersion`, 불일치 409 | `DocumentController.update` |
| 13 | 문서 다중 고객사 공유 모델 없음 | `document_share` 조인 테이블 | `document_share` |
| 14 | 온보딩/오프보딩 요청 바디 없음 | 온보딩: 시스템 초기 등록. 오프보딩: 미해결 티켓 검사 후 종료 + 자산 비활성화 | `ContractController` |
| 15 | 담당 고객사 저장 위치 없음 | `user_client` 조인 테이블 | `user_client` |
| 16 | 전문검색 엔진 미정 | 1차 `LIKE`, `document.search_tsv`(tsvector) + GIN 인덱스는 마이그레이션에 추가 완료 | `V3` |
| 17 | 비밀번호 해시 | BCrypt로 전환("SHA-256 이상" 충족·강화) | `security/PasswordHasher` |
| 18 | 계정 비활성화/재배정 | `PATCH /api/users/{id}/deactivate` — 비활성화 + 열린 티켓 재배정 + 토큰 폐기 | `AssignmentService.reassignOpenTickets` |
| 19 | 티켓 우선순위 산정 로직 없음 | 키워드 규칙(`PriorityRules`)으로 등록 시 산정 + 수동 조정 | `feature/ticket/PriorityRules` |
| 20 | 계약 상태 자동 전이 없음 | `ContractStatusJob`(매일 + 부팅 시): ACTIVE→EXPIRING→ENDED | `feature/contract/ContractStatusJob` |
| 21 | SLA 준수율이 `updated_at` 기준 | `resolved_at`/`closed_at`/`first_responded_at` 추가, 준수율은 `resolved_at` 기준 | `Ticket.isSlaMet()`, `V3` |
| 22 | 이벤트 로그 없음 | `ticket_event` append-only 테이블 | `TicketEventService` |
| 23 | 첨부파일 없음 | `attachment` 테이블 + 업/다운로드 API(로컬 디스크, S3 어댑터로 교체 가능) | `feature/attachment/*` |
| 24 | 로그인 rate-limit 이메일 단위만 | IP 단위 제한 추가(20회/15분), 잠금 만료 시 카운터 리셋 | `AuthService` |
| 25 | 계약상태 한글/enum 불일치 | enum name으로 통일, 프론트에서 현지화 | `ClientController`, `labels.js` |
| 26 | 티켓 목록 N+1 | 담당자·작성자 이름을 `findAllById` 배치 조회. `QueryCountTest` 회귀 방지 | `TicketController` |
| 27 | 문서 검색이 전체 로드 후 메모리 필터 | scope·키워드를 JPQL로 내림 | `DocumentRepo` |
| 28 | 인증마다 폐기 토큰 DB 조회 | `TokenRevocationRegistry` — 인메모리 jti 집합, 필터 O(1) | `security/TokenRevocationRegistry` |
| 29 | SI 대리 등록이 요청자를 임의 지정 | `POST /tickets`에 `requesterId` 필수, 소속·활성 검증 | `TicketController.create` |
| 30 | 승인자 워크플로 부재 | 관리자 = 승인자. `RESOLVED→CLOSED`는 `/approve`로만, `/reject {reason}`은 처리중 복귀 + 사유 코멘트 | `TicketController.approve/reject` |
| 31 | 비밀번호 재설정 플로우 없음 | `forgot-password`(계정 존재 노출 안 함) → 이메일 토큰(SHA-256 해시, 30분, 이전 무효화) → `reset-password`. 재설정·변경 시 리프레시 토큰 전부 폐기 | `feature/auth/PasswordService`, `V4` |
| 32 | 문서 첨부 UI 없음 | `DocEditView` 첨부 섹션. 문서 첨부는 공유받은 고객사만 다운로드, 업로드/삭제는 SI만 | `components/AttachmentSection.vue` |
| 33 | 감사 로그 화면 없음 | `audit_log` 테이블(V5) — 로그인·로그아웃·비밀번호·계정·오프보딩·문서 공개범위. 인증 이벤트는 REQUIRES_NEW로 실패 요청도 기록. `/audit` 화면(관리자) | `feature/audit/AuditService` |
| 34 | 문서 본문이 평문 textarea | TipTap 에디터. 저장 시 서버 `HtmlSanitizer`(OWASP)로 허용 태그만 — script·on*·`javascript:` 제거 | `common/HtmlSanitizer.java` |
| 35 | 알림이 30초 폴링 | `GET /api/notifications/stream` SSE — fetch 스트리밍 + 지수 백오프. 폴링은 120초 백스톱 | `feature/notification/SseHub.java` |
| 36 | 리포트·감사 데이터 내보내기 없음 | 감사·티켓이벤트 CSV(UTF-8 BOM). `GET /api/reports/sla` 준수율(전체·고객사별·카테고리별) + 화면 | `feature/report/ReportController` |
| 37 | 관련 문서가 카테고리 완전일치뿐 | 단계 2 — pgvector 하이브리드 검색(`V7`). e5-small 임베딩 + BM25 RRF. 테넌시 필터 SQL 강제. RAG 답변 초안 | `feature/rag/*` |
| 38 | 분류·우선순위·배정이 각각 따로 | 단계 3 — `TriageService`가 세 규칙 + 유사 티켓 + 담당자 실적 + (선택)LLM을 신뢰도와 함께 종합(`V8`). `SlaRiskService` 사전 경고 | `feature/triage/*` |
| 39 | 테넌시가 애플리케이션 레벨 필터뿐 | 단계 4 — PostgreSQL RLS(`V9`): 비특권 롤 + `FORCE RLS` + `tenant_isolation` 정책. `RlsDataSource`가 요청 컨텍스트로 세션변수 세팅. 첨부는 `BlobStorage` 어댑터, 스케줄러는 ShedLock | `common/tenant/*`, `config/SchedulingConfig` |

### 6.3 예외 처리 (요구사항 5장)

- **E-001** 계약 만료 후에도 기존 열린 티켓 상태 변경 허용
- **E-002** 오프보딩 시 미해결 티켓 있으면 409 차단
- **E-003** SI 직원 비활성화 시 열린 티켓 부서 관리자에게 자동 재배정, 이력 유지
- **E-004** 고객사 담당자 비활성화 — 로그인만 차단, 티켓/이력 유지
- **E-005** system/category 삭제는 `active=false` soft delete
- **E-006** 계약 SLA 변경 시 기존 티켓 `sla_due_at` 소급 변경 안 함
- **E-007** 유효 계약 없으면 티켓 등록 409 차단
- **E-008** 문서 동시수정 → `expectedVersion` 불일치 시 409
- **E-009** 로그인 5회 실패 → 15분 잠금, 만료 시 카운터 리셋
- **E-010** 모든 시각 UTC 저장, 표시 변환은 프론트

### 6.4 티켓 상태 전이 (`Enums.TicketStatus.canTransitionTo`)

```
RECEIVED     → IN_PROGRESS
IN_PROGRESS  → RESOLVED | RECEIVED
RESOLVED     → CLOSED(승인 필요) | IN_PROGRESS(재오픈/반려)
CLOSED       → IN_PROGRESS(재오픈)
```

허용되지 않는 전이는 409 `INVALID_TRANSITION`.
`RESOLVED → CLOSED`는 `/status`로 직접 불가 — 관리자의 `POST /tickets/{id}/approve`로만(409 `APPROVAL_REQUIRED`).

### 6.5 스케줄러 (`@Scheduled` · `test` 프로파일에서 비활성 · ShedLock 분산락)

| 잡 | 주기 | 역할 |
|---|---|---|
| `SlaMonitorJob` | 5분 | 열린 티켓 SLA 임박/초과 스캔 + 8h 관찰창 위험 사전 경고 |
| `ContractStatusJob` | 매일 00:10 UTC + 부팅 시 | 계약 상태 전이 |
| `AuthMaintenanceJob` | 매일 03:30 UTC | 만료 토큰 정리 |
| `SseHeartbeatJob` | 25초 | SSE keep-alive + 죽은 연결 정리 |
| `AnalyticsRefreshJob` | 매일 00:20 UTC | `ticket_resolution_stats` MView 갱신 |
| `RagReconcileJob` | 10분 | 미색인·변경 문서·티켓 벡터 재색인 (`rag.enabled` 시) |

---

## 7. 데이터 분석 · AI 상세 (단계 1~3)

Python 컴포넌트는 [`analytics/README.md`](analytics/README.md).

**단계 1 — 데이터 분석 · 분류**
- **1.1 파이프라인**: `V6__analytics.sql` — `analytics` 스키마 뷰/마트. `/api/analytics/*` + `/analytics` 화면(히트맵·SLA 권장값)
- **1.2 EDA·검정**: Tobi-Bueck/customer-support-tickets(HF, CC-BY-NC-4.0). 큐 ⟂ 우선순위 카이제곱(V=0.28), 큐별 본문길이 Kruskal → `reports/*.md`
- **1.3 분류 모델**: TF-IDF(word 1–2gram + char_wb 3–5gram) + LinearSVC(calibrated). macro-F1 규칙 0.18 → **0.49**

**단계 2 — RAG 추천** (`smartdesk.rag.enabled=true` + `/embed` 서비스)
- **2.1 색인**: pgvector `embedding`(384차원, HNSW). 문서 + 종료 티켓 청킹·임베딩(multilingual-e5-small). `@TransactionalEventListener` AFTER_COMMIT + 10분 재조정. 동시 색인은 `pg_advisory_xact_lock`으로 직렬화
- **2.2 하이브리드 검색**: 벡터(`<=>`) + BM25(`ts_rank`) → RRF 융합. 테넌시 필터 SQL 강제
- **2.3 답변 초안**: 검색 문서를 번호 컨텍스트로 LLM 초안, `[n]` 출처 인용 강제. `RAG_LLM_PROVIDER=anthropic` + `ANTHROPIC_API_KEY`로 활성

**단계 3 — AI 에이전트** (`feature/triage/`)
- **3.1 지능형 트리아지**: `TriageService` 종합 → 카테고리·우선순위·담당자·신뢰도. 신규 티켓 자동 적용, 저신뢰 시 관리자 검토
- **3.2 SLA 위반 예측**: `SlaRiskService` 휴리스틱(경과율·부하·카테고리 p90·재오픈). 구조는 ML 교체 가능
- **3.3 오케스트레이션**: 순수 파이프라인(LangGraph 미사용). `TriageEvalTest` 회귀 평가셋

---

## 8. 배포 / 운영

VM 1대 + Compose 기준 상세는 **[docs/DEPLOY.md](docs/DEPLOY.md)**.

```bash
docker compose -f docker-compose.prod.yml up -d --build          # + --profile ml
```

`SPRING_PROFILES_ACTIVE=prod` + [application-prod.yml](backend/src/main/resources/application-prod.yml). 필수 환경변수:

| 변수 | 설명 |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL (pgvector 필요) |
| `JWT_SECRET` | Base64 32바이트+. **미설정 시 부팅 거부** |
| `SMARTDESK_CORS_ALLOWED_ORIGINS`, `PASSWORD_RESET_URL_BASE` | 공개 도메인 |
| `SMARTDESK_ADMIN_PASSWORD` | (선택) admin 재해싱 |
| `SMARTDESK_STORAGE_TYPE=s3` + `SMARTDESK_STORAGE_S3_*` | 다중 인스턴스 시 |

- **RLS**: 마이그레이션은 소유자, 앱 커넥션은 `smartdesk_app` 비특권 롤. `V9`가 롤 생성에 `CREATEROLE` 필요
- **ShedLock**: 스케줄러가 다중 인스턴스에서도 1회 (`shedlock` 테이블)
- **DemoAccountGuard**: prod 첫 부팅에 데모 계정 자동 비활성화
- TLS는 리버스 프록시에서 종료 · prod 로그는 JSON(logstash-logback-encoder)

**관측성** — `/actuator/health/{liveness,readiness}`(k8s 프로브), `/actuator/prometheus`(스크레이프),
응답 헤더 `X-Request-Id`(MDC로 전 로그 + 500 응답 `ref`와 동일). actuator는 운영에서 네트워크로 제한할 것.

---

## 9. 로드맵

`docs/ROADMAP.md` — 단계별 상세 + 아키텍처 진화.

- 단계 0.5 ✅ · 1 분석·분류 ✅ · 2 RAG ✅ · 3 트리아지·SLA 예측 ✅ · 4 핵심(RLS·S3·ShedLock·배포) ✅
- **단계 4 잔여**: K8s/Helm, 이미지 CD, Kafka 이벤트 버스(outbox), OpenTelemetry — 실제 배포 규모 확정 후
- SLA 예측 휴리스틱 → 학습 모델 · cross-encoder 재순위 · 색인 outbox · 첨부 바이러스 스캔
