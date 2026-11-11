"""Load the trained synthetic model; never fit a model during API startup."""
import gzip
import hashlib
import json
import math
from pathlib import Path
from learning import Tfidf, LogisticModel
from medications import medication_options
from clinical_language import clinical_language

ARTIFACT = Path(__file__).resolve().parent / "artifacts" / "model.json.gz"
if not ARTIFACT.is_file():
    raise RuntimeError("Model artifact missing. Run: python train.py")
with gzip.open(ARTIFACT, "rt", encoding="utf-8") as handle:
    _artifact = json.load(handle)
if _artifact.get("format") != "medflow-portable-logistic-v1":
    raise ValueError("Unsupported model artifact format")
METADATA = _artifact["metadata"]
if METADATA.get("demo_only") is not True or METADATA.get("clinical_validation") is not False:
    raise ValueError("Model must explicitly identify itself as an unvalidated demonstration")
vectorizer = Tfidf.from_dict(_artifact["vectorizer"])
triage_model = LogisticModel.from_dict(_artifact["models"]["triage"])
syndrome_model = LogisticModel.from_dict(_artifact["models"]["syndrome"])
CATALOG = _artifact["syndrome_catalog"]
VERSION = METADATA["version"] + "-medications2"
TRAINING_EXAMPLES = METADATA["training_examples"]
DISCLAIMER = "Demo academic — date sintetice."
with (ARTIFACT.parent / "guidance_catalog.json").open(encoding="utf-8") as handle:
    _guidance = json.load(handle)
if _guidance.get("train_sha256") != METADATA["data_sha256"]["train"]:
    raise ValueError("Guidance catalog does not match the model training dataset")
GUIDANCE = _guidance["profiles"]
for classifier in [triage_model, syndrome_model]:
    if len(classifier.weights) != len(classifier.classes) or len(classifier.bias) != len(classifier.classes):
        raise ValueError("Invalid model dimensions")
    if any(len(row) != len(vectorizer.vocabulary) for row in classifier.weights):
        raise ValueError("Weights and vocabulary dimensions do not match")
    if any(not math.isfinite(value) for row in classifier.weights for value in row):
        raise ValueError("Model contains non-finite weights")


with gzip.open(ARTIFACT.parent / "short_symptoms.json.gz", "rt", encoding="utf-8") as handle:
    _short = json.load(handle)
_short_report=json.loads((ARTIFACT.parent / "short_symptoms_evaluation.json").read_text())
if hashlib.sha256((ARTIFACT.parent / "short_symptoms.json.gz").read_bytes()).hexdigest()!=_short_report["modelSha256"] or _short_report["sourceSha256"]!=METADATA["data_sha256"]:
    raise ValueError("Short symptom model does not match the evaluated source dataset")
short_vectorizer = Tfidf.from_dict(_short["vectorizer"])
short_classifier = LogisticModel.from_dict(_short["model"])


def predict_case(text, current_symptoms=None):
    vector = vectorizer.transform(text)
    category, confidence = triage_model.predict(vector)
    classifier = syndrome_model
    if current_symptoms and len(current_symptoms)<350:
        short_vector=short_vectorizer.transform(clinical_language(current_symptoms))
        short_code,short_confidence=short_classifier.predict(short_vector)
        if len(short_vector)>=2 and short_confidence>=0.55:
            classifier=short_classifier
            vector=short_vector
            category=GUIDANCE[short_code]["category"]
            confidence=short_confidence
    probabilities = classifier.probabilities(vector)
    order = sorted(range(len(probabilities)), key=lambda i: probabilities[i], reverse=True)
    strongest = probabilities[order[0]]
    candidates = []
    if len(vector) >= 2:
        for index in order:
            if len(candidates) >= 3 or probabilities[index] < strongest * 0.55:
                break
            code = classifier.classes[index]
            candidates.append({"code": code, "score": probabilities[index], **GUIDANCE[code]})
    insufficient = len(vector) < 2 or confidence < 0.45
    return {"category": "UNCLASSIFIED_DEMO" if insufficient else category,
            "confidence": confidence, "knownFeatures": len(vector), "insufficientData": insufficient,
            "candidates": candidates,
            "confidenceLevel": "tentative" if insufficient or strongest < 0.45 else "supported"}


def _display_options(options):
    # Remove application boilerplate, retaining the actual conservative options supplied by the dataset.
    return [text for text in options if not any(phrase in text.lower() for phrase in ["aplicația", "aplicatia", "în aplicație", "in aplicatie"])]


def demo_response(symptoms, observations="", medical_history=None, patient_context=None):
    history = medical_history or []
    description = " ".join((symptoms + " " + observations).split())
    result = predict_case(description, symptoms)
    conditions = []
    for candidate in result["candidates"]:
        conditions.append({"code": candidate["code"], "label": candidate["label"], "score": candidate["score"],
                           "differentials": candidate["differentials"],
                           "managementOptions": _display_options(candidate["managementOptions"]),
                           "investigations": candidate["investigations"], "referral": candidate["referral"]})
    if conditions:
        primary = conditions[0]
        summary = "Simptomele pot fi compatibile cu: " + primary["label"] + "."
        options = primary["managementOptions"]
        investigations = primary["investigations"]
        referral = primary["referral"]
    else:
        summary = "Descrierea nu oferă încă suficiente indicii pentru o ipoteză. Completează simptomele și durata lor."
        options, investigations, referral = [], [], ""
    medications,withheld,medication_note=medication_options(
        conditions[0]["code"] if conditions else None,patient_context,symptoms+" "+observations,
        supported=result["confidenceLevel"]=="supported",urgent=result["category"]=="ATTENTION_FLAG")
    clarification = []
    if result["confidenceLevel"] == "tentative":
        clarification = ["De când au apărut simptomele?", "Ce intensitate au și ce alte manifestări sunt prezente?"]
    return {"category": result["category"], "summary": summary,
            "evidence": f"TF-IDF + regresie logistică; {TRAINING_EXAMPLES} exemple de antrenare; "
                        f"{result['knownFeatures']} caracteristici recunoscute; scor de clasificare: {result['confidence']:.2f}.",
            "medicationOptions":medications,"withheldMedications":withheld,"medicationNote":medication_note,
            "modelVersion": VERSION, "demoOnly": True, "insufficientData": result["insufficientData"],
            "diagnosticSuggestions": [condition["label"] for condition in conditions], "therapyOptions": options,
            "possibleConditions": conditions, "investigations": investigations, "referral": referral,
            "confidenceLevel": result["confidenceLevel"], "clarificationQuestions": clarification,
            "priority": {"GENERAL_REVIEW": "DEMO_GENERAL", "EXTENDED_REVIEW": "DEMO_EXTENDED", "ATTENTION_FLAG": "DEMO_ATTENTION"}.get(result["category"], "NOT_ASSESSED"),
            "basis": ["Simptomele și observațiile introduse", f"{len(history)} note istorice accesibile",
                      f"Context structurat: {len((patient_context or {}).get('allergies', []))} alergii consemnate, {len((patient_context or {}).get('recentLabResults', []))} rezultate de laborator accesibile",
                      "Opțiuni medicamentoase: catalog cu surse NHS/NICE/SmPC și model separat de rutare pe scenarii sintetice.",
                      "Clasificatoare pentru 3 categorii și 30 de profiluri; catalog de opțiuni din setul TRAIN."]}
