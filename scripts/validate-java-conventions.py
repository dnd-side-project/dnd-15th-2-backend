#!/usr/bin/env python3
"""Validate Java convention baseline metadata without mutating Git state."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from datetime import date
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
KNOWN_RULES = {
    "QELLO-JAVA-CTOR-001",
    "QELLO-JAVA-IMPORT-001",
    "QELLO-JAVA-SIZE-001",
    "QELLO-JAVA-CPLX-001",
    "QELLO-JAVA-BYPASS-001",
    "QELLO-JAVA-INJECTION-001",
    "QELLO-JAVA-TX-001",
    "QELLO-JAVA-TX-002",
    "QELLO-JAVA-TX-003",
}
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
DECISION_RE = re.compile(r"^- `(?P<decision>DEC-[A-Z0-9-]+)`: ", re.MULTILINE)


def error(code: str, message: str) -> tuple[str, str]:
    return code, message


def is_exact_target(target: object) -> bool:
    return isinstance(target, str) and bool(target) and not any(value in target for value in ("*", "?", ".."))


def validate_entry(entry: object, decisions: set[str]) -> list[tuple[str, str]]:
    if not isinstance(entry, dict):
        return [error("QELLO-JAVA-BASELINE-001", "entry must be an object")]

    errors: list[tuple[str, str]] = []
    for field in ("id", "rule", "target", "sourceSha256", "classification", "reason"):
        if not isinstance(entry.get(field), str) or not entry[field].strip():
            errors.append(error("QELLO-JAVA-BASELINE-001", f"missing {field}"))
    if entry.get("rule") not in KNOWN_RULES:
        errors.append(error("QELLO-JAVA-BASELINE-001", "unknown rule"))
    if not is_exact_target(entry.get("target")):
        errors.append(error("QELLO-JAVA-BASELINE-003", "target must be an exact class or member"))
    if not isinstance(entry.get("sourceSha256"), str) or not SHA256_RE.fullmatch(entry["sourceSha256"]):
        errors.append(error("QELLO-JAVA-BASELINE-001", "sourceSha256 must be lowercase SHA-256"))

    classification = entry.get("classification")
    if classification == "LEGACY":
        if not isinstance(entry.get("trackingReference"), str) or not entry["trackingReference"].strip():
            errors.append(error("QELLO-JAVA-BASELINE-004", "LEGACY requires trackingReference"))
        try:
            date.fromisoformat(str(entry.get("reviewBy")))
        except ValueError:
            errors.append(error("QELLO-JAVA-BASELINE-004", "LEGACY requires ISO reviewBy"))
    elif classification == "JUSTIFIED_EXCEPTION":
        if not isinstance(entry.get("designReference"), str) or not entry["designReference"].strip():
            errors.append(error("QELLO-JAVA-BASELINE-005", "JUSTIFIED_EXCEPTION requires designReference"))
        decision = entry.get("decisionId")
        if not isinstance(decision, str) or decision not in decisions:
            errors.append(error("QELLO-JAVA-BASELINE-005", "JUSTIFIED_EXCEPTION requires approved decisionId"))
    else:
        errors.append(error("QELLO-JAVA-BASELINE-001", "unknown classification"))
    return errors


def validate_document(document: object, decisions: set[str]) -> list[tuple[str, str]]:
    if not isinstance(document, dict) or document.get("schemaVersion") != 1:
        return [error("QELLO-JAVA-BASELINE-001", "schemaVersion must be 1")]
    entries = document.get("entries")
    if not isinstance(entries, list):
        return [error("QELLO-JAVA-BASELINE-001", "entries must be an array")]

    errors: list[tuple[str, str]] = []
    identifiers: set[str] = set()
    rule_targets: set[tuple[object, object]] = set()
    for entry in entries:
        errors.extend(validate_entry(entry, decisions))
        if not isinstance(entry, dict):
            continue
        identifier = entry.get("id")
        if identifier in identifiers:
            errors.append(error("QELLO-JAVA-BASELINE-002", f"duplicate id {identifier}"))
        identifiers.add(identifier)
        rule_target = (entry.get("rule"), entry.get("target"))
        if rule_target in rule_targets:
            errors.append(error("QELLO-JAVA-BASELINE-002", "duplicate rule and target"))
        rule_targets.add(rule_target)
    return errors


def source_path(target: str) -> str:
    class_name = target.split("#", maxsplit=1)[0]
    return "src/main/java/" + class_name.replace(".", "/") + ".java"


def git_blob_sha256(revision: str, path: str) -> str:
    result = subprocess.run(
        ["git", "show", f"{revision}:{path}"],
        cwd=ROOT,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise ValueError(f"cannot read Git blob {revision}:{path}")
    return hashlib.sha256(result.stdout).hexdigest()


def validate_hashes(document: object, revision: str) -> list[tuple[str, str]]:
    if not isinstance(document, dict) or not isinstance(document.get("entries"), list):
        return []
    errors: list[tuple[str, str]] = []
    for entry in document["entries"]:
        if not isinstance(entry, dict) or not is_exact_target(entry.get("target")):
            continue
        try:
            actual = git_blob_sha256(revision, source_path(entry["target"]))
        except ValueError as exception:
            errors.append(error("QELLO-JAVA-BASELINE-006", str(exception)))
            continue
        if entry.get("sourceSha256") != actual:
            errors.append(error("QELLO-JAVA-BASELINE-006", f"stale hash for {entry['target']}"))
    return errors


def self_test() -> list[tuple[str, str]]:
    decisions = {"DEC-208-001"}
    valid = {
        "schemaVersion": 1,
        "entries": [
            {
                "id": "JAVA-CONV-0001",
                "rule": "QELLO-JAVA-IMPORT-001",
                "target": "com.dnd.qello.Example",
                "sourceSha256": "a" * 64,
                "classification": "LEGACY",
                "reason": "fixture",
                "trackingReference": "fixture",
                "reviewBy": "2026-12-31",
            }
        ],
    }
    if validate_document(valid, decisions):
        return [error("QELLO-JAVA-BASELINE-001", "valid self-test fixture was rejected")]
    invalid = {"schemaVersion": 1, "entries": [valid["entries"][0], valid["entries"][0]]}
    codes = {code for code, _ in validate_document(invalid, decisions)}
    if "QELLO-JAVA-BASELINE-002" not in codes:
        return [error("QELLO-JAVA-BASELINE-001", "duplicate self-test fixture was accepted")]
    return []


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--baseline", default="config/java-conventions/baseline.json")
    parser.add_argument("--task-file", default="TASK.md")
    parser.add_argument("--base-ref", default="origin/main")
    args = parser.parse_args()

    if args.self_test:
        errors = self_test()
        if not errors:
            print("Java convention baseline self-test passed.")
            return 0
    else:
        try:
            task_text = (ROOT / args.task_file).read_text(encoding="utf-8")
            decisions = set(DECISION_RE.findall(task_text))
            document = json.loads((ROOT / args.baseline).read_text(encoding="utf-8"))
            errors = validate_document(document, decisions)
            if not errors:
                errors.extend(validate_hashes(document, args.base_ref))
        except (OSError, ValueError, json.JSONDecodeError) as exception:
            errors = [error("QELLO-JAVA-BASELINE-001", str(exception))]
        if not errors:
            print("Java convention baseline validation passed.")
            return 0

    for code, message in errors:
        print(f"{code}: {message}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
