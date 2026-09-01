"""EDA — 큐·유형·우선순위 분포, 텍스트 길이, 큐×우선순위 교차표.

`reports/eda.md` + PNG 그림 생성. 데이터가 SLA/처리시간을 담고 있지 않으므로
(외부 데이터셋 한계) 처리시간 분석은 운영 DB 의 analytics.ticket_metrics 를 별도로 본다.
"""

from __future__ import annotations

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd

from . import config
from .data import download, load_frame


def _bar(series: pd.Series, title: str, fname: str, rotate: int = 30) -> str:
    ax = series.plot(kind="bar", color="#4f46e5", figsize=(8, 4))
    ax.set_title(title)
    ax.set_ylabel("count")
    plt.xticks(rotation=rotate, ha="right")
    plt.tight_layout()
    path = config.REPORTS_DIR / fname
    plt.savefig(path, dpi=110)
    plt.close()
    return fname


def run() -> None:
    download()
    df = load_frame()
    lines: list[str] = []
    figs: list[str] = []

    lines.append(f"# EDA — 티켓 분류 데이터셋 ({config.LANG})\n")
    lines.append(f"- 원천: `{config.DATASET_URL}`")
    lines.append(f"- 정제 후 행 수: **{len(df):,}**  ·  큐 {df.queue.nunique()}종")
    lines.append(f"- 텍스트 길이(문자) 중앙값 {int(df.text.str.len().median())}, "
                 f"p90 {int(df.text.str.len().quantile(0.9))}\n")

    figs.append(_bar(df.queue.value_counts(), "Tickets by queue", "eda_queue.png"))
    figs.append(_bar(df.type.value_counts(), "Tickets by type", "eda_type.png", rotate=0))
    figs.append(_bar(df.priority.value_counts(), "Tickets by priority", "eda_priority.png", rotate=0))

    lines.append("## 큐 분포\n")
    vc = df.queue.value_counts()
    lines.append((vc.to_frame("count").assign(pct=(vc / len(df) * 100).round(1))).to_markdown())
    lines.append("\n최다 클래스 비율 "
                 f"{vc.iloc[0] / len(df) * 100:.1f}% → 불균형 완만, macro-F1 로 평가 타당.\n")

    lines.append("## 큐 × 우선순위 교차표 (행 비율 %)\n")
    ct = pd.crosstab(df.queue, df.priority, normalize="index").mul(100).round(1)
    lines.append(ct.to_markdown())

    lines.append("\n## 큐 × 유형 교차표 (건수)\n")
    lines.append(pd.crosstab(df.queue, df.type).to_markdown())

    # 텍스트 길이 히스토그램
    ax = df.text.str.len().clip(upper=2000).plot(kind="hist", bins=40, color="#4f46e5", figsize=(8, 4))
    ax.set_title("Body length distribution (chars, clip 2000)")
    plt.tight_layout()
    plt.savefig(config.REPORTS_DIR / "eda_textlen.png", dpi=110)
    plt.close()
    figs.append("eda_textlen.png")

    lines.append("\n## 그림\n")
    for f in figs:
        lines.append(f"![{f}]({f})")

    out = config.REPORTS_DIR / "eda.md"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"작성: {out}")
    for f in figs:
        print(f"  - reports/{f}")


if __name__ == "__main__":
    run()
