"""티켓 분류 서빙 (FastAPI).

Spring `MlCategorySuggester` 가 호출. 모델 아티팩트가 없거나 오류면 Spring 쪽에서 규칙 기반으로 폴백하므로
여기서는 명확한 에러만 반환한다.
"""

from __future__ import annotations

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from smartdesk_analytics.model import predict as _predict

app = FastAPI(title="SmartDesk 티켓 분류", version="1.0")


class ClassifyRequest(BaseModel):
    subject: str = Field(default="", max_length=2000)
    body: str = Field(default="", max_length=20000)


class ClassifyResponse(BaseModel):
    queue: str
    confidence: float | None = None
    model: str | None = None
    lang: str | None = None


@app.get("/health")
def health() -> dict:
    try:
        info = _predict.model_info()
        return {"status": "UP", **info}
    except FileNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e


@app.post("/classify", response_model=ClassifyResponse)
def classify(req: ClassifyRequest) -> ClassifyResponse:
    text = f"{req.subject}. {req.body}".strip()
    if len(text) < 3:
        raise HTTPException(status_code=422, detail="텍스트가 너무 짧습니다.")
    try:
        return ClassifyResponse(**_predict.predict(text))
    except FileNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
