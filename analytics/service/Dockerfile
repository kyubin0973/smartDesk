# 티켓 분류 서빙. 빌드 시 모델을 학습해 이미지에 포함한다 (데이터셋은 빌드 중 1회 다운로드).
FROM python:3.11-slim AS build
WORKDIR /app
ENV PIP_NO_CACHE_DIR=1
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY smartdesk_analytics ./smartdesk_analytics
RUN python -m smartdesk_analytics.model.train

FROM python:3.11-slim
WORKDIR /app
ENV PIP_NO_CACHE_DIR=1 PYTHONUNBUFFERED=1
COPY requirements.txt .
RUN pip install -r requirements.txt && \
    adduser --disabled-password --gecos "" app
COPY smartdesk_analytics ./smartdesk_analytics
COPY service ./service
COPY --from=build /app/model/artifacts ./model/artifacts
USER app
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8000/health').status==200 else 1)"
CMD ["uvicorn", "service.app:app", "--host", "0.0.0.0", "--port", "8000"]
