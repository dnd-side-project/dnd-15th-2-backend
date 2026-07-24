#!/usr/bin/env python3
"""Validate the repository-local Husky contract without installing hooks."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HOOKS = {
    "pre-commit": "scripts/run-hook.py pre-commit",
    "prepare-commit-msg": 'scripts/run-hook.py prepare-commit-msg "$1"',
    "commit-msg": 'scripts/run-hook.py commit-msg "$1"',
    "pre-push": "scripts/run-hook.py pre-push",
}


def tracked_mode(path: str) -> str:
    result = subprocess.run(
        ["git", "ls-files", "--stage", "--", path],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0 or not result.stdout.strip():
        return ""
    return result.stdout.split(maxsplit=1)[0]


def main() -> int:
    errors: list[str] = []
    package_path = ROOT / "package.json"
    lock_path = ROOT / "package-lock.json"

    if not package_path.exists():
        errors.append("package.json is missing")
        package: dict[str, object] = {}
    else:
        package = json.loads(package_path.read_text(encoding="utf-8"))

    scripts = package.get("scripts") or {}
    dependencies = package.get("devDependencies") or {}
    if not isinstance(scripts, dict) or scripts.get("prepare") != "husky":
        errors.append('package.json must define "prepare": "husky"')
    if (
        not isinstance(scripts, dict)
        or scripts.get("hooks:prepare-commit-msg")
        != "node scripts/python.mjs scripts/format-commit-msg.py --self-test"
    ):
        errors.append(
            "package.json must define hooks:prepare-commit-msg for local testing"
        )
    if not isinstance(dependencies, dict) or dependencies.get("husky") != "9.1.7":
        errors.append("package.json must pin husky 9.1.7")
    if not lock_path.exists():
        errors.append("package-lock.json is missing")
    if not (ROOT / "scripts" / "format-commit-msg.py").exists():
        errors.append("scripts/format-commit-msg.py is missing")

    for name, marker in HOOKS.items():
        relative = f".husky/{name}"
        path = ROOT / relative
        if not path.exists():
            errors.append(f"{relative} is missing")
            continue
        text = path.read_text(encoding="utf-8")
        if marker not in text:
            errors.append(f"{relative} does not call the shared hook runner")
        mode = tracked_mode(relative)
        if mode and mode != "100755":
            errors.append(f"{relative} must be executable in Git (found {mode})")

    gitignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
    if "/node_modules/" not in gitignore:
        errors.append(".gitignore must exclude /node_modules/")

    if errors:
        print("Husky validation failed.")
        for error in errors:
            print(f"- {error}")
        return 1
    print("Husky validation passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
