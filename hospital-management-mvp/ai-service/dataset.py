"""Dataset integrity and provenance checks, without access to other directories."""
from collections import Counter
import csv
import hashlib
import re
from pathlib import Path

DATA = Path(__file__).resolve().parent / "data" / "medflow_3000"
SPLITS = {"train": 2250, "validation": 375, "test": 375}
CATEGORIES = {"GENERAL_REVIEW", "EXTENDED_REVIEW", "ATTENTION_FLAG"}


def load_split(name, directory=DATA):
    if name not in SPLITS:
        raise ValueError("Unknown dataset split")
    with (directory / f"{name}.csv").open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def verify_dataset(directory=DATA):
    all_ids = set()
    all_patients = set()
    all_encounters = set()
    all_inputs = set()
    splits = {}
    hashes = {}
    for name, expected in SPLITS.items():
        rows = load_split(name, directory)
        if len(rows) != expected:
            raise ValueError(f"{name}: expected {expected} examples, got {len(rows)}")
        for row in rows:
            if row["split"] != name or row["target_triage_category"] not in CATEGORIES:
                raise ValueError(f"{name}: invalid split or class")
            if row["target_clinician_review_required"] != "True" or row["label_source"] != "GENERATED_RULE_BASED_SYNTHETIC_NOT_CLINICALLY_VALIDATED":
                raise ValueError("Unexpected provenance or missing human-review requirement")
            if not row["clinical_input_text"].strip() or re.search(r"\b(?:D\d{2}|MF-\d+|PAT-\d+|ENC-\d+|HOSP-\d+)\b", row["clinical_input_text"]):
                raise ValueError("Empty input or identifier/target-code leakage in text")
            normalized = " ".join(row["clinical_input_text"].casefold().split())
            for value, seen, title in [(row["case_id"], all_ids, "case"),
                                      (row["synthetic_patient_id"], all_patients, "patient"),
                                      (row["synthetic_encounter_id"], all_encounters, "encounter"),
                                      (normalized, all_inputs, "input text")]:
                if value in seen:
                    raise ValueError(f"Duplicate {title} within/across splits")
                seen.add(value)
        splits[name] = {"examples": len(rows), "triage": dict(Counter(r["target_triage_category"] for r in rows)),
                        "syndromes": dict(sorted(Counter(r["target_syndrome_code"] for r in rows).items()))}
        hashes[name] = hashlib.sha256((directory / f"{name}.csv").read_bytes()).hexdigest()
    if set(splits["train"]["syndromes"]) != {f"D{i:02}" for i in range(1, 31)}:
        raise ValueError("Expected 30 synthetic syndrome classes")
    for name in SPLITS:
        if set(splits[name]["syndromes"]) != set(splits["train"]["syndromes"]):
            raise ValueError("Split syndrome classes differ")
    return {"records": len(all_ids), "splits": splits, "sha256": hashes,
            "features": ["clinical_input_text"],
            "excluded_features": ["case_id", "split", "synthetic_patient_id", "synthetic_encounter_id",
                                  "synthetic_hospital_id", "label_source", "all target_* fields"],
            "clinical_validation": False,
            "limitation": "All splits use the same 30 synthetic templates, not independent clinical sources."}
