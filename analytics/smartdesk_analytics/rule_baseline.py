"""규칙 기반 분류 베이스라인.

백엔드 `CategorySuggestionService` 와 같은 방식: 큐(queue)별 키워드 목록을 두고,
텍스트에 등장한 키워드 수가 가장 많은 큐를 예측. 매칭이 전혀 없으면 최빈 큐로.
TF-IDF 모델의 macro-F1 향상폭(목표 +10%p)을 재는 기준선.
"""

from __future__ import annotations

import re

import numpy as np

# 데이터셋 큐(10종)별 키워드 — HF customer-support-tickets 의 라벨 체계에 맞춤
KEYWORDS: dict[str, list[str]] = {
    "IT Support": [
        "vpn", "login", "log in", "password", "account", "access", "active directory",
        "network", "wifi", "wi-fi", "laptop", "printer", "outlook", "email client",
        "permission", "mfa", "sso", "device", "workstation",
    ],
    "Technical Support": [
        "error", "bug", "crash", "exception", "stack trace", "api", "endpoint", "500",
        "timeout", "deployment", "server", "database", "query", "integration", "log file",
        "configuration", "ssl", "certificate", "port",
    ],
    "Product Support": [
        "feature", "how do i", "how to", "documentation", "guide", "tutorial", "setting",
        "dashboard", "report", "export", "import", "template", "workflow", "module",
        "does the product", "functionality",
    ],
    "Billing and Payments": [
        "invoice", "billing", "payment", "charge", "charged", "refund", "credit card",
        "subscription", "renewal", "price", "pricing", "receipt", "tax", "overcharge",
    ],
    "Customer Service": [
        "complaint", "feedback", "help", "assistance", "information", "inquiry", "question",
        "contact", "support team", "representative", "unhappy", "dissatisfied",
    ],
    "Returns and Exchanges": [
        "return", "exchange", "replace", "replacement", "defective", "damaged", "wrong item",
        "rma", "send back", "warranty claim",
    ],
    "Service Outages and Maintenance": [
        "outage", "down", "downtime", "unavailable", "maintenance", "not working", "503",
        "cannot access the service", "service is offline", "degraded",
    ],
    "Sales and Pre-Sales": [
        "quote", "pricing plan", "demo", "trial", "purchase", "buy", "upgrade plan",
        "enterprise plan", "contract", "proposal", "interested in", "sales",
    ],
    "Human Resources": [
        "payroll", "onboarding", "employee", "leave request", "vacation", "benefits",
        "hr ", "human resources", "recruitment", "offboarding", "timesheet",
    ],
    "General Inquiry": [
        "general question", "just wondering", "curious", "clarification", "not sure who",
    ],
}


def _norm(text: str) -> str:
    return re.sub(r"\s+", " ", str(text).lower())


class RuleBaseline:
    def __init__(self, fallback: str = "Technical Support"):
        self.fallback = fallback
        self._compiled = {
            q: [re.compile(r"\b" + re.escape(k) + r"\b") for k in kws]
            for q, kws in KEYWORDS.items()
        }

    def fit(self, X, y):  # noqa: N803 - sklearn 호환 시그니처
        # 규칙 기반은 학습 없음. fallback 만 최빈값으로 조정.
        vals, counts = np.unique(list(y), return_counts=True)
        self.fallback = str(vals[counts.argmax()])
        return self

    def predict(self, X):  # noqa: N803
        out = []
        for doc in X:
            norm = _norm(doc)
            best, best_score = self.fallback, 0
            for q, pats in self._compiled.items():
                score = sum(1 for p in pats if p.search(norm))
                if score > best_score:
                    best, best_score = q, score
            out.append(best)
        return np.array(out)
