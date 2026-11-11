"""Tests requiring only the Python standard library and project-local files."""
import gzip
import hashlib
import json
import unittest
from unittest.mock import patch

from dataset import load_split, verify_dataset
from learning import Tfidf, LogisticModel, classification_metrics, fit_logistic, softmax
from model import ARTIFACT, METADATA, TRAINING_EXAMPLES, demo_response, predict_case, triage_model, syndrome_model, GUIDANCE


class LearningTests(unittest.TestCase):
    def test_softmax_is_stable_for_large_logits(self):
        probabilities = softmax([10000, 10001, 9999])
        self.assertAlmostEqual(sum(probabilities), 1)
        self.assertGreater(probabilities[1], probabilities[0])
        self.assertEqual(probabilities, softmax([0, 1, -1]))

    def test_vocabulary_uses_training_text_only(self):
        vectorizer = Tfidf.fit(["febră tuse", "febra tuse"], min_df=2)
        self.assertIn("febra", vectorizer.vocabulary)
        self.assertEqual(vectorizer.transform("validationonly"), [])
        self.assertEqual(vectorizer.transform("febră"), vectorizer.transform("febra"))

    def test_logistic_training_learns_three_separable_classes(self):
        vectors = [[(0, 1.0)], [(1, 1.0)], [(2, 1.0)]] * 8
        labels = ["a", "b", "c"] * 8
        model, report = fit_logistic(vectors, labels, vectors[:3], labels[:3], 3, epochs=12)
        self.assertEqual([model.predict(v)[0] for v in vectors[:3]], ["a", "b", "c"])
        self.assertGreater(report["best_epoch"], 0)

    def test_model_json_roundtrip_retains_probabilities(self):
        original = LogisticModel(["a", "b", "c"], [[1, 0], [0, 1], [-1, -1]], [0, 0, 0])
        clone = LogisticModel.from_dict(json.loads(json.dumps(original.to_dict())))
        self.assertEqual(original.probabilities([(0, 0.8), (1, 0.2)]), clone.probabilities([(0, 0.8), (1, 0.2)]))

    def test_metrics_include_missing_predictions_without_zero_division(self):
        result = classification_metrics(["a", "b"], ["a", "a"], ["a", "b"])
        self.assertEqual(result["accuracy"], 0.5)
        self.assertEqual(result["per_class"]["b"]["f1"], 0)
        self.assertEqual(result["confusion_matrix"], [[1, 0], [1, 0]])


class DatasetAndArtifactTests(unittest.TestCase):
    def test_dataset_has_disjoint_cases_and_expected_splits(self):
        integrity = verify_dataset()
        self.assertEqual(integrity["records"], 3000)
        self.assertEqual(integrity["sha256"], METADATA["data_sha256"])
        self.assertEqual(integrity["features"], ["clinical_input_text"])

    def test_duplicate_identity_is_rejected(self):
        splits = {name: load_split(name) for name in ["train", "validation", "test"]}
        splits["validation"][0] = {**splits["validation"][0], "synthetic_patient_id": splits["train"][0]["synthetic_patient_id"]}
        with patch("dataset.load_split", side_effect=lambda name, directory: splits[name]):
            with self.assertRaisesRegex(ValueError, "Duplicate patient"):
                verify_dataset()

    def test_artifact_is_frozen_and_matches_its_report(self):
        report = json.loads((ARTIFACT.parent / "evaluation.json").read_text())
        self.assertEqual(hashlib.sha256(ARTIFACT.read_bytes()).hexdigest(), report["model_sha256"])
        self.assertEqual(TRAINING_EXAMPLES, 2250)
        self.assertEqual(len(triage_model.classes), 3)
        self.assertEqual(len(syndrome_model.classes), 30)
        with gzip.open(ARTIFACT, "rt", encoding="utf-8") as handle:
            self.assertEqual(json.load(handle)["metadata"]["input_features"], ["clinical_input_text"])

    def test_unknown_text_does_not_generate_a_profile_or_treatment(self):
        result = demo_response("xyzq abcdefgh necunoscut")
        self.assertTrue(result["insufficientData"])
        self.assertEqual(result["category"], "UNCLASSIFIED_DEMO")
        self.assertEqual(result["diagnosticSuggestions"], [])
        self.assertEqual(result["therapyOptions"], [])

    def test_response_retains_demo_contract_and_human_review_boundary(self):
        row = load_split("test")[0]
        response = demo_response(row["clinical_input_text"], medical_history=["notă fictivă"])
        self.assertTrue(response["demoOnly"])
        self.assertTrue(response["possibleConditions"])
        self.assertEqual(response["therapyOptions"], response["possibleConditions"][0]["managementOptions"])
        self.assertTrue(response["therapyOptions"])
        self.assertIn("compatibile", response["summary"])
        self.assertTrue(response["modelVersion"].startswith(METADATA["version"]))
        self.assertTrue(any("1 note" in text for text in response["basis"]))

    def test_tentative_profiles_remain_visible_for_a_short_recognized_description(self):
        probabilities = [0.8 / 29] * 30
        probabilities[syndrome_model.classes.index("D22")] = 0.2
        with patch("model.vectorizer.transform", return_value=[(i, 0.1) for i in range(11)]), \
             patch.object(triage_model, "predict", return_value=("ATTENTION_FLAG", 0.49)), \
             patch.object(syndrome_model, "probabilities", return_value=probabilities):
            result = predict_case("descriere scurtă")
        self.assertEqual(result["confidenceLevel"], "tentative")
        self.assertEqual(result["candidates"][0]["code"], "D22")

    def test_guidance_is_taken_from_training_metadata_not_invented(self):
        row = load_split("test")[0]
        response = demo_response(row["clinical_input_text"])
        for condition in response["possibleConditions"]:
            source = GUIDANCE[condition["code"]]
            self.assertEqual(condition["label"], source["label"])
            self.assertTrue(set(condition["managementOptions"]) <= set(source["managementOptions"]))
            self.assertEqual(condition["investigations"], source["investigations"])
        self.assertLessEqual(len(response["possibleConditions"]), 3)

    def test_frozen_evaluation_uses_375_test_cases(self):
        from evaluate import evaluate
        result = evaluate()
        self.assertEqual(result["holdoutExamples"], 375)
        self.assertFalse(result["clinicalValidation"])
        self.assertGreaterEqual(result["models"]["triage"]["accuracy"], 0)
        self.assertLessEqual(result["models"]["syndrome"]["accuracy"], 1)


if __name__ == "__main__":
    unittest.main()
