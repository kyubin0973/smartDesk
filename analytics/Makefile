PY ?= .venv/bin/python

.PHONY: setup data eda stats train test serve load-db clean

setup:
	python3 -m venv .venv && .venv/bin/pip install -r requirements.txt

data:
	$(PY) -m smartdesk_analytics.data download

eda:
	$(PY) -m smartdesk_analytics.eda

stats:
	$(PY) -m smartdesk_analytics.stats_tests

train:
	$(PY) -m smartdesk_analytics.model.train

test:
	$(PY) -m pytest -q

serve:
	$(PY) -m uvicorn service.app:app --host 0.0.0.0 --port 8000

load-db:
	$(PY) -m smartdesk_analytics.data load-db

clean:
	rm -rf reports/*.png reports/*.md model/artifacts .pytest_cache
