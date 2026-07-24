#!/usr/bin/env python3
"""Safe, repository-scoped shortcuts for the Miri engineering harness."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Sequence


ROOT = Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / "harness.config.json").read_text(encoding="utf-8"))
JIRA_RE = re.compile(r"^[A-Z][A-Z0-9]+-\d+$")
BRANCH_RE = re.compile(
    r"^(?:feat|feature|fix|test|infra|docs|refactor|chore|ci|build|perf)/"
    r"(?P<jira>[A-Z][A-Z0-9]+-\d+)-gh-(?P<issue>\d+)-"
    r"[a-z0-9]+(?:-[a-z0-9]+)*$"
)
SLUG_RE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
ID_RE = re.compile(r"^[A-Z0-9]+(?:-[A-Z0-9]+)*$")


class HarnessError(RuntimeError):
    pass


def run(
    command: Sequence[str],
    *,
    capture: bool = False,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    resolved = list(command)
    if os.name == "nt" and resolved and resolved[0] == "./gradlew":
        resolved[0] = str(ROOT / "gradlew.bat")
    return subprocess.run(
        resolved,
        cwd=ROOT,
        text=True,
        capture_output=capture,
        check=check,
    )


def git_text(*arguments: str) -> str:
    result = run(["git", *arguments], capture=True, check=False)
    return result.stdout.strip() if result.returncode == 0 else ""


def current_branch() -> str:
    return git_text("branch", "--show-current")


def branch_context() -> tuple[str, int]:
    match = BRANCH_RE.fullmatch(current_branch())
    if not match:
        raise HarnessError(
            "current branch must be <type>/<JIRA>-gh-<issue>-<slug>"
        )
    return match.group("jira"), int(match.group("issue"))


def ensure_clean_worktree() -> None:
    if git_text("status", "--porcelain"):
        raise HarnessError("worktree must be clean before creating a branch")


def ensure_tool(name: str) -> None:
    if not shutil.which(name):
        raise HarnessError(f"required tool is unavailable: {name}")


def safe_identifier(value: str) -> str:
    normalized = value.upper()
    if not ID_RE.fullmatch(normalized):
        raise HarnessError("identifier must use uppercase letters, digits, and hyphens")
    return normalized


def render_template(template: str, replacements: dict[str, str]) -> str:
    text = (ROOT / template).read_text(encoding="utf-8")
    for source, target in replacements.items():
        text = text.replace(source, target)
    return text


def scaffold(template: str, destination: Path, replacements: dict[str, str]) -> None:
    if destination.exists():
        raise HarnessError(f"refusing to overwrite existing file: {destination}")
    text = render_template(template, replacements)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(text, encoding="utf-8")
    print(destination.relative_to(ROOT))


def command_doctor(_: argparse.Namespace) -> None:
    print("Miri harness doctor")
    required = ("git", "python3", "java")
    optional = (
        "gh",
        "claude",
        "codex",
        "jq",
        "shellcheck",
        "aws",
        "terraform",
    )
    failed = False
    for name in required:
        available = shutil.which(name) is not None
        print(f"[{'ok' if available else 'missing'}] {name}")
        failed |= not available
    for name in optional:
        print(f"[{'ok' if shutil.which(name) else 'optional'}] {name}")
    print(f"branch: {current_branch() or '(detached)'}")
    print(f"worktree: {'dirty' if git_text('status', '--porcelain') else 'clean'}")
    if failed:
        raise HarnessError("required tools are missing")


def command_status(_: argparse.Namespace) -> None:
    branch = current_branch()
    print(f"branch: {branch or '(detached)'}")
    print(f"commit: {git_text('rev-parse', '--short', 'HEAD') or '(none)'}")
    print(f"worktree: {'dirty' if git_text('status', '--porcelain') else 'clean'}")
    match = BRANCH_RE.fullmatch(branch)
    if match:
        print(f"jira: {match.group('jira')}")
        print(f"github issue: #{match.group('issue')}")
    else:
        print("gate: branch convention not satisfied")


def command_context(_: argparse.Namespace) -> None:
    jira, issue = branch_context()
    print(f"JIRA_KEY={jira}")
    print(f"GITHUB_ISSUE={issue}")
    print(f"BRANCH={current_branch()}")


def command_start(args: argparse.Namespace) -> None:
    ensure_tool("git")
    ensure_tool("gh")
    jira = args.jira.upper()
    if not JIRA_RE.fullmatch(jira):
        raise HarnessError("Jira key must look like PROJ-123")
    if args.issue <= 0:
        raise HarnessError("GitHub issue must be positive")
    slug = args.slug.lower()
    if not SLUG_RE.fullmatch(slug):
        raise HarnessError("slug must use lowercase letters, digits, and hyphens")
    if not args.confirm_jira_linked:
        raise HarnessError("--confirm-jira-linked is required")
    ensure_clean_worktree()
    issue = run(
        [
            "gh",
            "issue",
            "view",
            str(args.issue),
            "--json",
            "number,title,url",
        ],
        capture=True,
        check=False,
    )
    if issue.returncode != 0:
        raise HarnessError("unable to read the GitHub issue with gh")
    issue_data = json.loads(issue.stdout)
    if jira not in issue_data.get("title", ""):
        raise HarnessError("GitHub issue title does not contain the Jira key")
    branch = f"{args.type}/{jira}-gh-{args.issue}-{slug}"
    result = run(["git", "switch", "-c", branch], check=False)
    if result.returncode != 0:
        raise HarnessError(f"unable to create branch: {branch}")
    print(branch)


def command_task_init(args: argparse.Namespace) -> None:
    jira, issue = branch_context()
    parent_jira = "N/A"
    if args.parent_jira:
        parent_jira = args.parent_jira.upper()
        if not JIRA_RE.fullmatch(parent_jira):
            raise HarnessError("parent Jira key must look like PROJ-123")

    destination = ROOT / "TASK.md"
    if destination.exists() and not args.replace:
        raise HarnessError(
            "TASK.md already exists; pass --replace after confirming the old "
            "task contract is committed"
        )
    if destination.exists() and git_text(
        "status", "--porcelain", "--", "TASK.md"
    ):
        raise HarnessError(
            "TASK.md has uncommitted changes; commit or preserve them before "
            "using --replace"
        )

    created_at = datetime.now().astimezone().isoformat(timespec="seconds")
    destination.write_text(
        render_template(
            "templates/task-contract.md",
            {
                "<JIRA-KEY>": jira,
                "<GITHUB-ISSUE>": str(issue),
                "<PARENT-JIRA>": parent_jira,
                "<TASK-TITLE>": args.title,
                "<BRANCH>": current_branch(),
                "<CREATED-AT>": created_at,
            },
        ),
        encoding="utf-8",
    )
    print(destination.relative_to(ROOT))


def command_test_plan(args: argparse.Namespace) -> None:
    jira, issue = branch_context()
    identifier = safe_identifier(args.id)
    created_at = datetime.now().astimezone().isoformat(timespec="seconds")
    scaffold(
        "templates/test-plan.md",
        ROOT / "docs" / "test-plans" / f"{jira}-{identifier}.md",
        {
            "<JIRA-KEY>": jira,
            "<GITHUB-ISSUE>": str(issue),
            "<TEST-PLAN-ID>": identifier,
            "<CREATED-AT>": created_at,
        },
    )


def command_test_run(args: argparse.Namespace) -> None:
    jira, issue = branch_context()
    identifier = safe_identifier(args.id)
    unit_command = CONFIG["commands"]["unit"]
    if not unit_command:
        raise HarnessError("unit test command is not configured")
    run(unit_command)
    integration_command = CONFIG["commands"].get("integration") or []
    if integration_command:
        run(integration_command)
    created_at = datetime.now().astimezone().isoformat(timespec="seconds")
    scaffold(
        "templates/test-report.md",
        ROOT / "docs" / "reports" / "tests" / f"{jira}-{identifier}.md",
        {
            "<JIRA-KEY>": jira,
            "<GITHUB-ISSUE>": str(issue),
            "<TEST-PLAN-ID>": identifier,
            "<CREATED-AT>": created_at,
            "<BRANCH>": current_branch(),
            "<COMMIT>": git_text("rev-parse", "--short", "HEAD") or "uncommitted",
        },
    )


def command_infra_design(args: argparse.Namespace) -> None:
    jira, issue = branch_context()
    identifier = safe_identifier(args.id)
    created_at = datetime.now().astimezone().isoformat(timespec="seconds")
    scaffold(
        "templates/infrastructure-design-report.md",
        ROOT / "docs" / "reports" / "infrastructure" / f"{jira}-{identifier}.md",
        {
            "<JIRA-KEY>": jira,
            "<GITHUB-ISSUE>": str(issue),
            "<DESIGN-ID>": identifier,
            "<CREATED-AT>": created_at,
        },
    )


def validation_commands() -> list[list[str]]:
    python = sys.executable
    return [
        [python, "scripts/preflight.py"],
        [python, "scripts/validate-java-tests.py"],
        [python, "scripts/validate-conventions.py", "--self-test"],
        [python, "scripts/format-commit-msg.py", "--self-test"],
        [python, "scripts/validate-workflows.py"],
        [python, "scripts/validate-husky.py"],
    ]


def command_check(_: argparse.Namespace) -> None:
    for command in validation_commands():
        run(command)
    print("Harness checks passed.")


def command_pr_ready(args: argparse.Namespace) -> None:
    branch_context()
    command_check(args)
    if args.project_tests:
        command = CONFIG["commands"]["full"]
        if not command:
            raise HarnessError("full project check is not configured")
        run(command)
    diff_check = run(["git", "diff", "--check"], check=False)
    if diff_check.returncode != 0:
        raise HarnessError("git diff --check failed")
    print("Local PR readiness checks passed.")


def command_cheatsheet(_: argparse.Namespace) -> None:
    print((ROOT / "docs" / "harness" / "CHEATSHEET.md").read_text(encoding="utf-8"))


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(prog="harness")
    sub = root.add_subparsers(dest="command", required=True)
    sub.add_parser("doctor").set_defaults(handler=command_doctor)
    sub.add_parser("status").set_defaults(handler=command_status)
    sub.add_parser("context").set_defaults(handler=command_context)

    start = sub.add_parser("start")
    start.add_argument("--jira", required=True)
    start.add_argument("--issue", required=True, type=int)
    start.add_argument(
        "--type",
        required=True,
        choices=("feat", "fix", "test", "infra", "docs", "refactor", "chore", "ci", "build", "perf"),
    )
    start.add_argument("--slug", required=True)
    start.add_argument("--confirm-jira-linked", action="store_true")
    start.set_defaults(handler=command_start)

    task_init = sub.add_parser("task-init")
    task_init.add_argument("--title", required=True)
    task_init.add_argument("--parent-jira")
    task_init.add_argument("--replace", action="store_true")
    task_init.set_defaults(handler=command_task_init)

    for name, handler in (
        ("test-plan", command_test_plan),
        ("test-run", command_test_run),
        ("infra-design", command_infra_design),
    ):
        item = sub.add_parser(name)
        item.add_argument("--id", required=True)
        item.set_defaults(handler=handler)

    sub.add_parser("check").set_defaults(handler=command_check)
    ready = sub.add_parser("pr-ready")
    ready.add_argument("--project-tests", action="store_true")
    ready.set_defaults(handler=command_pr_ready)
    sub.add_parser("cheatsheet").set_defaults(handler=command_cheatsheet)
    return root


def main() -> int:
    try:
        args = parser().parse_args()
        args.handler(args)
        return 0
    except HarnessError as error:
        print(f"harness: {error}", file=sys.stderr)
        return 2
    except subprocess.CalledProcessError as error:
        print(f"harness: command failed with exit code {error.returncode}", file=sys.stderr)
        return error.returncode or 1


if __name__ == "__main__":
    raise SystemExit(main())
