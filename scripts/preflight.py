#!/usr/bin/env python3
"""Detect tracked environment files and obvious secrets without printing values."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SKIP_PARTS = {".git", ".gradle", ".idea", "build", "out", "__pycache__"}
TEXT_SUFFIXES = {
    "",
    ".conf",
    ".gradle",
    ".java",
    ".json",
    ".kts",
    ".md",
    ".properties",
    ".py",
    ".sh",
    ".tf",
    ".tfvars",
    ".txt",
    ".yaml",
    ".yml",
    ".zsh",
}
RULES = (
    ("private-key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
    ("aws-access-key", re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b")),
    ("github-token", re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{30,}\b")),
    ("slack-token", re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{20,}\b")),
    (
        "assigned-secret",
        re.compile(
            r"(?i)\b(?:password|passwd|api[_-]?key|secret[_-]?key|access[_-]?token)"
            r"\s*[:=]\s*[\"']?(?!\$\{|<|example|redacted|masked)[^\s\"']{12,}"
        ),
    ),
)


def git_paths(*arguments: str) -> list[Path]:
    result = subprocess.run(
        ["git", *arguments],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        return []
    return [ROOT / line for line in result.stdout.splitlines() if line]


def staged_paths() -> list[Path]:
    result = subprocess.run(
        [
            "git",
            "diff",
            "--cached",
            "--name-only",
            "--diff-filter=ACMR",
            "-z",
        ],
        cwd=ROOT,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        return []
    return [
        ROOT / item.decode("utf-8", errors="surrogateescape")
        for item in result.stdout.split(b"\0")
        if item
    ]


def candidate_paths(staged: bool) -> list[Path]:
    paths = (
        staged_paths()
        if staged
        else git_paths("ls-files", "-co", "--exclude-standard")
    )
    return sorted(
        {
            path
            for path in paths
            if path.is_file()
            and path.resolve() != Path(__file__).resolve()
            and not SKIP_PARTS.intersection(path.relative_to(ROOT).parts)
            and path.suffix.lower() in TEXT_SUFFIXES
        }
    )


def tracked_environment_files(staged: bool) -> list[Path]:
    tracked = staged_paths() if staged else git_paths("ls-files")
    return [
        path
        for path in tracked
        if path.name.startswith(".env")
        and path.name not in {".env.example", ".env.template", ".env.sample"}
    ]


def read_lines(path: Path, staged: bool) -> list[str]:
    if not staged:
        return path.read_text(encoding="utf-8").splitlines()
    relative = path.relative_to(ROOT).as_posix()
    result = subprocess.run(
        ["git", "show", f":{relative}"],
        cwd=ROOT,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise OSError(f"unable to read staged file: {relative}")
    return result.stdout.decode("utf-8").splitlines()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--staged",
        action="store_true",
        help="inspect only the exact staged blobs",
    )
    args = parser.parse_args()
    findings: list[str] = []
    paths = candidate_paths(args.staged)
    for path in tracked_environment_files(args.staged):
        findings.append(f"{path.relative_to(ROOT)}: tracked-env-file")

    for path in paths:
        try:
            lines = read_lines(path, args.staged)
        except (UnicodeDecodeError, OSError):
            continue
        for line_number, line in enumerate(lines, start=1):
            for rule_name, pattern in RULES:
                if pattern.search(line):
                    findings.append(
                        f"{path.relative_to(ROOT)}:{line_number}: {rule_name}"
                    )

    if findings:
        print("Secret preflight failed. Matched values are intentionally hidden.")
        for finding in findings:
            print(f"- {finding}")
        return 1

    scope = "staged" if args.staged else "repository"
    print(f"Secret preflight passed: {len(paths)} {scope} text files inspected.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
