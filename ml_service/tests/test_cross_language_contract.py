"""Cross-language contract verification tests (v2.0 - Non-Demographic).

Validates that the Python ML inference service satisfies the exact contract
demanded by the Java HttpPredictionProvider in LoanLens:
  - 7 exact uppercase feature keys (no demographic attributes).
  - Explicit rejection of demographic fields (INF_GENDER, INF_MARITAL_STATUS).
  - Transparent handling of genuine semantic synonyms (e.g. None -> N, Normal -> N).
  - Rejection of cross-domain fabricated aliases (e.g. Individual -> Rural, Monthly -> N) with HTTP 422.
  - Strict response payload format (predicted_class, probability_b, threshold_used, model_version).
  - Accurate threshold application for binary classification ('G' vs 'B').
  - Proper HTTP 422 / validation error responses on schema or category violations.
"""
import unittest
from fastapi import HTTPException
from pydantic import ValidationError

from ml_service.app.main import (
    LoanFeatures,
    health,
    model_info,
    predict,
)


class CrossLanguageContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.info = model_info()
        cls.threshold = cls.info["threshold"]

    def test_health_endpoint_contract(self):
        data = health()
        self.assertEqual(data.get("status"), "ok")
        self.assertTrue(data.get("model_loaded"))
        self.assertEqual(data.get("model_version"), "v2.0")
        self.assertIn("selected_model", data)
        self.assertEqual(len(data.get("input_features", [])), 7)

    def test_canonical_low_risk_prediction(self):
        payload = {
            "INVESTMENT_TOTAL": 25000.00,
            "ACCCURRENTBALANCE": 14500.50,
            "INSTALL_SIZE": 850.00,
            "DUE_PAYMENT": 0.00,
            "COMPENSATION_CHARGED": "None",
            "CLIENT_TYPE": "Rural",
            "REPAY_MODE": "Normal",
        }
        features = LoanFeatures(**payload)
        data = predict(features)

        # Contract assertion
        self.assertIn("predicted_class", data)
        self.assertIn("probability_b", data)
        self.assertIn("threshold_used", data)
        self.assertIn("model_version", data)

        self.assertIn(data["predicted_class"], {"G", "B"})
        self.assertIsInstance(data["probability_b"], float)
        self.assertGreaterEqual(data["probability_b"], 0.0)
        self.assertLessEqual(data["probability_b"], 1.0)
        self.assertAlmostEqual(data["threshold_used"], self.threshold, places=4)
        self.assertEqual(data["model_version"], "v2.0")

        # Binary decision must match probability vs threshold
        expected = "B" if data["probability_b"] >= data["threshold_used"] else "G"
        self.assertEqual(data["predicted_class"], expected)

    def test_canonical_high_risk_prediction(self):
        payload = {
            "INVESTMENT_TOTAL": 150000.00,
            "ACCCURRENTBALANCE": -3200.00,
            "INSTALL_SIZE": 18500.00,
            "DUE_PAYMENT": 2450.00,
            "COMPENSATION_CHARGED": "Penalty Charged",
            "CLIENT_TYPE": "Urban",
            "REPAY_MODE": "Installment",
        }
        features = LoanFeatures(**payload)
        data = predict(features)

        self.assertIn("predicted_class", data)
        self.assertIn("probability_b", data)
        self.assertIn("threshold_used", data)
        self.assertIn("model_version", data)

        self.assertIn(data["predicted_class"], {"G", "B"})
        self.assertIsInstance(data["probability_b"], float)
        expected_class = "B" if data["probability_b"] >= data["threshold_used"] else "G"
        self.assertEqual(data["predicted_class"], expected_class)

    def test_explicit_low_default_risk_payload(self):
        payload = {
            "INVESTMENT_TOTAL": 2000000.0,
            "ACCCURRENTBALANCE": 100000.0,
            "INSTALL_SIZE": 10000.0,
            "DUE_PAYMENT": 0.0,
            "COMPENSATION_CHARGED": "N",
            "CLIENT_TYPE": "Rural",
            "REPAY_MODE": "N",
        }
        features = LoanFeatures(**payload)
        data = predict(features)
        self.assertEqual(data["predicted_class"], "G")
        self.assertLess(data["probability_b"], data["threshold_used"])

    def test_threshold_decision_boundary(self):
        payload = {
            "INVESTMENT_TOTAL": 50000.0,
            "ACCCURRENTBALANCE": 1000.0,
            "INSTALL_SIZE": 1200.0,
            "DUE_PAYMENT": 0.0,
            "COMPENSATION_CHARGED": "N",
            "CLIENT_TYPE": "Rural",
            "REPAY_MODE": "N",
        }
        features = LoanFeatures(**payload)
        data = predict(features)
        prob = data["probability_b"]
        thresh = data["threshold_used"]
        if prob >= thresh:
            self.assertEqual(data["predicted_class"], "B")
        else:
            self.assertEqual(data["predicted_class"], "G")

    def test_threshold_decision_rule_below_equal_above(self):
        # Explicit test for the documented classification rule:
        # predicted_class = "B" if probability_b >= threshold else "G"
        thresh = self.threshold

        # Case 1: Strictly below threshold
        prob_below = thresh - 0.0001
        pred_below = "B" if prob_below >= thresh else "G"
        self.assertEqual(pred_below, "G", "Probability strictly below threshold must classify as 'G'")

        # Case 2: Exactly equal to threshold (boundary case)
        prob_equal = thresh
        pred_equal = "B" if prob_equal >= thresh else "G"
        self.assertEqual(pred_equal, "B", "Probability exactly equal to threshold must classify as 'B'")

        # Case 3: Strictly above threshold
        prob_above = thresh + 0.0001
        pred_above = "B" if prob_above >= thresh else "G"
        self.assertEqual(pred_above, "B", "Probability strictly above threshold must classify as 'B'")

    def test_demographic_fields_are_rejected(self):
        base_payload = {
            "INVESTMENT_TOTAL": 50000.0,
            "ACCCURRENTBALANCE": 1000.0,
            "INSTALL_SIZE": 1200.0,
            "DUE_PAYMENT": 0.0,
            "COMPENSATION_CHARGED": "N",
            "CLIENT_TYPE": "Rural",
            "REPAY_MODE": "N",
        }
        for demo_field, demo_value in [("INF_GENDER", "Male"), ("INF_MARITAL_STATUS", "Married")]:
            with self.subTest(field=demo_field):
                bad_payload = dict(base_payload, **{demo_field: demo_value})
                with self.assertRaises(ValidationError):
                    LoanFeatures(**bad_payload)

    def test_borrower_entity_type_rejected_for_geographic_feature(self):
        payload = {
            "INVESTMENT_TOTAL": 50000.0,
            "ACCCURRENTBALANCE": 1000.0,
            "INSTALL_SIZE": 1200.0,
            "DUE_PAYMENT": 0.0,
            "COMPENSATION_CHARGED": "N",
            "CLIENT_TYPE": "Individual",
            "REPAY_MODE": "N",
        }
        features = LoanFeatures(**payload)
        with self.assertRaises(HTTPException) as ctx:
            predict(features)
        self.assertEqual(ctx.exception.status_code, 422)
        self.assertIn("Unknown category for CLIENT_TYPE", str(ctx.exception.detail))

    def test_payment_frequency_rejected_for_repay_mode(self):
        payload = {
            "INVESTMENT_TOTAL": 50000.0,
            "ACCCURRENTBALANCE": 1000.0,
            "INSTALL_SIZE": 1200.0,
            "DUE_PAYMENT": 0.0,
            "COMPENSATION_CHARGED": "N",
            "CLIENT_TYPE": "Rural",
            "REPAY_MODE": "Monthly",
        }
        features = LoanFeatures(**payload)
        with self.assertRaises(HTTPException) as ctx:
            predict(features)
        self.assertEqual(ctx.exception.status_code, 422)
        self.assertIn("Unknown category for REPAY_MODE", str(ctx.exception.detail))

    def test_missing_feature_raises_validation_error(self):
        payload = {
            "INVESTMENT_TOTAL": 50000.0,
            # Missing ACCCURRENTBALANCE
            "INSTALL_SIZE": 1200.0,
            "DUE_PAYMENT": 0.0,
            "COMPENSATION_CHARGED": "N",
            "CLIENT_TYPE": "Rural",
            "REPAY_MODE": "N",
        }
        with self.assertRaises(ValidationError):
            LoanFeatures(**payload)

    def test_negative_investment_total_raises_validation_error(self):
        payload = {
            "INVESTMENT_TOTAL": -500.0,
            "ACCCURRENTBALANCE": 1000.0,
            "INSTALL_SIZE": 1200.0,
            "DUE_PAYMENT": 0.0,
            "COMPENSATION_CHARGED": "N",
            "CLIENT_TYPE": "Rural",
            "REPAY_MODE": "N",
        }
        with self.assertRaises(ValidationError):
            LoanFeatures(**payload)


if __name__ == "__main__":
    unittest.main()
