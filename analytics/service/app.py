"""ML 서빙 (FastAPI).

- POST /classify : 티켓 큐 분류 (단계 1.3). Spring MlCategorySuggester 가 호출.
- POST /embed    : 문장 임베딩 (단계 2.1). Spring RAG 인덱싱·검색이 호출.
아티팩트/모델 오류는 Spring 쪽에서 폴백하므로 여기서는 명확한 에러만 반환한다.
"""

from __future__ import annotations

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from smartdesk_analytics import embedding as _embed
from smartdesk_analytics.model import predict as _predict

from contextlib import asynccontextmanager


@asynccontextmanager
async def lifespan(_app: FastAPI):
    _embed.embed(["warmup"])  # 임베딩 모델 미리 로드 (첫 요청 지연 방지)
    yield


app = FastAPI(title="SmartDesk ML", version="2.0", lifespan=lifespan)


class ClassifyRequest(BaseModel):
    subject: str = Field(default="", max_length=2000)
    body: str = Field(default="", max_length=20000)


class ClassifyResponse(BaseModel):
    queue: str
    confidence: float | None = None
    model: str | None = None
    lang: str | None = None


class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=64)
    kind: str = Field(default="passage", pattern="^(query|passage)$")


class EmbedResponse(BaseModel):
    model: str
    dim: int
    vectors: list[list[float]]


@app.get("/health")
def health() -> dict:
    out: dict = {"status": "UP", "embed_model": _embed.model_name()}
    try:
        out["classifier"] = _predict.model_info()
    except FileNotFoundError:
        out["classifier"] = None
    return out


@app.post("/classify", response_model=ClassifyResponse)
def classify(req: ClassifyRequest) -> ClassifyResponse:
    text = f"{req.subject}. {req.body}".strip()
    if len(text) < 3:
        raise HTTPException(status_code=422, detail="텍스트가 너무 짧습니다.")
    try:
        return ClassifyResponse(**_predict.predict(text))
    except FileNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e


@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest) -> EmbedResponse:
    vectors = _embed.embed(req.texts, kind=req.kind)
    return EmbedResponse(model=_embed.model_name(), dim=_embed.DIM, vectors=vectors)
