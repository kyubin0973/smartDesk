"""학습된 파이프라인 로드 · 예측 (서빙에서 사용)."""

from __future__ import annotations

import functools

import joblib

from .. import config

ARTIFACT = config.ARTIFACTS_DIR / "clf.joblib"


@functools.lru_cache(maxsize=1)
def _bundle() -> dict:
    if not ARTIFACT.exists():
        raise FileNotFoundError(f"모델 아티팩트 없음: {ARTIFACT} — `python -m smartdesk_analytics.model.train` 먼저 실행")
    return joblib.load(ARTIFACT)


def predict(text: str) -> dict:
    b = _bundle()
    pipe = b["pipeline"]
    label = str(pipe.predict([text])[0])
    conf = None
    if hasattr(pipe, "predict_proba"):
        proba = pipe.predict_proba([text])[0]
        conf = float(max(proba))
    return {"queue": label, "confidence": conf, "model": b.get("model"), "lang": b.get("lang")}


def model_info() -> dict:
    b = _bundle()
    return {"model": b.get("model"), "lang": b.get("lang"), "labels": b.get("labels")}
