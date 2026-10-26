"""Regression tests for the model-backed prediction API (v2.0 - Non-Demographic).

Run from the repository root with:
    python -m unittest discover -s ml_service/tests -v
"""
import asyncio
import json
import unittest

import numpy as np
from fastapi import HTTPException
from fastapi.exceptions import RequestValidationError
from pydantic import ValidationError

from ml_service.app.main import (
    LoanFeatures,
    health,
    model_info,
    predict,
    request_validation_exception_handler,
)


VALID_PAYLOAD = {
    "INVESTMENT_TOTAL": 2_000_000,
    "ACCCURRENTBALANCE": 100_000,
    "INSTALL_SIZE": 10_000,
    "DUE_PAYMENT": 0,
    "COMPENSATION_CHARGED": "N",
    "CLIENT_TYPE": "Rural",
    "REPAY_MODE": "N",
}


class PredictionApiTests(unittest.TestCase):
    def test_health_reports_loaded_model(self):
        result = health()
        self.assertEqual(result["status"], "ok")
        self.assertTrue(result["model_loaded"])
        self.assertEqual(result["stage"], "model-backed")
        self.assertEqual(result["model_version"], "v2.0")

    def test_model_info_has_valid_threshold(self):
        result = model_info()
        self.assertGreaterEqual(result["threshold"], 0.0)
        self.assertLessEqual(result["threshold"], 1.0)
        self.assertEqual(result["model_version"], "v2.0")
        self.assertIn("selected_model", result)

    def test_valid_prediction_contract(self):
        result = predict(LoanFeatures(**VALID_PAYLOAD))
        self.assertIn(result["predicted_class"], {"B", "G"})
        self.assertTrue(np.isfinite(result["probability_b"]))
        self.assertGreaterEqual(result["probability_b"], 0.0)
        self.assertLessEqual(result["probability_b"], 1.0)
        self.assertEqual(result["threshold_used"], model_info()["threshold"])
        expected = "B" if result["probability_b"] >= result["threshold_used"] else "G"
        self.assertEqual(result["predicted_class"], expected)

    def test_category_case_variant_is_canonicalized(self):
        canonical = predict(LoanFeatures(**VALID_PAYLOAD))
        variant_payload = dict(VALID_PAYLOAD)
        variant_payload["CLIENT_TYPE"] = "rUrAl"
        variant = predict(LoanFeatures(**variant_payload))
        self.assertEqual(variant["predicted_class"], canonical["predicted_class"])
        self.assertAlmostEqual(variant["probability_b"], canonical["probability_b"], places=12)

    def test_semi_urban_case_variant_is_canonicalized(self):
        canonical_payload = dict(VALID_PAYLOAD, CLIENT_TYPE="Semi-urban")
        variant_payload = dict(VALID_PAYLOAD, CLIENT_TYPE="Semi-Urban")
        canonical = predict(LoanFeatures(**canonical_payload))
        variant = predict(LoanFeatures(**variant_payload))
        self.assertEqual(variant["predicted_class"], canonical["predicted_class"])
        self.assertAlmostEqual(variant["probability_b"], canonical["probability_b"], places=12)

    def test_unknown_category_returns_422(self):
        bad_payload = dict(VALID_PAYLOAD, CLIENT_TYPE="Metropolitan")
        with self.assertRaises(HTTPException) as caught:
            predict(LoanFeatures(**bad_payload))
        self.assertEqual(caught.exception.status_code, 422)

    def test_invalid_numeric_ranges_are_rejected_before_inference(self):
        invalid_cases = [
            ("INVESTMENT_TOTAL", 0),
            ("INVESTMENT_TOTAL", -1),
            ("INSTALL_SIZE", -1),
            ("DUE_PAYMENT", -1),
            ("ACCCURRENTBALANCE", float("nan")),
            ("DUE_PAYMENT", float("inf")),
        ]
        for field, value in invalid_cases:
            with self.subTest(field=field, value=value):
                payload = dict(VALID_PAYLOAD, **{field: value})
                with self.assertRaises(ValidationError):
                    LoanFeatures(**payload)

    def test_zero_install_size_is_allowed(self):
        payload = dict(VALID_PAYLOAD, INSTALL_SIZE=0)
        result = predict(LoanFeatures(**payload))
        self.assertIn(result["predicted_class"], {"B", "G"})
        self.assertTrue(np.isfinite(result["probability_b"]))

    def test_negative_current_balance_is_allowed(self):
        payload = dict(VALID_PAYLOAD, ACCCURRENTBALANCE=-100)
        result = predict(LoanFeatures(**payload))
        self.assertTrue(np.isfinite(result["probability_b"]))

    def test_non_finite_input_validation_error_is_json_safe(self):
        error = RequestValidationError([{
            "type": "finite_number",
            "loc": ("body", "ACCCURRENTBALANCE"),
            "msg": "Input should be a finite number",
            "input": float("nan"),
        }])
        response = asyncio.run(request_validation_exception_handler(None, error))
        body = json.loads(response.body.decode("utf-8"))
        self.assertEqual(response.status_code, 422)
        self.assertEqual(body["detail"][0]["input"], "nan")

    def test_extra_fields_are_rejected(self):
        payload = dict(VALID_PAYLOAD, ACC_NO=123456789)
        with self.assertRaises(ValidationError):
            LoanFeatures(**payload)

    def test_demographic_fields_are_rejected(self):
        for field in ["INF_GENDER", "INF_MARITAL_STATUS"]:
            with self.subTest(field=field):
                payload = dict(VALID_PAYLOAD, **{field: "M"})
                with self.assertRaises(ValidationError):
                    LoanFeatures(**payload)


if __name__ == "__main__":
    unittest.main()
