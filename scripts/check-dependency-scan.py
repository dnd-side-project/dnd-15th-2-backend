#!/usr/bin/env python3
"""Fail closed on incomplete OSV scans and expired or overly broad exceptions."""

from __future__ import annotations

import argparse
import json
import math
import re
import subprocess
import sys
import tempfile
import time
from datetime import date, datetime, timezone
from pathlib import Path


SCANNER_VERSION = "2.7.1"


class ScanError(ValueError):
    """A safe field name or failure category, never an untrusted input value."""


def text(value, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ScanError(field)
    return value


def exceptions_from(raw, today: date) -> list[dict]:
    if not isinstance(raw, list):
        raise ScanError("exceptions")
    seen = set()
    for item in raw:
        if not isinstance(item, dict):
            raise ScanError("exceptions")
        for field in ("advisory", "package", "version", "reason", "expires_at"):
            text(item.get(field), field)
        for field in ("advisory", "package", "version"):
            if any(char in item[field] for char in "*?[]") or item[field] != item[field].strip():
                raise ScanError(field)
        if not re.fullmatch(r"[^:\s]+:[^:\s]+", item["package"]):
            raise ScanError("package")
        if type(item.get("issue")) is not int or item["issue"] <= 0:
            raise ScanError("issue")
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", item["expires_at"]):
            raise ScanError("expires_at")
        try:
            expires = date.fromisoformat(item["expires_at"])
        except ValueError:
            raise ScanError("expires_at") from None
        if not 0 < (expires - today).days <= 30:
            raise ScanError("expires_at")
        key = (item["advisory"], item["package"], item["version"])
        if key in seen:
            raise ScanError("duplicate")
        seen.add(key)
    return raw


def numeric_score(raw) -> float | None:
    try:
        score = float(raw)
    except (TypeError, ValueError):
        return None
    return score if math.isfinite(score) and 0 <= score <= 10 else None


def assess(raw_json: str, exceptions: list[dict], expected: set[str]) -> dict:
    raw = json.loads(raw_json)
    if not isinstance(raw, dict) or not isinstance(raw.get("results"), list):
        raise ScanError("results")
    found = set()
    findings = []
    for source in raw["results"]:
        if not isinstance(source, dict) or not isinstance(source.get("packages"), list):
            raise ScanError("packages")
        for entry in source["packages"]:
            if not isinstance(entry, dict) or not isinstance(entry.get("package"), dict):
                raise ScanError("package")
            package = entry["package"]
            name = text(package.get("name"), "name")
            version = text(package.get("version"), "version")
            if package.get("ecosystem") != "Maven":
                raise ScanError("ecosystem")
            found.add(f"{name}:{version}")
            vulns = entry.get("vulnerabilities", [])
            groups = entry.get("groups", [])
            if not isinstance(vulns, list) or not isinstance(groups, list):
                raise ScanError("vulnerabilities")
            for vuln in vulns:
                if not isinstance(vuln, dict):
                    raise ScanError("vulnerability")
                advisory = text(vuln.get("id"), "id")
                aliases = vuln.get("aliases", [])
                if not isinstance(aliases, list) or not all(isinstance(a, str) for a in aliases):
                    raise ScanError("aliases")
                ids = {advisory, *aliases}
                scores = []
                for group in groups:
                    if not isinstance(group, dict) or not isinstance(group.get("ids"), list):
                        raise ScanError("groups")
                    if advisory in group["ids"]:
                        score = numeric_score(group.get("max_severity"))
                        if score is not None:
                            scores.append(score)
                score = max(scores) if scores else None
                database = vuln.get("database_specific", {})
                if not isinstance(database, dict):
                    raise ScanError("database_specific")
                official = database.get("severity")
                if score is not None:
                    blocked = score >= 7
                elif official in ("LOW", "MODERATE", "MEDIUM"):
                    blocked = False
                else:
                    blocked = True
                matches = [
                    item for item in exceptions
                    if item["advisory"] in ids and item["package"] == name
                    and item["version"] == version
                ]
                if len(matches) > 1:
                    raise ScanError("duplicate")
                findings.append({
                    "advisory": advisory, "aliases": aliases, "package": name,
                    "version": version, "score": score, "official_severity": official,
                    "excepted": bool(matches), "blocked": blocked and not matches,
                })
    if not found or found != expected:
        raise ScanError("coverage")
    return {
        "packages": len(found), "findings": findings,
        "blocked": sum(bool(item["blocked"]) for item in findings),
        "excepted": sum(item["excepted"] for item in findings),
    }


def lock_modules(path: Path) -> set[str]:
    modules = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#") or line.startswith("empty="):
            continue
        coordinate, separator, configurations = line.partition("=")
        if not separator or configurations != "runtimeClasspath" or len(coordinate.split(":")) != 3:
            raise ScanError("lockfile")
        modules.add(coordinate)
    if not modules:
        raise ScanError("lockfile")
    return modules


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scanner", required=True, type=Path)
    parser.add_argument("--lockfile", required=True, type=Path)
    parser.add_argument("--modules", required=True, type=Path)
    parser.add_argument("--exceptions", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    metadata = {
        "scanner_version": SCANNER_VERSION,
        "started_at": datetime.now(timezone.utc).isoformat(),
        "database": "live OSV API; no immutable database snapshot timestamp available",
    }
    status = 2
    try:
        today = datetime.now(timezone.utc).date()
        exceptions = exceptions_from(json.loads(args.exceptions.read_text(encoding="utf-8")), today)
        expected = lock_modules(args.lockfile)
        resolved = set(args.modules.read_text(encoding="utf-8").splitlines())
        if resolved != expected:
            raise ScanError("resolved_coverage")
        version = subprocess.run(
            [str(args.scanner), "--version"], capture_output=True, text=True,
            timeout=15, check=True,
        )
        if not re.search(rf"\b{re.escape(SCANNER_VERSION)}\b", version.stdout):
            raise ScanError("scanner_version")
        # An explicit empty config prevents native ignores from hiding policy inputs.
        with tempfile.NamedTemporaryFile(suffix=".toml") as config:
            result = subprocess.run(
                [str(args.scanner), "scan", "source", "--all-packages", "--all-vulns",
                 "--format=json", "--config", config.name, "-L", str(args.lockfile.resolve())],
                capture_output=True, text=True, timeout=300, check=False,
            )
        metadata["scanner_exit_code"] = result.returncode
        (args.output_dir / "osv.json").write_text(result.stdout, encoding="utf-8")
        if result.returncode not in (0, 1):
            raise ScanError("scanner")
        summary = assess(result.stdout, exceptions, expected)
        if (result.returncode == 0) != (len(summary["findings"]) == 0):
            raise ScanError("scanner_result")
        metadata.update(summary)
        status = 1 if summary["blocked"] else 0
        metadata["result"] = "policy_failure" if status else "pass"
        print(f"Dependency scan: packages={summary['packages']} blocked={summary['blocked']} excepted={summary['excepted']}")
    except ScanError as error:
        metadata.update(result="tool_or_input_failure", field=str(error))
        print(f"Dependency scan failed: {error}", file=sys.stderr)
    except (OSError, json.JSONDecodeError, UnicodeError, subprocess.SubprocessError) as error:
        metadata.update(result="tool_or_input_failure", field=type(error).__name__)
        print(f"Dependency scan failed: {type(error).__name__}", file=sys.stderr)
    metadata["duration_seconds"] = round(time.monotonic() - started, 3)
    (args.output_dir / "summary.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8",
    )
    return status


if __name__ == "__main__":
    sys.exit(main())
