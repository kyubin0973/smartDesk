# EDA — 티켓 분류 데이터셋 (en)

- 원천: `https://huggingface.co/datasets/Tobi-Bueck/customer-support-tickets/resolve/main/dataset-tickets-multi-lang-4-20k.csv`
- 정제 후 행 수: **11,922**  ·  큐 10종
- 텍스트 길이(문자) 중앙값 388, p90 696

## 큐 분포

| queue                           |   count |   pct |
|:--------------------------------|--------:|------:|
| Technical Support               |    3412 |  28.6 |
| Product Support                 |    2232 |  18.7 |
| Customer Service                |    1859 |  15.6 |
| IT Support                      |    1391 |  11.7 |
| Billing and Payments            |    1301 |  10.9 |
| Returns and Exchanges           |     582 |   4.9 |
| Service Outages and Maintenance |     442 |   3.7 |
| Sales and Pre-Sales             |     330 |   2.8 |
| Human Resources                 |     205 |   1.7 |
| General Inquiry                 |     168 |   1.4 |

최다 클래스 비율 28.6% → 불균형 완만, macro-F1 로 평가 타당.

## 큐 × 우선순위 교차표 (행 비율 %)

| queue                           |   high |   low |   medium |
|:--------------------------------|-------:|------:|---------:|
| Billing and Payments            |   27.9 |  21   |     51.1 |
| Customer Service                |   18.1 |  30.4 |     51.4 |
| General Inquiry                 |   16.7 |  65.5 |     17.9 |
| Human Resources                 |   11.2 |  44.9 |     43.9 |
| IT Support                      |   46.2 |  10.9 |     43   |
| Product Support                 |   31.4 |  18.5 |     50.1 |
| Returns and Exchanges           |   22.3 |  35.1 |     42.6 |
| Sales and Pre-Sales             |   18.8 |  35.2 |     46.1 |
| Service Outages and Maintenance |   69.9 |  13.1 |     17   |
| Technical Support               |   57.9 |  12.2 |     29.9 |

## 큐 × 유형 교차표 (건수)

| queue                           |   Change |   Incident |   Problem |   Request |
|:--------------------------------|---------:|-----------:|----------:|----------:|
| Billing and Payments            |       74 |        302 |       274 |       651 |
| Customer Service                |      152 |        496 |       355 |       856 |
| General Inquiry                 |       42 |         59 |        22 |        45 |
| Human Resources                 |       19 |         96 |        29 |        61 |
| IT Support                      |      248 |        476 |       383 |       284 |
| Product Support                 |      273 |        917 |       524 |       518 |
| Returns and Exchanges           |       69 |        234 |       116 |       163 |
| Sales and Pre-Sales             |       68 |         59 |        50 |       153 |
| Service Outages and Maintenance |      104 |        245 |        25 |        68 |
| Technical Support               |      236 |       1758 |       720 |       698 |

## 그림

![eda_queue.png](eda_queue.png)
![eda_type.png](eda_type.png)
![eda_priority.png](eda_priority.png)
![eda_textlen.png](eda_textlen.png)