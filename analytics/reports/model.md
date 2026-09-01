# 티켓 큐 분류 모델

- 데이터: 11,922행 (en), 큐 10종, 홀드아웃 20%
- 특징: TF-IDF word(1–2gram) + char_wb(3–5gram)

## macro-F1

| 모델 | macro-F1 |
|---|---|
| rule_baseline | 0.180 |
| tfidf_logreg | 0.428 |
| tfidf_linearsvc | 0.492 |

**tfidf_linearsvc** 채택. 규칙 대비 **+31.2%p** (목표(+10%p) 달성).

## 분류 리포트 (best)

```
                                 precision    recall  f1-score   support

           Billing and Payments      0.840     0.785     0.811       260
               Customer Service      0.428     0.406     0.417       372
                General Inquiry      1.000     0.206     0.341        34
                Human Resources      0.900     0.439     0.590        41
                     IT Support      0.535     0.327     0.406       278
                Product Support      0.483     0.477     0.480       447
          Returns and Exchanges      0.780     0.276     0.408       116
            Sales and Pre-Sales      0.560     0.212     0.308        66
Service Outages and Maintenance      0.745     0.466     0.573        88
              Technical Support      0.484     0.731     0.583       683

                       accuracy                          0.532      2385
                      macro avg      0.676     0.432     0.492      2385
                   weighted avg      0.561     0.532     0.522      2385

```

![혼동행렬](model_confusion.png)