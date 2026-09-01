"""통계 검정 → reports/stats.md.

외부 데이터셋(HF): 큐 ⟂ 우선순위 / 큐 ⟂ 유형 독립성(카이제곱), 큐별 본문 길이 차이(Kruskal-Wallis).
운영 DB(있으면): 카테고리별 처리시간 차이(ANOVA/Kruskal), 요청 시간대 ⟂ SLA 위반(카이제곱).
"""

from __future__ import annotations

import pandas as pd
from scipy import stats

from . import config
from .data import download, load_frame


def _chi2(ct: pd.DataFrame) -> str:
    chi2, p, dof, _ = stats.chi2_contingency(ct)
    n = ct.to_numpy().sum()
    k = min(ct.shape) - 1
    cramers_v = (chi2 / (n * k)) ** 0.5 if k else float("nan")
    verdict = "종속(연관 있음)" if p < 0.05 else "독립"
    return f"χ²={chi2:.1f}, dof={dof}, p={p:.2e}, Cramér's V={cramers_v:.3f} → **{verdict}**"


def _external(lines: list[str]) -> None:
    download()
    df = load_frame()
    lines.append("## 외부 데이터셋 검정\n")

    lines.append("### 큐 ⟂ 우선순위 독립성")
    lines.append(_chi2(pd.crosstab(df.queue, df.priority)) + "\n")

    lines.append("### 큐 ⟂ 유형 독립성")
    lines.append(_chi2(pd.crosstab(df.queue, df.type)) + "\n")

    lines.append("### 큐별 본문 길이 차이 (Kruskal-Wallis)")
    groups = [g.text.str.len().to_numpy() for _, g in df.groupby("queue")]
    h, p = stats.kruskal(*groups)
    lines.append(f"H={h:.1f}, p={p:.2e} → **{'차이 있음' if p < 0.05 else '차이 없음'}**\n")


def _operational(lines: list[str]) -> None:
    try:
        from sqlalchemy import create_engine

        engine = create_engine(config.DB_URL)
        m = pd.read_sql("SELECT * FROM analytics.ticket_metrics", engine)
    except Exception as e:  # noqa: BLE001
        lines.append(f"\n## 운영 DB 검정\n\n_건너뜀 ({e.__class__.__name__}): "
                     "운영 DB 미접속 또는 analytics 스키마 없음._\n")
        return

    resolved = m.dropna(subset=["resolution_minutes"])
    lines.append("\n## 운영 DB 검정 (analytics.ticket_metrics)\n")
    lines.append(f"- 해결 티켓 표본 {len(resolved)}건\n")
    if resolved["category_id"].nunique() >= 2 and len(resolved) >= 20:
        groups = [g.resolution_minutes.to_numpy() for _, g in resolved.groupby("category_id")]
        f, p = stats.f_oneway(*groups)
        h, ph = stats.kruskal(*groups)
        lines.append("### 카테고리별 처리시간 차이")
        lines.append(f"ANOVA F={f:.2f}, p={p:.3f}  ·  Kruskal H={h:.2f}, p={ph:.3f} "
                     f"→ **{'차이 있음' if min(p, ph) < 0.05 else '차이 없음/표본 부족'}**\n")
    else:
        lines.append("_카테고리별 처리시간 검정: 표본 부족 (운영 데이터 축적 필요)._\n")

    if resolved["sla_met"].notna().sum() >= 20:
        resolved = resolved.assign(
            hour_bucket=pd.cut(
                resolved["created_hour"], [-1, 8, 12, 18, 24],
                labels=["새벽", "오전", "오후", "야간"],
            ),
            breached=~resolved["sla_met"].astype(bool),
        )
        lines.append("### 요청 시간대 ⟂ SLA 위반")
        lines.append(_chi2(pd.crosstab(resolved.hour_bucket, resolved.breached)) + "\n")


def run() -> None:
    lines = ["# 통계 검정\n"]
    _external(lines)
    _operational(lines)
    out = config.REPORTS_DIR / "stats.md"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"작성: {out}")


if __name__ == "__main__":
    run()
