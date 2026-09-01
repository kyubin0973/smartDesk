"""데이터셋 다운로드 · 정제 · 운영 DB 적재."""

from __future__ import annotations

import sys
import urllib.request

import pandas as pd

from . import config

TEXT_COLS = ["subject", "body"]
KEEP = ["subject", "body", "answer", "type", "queue", "priority", "language"]
TAG_COLS = [f"tag_{i}" for i in range(1, 9)]


def download() -> None:
    if config.DATASET_CSV.exists():
        print(f"이미 존재: {config.DATASET_CSV} ({config.DATASET_CSV.stat().st_size:,} bytes)")
        return
    print(f"다운로드: {config.DATASET_URL}")
    urllib.request.urlretrieve(config.DATASET_URL, config.DATASET_CSV)  # noqa: S310 (신뢰 URL)
    print(f"저장: {config.DATASET_CSV} ({config.DATASET_CSV.stat().st_size:,} bytes)")


def load_frame(lang: str | None = None) -> pd.DataFrame:
    """정제된 DataFrame. text = subject + body, 빈 텍스트·라벨 제거."""
    lang = lang or config.LANG
    df = pd.read_csv(config.DATASET_CSV)
    for c in TEXT_COLS:
        df[c] = df[c].fillna("").astype(str)
    df["text"] = (df["subject"] + ". " + df["body"]).str.strip()
    df = df[df["text"].str.len() >= 10]
    df = df.dropna(subset=["queue"])
    if lang and lang != "all":
        df = df[df["language"] == lang]
    df["tags"] = df[TAG_COLS].apply(
        lambda r: [t for t in r.tolist() if isinstance(t, str) and t.strip()], axis=1
    )
    return df.reset_index(drop=True)


def load_to_db() -> None:
    """analytics.external_ticket 테이블에 적재 (기존 행 삭제 후 삽입)."""
    from sqlalchemy import create_engine, text

    df = load_frame(lang="all")[KEEP + ["tags"]].copy()
    engine = create_engine(config.DB_URL)
    with engine.begin() as conn:
        conn.execute(text("TRUNCATE analytics.external_ticket RESTART IDENTITY"))
        rows = df.to_dict("records")
        conn.execute(
            text(
                """
                INSERT INTO analytics.external_ticket
                    (subject, body, answer, type, queue, priority, language, tags)
                VALUES (:subject, :body, :answer, :type, :queue, :priority, :language, :tags)
                """
            ),
            [
                {
                    **{k: (None if pd.isna(r.get(k)) else r.get(k)) for k in KEEP},
                    "tags": r["tags"],
                }
                for r in rows
            ],
        )
    print(f"적재 완료: analytics.external_ticket <- {len(df):,} 행")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "download"
    if cmd == "download":
        download()
    elif cmd == "load-db":
        download()
        load_to_db()
    else:
        sys.exit(f"unknown command: {cmd}")
