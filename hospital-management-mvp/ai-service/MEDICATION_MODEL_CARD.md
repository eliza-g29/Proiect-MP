# Academic medication routing v1

The existing TF-IDF / logistic classifiers still infer synthetic syndrome/triage profiles. A separate logistic model now ranks source-linked medication options. This is **rule imitation**, not a learned clinical prescribing model. It does not infer clinical efficacy, interactions outside the small catalog, dose, duration, pregnancy suitability, or antibiotic susceptibility.

The 7-substance catalog (`artifacts/medication_catalog.json`) contains paraphrased indications, relevant exclusions and primary-source URLs reviewed on 2026-11-12. It covers paracetamol, cetirizine, sodium alginate, omeprazole, macrogol, topical hydrocortisone and a restricted lower-UTI nitrofurantoin discussion option. Source guidance reflects its original jurisdiction; local product information and local prescribing rules still determine any real prescription. Sources are NHS, NICE, NHS SPS and a product SmPC. No web text was bulk scraped into training examples.

`train_medications.py` creates 21,000 labeled synthetic profile/drug/risk combinations: 14,700 train, 3,150 validation, 3,150 test. All pairs sharing a complete risk-context group belong to one split; exact feature vectors do not overlap across train/test. Labels come from manually encoded catalog rules. Features include indication match, relevant risks and missing-context indicators. No patient identities or database records enter this training dataset. The model is selected on validation loss, then evaluated on the frozen test split. The evaluation report includes data, catalog and model SHA-256 values. A perfect score here measures reproduction of these rules, not medical accuracy or coverage.

Hard exclusion gates run independently of ranking scores. Allergy, pediatric/pregnancy contexts, relevant organ disease, duplicate medicines, a known clopidogrel interaction, possible obstruction, cutaneous infection, or fever/flank symptoms can remove the relevant option. The catalog is intentionally incomplete; these checks are not a comprehensive interaction engine. Unknown age/allergy assessment/current medicines/pregnancy/renal function remain unknown and change the display to “Necesită verificări”. An empty allergy table does not prove absence of allergies. Past prescriptions have no active/end-date field, so they are transmitted as history, never silently considered current.

Medication options are withheld for tentative or urgent classifications and selected explicit alarm symptoms. No prescription is written by AI; its output remains in the consultation for clinician review. No dose or schedule is generated. Historical notes no longer enter the current-symptom classification vector, preventing old diagnoses from overwhelming the new presentation. Accessible structured context is stored with the analysis for inspection. Backend queries retain hospital, patient, encounter-access and temporal boundaries.

Reproduce locally inside `ai-service`:

```sh
.venv/bin/python build_medication_catalog.py
.venv/bin/python train_medications.py
.venv/bin/python -m unittest discover -s tests -p 'test_medications.py' -v
```

The database history fixtures use only the existing MedFlow TRAIN split. They are independent of the medication-routing train/test split and are not clinical validation samples.

## Symptom wording update — medications2

`train_short_symptoms.py` trains an additional 30-profile TF-IDF/logistic classifier using only `symptoms_text` from the existing 2250/375/375 splits. Its frozen artifact/report are `short_symptoms.json.gz` and `short_symptoms_evaluation.json`. The original full-record classifier and its weights are preserved. At inference the short classifier is used only for short current symptom descriptions with at least two known features and a score of at least 0.55; otherwise the existing model remains in use. These scores are not clinical probabilities.

`clinical_language.py` maps selected common Romanian expressions to terms already present in TRAIN (for example everyday urinary/reflux terminology). It does not generate new clinical treatment labels. The test report separately counts symptom texts absent from training: patient/case identities remain disjoint, but templates may repeat. This is not external clinical validation.

The original allergy/organ-disease/interaction/urgency exclusions still apply. A recognized ordinary rhinitis, reflux or tension-headache description can now show source-linked named options. Insufficient evidence, priorities, a profile outside the small formulary or exclusion of all options results in an explicit explanation in the consultation. Old saved results are identified separately and require regeneration. Runtime checks ensure that the evaluated short classifier matches its frozen report and source dataset. No dose or prescription is generated automatically.
