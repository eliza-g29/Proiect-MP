"""Synthetic demonstration API. No patient identifiers or clinical decisions are stored."""
import hashlib
import json
from typing import Annotated
from fastapi import FastAPI
from pydantic import BaseModel, ConfigDict, Field
from model import VERSION, TRAINING_EXAMPLES, DISCLAIMER, METADATA, demo_response, _short_report

from medications import MODEL, ROOT as MEDICATION_ROOT
if MODEL is None:
    raise RuntimeError("Medication routing model missing. Run: python train_medications.py")
medication_report=json.loads((MEDICATION_ROOT/'artifacts/medication_evaluation.json').read_text())
for filename,key in [('medication_model.json','modelSha256'),('medication_catalog.json','catalogSha256')]:
    if hashlib.sha256((MEDICATION_ROOT/'artifacts'/filename).read_bytes()).hexdigest()!=medication_report[key]:
        raise ValueError("Medication artifact does not match its evaluation report: "+filename)

app = FastAPI(title="Suport decizional demonstrativ", version="0.4.0")


class PatientContext(BaseModel):
    model_config = ConfigDict(extra="forbid")
    ageYears: int | None = Field(default=None, ge=0, le=130)
    allergies: list[Annotated[str, Field(max_length=200)]] = Field(default_factory=list, max_length=100)
    allergiesReviewed: bool = False
    conditions: list[Annotated[str, Field(max_length=8000)]] = Field(default_factory=list, max_length=40)
    currentMedicines: list[Annotated[str, Field(max_length=200)]] = Field(default_factory=list, max_length=40)
    medicationHistory: list[Annotated[str, Field(max_length=200)]] = Field(default_factory=list, max_length=40)
    recentLabResults: list[Annotated[str, Field(max_length=4500)]] = Field(default_factory=list, max_length=20)
    medicinesReviewed: bool = False
    pregnancyStatus: str = Field(default="UNKNOWN", pattern="^(UNKNOWN|PREGNANT_OR_BREASTFEEDING|NOT_APPLICABLE_OR_EXCLUDED)$")
    egfr: float | None = Field(default=None, ge=0, le=200)


class TriageInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    symptoms: str = Field(min_length=3, max_length=4000)
    observations: str = Field(default="", max_length=4000)
    medicalHistory: list[Annotated[str, Field(max_length=2000)]] = Field(default_factory=list, max_length=10)
    patientContext: PatientContext = Field(default_factory=PatientContext)


class PossibleCondition(BaseModel):
    code: str
    label: str
    score: float
    differentials: list[str]
    managementOptions: list[str]
    investigations: list[str]
    referral: str


class MedicationSource(BaseModel):
    title: str
    url: str

class MedicationOption(BaseModel):
    name: str
    indication: str
    status: str
    checks: list[str]
    sources: list[MedicationSource]
    reasons: list[str]
    rankingScore: float


class TriageOutput(BaseModel):
    category: str
    summary: str
    evidence: str
    modelVersion: str
    demoOnly: bool
    insufficientData: bool
    diagnosticSuggestions: list[str]
    therapyOptions: list[str]
    priority: str
    basis: list[str]
    possibleConditions: list[PossibleCondition]
    investigations: list[str]
    referral: str
    confidenceLevel: str
    clarificationQuestions: list[str]
    medicationOptions: list[MedicationOption]
    withheldMedications: list[MedicationOption]
    medicationNote: str


@app.get("/health")
def health():
    return {"status": "ok", "model": VERSION, "demoOnly": True, "trainingExamples": TRAINING_EXAMPLES}


@app.get("/model-info")
def info():
    return {"version": VERSION, "trainingExamples": TRAINING_EXAMPLES,
            "source": "MedFlow AI Dataset 3000; synthetic examples only", "disclaimer": DISCLAIMER,
            "metadata": METADATA,"shortSymptomModel":{"version":_short_report["version"],"counts":_short_report["counts"],"clinicalValidation":False},"medicationRouting": {"version":medication_report["version"],"counts":medication_report["counts"],"scope":medication_report["scope"],"clinicalValidation":False}}


@app.post("/triage", response_model=TriageOutput)
def triage(request: TriageInput):
    return TriageOutput(**demo_response(request.symptoms, request.observations, request.medicalHistory, request.patientContext.model_dump()))
