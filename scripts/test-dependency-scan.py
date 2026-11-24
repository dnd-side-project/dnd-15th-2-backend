#!/usr/bin/env python3
"""TEST-PLAN-GH-382-DEPENDENCY-SCAN UNIT-001 through UNIT-006."""

from __future__ import annotations

import importlib.util
import json
import unittest
from datetime import date
from pathlib import Path


SPEC = importlib.util.spec_from_file_location("dependency_scan", Path(__file__).with_name("check-dependency-scan.py"))
POLICY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(POLICY)
TODAY = date(2026, 11, 23)


def exception() -> dict:
    return {
        "advisory": "CVE-2021-44228", "package": "org.example:core",
        "version": "1.0", "reason": "Temporary mitigation pending upgrade",
        "issue": 382, "expires_at": "2026-11-24",
    }


def result(score="7.0", name="org.example:core", version="1.0") -> str:
    return json.dumps({"results": [{"packages": [{
        "package": {"name": name, "version": version, "ecosystem": "Maven"},
        "vulnerabilities": [{"id": "GHSA-example", "aliases": ["CVE-2021-44228"]}],
        "groups": [{"ids": ["GHSA-example"], "max_severity": score}],
    }]}]})


class DependencyScanTest(unittest.TestCase):
    def test_unit_001_severity_boundary_and_unknown(self):
        for score, blocked in (("6.9", 0), ("7.0", 1), ("9.0", 1), ("", 1), ("NaN", 1)):
            with self.subTest(score=score):
                self.assertEqual(POLICY.assess(result(score), [], {"org.example:core:1.0"})["blocked"], blocked)
        for severity, blocked in (("LOW", 0), ("MODERATE", 0), ("HIGH", 1), ("CRITICAL", 1), ("unknown", 1)):
            with self.subTest(severity=severity):
                raw = json.loads(result(""))
                raw["results"][0]["packages"][0]["vulnerabilities"][0]["database_specific"] = {"severity": severity}
                self.assertEqual(POLICY.assess(json.dumps(raw), [], {"org.example:core:1.0"})["blocked"], blocked)

    def test_unit_002_required_exception_fields(self):
        for field in ("advisory", "package", "version", "reason", "issue", "expires_at"):
            for value in (None, "", " ", [], 0):
                with self.subTest(field=field, value=value):
                    item = exception()
                    item[field] = value
                    with self.assertRaisesRegex(POLICY.ScanError, f"^{field}$"):
                        POLICY.exceptions_from([item], TODAY)
            item = exception()
            del item[field]
            with self.assertRaisesRegex(POLICY.ScanError, f"^{field}$"):
                POLICY.exceptions_from([item], TODAY)

    def test_unit_003_expiry_utc_boundary(self):
        for expires, valid in (("2026-11-22", False), ("2026-11-23", False), ("2026-11-24", True), ("2026-12-24", False)):
            with self.subTest(expires=expires):
                item = exception()
                item["expires_at"] = expires
                if valid:
                    self.assertEqual(POLICY.exceptions_from([item], TODAY), [item])
                else:
                    with self.assertRaisesRegex(POLICY.ScanError, "^expires_at$"):
                        POLICY.exceptions_from([item], TODAY)

    def test_unit_004_exception_matches_alias_and_exact_coordinate(self):
        for name, version, blocked in (("org.example:core", "1.0", 0), ("org.example:other", "1.0", 1), ("org.example:core", "1.1", 1)):
            with self.subTest(name=name, version=version):
                summary = POLICY.assess(result(name=name, version=version), [exception()], {f"{name}:{version}"})
                self.assertEqual(summary["blocked"], blocked)

    def test_unit_005_incomplete_schema_and_coverage_fail_closed(self):
        for raw in (None, {}, {"results": None}, {"results": []}, {"results": [{}]}, {"results": [{"packages": []}]}):
            with self.subTest(raw=raw), self.assertRaises(POLICY.ScanError):
                POLICY.assess(json.dumps(raw), [], {"org.example:core:1.0"})
        for raw in ("", "{", '{"results":'):
            with self.subTest(raw=raw), self.assertRaises(json.JSONDecodeError):
                POLICY.assess(raw, [], {"org.example:core:1.0"})
        with self.assertRaisesRegex(POLICY.ScanError, "^coverage$"):
            POLICY.assess(result(), [], {"org.example:missing:1.0"})

    def test_unit_006_duplicate_and_absent_expired_exception(self):
        with self.assertRaisesRegex(POLICY.ScanError, "^duplicate$"):
            POLICY.exceptions_from([exception(), exception()], TODAY)
        item = exception()
        item["expires_at"] = "2026-11-22"
        with self.assertRaisesRegex(POLICY.ScanError, "^expires_at$"):
            POLICY.exceptions_from([item], TODAY)


if __name__ == "__main__":
    unittest.main()
