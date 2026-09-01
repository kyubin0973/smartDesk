# SmartDesk 단계 1 — 데이터 분석 · 티켓 분류

`docs/ROADMAP.md` 단계 1. 규칙 기반 자동분류/우선순위를 실데이터로 검증하고, TF-IDF 분류
모델을 학습해 규칙 대비 성능 향상을 측정한다. 모델은 FastAPI 로 서빙하고 백엔드가 호출한다
(다운 시 규칙 기반으로 폴백).

## 데이터셋

**Tobi-Bueck/customer-support-tickets** (HuggingFace, CC-BY-NC-4.0).
Kaggle *"Customer IT Support - Ticket Dataset"* 와 동일 데이터를 저자가 HF 에 올린 미러라
Kaggle 인증 없이 받을 수 있다. 20,000행(EN 11,923 / DE 8,077), 컬럼: `subject, body, answer,
type(Incident/Request/Problem/Change), queue(10종), priority(low/medium/high), language, tag_1..8`.
**분류 대상 = `queue`** (SmartDesk 의 카테고리→부서 라우팅에 대응). 비상업적 연구·학습 용도로만 사용.

## 구성

| 경로 | 내용 |
|---|---|
| `smartdesk_analytics/data.py` | 데이터셋 다운로드 · 정제 · `analytics.external_ticket` 적재 |
| `smartdesk_analytics/eda.py` | 분포·교차표·텍스트길이 → `reports/eda.md` + PNG |
| `smartdesk_analytics/stats_tests.py` | 카이제곱·Kruskal·(운영 DB 있으면) ANOVA → `reports/stats.md` |
| `smartdesk_analytics/rule_baseline.py` | 백엔드 `CategorySuggestionService` 와 같은 방식의 키워드 규칙 베이스라인 |
| `smartdesk_analytics/model/train.py` | TF-IDF(word+char) + LinearSVC/LogReg 학습·평가 → `model/artifacts/`, `reports/model.md` |
| `smartdesk_analytics/model/predict.py` | 아티팩트 로드·예측 |
| `service/app.py` | FastAPI: `POST /classify`, `GET /health` |

## 실행

```bash
cd analytics
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
make data      # 데이터셋 다운로드
make eda       # reports/eda.md
make stats     # reports/stats.md
make train     # 모델 학습 (~1분) → reports/model.md, model/artifacts/clf.joblib
make test
make serve     # http://localhost:8000  (POST /classify {subject, body})
make load-db   # analytics.external_ticket 적재 (운영 DB 필요, V6 마이그레이션 후)
```

DB URL 등은 환경변수로 오버라이드: `SMARTDESK_DB_URL`, `SMARTDESK_LANG`(en/de/all), `SMARTDESK_DATASET_URL`.

## 결과 요약 (EN, 홀드아웃 20%)

| 모델 | macro-F1 |
|---|---|
| 규칙 기반 | ~0.18 |
| TF-IDF + LogReg | ~0.43 |
| **TF-IDF + LinearSVC** | **~0.49** (규칙 대비 +31%p, 목표 +10%p 초과) |

`Billing and Payments`·`Service Outages` 는 F1 0.57~0.81 로 잘 잡고, `IT Support`↔`Technical
Support`↔`Customer Service` 는 의미 중첩으로 recall 이 낮다 → 단계 2(임베딩/하이브리드 검색)에서 개선.

## 백엔드 연동

`smartdesk.classification.provider=ml` 이고 서비스가 헬시하면 백엔드가 `POST /classify` 를
호출해 카테고리를 제안한다. 신뢰도가 `smartdesk.classification.ml-min-confidence` 미만이거나
서비스 오류면 규칙 기반(`RuleBasedCategorySuggester`)으로 폴백한다.
