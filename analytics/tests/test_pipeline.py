"""빠른 스모크 테스트 — 무거운 학습은 하지 않음 (아티팩트가 있으면 서빙만 확인)."""

from __future__ import annotations

import pytest

from smartdesk_analytics.config import ARTIFACTS_DIR
from smartdesk_analytics.rule_baseline import KEYWORDS, RuleBaseline

ARTIFACT = ARTIFACTS_DIR / "clf.joblib"


def test_rule_baseline_predicts_known_queue():
    rb = RuleBaseline()
    rb.fit(["x"], ["Technical Support"])
    pred = rb.predict([
        "I cannot log in with my VPN account, password reset did not work",
        "I was charged twice on my invoice and need a refund",
    ])
    assert pred[0] == "IT Support"
    assert pred[1] == "Billing and Payments"


def test_keyword_queues_match_dataset_labels():
    expected = {
        "IT Support", "Technical Support", "Product Support", "Billing and Payments",
        "Customer Service", "Returns and Exchanges", "Service Outages and Maintenance",
        "Sales and Pre-Sales", "Human Resources", "General Inquiry",
    }
    assert set(KEYWORDS) == expected


@pytest.mark.skipif(not ARTIFACT.exists(), reason="모델 아티팩트 없음 (train 먼저)")
def test_service_classify_billing():
    from fastapi.testclient import TestClient

    from service.app import app

    c = TestClient(app)
    assert c.get("/health").json()["status"] == "UP"
    r = c.post("/classify", json={
        "subject": "Duplicate charge on my card",
        "body": "I was billed twice for the same subscription invoice, please issue a refund",
    })
    assert r.status_code == 200
    assert r.json()["queue"] == "Billing and Payments"


@pytest.mark.skipif(not ARTIFACT.exists(), reason="모델 아티팩트 없음")
def test_meets_target_gain():
    import json

    m = json.loads((ARTIFACTS_DIR / "metrics.json").read_text())
    assert m["gain_over_rule_pp"] >= 10.0
