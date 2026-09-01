"""경로 · DB 접속 설정. 값은 모두 환경변수로 오버라이드 가능."""

from __future__ import annotations

import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = ROOT / "data"
REPORTS_DIR = ROOT / "reports"
ARTIFACTS_DIR = ROOT / "model" / "artifacts"

# 외부 데이터셋 — Tobi-Bueck/customer-support-tickets (HuggingFace, CC-BY-NC-4.0)
# Kaggle "Customer IT Support - Ticket Dataset" 와 동일 데이터. 저자 미러라 인증 불필요.
DATASET_URL = os.environ.get(
    "SMARTDESK_DATASET_URL",
    "https://huggingface.co/datasets/Tobi-Bueck/customer-support-tickets/resolve/main/dataset-tickets-multi-lang-4-20k.csv",
)
DATASET_CSV = DATA_DIR / "tickets-20k.csv"

# 분류 대상 언어 (기본: 영어). "de" 또는 "all" 도 가능.
LANG = os.environ.get("SMARTDESK_LANG", "en")

# 운영 DB (analytics 스키마 조회 · external_ticket 적재)
DB_URL = os.environ.get(
    "SMARTDESK_DB_URL", "postgresql+psycopg2://smartdesk:smartdesk@localhost:5432/smartdesk"
)

for d in (DATA_DIR, REPORTS_DIR, ARTIFACTS_DIR):
    d.mkdir(parents=True, exist_ok=True)
