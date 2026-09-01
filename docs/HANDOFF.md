# SmartDesk 인수인계 (2026-09-01)

> 다음 세션/작업자용. 로드맵 5개 단계(0.5→4 핵심) 완료 상태. 코드보다 이 문서 + `docs/ROADMAP.md` +
> 메모리 파일(`~/.claude/projects/-Users-kimkyubin-private-smartDesk/memory/`)을 먼저 읽으세요.

## 1. 지금 상태

- **레포**: `github.com/kyubin0973/smartDesk`, 브랜치 `main`, **모두 푸시됨, 작업 폴더 깨끗**.
- **완료**: 단계 0(스캐폴드+하드닝) · 0.5(a~h) · 1(데이터분석/분류) · 2(RAG) · 3(트리아지/SLA예측) · 4 핵심(RLS/S3/ShedLock/배포준비)
- **테스트**: 백엔드 **120개 green** (27개 테스트 클래스), 파이썬 4개 green, 프론트 lint+build 통과
- **마이그레이션**: V1~V10
- 최근 커밋: `cd1d344` docs / `9811473` 단계4.2-4.4 / `658b349` 단계4.1 RLS

## 2. 구성

| 디렉터리 | 내용 | 실행 |
|---|---|---|
| `backend/` | Spring Boot 3.3 / Java 21 | `mvn spring-boot:run` (아래 JAVA_HOME 필수) |
| `frontend/` | Vue 3 + Vite | `npm i && npx vite --port 5173 --strictPort` |
| `analytics/` | Python 3.11 — 분석 + TF-IDF 분류 + 임베딩 FastAPI | `.venv` + `make …` |
| `docs/` | ROADMAP · DEPLOY · HANDOFF · 원본 산출물 |

## 3. 빌드 & 실행 — 반드시 지킬 것

```bash
# 백엔드 (JDK 26이 기본이라 Lombok 깨짐 → 21 강제)
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run     # :8080
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test                # Testcontainers(Docker 필요)

# 프런트
cd frontend && npm install && npx vite --port 5173 --strictPort  # :5173, /api→8080 프록시

# 분석/ML (단계 1·2)
cd analytics && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
make train      # 분류 모델 (~1분) — serve 전에 1회
make serve      # FastAPI :8000  (/classify, /embed)

# AI 연동해서 백엔드 띄우기
RAG_ENABLED=true CLASSIFICATION_PROVIDER=ml \
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run
# RAG 답변 초안까지: + RAG_LLM_PROVIDER=anthropic ANTHROPIC_API_KEY=sk-ant-...
```

**DB**: 로컬 Homebrew `postgresql@17` (:5432, `smartdesk` 롤/DB). 단계 4에서 `smartdesk` 에
`CREATEROLE` 부여 완료(`ALTER ROLE smartdesk CREATEROLE`) — V9가 `smartdesk_app` 롤을 만든다.
테스트는 `pgvector/pgvector:pg17` + `localstack` 컨테이너 (Testcontainers).

**주의**: `mvn spring-boot:run` 은 반드시 `backend/` 에서. 서브셸 체이닝 시 `(cd backend && …)`.

**데모 계정**: 비번 `Passw0rd!` — `admin@smartdesk.io`(MANAGER), `infra@/app@/sec@smartdesk.io`(AGENT),
`user@a-corp.com`(client 1), `user@b-corp.com`(client 2). prod 프로파일에선 `DemoAccountGuard` 가 비활성화.

## 4. 단계별 위치 (코드 포인터)

| 단계 | 핵심 파일 |
|---|---|
| 0.5 | `feature/notification/{SseHub,NotificationChannels}`, `feature/report/ReportController`, `components/{RichTextEditor,SessionsCard}.vue`, `common/HtmlSanitizer`, Testcontainers(`support/PgVectorContainer`) |
| 1 | `V6__analytics.sql`, `feature/analytics/*`, `views/AnalyticsView.vue`, `analytics/` (Python), `feature/ticket/classify/{CategorySuggester,Ml…,RuleBased…}` |
| 2 | `V7__vector_search.sql`, `feature/rag/*` (EmbeddingStore·IndexingService·RagSearchService·AnswerDraftService·LlmClient), `analytics/smartdesk_analytics/embedding.py`, TicketDetailView "유사 문서·티켓" |
| 3 | `V8__triage.sql`, `feature/triage/*` (TriageService·AssigneeScorer·SlaRiskService·TriageAdvisor·TriageOnCreateListener), `POST /api/tickets/{id}/triage[/apply]` + `GET /sla-risk` |
| 4 | `V9__row_level_security.sql`·`V10__shedlock.sql`, `common/tenant/*` (TenantContext·TenantContextFilter·RlsDataSource·RlsConfig), `feature/attachment/{BlobStorage,LocalDiskStorage,S3BlobStorage,StorageConfig}`, `config/SchedulingConfig`(ShedLock), `feature/auth/DemoAccountGuard`, `docker-compose.prod.yml`, `docs/DEPLOY.md` |

## 5. 열려있는 이슈 / 기술부채 (우선순위 순)

### 중간
1. **RAG 답변 초안 견고성** — `AnthropicLlmClient` 가 `stop_reason`(refusal/max_tokens) 미확인,
   claude-api 스킬이 권장하는 `fallbacks` 미적용. opus-5 는 adaptive thinking 기본이라
   `maxTokens=1500` 이 빠듯할 수 있음.
2. **분류 모델이 외부 데이터셋 라벨** — HF `queue`(10종)를 `smartdesk.classification.queue-map` 으로
   SmartDesk 카테고리(5종)에 근사. 운영 티켓 라벨 쌓이면 **재학습** 필요 (그러면 map 불필요).
3. **SLA 위반 예측이 휴리스틱** — `SlaRiskService`. 운영 데이터 축적 후 학습 모델로 (구조는 교체 가능하게 됨).
4. **`RagReconcileJob` 10분마다 전체 스캔** — 원본당 `SELECT count(*)` + sha256. 수천 건이면 부담 →
   `indexed_at` watermark 또는 변경분만.

### 낮음
5. BM25 문서 스니펫에 HTML 태그 남음 (`EmbeddingStore.bm25Documents` 의 `left(d.content,600)` 을 stripHtml 안 함).
6. `analytics/reports/*.md` 는 커밋됐지만 PNG 는 gitignore → GitHub 에서 이미지 링크 깨짐.
7. `analytics/model/train.py` `n_jobs=-1` → sklearn FutureWarning. 제거.
8. `TicketDetailView` 가 티켓 로드마다 `/related-documents`(구) + `/ai/.../related`(신) 둘 다 호출.
9. `V7` 의 `vector(384)` 하드코딩 — 임베딩 모델 교체 시 차원 안 맞으면 INSERT 실패(잡히긴 함).
10. `@MockBean` deprecated (Spring Boot 3.4+ `@MockitoBean`) — 3.3.4 는 아직 OK.
11. 테스트 스위트 점점 느려짐 (`@TestPropertySource`/`@MockBean`/localstack 로 컨텍스트·컨테이너 증가).
12. RAG 답변 초안 프롬프트 인젝션 표면 (티켓 본문이 그대로 프롬프트에 들어감 — 담당자 검수가 완화).

### RLS 관련 알아둘 것
- RLS 적용 테이블: `ticket, contract, system_asset, comment, document, document_share` 만.
  `client_user`(로그인 흐름), `embedding/notification/ticket_event/triage_snapshot/audit_log`(내부/횡단) 는 **미적용** (앱 레벨 필터 유지).
- 크로스테넌트 접근이 이제 **404**(기존 403). `TenancyIsolationTest`/`AttachmentTest` 조정됨.
- `smartdesk_app` 롤 생성에 CREATEROLE 필요 → 관리형 DB면 사전 생성 (`docs/DEPLOY.md`).
- 스케줄러/@Async/부팅 스레드는 컨텍스트 미설정 = **SYSTEM**(전체 접근). 새 배치 코드 작성 시 유의.

## 6. 미구현 / 다음 후보

- **단계 4 잔여**: K8s/Helm 차트, GitHub Actions 이미지 레지스트리 push(GHCR), Kafka 이벤트 버스(outbox),
  OpenTelemetry 분산추적 — **실제 배포 규모가 정해지면.**
- **모델 재학습**: 운영 티켓 라벨로 분류 모델 재학습 → `queue-map` 제거.
- cross-encoder 재순위 (RAG 정확도), 첨부 바이러스 스캔 훅.
- 위 5장 이슈 정리 (특히 1, 5, 6).

## 7. 환경 정리 메모

- `analytics/.venv` (1.2G), `analytics/data/tickets-20k.csv` (18M) — 둘 다 gitignore. 재생성 가능.
- Docker 에 **`smartdesk-db-1` (postgres:16, Exited, 빈 DB)** — 예전 세션 잔재. `docker rm smartdesk-db-1` 해도 됨
  (compose 는 이제 `pgvector/pgvector:pg16` 사용). `lecture-*` 컨테이너들은 이 프로젝트와 무관.
- ML 이미지 로컬 빌드 검증됨 (`smartdesk-ml:latest`, ~1GB, 오프라인 동작) — 지금은 삭제된 상태.

## 8. 참고 문서

- `docs/ROADMAP.md` — 단계별 상세 + 아키텍처 진화 + "지금 코드에서 확장을 염두에 둔 지점" 표
- `docs/DEPLOY.md` — VM 배포 절차, RLS 롤, 체크리스트
- `README.md` §3 — "산출물만으로 부족했던 부분" 결정 목록 (#1~39)
- 메모리 `smartdesk-build-setup.md` — 빌드 gotcha (JDK21, Postgres null-param, RLS 롤, TC 버전 등)
