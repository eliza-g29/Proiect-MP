"""Evaluate a frozen artifact. Does not fit, tune, or change model files."""
import argparse
import json
from dataset import load_split, verify_dataset
from learning import classification_metrics
from model import triage_model, syndrome_model, vectorizer, METADATA


def evaluate(split="test"):
    integrity = verify_dataset()
    if integrity["sha256"] != METADATA["data_sha256"]:
        raise ValueError("Dataset differs from the dataset used to train this artifact")
    rows = load_split(split)
    vectors = [vectorizer.transform(row["clinical_input_text"]) for row in rows]
    results = {}
    for name, classifier, target in [("triage", triage_model, "target_triage_category"),
                                     ("syndrome", syndrome_model, "target_syndrome_code")]:
        results[name] = classification_metrics([row[target] for row in rows],
                                               [classifier.predict(vector)[0] for vector in vectors], classifier.classes)
    return {"model": METADATA["version"], "demoOnly": True, "clinicalValidation": False,
            "split": split, "trainingExamples": METADATA["training_examples"],
            "holdoutExamples": len(rows), "accuracy": results["triage"]["accuracy"], "models": results,
            "warning": "All splits share synthetic generation templates. These are not clinical performance estimates."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--split", choices=["validation", "test"], default="test")
    print(json.dumps(evaluate(parser.parse_args().split), ensure_ascii=False, indent=2))
