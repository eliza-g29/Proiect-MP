from fastapi.testclient import TestClient
from main import app

client = TestClient(app)

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["demoOnly"] is True

def test_triage_result_is_clearly_demonstrative():
    response = client.post("/triage", json={"symptoms": "control periodic fara simptome noi"})
    assert response.status_code == 200
    data = response.json()
    assert data["modelVersion"].startswith("synthetic")
    assert data["demoOnly"] is True
    assert "possibleConditions" in data

def test_validation():
    response = client.post("/triage", json={"symptoms": ""})
    assert response.status_code == 422

def test_unseen_text_signals_insufficient_data():
    response = client.post("/triage", json={"symptoms": "xyzq abcdefgh necunoscut"})
    data = response.json()
    assert data["insufficientData"] is True
    assert data["priority"] == "NOT_ASSESSED"
    assert data["diagnosticSuggestions"] == []
    assert data["therapyOptions"] == []

def test_history_is_accepted_without_patient_identifiers():
    response = client.post("/triage", json={"symptoms": "control periodic fara simptome noi", "medicalHistory": ["control preventiv"]})
    assert response.status_code == 200
    assert response.json()["demoOnly"] is True
    assert any("istorice" in item for item in response.json()["basis"])

def test_dataset_splits_are_separate_and_metrics_are_not_clinical():
    from evaluate import evaluate
    result = evaluate()
    assert result["clinicalValidation"] is False
    assert result["holdoutExamples"] == 375
    assert 0 <= result["accuracy"] <= 1


def test_patient_identifiers_are_not_accepted_as_model_features():
    response = client.post("/triage", json={"symptoms": "control periodic", "patientId": 123})
    assert response.status_code == 422


def test_known_case_returns_readable_conditions_and_management_options():
    from dataset import load_split
    case = load_split("test")[0]
    response = client.post("/triage", json={"symptoms": case["clinical_input_text"]})
    assert response.status_code == 200
    data = response.json()
    assert data["possibleConditions"]
    assert data["therapyOptions"]
    assert data["investigations"]
    assert "compatibile" in data["summary"]

def test_medication_context_contract_without_identifiers():
    from dataset import load_split
    case=next(r for r in load_split('train') if r['target_syndrome_code']=='D02')
    response=client.post('/triage',json={'symptoms':case['clinical_input_text'],'patientContext':{'ageYears':35,'allergies':['Cetirizină'],'allergiesReviewed':True}})
    assert response.status_code==200
    data=response.json()
    assert 'medicationOptions' in data
    assert all(m['name']!='Cetirizină' for m in data['medicationOptions'])
    assert any(m['name']=='Cetirizină' for m in data['withheldMedications'])

def test_current_medications_are_present_for_colloquial_allergy_symptoms():
    response=client.post('/triage',json={'symptoms':'stranut, imi curge nasul si ma mananca nasul'})
    assert response.status_code==200
    data=response.json()
    assert data['modelVersion'].endswith('medications2')
    assert any(m['name']=='Cetirizină' for m in data['medicationOptions'])
    assert data['medicationNote']
