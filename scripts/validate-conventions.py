#!/usr/bin/env python3
"""Validate GitHub Issue based branch, commit, and PR naming conventions."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BRANCH_RE = re.compile(
    r"^(?P<type>feat|feature|fix|test|infra|docs|refactor|chore|ci|build|perf)/"
    r"gh-(?P<issue>\d+)-[a-z0-9]+(?:-[a-z0-9]+)*$"
)
COMMIT_RE = re.compile(
    r"^(?P<type>feat|fix|test|infra|docs|refactor|chore|ci|build|perf)"
    r"(?:\([a-z0-9-]+\))?: .+ \(#(?P<issue>\d+)\)$"
)
PR_RE = re.compile(
    r"^(?P<type>feat|fix|test|infra|docs|refactor|chore|ci|build|perf): .+$"
)
PR_ISSUE_RE = re.compile(
    r"(?im)^\s*(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s+#(?P<issue>\d+)\s*$"
)
TYPE_ALIASES = {"feature": "feat"}


def check(label: str, value: str, pattern: re.Pattern[str]) -> list[str]:
    return [] if pattern.fullmatch(value) else [f"{label} does not match: {value}"]


def check_context(
    branch: str,
    commits: list[str],
    pr_title: str | None,
    pr_body: str | None,
) -> list[str]:
    errors: list[str] = []
    branch_match = BRANCH_RE.fullmatch(branch)
    if not branch_match:
        return errors

    expected_type = TYPE_ALIASES.get(
        branch_match.group("type"), branch_match.group("type")
    )
    expected_issue = branch_match.group("issue")
    for message in commits:
        first_line = message.splitlines()[0]
        commit_match = COMMIT_RE.fullmatch(first_line)
        if not commit_match:
            continue
        if commit_match.group("type") != expected_type:
            errors.append(
                "commit type does not match branch: "
                f"{commit_match.group('type')} != {expected_type}"
            )
        if commit_match.group("issue") != expected_issue:
            errors.append(
                "commit GitHub issue does not match branch: "
                f"#{commit_match.group('issue')} != #{expected_issue}"
            )

    if pr_title:
        pr_match = PR_RE.fullmatch(pr_title)
        if pr_match and pr_match.group("type") != expected_type:
            errors.append(
                "PR type does not match branch: "
                f"{pr_match.group('type')} != {expected_type}"
            )

    if pr_body is not None:
        issue_match = PR_ISSUE_RE.search(pr_body)
        if not issue_match:
            errors.append(
                "PR body must contain a closing reference such as "
                f"'Closes #{expected_issue}'"
            )
        elif issue_match.group("issue") != expected_issue:
            errors.append(
                "PR GitHub issue does not match branch: "
                f"#{issue_match.group('issue')} != #{expected_issue}"
            )
    return errors


def self_test() -> list[str]:
    errors: list[str] = []
    valid = (
        (BRANCH_RE, "chore/gh-9-repository-harness"),
        (BRANCH_RE, "feat/gh-42-issue-context"),
        (COMMIT_RE, "feat(harness): use branch context (#42)"),
        (PR_RE, "feat: use branch context"),
    )
    invalid = (
        (BRANCH_RE, "chore/no-issue"),
        (COMMIT_RE, "chore: add harness"),
        (PR_RE, "Add harness"),
    )
    for pattern, value in valid:
        if not pattern.fullmatch(value):
            errors.append(f"self-test rejected valid value: {value}")
    for pattern, value in invalid:
        if pattern.fullmatch(value):
            errors.append(f"self-test accepted invalid value: {value}")

    errors.extend(
        check_context(
            "feat/gh-42-issue-context",
            ["feat(harness): use branch context (#42)"],
            "feat: use branch context",
            "Closes #42",
        )
    )
    mismatch_errors = check_context(
        "feat/gh-42-issue-context",
        ["fix(harness): use branch context (#7)"],
        "fix: use branch context",
        "Closes #7",
    )
    if len(mismatch_errors) != 4:
        errors.append(
            "self-test did not reject all branch/commit/PR context mismatches"
        )
    return errors


def current_branch() -> str:
    result = subprocess.run(
        ["git", "branch", "--show-current"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    return result.stdout.strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--branch")
    parser.add_argument("--commit", action="append", default=[])
    parser.add_argument("--commit-file")
    parser.add_argument("--pr-title")
    parser.add_argument("--pr-body")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    errors: list[str] = []
    commits = list(args.commit)
    if args.self_test:
        errors.extend(self_test())
    if args.branch:
        errors.extend(check("branch", args.branch, BRANCH_RE))
    for message in args.commit:
        errors.extend(check("commit", message.splitlines()[0], COMMIT_RE))
    if args.commit_file:
        path = Path(args.commit_file)
        if not path.is_absolute():
            path = ROOT / path
        try:
            message = path.read_text(encoding="utf-8").splitlines()[0]
        except (OSError, IndexError):
            errors.append(f"unable to read commit message: {args.commit_file}")
        else:
            errors.extend(check("commit", message, COMMIT_RE))
            commits.append(message)
    if args.pr_title:
        errors.extend(check("PR title", args.pr_title, PR_RE))
    if not any(
        (
            args.self_test,
            args.branch,
            args.commit,
            args.commit_file,
            args.pr_title,
            args.pr_body,
        )
    ):
        errors.extend(check("branch", current_branch(), BRANCH_RE))
    if any((commits, args.pr_title, args.pr_body is not None)):
        branch = args.branch or current_branch()
        if not args.branch:
            errors.extend(check("branch", branch, BRANCH_RE))
        errors.extend(check_context(branch, commits, args.pr_title, args.pr_body))

    if errors:
        print("Convention validation failed.")
        for error in errors:
            print(f"- {error}")
        print("Expected branch: <type>/gh-<ISSUE>-<slug>")
        print("Expected commit: type(scope): short summary (#<ISSUE>)")
        print("Expected PR title: type: short summary")
        return 1
    print("Convention validation passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
