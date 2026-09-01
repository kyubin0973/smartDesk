# SmartDesk — AI 기반 SI 고객사 IT 지원 플랫폼

4개 산출물(요구사항정의서 · 데이터모델링(ERD) · API 명세서 · 화면설계서, 작성자 김규빈 / 울산 2반)을
바탕으로 생성한 **풀 스캐폴드**입니다.

- **백엔드**: Java 21 · Spring Boot 3.3 · Spring Security(JWT) · Spring Data JPA · Flyway · PostgreSQL
- **프론트엔드**: Vue 3 · Vite · Pinia · Vue Router

```
smartDesk/
├─ backend/          Spring Boot REST API (엔티티 16개, 엔드포인트 40여 개)
├─ frontend/         Vue 3 SPA
├─ analytics/        Python — 데이터 분석 + TF-IDF 티켓 분류 모델 + FastAPI 서빙 (단계 1)
├─ docker-compose.yml   PostgreSQL + (profile app) backend/frontend/ml
└─ docs/            원본 산출물 PDF/XLSX + ROADMAP.md
```

---

## 1. 실행 방법

### 사전 준비
| 도구 | 버전 | 비고 |
|---|---|---|
| JDK | **21** | Homebrew 기본 JDK 26 은 Lombok 과 충돌. `JAVA_HOME` 을 21 로 지정하세요 |
| Maven | 3.9+ | |
| Node | 20+ | |
| Docker | — | PostgreSQL 구동용 (없으면 로컬 PG 5432 직접 준비) |

### 1) DB 기동

**A. Docker 사용 (기본)**
```bash
docker compose up -d
```

**B. 이미 로컬 PostgreSQL 이 5432 에서 돌고 있는 경우** (이 환경이 여기 해당 — Homebrew `postgresql@17`)
```bash
psql -d postgres -c "CREATE ROLE smartdesk LOGIN PASSWORD 'smartdesk';"
createdb -O smartdesk smartdesk
psql -d smartdesk -c "GRANT ALL ON SCHEMA public TO smartdesk;"
brew install pgvector && psql -d smartdesk -c "CREATE EXTENSION IF NOT EXISTS vector;"   # 단계 2
```
> 이 저장소 세팅 시 위 B 를 이미 실행해 뒀습니다. 백엔드가 `jdbc:postgresql://localhost:5432/smartdesk` 로 접속합니다.
> Docker 로 실행하면 `pgvector/pgvector` 이미지를 쓰므로 별도 설치 불필요.

### 2) 백엔드
```bash
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run
# → http://localhost:8080  (기동 시 Flyway 가 스키마 + 데모 시드 자동 적용)
```

### 3) 프런트엔드
```bash
cd frontend
npm install
npm run dev
# → http://localhost:5173  (/api 요청은 8080 으로 프록시)
```

### 데모 계정 (비밀번호 공통: `Passw0rd!`)
| 구분 | 이메일 | 역할 |
|---|---|---|
| SI 관리자 | `admin@smartdesk.io` | MANAGER |
| SI 담당자 | `infra@smartdesk.io`, `app@smartdesk.io`, `sec@smartdesk.io` | AGENT |
| 고객사 담당자 | `user@a-corp.com` (A고객사), `user@b-corp.com` (B고객사) | CLIENT_USER |

---

## 2. 산출물 → 코드 매핑

### 화면 (화면설계서)
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

### API (API 명세서) — **모든 경로에 `/api` 프리픽스 추가**
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

**확장 전 정지작업으로 추가된 엔드포인트** (`docs/ROADMAP.md` 단계 0):
| 엔드포인트 | 설명 |
|---|---|
| `POST /api/auth/refresh` | 리프레시 토큰으로 액세스 토큰 재발급 (회전) |
| `POST /api/auth/forgot-password`, `/reset-password`, `/change-password` | 비밀번호 재설정(이메일 토큰)·변경 |
| `GET /api/users`, `POST /api/users`, `PATCH /api/users/{id}/deactivate` | SI 직원 목록/생성(관리자)/비활성화(+열린 티켓 재배정, REQ-E-003) |
| `GET/POST /api/clients/{id}/users`, `PATCH /api/client-users/{id}/deactivate` | 고객사 담당자 계정 발급/비활성화 (REQ-F-006 온보딩, REQ-E-004) |
| `GET/PATCH /api/notifications`, `/api/notifications/{id}/read`, `/read-all` | 인앱 알림 |
| `GET/POST/GET/DELETE /api/attachments` | 티켓/문서 첨부파일 (로컬 디스크) |
| `GET /api/tickets/{id}/related-documents` | 동일 카테고리 지식문서 (RAG 규칙 기반 전신) |
| `PUT /api/tickets/{id}/priority` | 우선순위 수동 조정 |
| `GET /api/audit`, `/api/audit/ticket-events` (+ `/export`) | 감사 로그 조회·CSV (관리자, REQ-F-014) |
| `GET /api/auth/sessions`, `DELETE /api/auth/sessions/{id}`, `DELETE /api/auth/sessions` | 로그인 세션 목록·개별/전체 종료 (0.5-g) |
| `GET /api/notifications/stream` | 실시간 알림 SSE (0.5-b) |
| `GET /api/reports/sla` (+ `/export`) | SLA 준수율 리포트 (관리자, 0.5-d) |
| `GET /api/analytics/*` | 운영 분석 마트 (관리자, 단계 1.1) |
| `POST /api/ai/tickets/{id}/related` · `/answer-draft` | 유사 문서·티켓 추천 · RAG 답변 초안 (단계 2) |
| `GET /api/ai/rag/status` · `POST /api/ai/rag/reindex` | 벡터 색인 상태·재색인 (관리자, 단계 2.1) |

### 데이터 (ERD)
`backend/src/main/resources/db/migration/V1__init.sql` 가 ERD 테이블 정의서를 그대로 반영.
`user` → `app_user`, `system` → `system_asset` (SQL 예약어 회피).

---

## 3. "산출물만으로는 부족했던 부분" — 채운 결정 목록

산출물은 요구사항 ID로 잘 교차 연결돼 있어 스캐폴드 생성은 가능했지만,
아래 항목은 명세에 없어 **합리적 기본값으로 구현**했습니다. 실제 확정값이 정해지면 교체하세요.

| # | 공백 | 적용한 결정 | 위치 |
|---|---|---|---|
| 1 | 인증 방식 미지정 | JWT 액세스(HS256, 1h) + **리프레시 토큰(7d, 원자적 회전 — `revokeIfActive`)** + **로그아웃 시 액세스 토큰 폐기(jti blacklist)** | `security/JwtService`, `feature/auth/AuthService` |
| 2 | API 응답 스키마 없음 | 컨트롤러별 record DTO. 오류는 `{timestamp,status,code,message,path}` 통일. 500 은 세부정보 숨기고 ref id 로그 | `common/GlobalExceptionHandler` |
| 3 | 목록 페이지네이션 규격 없음 | 오프셋 `?page=&size=`, 응답 `PageResponse{content,page,size,totalElements,totalPages}` | `common/PageResponse` |
| 4 | 엔드포인트별 RBAC 규칙 없음 | SI 직원 / 관리자 / 고객사 격리를 `CurrentUser` 헬퍼로 강제. 미인증은 JSON 401, 권한없음 JSON 403 | `security/CurrentUser`, `config/SecurityConfig` |
| 5 | `client_user` 계정 생성 API 없음 | **`POST /api/clients/{id}/users` 추가** (관리자, REQ-F-006 온보딩 화면에서 발급) | `feature/client/ClientUserController` |
| 6 | category / department 조회 API 누락 | `GET /api/categories`, `/api/departments`, `/api/users` 추가 | `feature/meta/MetaController`, `UserController` |
| 7 | 카테고리 자동분류(REQ-F-009) 알고리즘 미정 | 키워드 규칙 기반 제안(`RuleBasedCategorySuggester`) + 단계 1 에서 **TF-IDF 분류 모델**(`analytics/`, FastAPI). `provider=ml` 시 ML 호출, 실패·저신뢰 시 규칙 폴백 | `feature/ticket/classify/*`, `analytics/` |
| 8 | 담당자 자동배정(REQ-F-010) 규칙 미정 | `category_routing` + `user_client` 기준, 열린 티켓 최소 담당자 | `AssignmentService` |
| 9 | SLA 계산식 미정 | 기본 `sla_due_at = created_at + sla_resolution_min` (24h). `business-hours-only=true` 시 **영업시간만 카운트** (평일 09-18 Asia/Seoul, 설정 가능) | `SlaService` |
| 10 | SLA 초과 알림 수단 미정 | **`SlaMonitorJob`(5분) → `SlaMonitorService`** — 티켓 단위 처리(1건 실패가 전체 재알림 유발 안 함). **초과 경과 시간별 다단계 에스컬레이션**(L1 담당자 → L2 부서관리자 → L3 전체관리자, 0.5-h). 알림 저장은 `NotificationWriter`의 REQUIRES_NEW 트랜잭션 + 유니크로 중복 방지. 이메일(SMTP)·Slack 채널 어댑터는 유형별 설정(0.5-a) | `feature/ticket/SlaMonitorService`, `feature/notification/NotificationChannels` |
| 11 | 문서 버전 이력 테이블 없음 | `document_version` 스냅샷 + `GET /api/documents/{id}/versions` | `document_version` |
| 12 | REQ-E-008 낙관적 잠금 version 파라미터 없음 | `PUT /documents/{id}` 바디 `expectedVersion`, 불일치 409 | `DocumentController.update` |
| 13 | 문서 다중 고객사 공유 모델 없음 | `document_share` 조인 테이블 | `document_share` |
| 14 | 온보딩/오프보딩 요청 바디 없음 | 온보딩: 시스템 초기 등록. 오프보딩: 미해결 티켓 검사(REQ-E-002) 후 종료 + 자산 비활성화. 데이터 반환·파기 배치는 스텁 | `ContractController` |
| 15 | 담당 고객사(REQ-F-003) 저장 위치 없음 | `user_client` 조인 테이블 | `user_client` |
| 16 | 전문검색(REQ-F-015) 엔진 미정 | 1차는 `LIKE`. **`document.search_tsv`(tsvector) + GIN 인덱스**는 마이그레이션에 추가 완료 — 2단계 하이브리드 검색 전환 대비 | `V3` 마이그레이션 |
| 17 | 비밀번호 해시 | **BCrypt 로 전환** (REQ-N-003 "SHA-256 이상" 충족·강화). 시드 계정 재해싱 | `security/PasswordHasher` |
| 18 | 계정 비활성화/재배정(REQ-E-003·004) | **`PATCH /api/users/{id}/deactivate`** (관리자): 비활성화 + 열린 티켓 자동 재배정 + 토큰 폐기. `PATCH /api/client-users/{id}/deactivate` | `UserController`, `AssignmentService.reassignOpenTickets` |
| 19 | 티켓 우선순위 산정 로직 없음 (항상 MEDIUM) | 키워드 규칙(`PriorityRules`)으로 등록 시 산정 + `PUT /tickets/{id}/priority` 수동 조정 | `feature/ticket/PriorityRules` |
| 20 | 계약 상태 자동 전이 없음 | **`ContractStatusJob`**(매일 + 부팅 시): ACTIVE→EXPIRING→ENDED | `feature/contract/ContractStatusJob` |
| 21 | SLA 준수율이 `updated_at` 기준 (부정확) | `ticket.resolved_at`/`closed_at`/`first_responded_at` 추가, 준수율은 `resolved_at` 기준 | `Ticket.isSlaMet()`, `V3` |
| 22 | 이벤트 로그 없음 | `ticket_event` append-only 테이블 (분석·이벤트 소싱 기반) | `TicketEventService` |
| 23 | 첨부파일 없음 | `attachment` 테이블 + 업/다운로드 API (로컬 디스크, S3 어댑터로 교체 가능) | `feature/attachment/AttachmentController` |
| 24 | 로그인 rate-limit 이메일 단위만 | IP 단위 제한 추가 (`login_attempt` principal_type='IP', 20회/15분). 잠금 만료 시 카운터 리셋 | `AuthService` |
| 25 | ClientController 계약상태를 한글로 반환 (Dashboard 는 enum) | enum name 으로 통일, 프론트에서 현지화 (`labels.js`) | `ClientController` |
| 26 | 티켓 목록 N+1 (행마다 담당자 조회) | 담당자·코멘트 작성자 이름을 `findAllById` 배치 조회. `QueryCountTest` 로 회귀 방지 | `TicketController` |
| 27 | 문서 검색이 전체 로드 후 메모리 필터 | scope·키워드 조건을 JPQL 쿼리로 내림 (`searchAll`/`searchSharedWith`), 관련문서는 `findByCategoryId` | `DocumentRepo` |
| 28 | 인증 요청마다 폐기 토큰 DB 조회 | `TokenRevocationRegistry` — 인메모리 jti 집합 (로그아웃 즉시 반영 + 5분마다 재로드). 필터는 O(1) | `security/TokenRevocationRegistry` |
| 29 | SI 대리 티켓 등록이 요청자를 임의로 지정 | `POST /tickets` 에 **`requesterId` 필수** (SI 등록 시), 해당 고객사 소속·활성 검증 | `TicketController.create` |
| 30 | 승인자(approver) 워크플로 부재 (화면설계서 "승인대기" 지표) | **관리자 = 승인자.** 해결 → 종료는 `POST /tickets/{id}/approve` (관리자만) 로만. `POST /tickets/{id}/reject` {reason} 은 처리중으로 되돌리고 사유를 코멘트로. 대시보드 `pendingApproval` = RESOLVED 수. 이벤트 `APPROVED`/`REJECTED` (SLA 준수 여부 포함) | `TicketController.approve/reject` |
| 31 | 비밀번호 재설정 플로우 없음 | `POST /api/auth/forgot-password` (계정 존재 노출 안 함) → 이메일 토큰(SHA-256 해시 저장, 30분, 이전 토큰 무효화) → `POST /api/auth/reset-password`. 재설정·변경 시 해당 계정 리프레시 토큰 전부 폐기. `POST /api/auth/change-password` (로그인 상태). 메일은 `EmailSender` 인터페이스(현재 로그) | `feature/auth/PasswordService`, `password_reset_token`(V4) |
| 32 | 문서 첨부 UI 없음 (백엔드만 존재) | `DocEditView` 에 첨부 섹션(SI 업/삭제), 고객사 열람용 `DocDetailView`(읽기전용). 문서 첨부는 **공유받은 고객사만 다운로드**, 업로드/삭제는 SI 만 | `components/AttachmentSection.vue`, `AttachmentController.assertCanAccess` |
| 33 | 감사 로그 조회 화면 없음 (REQ-F-014) | **`audit_log` 테이블**(V5) — 로그인 성공·실패, 로그아웃, 비밀번호 재설정·변경, SI/고객사 계정 생성·비활성화, 계약 오프보딩, 문서 공개범위 변경. 인증 이벤트는 `REQUIRES_NEW` 로 **실패한 요청도 기록**. `/audit` 화면(관리자): "보안·관리" + "티켓 이벤트"(ticket_event) 탭, 액션·행위자·기간 필터 | `feature/audit/AuditService`, `AuditController`, `views/AuditLogView.vue` |
| 34 | 문서 본문이 평문 `textarea` (SCR-DOC-002 "리치텍스트") | TipTap 에디터(`RichTextEditor.vue`, 서식·제목·목록·인용·코드·링크). 저장 시 서버 `HtmlSanitizer`(OWASP java-html-sanitizer) 로 허용 태그만 남김 — script·on\*·`javascript:` 제거. `DocDetailView` 는 sanitize 된 HTML 을 렌더 | `components/RichTextEditor.vue`, `common/HtmlSanitizer.java` |
| 35 | 알림이 30초 폴링 (지연·부하) | `GET /api/notifications/stream` SSE — 새 알림 시 `notification` 이벤트로 즉시 신호, 프런트가 목록 재조회. EventSource 대신 fetch 스트리밍(Authorization 헤더)+지수 백오프 재연결. 폴링은 120초 백스톱으로 축소 | `feature/notification/SseHub.java`, `api/notificationStream.js` |
| 36 | 리포트·감사 데이터 내보내기 없음 | 감사 로그·티켓 이벤트 `?...&` 필터 그대로 `GET /api/audit/export`·`/api/audit/ticket-events/export` CSV(UTF-8 BOM). `GET /api/reports/sla` SLA 준수율(전체·고객사별·카테고리별) + `/reports/sla/export`, `/reports/sla` 화면(관리자) | `feature/report/ReportController`, `common/Csv.java`, `views/SlaReportView.vue` |
| 37 | 관련 문서가 카테고리 완전일치뿐 (REQ-F-015 의미 검색 아님) | 단계 2 — pgvector 하이브리드 검색(`V7`). 지식문서·종료 티켓을 e5-small 임베딩 + BM25 RRF 융합. 테넌시 필터 SQL 강제. `POST /api/ai/tickets/{id}/related`. RAG 답변 초안(`/answer-draft`, 출처 인용). `rag.enabled` 로 옵트인, LLM 은 `ANTHROPIC_API_KEY` 시 활성 | `feature/rag/*`, `analytics/service /embed`, `views/TicketDetailView.vue` |

### 구현한 예외 처리 (요구사항 5장)
- REQ-E-001 계약 만료 후에도 기존 열린 티켓 상태 변경 허용 (`updateStatus` 는 계약 검사 안 함)
- REQ-E-002 오프보딩 시 미해결 티켓 있으면 409 차단
- REQ-E-003 SI 직원 비활성화 시 열린 티켓 부서 관리자에게 자동 재배정, 이력 유지
- REQ-E-004 고객사 담당자 비활성화 — 로그인만 차단, 티켓/이력 유지
- REQ-E-005 system/category 삭제는 `active=false` soft delete
- REQ-E-006 계약 SLA 변경 시 기존 티켓 `sla_due_at` 소급 변경 안 함
- REQ-E-007 유효 계약 없으면 티켓 등록 409 차단
- REQ-E-008 문서 동시수정 → `expectedVersion` 불일치 시 409
- REQ-E-009 로그인 5회 실패 → 15분 잠금 (`login_attempt`), 만료 시 카운터 리셋
- REQ-E-010 모든 시각 UTC 저장, 표시 변환은 프론트

### 티켓 상태 전이 규칙 (`Enums.TicketStatus.canTransitionTo`)
```
RECEIVED → IN_PROGRESS
IN_PROGRESS → RESOLVED | RECEIVED
RESOLVED → CLOSED(승인 필요) | IN_PROGRESS(재오픈/반려)
CLOSED → IN_PROGRESS(재오픈)
```
허용되지 않는 전이는 409 `INVALID_TRANSITION`.
**RESOLVED → CLOSED 는 `/status` 로 직접 불가** — 관리자의 `POST /tickets/{id}/approve` 로만 (409 `APPROVAL_REQUIRED`).

---

## 4. 테스트

DB 는 **Testcontainers** 가 PostgreSQL 17 컨테이너를 자동 기동 (`application-test.yml` 의 `jdbc:tc:` URL).
Docker 만 실행 중이면 되고, 시드는 Flyway 가 넣습니다. 각 통합 테스트는 트랜잭션 롤백으로 격리.
```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test
```
로컬 PostgreSQL 을 대신 쓰려면 (컨테이너 없이):
```bash
psql -d postgres -c "CREATE DATABASE smartdesk_test OWNER smartdesk;"
TEST_DB_URL=jdbc:postgresql://localhost:5432/smartdesk_test TEST_DB_DRIVER=org.postgresql.Driver \
  TEST_DB_USERNAME=smartdesk TEST_DB_PASSWORD=smartdesk \
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test
```
백엔드 총 104개 + 파이썬 4개(`analytics/`). 통합 테스트 DB 는 `pgvector/pgvector:pg17` 컨테이너.

| 테스트 | 검증 |
|---|---|
| `TenancyIsolationTest` | REQ-N-001 고객사 간 격리 (403), 미인증 JSON 401 |
| `AnalyticsTest` | 단계 1.1 분석 마트 API — 관리자 전용, 요약/카테고리 통계/히트맵/SLA 권장값, 마트 갱신 |
| `RagSearchTest` | 단계 2.2 하이브리드 검색 (실제 pgvector) — 유사 문서·티켓 추천, 질의 티켓 제외, **고객사 담당자는 공유 문서만** (SI 내부 문서 차단), 타 고객사 티켓 조회 403, 초안 API 인가 |
| `RagUtilTest` | 단계 2 HTML 제거·청킹·SHA-256·벡터 리터럴 |
| `ClassificationStrategyTest` | 단계 1.3 자동분류 전략 — 규칙 기반 키워드 매칭, ML 서비스 불통 시 규칙 폴백 |
| `TicketLifecycleTest` | 우선순위 산정·SLA 계산·자동분류, 상태전이 검증, 자동배정, 재오픈, priority 엔드포인트, related-docs 스코프 |
| `AuthFlowTest` | 리프레시 회전, 로그아웃 폐기, 5회 실패 잠금, 탭 혼동 |
| `DeactivationTest` | REQ-E-003/004 비활성화 + 열린 티켓 재배정 + 로그인 차단 + 토큰 폐기, 관리자 가드, SI 계정 생성 |
| `AttachmentTest` | 업/다운로드 왕복, 타 고객사 티켓 업로드 차단, 용량 초과, **문서 첨부 — 공유 고객사만 열람·SI만 업로드** |
| `NotificationApiTest` | 목록 스코핑·안읽음 수, 읽음 처리, 타인 알림 접근 차단 |
| `ApprovalTest` | C8 승인/반려 — 관리자만 종료 승인, 직접 CLOSED 차단, 반려 시 사유 코멘트, 대시보드 승인대기 |
| `PasswordResetTest` | C9 재설정 — 계정 노출 안 함, 토큰 1회성·만료·이전 무효화, 세션 폐기, 로그인 상태 변경 |
| `AuditLogTest` | C11 감사 — 관리자 전용, 실패 로그인도 기록(REQUIRES_NEW), 관리 액션 감사, 티켓 이벤트 조회 |
| `SlaEscalationTest` | 미배정 티켓 SLA 초과 → 관리자 에스컬레이션, 재스캔 시 멱등(알림·이벤트 중복 없음) |
| `ContractStatusServiceTest` | 계약 상태 전이 (ENDED/EXPIRING/ACTIVE) |
| `QueryCountTest` | N+1 회귀 방지 — 데이터가 늘어도 목록·이력 쿼리 수 불변 |
| `SlaAndPriorityTest` | 순수 단위 (24h/영업시간 SLA 계산, 우선순위 규칙) |
| `ContextLoadTest` | 빈 배선 + 엔티티 매핑 |

## 5. 스케줄러 (`@Scheduled`, `test` 프로파일에서는 비활성)
| 잡 | 주기 | 역할 |
|---|---|---|
| `SlaMonitorJob` | 5분 | 열린 티켓 SLA 임박(2h)/초과 스캔 → 알림 + 이벤트 |
| `ContractStatusJob` | 매일 00:10 UTC + 부팅 시 | 계약 상태 전이 |
| `AuthMaintenanceJob` | 매일 03:30 UTC | 만료 토큰 정리 |
| `SseHeartbeatJob` | 25초 | 실시간 알림 SSE keep-alive + 죽은 연결 정리 (0.5-b) |
| `AnalyticsRefreshJob` | 매일 00:20 UTC | `analytics.ticket_resolution_stats` materialized view 갱신 (단계 1.1) |
| `RagReconcileJob` | 10분 | 미색인·변경 문서·티켓 벡터 재색인 (단계 2.1, `rag.enabled` 시) |

## 5.5 데이터 분석 · AI (`analytics/` + `feature/rag/`, 단계 1~2)

Python 컴포넌트 상세는 [`analytics/README.md`](analytics/README.md).

```bash
cd analytics && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
make data && make eda && make stats && make train   # 단계 1: reports/*.md
make serve                                           # FastAPI :8000 — POST /classify, /embed
```

**단계 1 — 데이터 분석 · 분류**
- **1.1 파이프라인**: `V6__analytics.sql` — `analytics` 스키마 뷰/마트. `/api/analytics/*` (관리자) + `/analytics` 화면
- **1.2 EDA·검정**: 데이터셋 Tobi-Bueck/customer-support-tickets(HF, CC-BY-NC-4.0). 카이제곱·Kruskal → `reports/*.md`
- **1.3 분류 모델**: TF-IDF + LinearSVC. macro-F1 규칙 0.18 → **0.49 (+31%p)**. `provider=ml` 시 `MlCategorySuggester` 호출, 실패·저신뢰 시 규칙 폴백

**단계 2 — RAG 추천** (`smartdesk.rag.enabled=true` + `/embed` 서비스 필요)
- **2.1 색인**: `V7__vector_search.sql` — pgvector `embedding`(384차원, HNSW). 문서 + 종료 티켓을 청킹·임베딩(multilingual-e5-small). 저장/종료 이벤트 + 10분 재조정
- **2.2 하이브리드 검색**: `POST /api/ai/tickets/{id}/related` — 벡터 + BM25 RRF 융합, 테넌시 필터를 SQL 에서 강제. `TicketDetailView` "유사 문서·티켓"
- **2.3 답변 초안**: `POST /api/ai/tickets/{id}/answer-draft` (SI) — 검색 문서 컨텍스트로 LLM 초안, `[n]` 출처 인용 강제. `RAG_LLM_PROVIDER=anthropic` + `ANTHROPIC_API_KEY` 로 활성 (미설정 시 근거 문서만)

## 6. 배포 / 운영

### 컨테이너로 전체 스택 실행
```bash
docker compose --profile app up --build
# frontend :5173(→nginx:80) · backend :8080 · db :5432
```
- `backend/Dockerfile` — maven(JDK21) 빌드 → JRE21-alpine 런타임, non-root, HEALTHCHECK
- `frontend/Dockerfile` — node 빌드 → nginx (SPA fallback + `/api` → `backend:8080` 프록시)

### 운영 프로파일
`SPRING_PROFILES_ACTIVE=prod` + [application-prod.yml](backend/src/main/resources/application-prod.yml). 필수 환경변수:

| 변수 | 설명 |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 접속 |
| `JWT_SECRET` | Base64 32바이트+. **미설정 시 부팅 거부** |
| `SMARTDESK_CORS_ALLOWED_ORIGINS` | 예: `https://desk.example.com` |
| `ATTACHMENT_DIR` | 다중 인스턴스면 S3/NFS 등 공유 스토리지 (현재 로컬 디스크) |

- TLS 는 리버스 프록시/인그레스에서 종료 (`server.forward-headers-strategy=framework`, `X-Forwarded-*` 신뢰)
- prod 에서 로그는 JSON (logstash-logback-encoder), 그 외엔 사람이 읽는 패턴 + `requestId`

### 관측성
| 엔드포인트 | 용도 |
|---|---|
| `/actuator/health/liveness`, `/readiness` | k8s 프로브 |
| `/actuator/prometheus` | Prometheus 스크레이프 (`http_server_requests_*`, JVM, HikariCP 등) |
| 응답 헤더 `X-Request-Id` | 요청 추적 ID. 들어온 값 이어받거나 생성, MDC 로 모든 로그에 포함. 500 응답의 `ref` 와 동일 |

> `/actuator/prometheus`·`health` 는 인증 없이 접근 가능 — 운영에선 네트워크(서비스 메시/방화벽)로 제한할 것.

### CI
[.github/workflows/ci.yml](.github/workflows/ci.yml) — PR/main push 시:
- backend: `mvn verify` (DB 는 Testcontainers) + 이미지 빌드
- frontend: `npm run lint` (eslint + prettier) + `npm run build` + 이미지 빌드

## 7. 다음 단계 (`docs/ROADMAP.md` 참고)

- 단계 0.5 (a~h) ✅ · 단계 1 (데이터 분석·분류) ✅ · 단계 2 (pgvector RAG 추천·초안) ✅
- **단계 3**: 지능형 트리아지(분류+우선순위+배정 통합), SLA 위반 확률 예측, LangGraph 오케스트레이션
- cross-encoder 재순위로 RAG 정확도 개선, 색인 outbox 테이블(다중 인스턴스)
- PostgreSQL Row-Level Security 로 테넌시 이중 방어, 스토리지 S3 어댑터
