# ML 서빙. 빌드 시 분류 모델 학습 + 임베딩 모델 가중치를 이미지에 포함한다
# (데이터셋·가중치는 빌드 중 1회 받아 캐시 → 런타임 네트워크 불필요).
FROM python:3.11-slim AS build
WORKDIR /app
ENV PIP_NO_CACHE_DIR=1 HF_HOME=/opt/hf-cache
COPY requirements.txt .
# CPU 전용 torch 먼저 설치 → sentence-transformers 가 CUDA(수백 MB) 를 끌어오지 않도록
RUN pip install --index-url https://download.pytorch.org/whl/cpu "torch>=2.2" && \
    pip install -r requirements.txt
COPY smartdesk_analytics ./smartdesk_analytics
# 분류 모델 학습(단계 1) + 임베딩 모델 가중치 프리페치(단계 2)
RUN python -m smartdesk_analytics.model.train && \
    python -c "from smartdesk_analytics import embedding; embedding.embed(['warmup'])"

FROM python:3.11-slim
WORKDIR /app
ENV PIP_NO_CACHE_DIR=1 PYTHONUNBUFFERED=1 HF_HOME=/opt/hf-cache \
    HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1
COPY requirements.txt .
RUN pip install --index-url https://download.pytorch.org/whl/cpu "torch>=2.2" && \
    pip install -r requirements.txt && \
    adduser --disabled-password --gecos "" app
COPY smartdesk_analytics ./smartdesk_analytics
COPY service ./service
COPY --from=build /app/model/artifacts ./model/artifacts
COPY --from=build /opt/hf-cache /opt/hf-cache
RUN chown -R app:app /app /opt/hf-cache
USER app
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8000/health').status==200 else 1)"
CMD ["uvicorn", "service.app:app", "--host", "0.0.0.0", "--port", "8000"]
