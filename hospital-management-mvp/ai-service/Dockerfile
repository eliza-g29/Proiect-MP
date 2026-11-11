FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY clinical_language.py train_short_symptoms.py medications.py build_medication_catalog.py train_medications.py main.py model.py learning.py dataset.py build_guidance.py train.py evaluate.py benchmark_model.py pytest.ini ./
COPY artifacts ./artifacts
COPY data ./data
COPY tests ./tests
COPY evaluation ./evaluation
RUN python -m pytest -q
EXPOSE 8000
CMD ["uvicorn","main:app","--host","0.0.0.0","--port","8000"]
