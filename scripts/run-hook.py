#!/usr/bin/env python3
"""Run fast local Git hooks while keeping CI as the authoritative gate."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path
from typing import Sequence


ROOT = Path(__file__).resolve().parents[1]


def run(command: Sequence[str]) -> None:
    print(f"[hook] {' '.join(command)}")
    result = subprocess.run(list(command), cwd=ROOT, check=False)
    if result.returncode != 0:
        raise SystemExit(result.returncode)


def staged_files() -> list[str]:
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
        raise SystemExit(result.returncode)
    return [
        item.decode("utf-8", errors="surrogateescape")
        for item in result.stdout.split(b"\0")
        if item
    ]


def pre_commit() -> None:
    files = staged_files()
    if not files:
        print("[hook] No staged files.")
        return

    run([sys.executable, "scripts/validate-conventions.py"])
    run(["git", "diff", "--cached", "--check"])
    run([sys.executable, "scripts/preflight.py", "--staged"])

    if any(
        path.endswith(".java")
        and path.startswith(("src/test/", "src/integrationTest/"))
        for path in files
    ):
        run([sys.executable, "scripts/validate-java-tests.py"])

    if any(path.startswith(".github/workflows/") for path in files):
        run([sys.executable, "scripts/validate-workflows.py"])

    if any(
        path == "package.json"
        or path == "package-lock.json"
        or path.startswith(".husky/")
        for path in files
    ):
        run([sys.executable, "scripts/validate-husky.py"])

    print("[hook] Pre-commit checks passed.")


def prepare_commit_msg(path: str, source: str) -> None:
    run(
        [
            sys.executable,
            "scripts/format-commit-msg.py",
            path,
            "--source",
            source,
        ]
    )
    print("[hook] Commit message prepared from branch context.")


def commit_msg(path: str) -> None:
    run(
        [
            sys.executable,
            "scripts/format-commit-msg.py",
            path,
            "--source",
            "commit-msg",
        ]
    )
    run(
        [
            sys.executable,
            "scripts/validate-conventions.py",
            "--commit-file",
            path,
        ]
    )
    print("[hook] Commit message check passed.")


def pre_push() -> None:
    run(
        [
            sys.executable,
            "scripts/harness.py",
            "pr-ready",
            "--project-tests",
        ]
    )
    print("[hook] Pre-push checks passed.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "hook",
        choices=("pre-commit", "prepare-commit-msg", "commit-msg", "pre-push"),
    )
    parser.add_argument("hook_arguments", nargs="*")
    args = parser.parse_args()

    if args.hook == "pre-commit":
        pre_commit()
    elif args.hook == "prepare-commit-msg":
        if not args.hook_arguments:
            parser.error("prepare-commit-msg requires the Git message file path")
        prepare_commit_msg(
            args.hook_arguments[0],
            args.hook_arguments[1] if len(args.hook_arguments) > 1 else "",
        )
    elif args.hook == "commit-msg":
        if not args.hook_arguments:
            parser.error("commit-msg requires the Git message file path")
        commit_msg(args.hook_arguments[0])
    else:
        pre_push()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
