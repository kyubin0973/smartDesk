# SmartDesk 배포 가이드

VM 1대 + Docker Compose 기준. 관리형 PostgreSQL·K8s 는 규모가 커지면 (로드맵 단계 4 표 참고).

## 1. 사전 준비

- Docker + Docker Compose
- 도메인 + TLS 리버스 프록시 (Caddy 권장)
- PostgreSQL: compose 의 `db` 서비스(간단) 또는 관리형(RDS/Cloud SQL/Supabase — **pgvector 지원 필수**)

### DB 롤 (RLS)

앱은 마이그레이션을 **소유자 롤**로 실행하고, 런타임 커넥션은 비특권 롤 `smartdesk_app` 으로
`SET ROLE` 하여 RLS 정책의 적용을 받습니다. `V9` 마이그레이션이 `smartdesk_app` 을 만들려면
마이그레이션 유저에 **CREATEROLE** 권한이 필요합니다:

```sql
ALTER ROLE smartdesk CREATEROLE;      -- 또는 슈퍼유저로 마이그레이션 실행
-- 관리형 DB 라 CREATEROLE 을 못 주면 미리 만들어 두세요:
--   CREATE ROLE smartdesk_app NOLOGIN;
--   GRANT smartdesk_app TO smartdesk;
```

## 2. `.env` (git 에 올리지 말 것)

```bash
DB_PASSWORD=$(openssl rand -base64 24)
JWT_SECRET=$(openssl rand -base64 48)
PUBLIC_ORIGIN=https://desk.example.com
ADMIN_PASSWORD=$(openssl rand -base64 18)     # admin@smartdesk.io 비번을 이 값으로 재설정
# 메일 (선택 — 없으면 비번재설정·알림 메일이 로그로만 나감)
SMTP_HOST=smtp.example.com
SMTP_USER=...
SMTP_PASSWORD=...
# AI (선택)
# RAG_ENABLED=true
# CLASSIFICATION_PROVIDER=ml
# RAG_LLM_PROVIDER=anthropic
# ANTHROPIC_API_KEY=sk-ant-...
```

## 3. 실행

```bash
git clone https://github.com/kyubin0973/smartDesk && cd smartDesk
vi .env
docker compose -f docker-compose.prod.yml up -d --build
# AI 포함: docker compose -f docker-compose.prod.yml --profile ml up -d --build
```

`prod` 프로파일이 필수 환경변수(`JWT_SECRET`, `DB_URL`, `SMARTDESK_CORS_ALLOWED_ORIGINS`,
`PASSWORD_RESET_URL_BASE`)를 요구하고, 없으면 **부팅을 거부**합니다.

## 4. TLS 리버스 프록시 (Caddy 예시)

`/etc/caddy/Caddyfile`:
```
desk.example.com {
    reverse_proxy 127.0.0.1:8080
}
```
frontend nginx 가 `/api` 를 backend 로 프록시하므로 도메인 1개로 끝납니다.

## 5. 배포 직후 체크리스트

- [ ] `admin@smartdesk.io` 로그인 → 비밀번호가 `ADMIN_PASSWORD` 값인지 확인 (로그: `[demo-guard]`)
- [ ] 실제 관리자 계정 생성 후 `admin@smartdesk.io` 비활성화
- [ ] 데모 계정(`infra@`, `app@`, `sec@`, `user@a-corp.com`, `user@b-corp.com`) 자동 비활성화 확인
- [ ] `/actuator/health` → `UP`, `/actuator/prometheus` 는 내부망/방화벽으로 제한
- [ ] 크로스테넌트 격리: 고객사 A 담당자로 다른 고객사 티켓 URL 접근 → 404 (RLS)
- [ ] DB 백업 스케줄 (`pg_dump` cron 또는 관리형 자동 백업)

## 6. 운영 메모

- **단일 인스턴스 기준.** 스케줄러는 ShedLock 으로 다중 인스턴스에서도 1회만 실행되지만,
  첨부파일 로컬 디스크는 인스턴스 간 공유가 안 됩니다 → 다중화하려면 `SMARTDESK_STORAGE_TYPE=s3`.
- 로그는 prod 에서 JSON(Logstash) — 수집기(Loki/ELK)로 보내세요.
- `X-Request-Id` 응답 헤더 = 요청 추적 ID (500 응답의 `ref` 와 동일).
- 이미지 CI: `.github/workflows/ci.yml` 이 빌드까지. 레지스트리 push + 서버 pull 로 CD 확장 가능.
