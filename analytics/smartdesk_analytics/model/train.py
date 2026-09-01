"""티켓 큐(queue) 분류 모델 학습 · 평가.

베이스라인: TF-IDF(1–2gram, char+word) + LinearSVC.
비교군: 규칙 기반(rule_baseline) · LogisticRegression.
평가: 층화 홀드아웃 macro-F1 + 혼동행렬. 목표: 규칙 대비 +10%p.
산출물: model/artifacts/{clf.joblib, metrics.json, confusion.png}, reports/model.md
"""

from __future__ import annotations

import json

import joblib
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from sklearn.calibration import CalibratedClassifierCV
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import ConfusionMatrixDisplay, classification_report, f1_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import FeatureUnion, Pipeline
from sklearn.svm import LinearSVC

from .. import config
from ..data import download, load_frame
from ..rule_baseline import RuleBaseline

RANDOM_STATE = 42


def _features() -> FeatureUnion:
    return FeatureUnion([
        ("word", TfidfVectorizer(sublinear_tf=True, min_df=3, ngram_range=(1, 2),
                                 stop_words="english", max_features=50_000)),
        ("char", TfidfVectorizer(sublinear_tf=True, min_df=3, analyzer="char_wb",
                                 ngram_range=(3, 5), max_features=50_000)),
    ])


def build_pipeline() -> Pipeline:
    # LinearSVC + Calibrated → predict_proba 로 신뢰도 제공 (서빙에서 임계값 폴백)
    svc = CalibratedClassifierCV(LinearSVC(C=1.0, class_weight="balanced"), cv=3)
    return Pipeline([("features", _features()), ("clf", svc)])


def run() -> None:
    download()
    df = load_frame()
    X = df["text"].to_numpy()
    y = df["queue"].to_numpy()
    X_tr, X_te, y_tr, y_te = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=RANDOM_STATE
    )

    results: dict[str, dict] = {}

    rule = RuleBaseline().fit(X_tr, y_tr)
    rule_pred = rule.predict(X_te)
    results["rule_baseline"] = {"macro_f1": f1_score(y_te, rule_pred, average="macro")}

    logreg = Pipeline([("features", _features()),
                       ("clf", LogisticRegression(max_iter=2000, class_weight="balanced",
                                                  n_jobs=-1))]).fit(X_tr, y_tr)
    results["tfidf_logreg"] = {"macro_f1": f1_score(y_te, logreg.predict(X_te), average="macro")}

    pipe = build_pipeline().fit(X_tr, y_tr)
    svc_pred = pipe.predict(X_te)
    results["tfidf_linearsvc"] = {"macro_f1": f1_score(y_te, svc_pred, average="macro")}

    best_name = max(results, key=lambda k: results[k]["macro_f1"])
    gain = results[best_name]["macro_f1"] - results["rule_baseline"]["macro_f1"]

    # 최종 모델은 전체 데이터로 재학습 후 저장
    final = build_pipeline().fit(X, y) if best_name != "tfidf_logreg" else logreg.fit(X, y)
    joblib.dump({"pipeline": final, "labels": sorted(set(y)), "lang": config.LANG,
                 "model": best_name}, config.ARTIFACTS_DIR / "clf.joblib")

    metrics = {
        "n_train": len(X_tr), "n_test": len(X_te), "classes": sorted(set(y)),
        "results": results, "best": best_name, "gain_over_rule_pp": round(gain * 100, 2),
        "target_met": gain >= 0.10,
    }
    (config.ARTIFACTS_DIR / "metrics.json").write_text(json.dumps(metrics, indent=2, ensure_ascii=False))

    ConfusionMatrixDisplay.from_predictions(
        y_te, svc_pred, xticks_rotation="vertical", normalize="true", values_format=".2f"
    )
    plt.title(f"{best_name} - normalized confusion matrix")
    plt.tight_layout()
    plt.savefig(config.REPORTS_DIR / "model_confusion.png", dpi=110)
    plt.close()

    lines = [
        "# 티켓 큐 분류 모델\n",
        f"- 데이터: {len(df):,}행 ({config.LANG}), 큐 {len(set(y))}종, 홀드아웃 20%",
        f"- 특징: TF-IDF word(1–2gram) + char_wb(3–5gram)\n",
        "## macro-F1\n",
        "| 모델 | macro-F1 |",
        "|---|---|",
        *[f"| {k} | {v['macro_f1']:.3f} |" for k, v in sorted(
            results.items(), key=lambda kv: kv[1]["macro_f1"])],
        "",
        f"**{best_name}** 채택. 규칙 대비 **+{gain * 100:.1f}%p** "
        f"({'목표(+10%p) 달성' if gain >= 0.10 else '목표 미달'}).\n",
        "## 분류 리포트 (best)\n",
        "```",
        classification_report(y_te, svc_pred, digits=3),
        "```",
        "",
        "![혼동행렬](model_confusion.png)",
    ]
    (config.REPORTS_DIR / "model.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"best={best_name}  macro-F1={results[best_name]['macro_f1']:.3f}  "
          f"(+{gain * 100:.1f}%p vs rule)")
    print(f"저장: {config.ARTIFACTS_DIR}/clf.joblib, reports/model.md")


if __name__ == "__main__":
    run()
