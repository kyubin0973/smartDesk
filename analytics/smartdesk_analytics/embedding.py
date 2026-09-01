"""문장 임베딩 — multilingual-e5-small (384차원, 한국어 지원).

e5 계열은 입력에 접두사가 필요하다: 질의="query: ...", 문서="passage: ...".
"""

from __future__ import annotations

import functools
import os

MODEL_NAME = os.environ.get("SMARTDESK_EMBED_MODEL", "intfloat/multilingual-e5-small")
DIM = 384


@functools.lru_cache(maxsize=1)
def _model():
    from sentence_transformers import SentenceTransformer

    return SentenceTransformer(MODEL_NAME)


def embed(texts: list[str], kind: str = "passage") -> list[list[float]]:
    prefix = "query: " if kind == "query" else "passage: "
    prepared = [prefix + (t or "").strip() for t in texts]
    vecs = _model().encode(prepared, normalize_embeddings=True, batch_size=32)
    return [v.tolist() for v in vecs]


def model_name() -> str:
    return MODEL_NAME
