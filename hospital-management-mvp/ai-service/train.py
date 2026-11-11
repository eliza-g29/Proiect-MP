"""Train portable synthetic classifiers; validate first, evaluate held-out test last."""
import argparse
from datetime import datetime, timezone
import gzip
import hashlib
import json
from pathlib import Path
import time

from dataset import load_split, verify_dataset
from learning import Tfidf, fit_logistic, classification_metrics
from build_guidance import build_catalog

BASE = Path(__file__).resolve().parent
ARTIFACTS = BASE / "artifacts"
VERSION = "synthetic-medflow-3000-v1"


def evaluate(model, vectorizer, rows, target):
    predicted = [model.predict(vectorizer.transform(row["clinical_input_text"]))[0] for row in rows]
    return classification_metrics([row[target] for row in rows], predicted, model.classes)


def train(epochs=40):
    started = time.perf_counter()
    integrity = verify_dataset()
    train_rows = load_split("train")
    validation_rows = load_split("validation")
    texts = [row["clinical_input_text"] for row in train_rows]
    vectorizer = Tfidf.fit(texts, min_df=2, max_features=12000)
    train_vectors = [vectorizer.transform(text) for text in texts]
    validation_vectors = [vectorizer.transform(row["clinical_input_text"]) for row in validation_rows]
    print(f"Data verified: 2250 train / 375 validation / 375 test; {len(vectorizer.vocabulary)} features", flush=True)
    models = {}
    training = {}
    validation = {}
    for name, target in [("triage", "target_triage_category"), ("syndrome", "target_syndrome_code")]:
        def progress(epoch, loss):
            if epoch == 1 or epoch % 5 == 0:
                print(f"{name}: epoch {epoch}, validation loss={loss:.5f}", flush=True)
        models[name], training[name] = fit_logistic(train_vectors, [row[target] for row in train_rows],
            validation_vectors, [row[target] for row in validation_rows], len(vectorizer.vocabulary),
            epochs=epochs, progress=progress)
        validation[name] = evaluate(models[name], vectorizer, validation_rows, target)
    # No model selection or fitting occurs after opening the test rows for evaluation.
    test_rows = load_split("test")
    test = {name: evaluate(models[name], vectorizer, test_rows, target)
            for name, target in [("triage", "target_triage_category"), ("syndrome", "target_syndrome_code")]}
    # This supplementary view tests the shorter input shape used by the application.
    short_input = [row["symptoms_text"] + ". " + row["clinical_observations"] + ". " + row["chronic_conditions"] for row in test_rows]
    short_test = {name: classification_metrics([row[target] for row in test_rows],
        [models[name].predict(vectorizer.transform(text))[0] for text in short_input], models[name].classes)
        for name, target in [("triage", "target_triage_category"), ("syndrome", "target_syndrome_code")]}
    catalog = {}
    for row in train_rows:
        code = row["target_syndrome_code"]
        entry = {"label": row["target_syndrome_label"], "triage": row["target_triage_category"]}
        if code in catalog and catalog[code] != entry:
            raise ValueError("Inconsistent training-only syndrome catalog")
        catalog[code] = entry
    metadata = {"version": VERSION, "trained_at": datetime.now(timezone.utc).isoformat(),
                "algorithm": "TF-IDF (word unigrams/bigrams, sublinear TF, L2 normalization) + multinomial logistic regression (SGD)",
                "engine": "Python standard library; portable JSON weights", "training_examples": len(train_rows),
                "validation_examples": len(validation_rows), "test_examples": len(test_rows),
                "feature_count": len(vectorizer.vocabulary), "clinical_validation": False, "demo_only": True,
                "data_sha256": integrity["sha256"], "input_features": ["clinical_input_text"],
                "selection": "validation log loss; test never used for fitting or epoch selection"}
    artifact = {"format": "medflow-portable-logistic-v1", "metadata": metadata,
                "vectorizer": vectorizer.to_dict(), "models": {name: model.to_dict() for name, model in models.items()},
                "syndrome_catalog": catalog}
    report = {"metadata": metadata, "integrity": integrity, "training": training,
              "validation": validation, "test": test, "short_api_input_test": short_test,
              "elapsed_seconds": time.perf_counter() - started,
              "warning": "Synthetic template recognition only. Not clinical accuracy, treatment evidence, or external clinical validation."}
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    # Publish completed files atomically. JSON model loading never executes arbitrary code.
    model_path = ARTIFACTS / "model.json.gz"
    temporary = ARTIFACTS / "model.json.gz.tmp"
    with gzip.open(temporary, "wt", encoding="utf-8") as handle:
        json.dump(artifact, handle, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
    temporary.replace(model_path)
    report["model_sha256"] = hashlib.sha256(model_path.read_bytes()).hexdigest()
    report_tmp = ARTIFACTS / "evaluation.json.tmp"
    report_tmp.write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    report_tmp.replace(ARTIFACTS / "evaluation.json")
    (ARTIFACTS / "guidance_catalog.json").write_text(json.dumps(build_catalog(train_rows, integrity["sha256"]["train"]), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (ARTIFACTS / "dataset_integrity.json").write_text(json.dumps(integrity, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for name in models:
        print(f"{name}: validation macro-F1={validation[name]['macro_f1']:.4f}; test macro-F1={test[name]['macro_f1']:.4f}; short-input test macro-F1={short_test[name]['macro_f1']:.4f}", flush=True)
    print(f"Model and evaluation saved to artifacts/ in {report['elapsed_seconds']:.1f}s. NOT clinically validated.", flush=True)
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs", type=int, default=40)
    args = parser.parse_args()
    if not 1 <= args.epochs <= 200:
        parser.error("epochs must be between 1 and 200")
    train(args.epochs)
