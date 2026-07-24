#!/usr/bin/env python3
"""Validate required JUnit 5 metadata and DisplayName declarations."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TEST_ROOTS = (
    ROOT / "src" / "test" / "java",
    ROOT / "src" / "integrationTest" / "java",
)
CREATED_RE = re.compile(
    r"Created at: \d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:Z|[+-]\d{2}:\d{2})"
)
SCENARIO_RE = re.compile(r"Source scenario: [A-Z0-9]+(?:-[A-Z0-9]+)+")
TEST_ANNOTATION_RE = re.compile(
    r"^\s*@(Test|ParameterizedTest|RepeatedTest|TestFactory)\b"
)
DISPLAY_RE = re.compile(r'^\s*@DisplayName\(".+"\)')


def validate(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    errors: list[str] = []
    header = "\n".join(lines[:30])
    if not CREATED_RE.search(header):
        errors.append(f"{path.relative_to(ROOT)}: missing exact Created at timestamp")
    if not SCENARIO_RE.search(header):
        errors.append(f"{path.relative_to(ROOT)}: missing Source scenario identifier")

    for index, line in enumerate(lines):
        if not TEST_ANNOTATION_RE.search(line):
            continue
        window = lines[max(0, index - 4) : min(len(lines), index + 6)]
        if not any(DISPLAY_RE.search(item) for item in window):
            errors.append(
                f"{path.relative_to(ROOT)}:{index + 1}: test is missing @DisplayName"
            )
    return errors


def main() -> int:
    files = sorted(
        path
        for test_root in TEST_ROOTS
        if test_root.exists()
        for path in test_root.rglob("*.java")
    )
    errors = [error for path in files for error in validate(path)]
    if errors:
        print("JUnit policy validation failed.")
        for error in errors:
            print(f"- {error}")
        return 1
    print(f"JUnit policy validation passed: {len(files)} files inspected.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
