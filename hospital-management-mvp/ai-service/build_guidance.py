"""Build presentation metadata from TRAIN targets only; does not fit classifiers."""
import hashlib
import json
from pathlib import Path
from dataset import DATA, load_split

FIELDS = {
    "label": "target_syndrome_label", "category": "target_triage_category",
    "differentials": "target_differential_labels", "managementOptions": "target_management_options",
    "investigations": "target_suggested_investigations", "referral": "target_referral",
    "explanation": "target_explanation",
}
LISTS = {"differentials", "managementOptions", "investigations"}


def build_catalog(rows, train_hash):
    profiles = {}
    for row in rows:
        entry = {key: [part.strip() for part in row[column].split(" | ") if part.strip()]
                 if key in LISTS else row[column] for key, column in FIELDS.items()}
        code = row["target_syndrome_code"]
        if code in profiles and profiles[code] != entry:
            raise ValueError(f"Ambiguous presentation metadata for {code}")
        profiles[code] = entry
    return {"format": "medflow-guidance-v1", "source": "MedFlow 3000 TRAIN targets",
            "train_sha256": train_hash, "demo_only": True,
            "clinical_validation": False, "profiles": profiles}


if __name__ == "__main__":
    train_hash = hashlib.sha256((DATA / "train.csv").read_bytes()).hexdigest()
    catalog = build_catalog(load_split("train"), train_hash)
    path = Path(__file__).resolve().parent / "artifacts" / "guidance_catalog.json"
    path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Catalog de prezentare: {len(catalog['profiles'])} profiluri, exclusiv din TRAIN.")
