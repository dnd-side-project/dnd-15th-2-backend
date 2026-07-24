#!/usr/bin/env python3
"""Validate the compact issue and pull-request label contract."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / ".github" / "label-catalog.json"
EXPECTED = {
    "type: feature",
    "type: bug",
    "type: refactor",
    "type: test",
    "type: docs",
    "type: infrastructure",
    "type: performance",
    "type: chore",
    "area: api",
    "area: database",
    "area: security",
    "area: operations",
    "status: blocked",
    "status: needs-review",
    "status: needs-triage",
}
NAME_RE = re.compile(r"^(type|area|status): [a-z0-9-]+$")
COLOR_RE = re.compile(r"^[0-9A-F]{6}$")


def main() -> int:
    errors: list[str] = []
    try:
        labels = json.loads(CATALOG.read_text(encoding="utf-8"))["labels"]
    except (OSError, KeyError, json.JSONDecodeError) as error:
        print(f"label catalog is invalid: {error}", file=sys.stderr)
        return 1

    names = [label.get("name", "") for label in labels]
    if len(labels) != 15:
        errors.append("label catalog must contain exactly 15 canonical labels")
    if len(names) != len(set(names)):
        errors.append("label names must be unique")
    if set(names) != EXPECTED:
        errors.append("label catalog does not match the canonical label set")

    for label in labels:
        name = label.get("name", "")
        if not NAME_RE.fullmatch(name):
            errors.append(f"invalid label name: {name!r}")
        if not COLOR_RE.fullmatch(label.get("color", "")):
            errors.append(f"invalid label color for {name!r}")
        if not label.get("description", "").strip():
            errors.append(f"missing label description for {name!r}")

    template_expectations = {
        "backend_work.yml": "area: api",
        "bug.yml": "type: bug",
        "design.yml": "type: docs",
        "feature.yml": "type: feature",
        "infrastructure.yml": "type: infrastructure",
        "test-scenario.yml": "type: test",
    }
    template_root = ROOT / ".github" / "ISSUE_TEMPLATE"
    for filename, expected in template_expectations.items():
        text = (template_root / filename).read_text(encoding="utf-8")
        if expected not in text:
            errors.append(f"{filename} must include {expected!r}")

    workflow = (ROOT / ".github" / "workflows" / "label-policy.yml").read_text(
        encoding="utf-8"
    )
    for marker in ("pull_request_target:", "classify-issue:", "classify-pull-request:"):
        if marker not in workflow:
            errors.append(f"label policy is missing {marker!r}")

    if errors:
        for error in errors:
            print(f"label-policy: {error}", file=sys.stderr)
        return 1
    print("Label policy validation passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
