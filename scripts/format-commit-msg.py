#!/usr/bin/env python3
"""Prepare a conventional commit subject from the current branch context."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BRANCH_RE = re.compile(
    r"^(?P<type>feat|feature|fix|test|infra|docs|refactor|chore|ci|build|perf)/"
    r"gh-(?P<issue>\d+)-"
    r"[a-z0-9]+(?:-[a-z0-9]+)*$"
)
FULL_COMMIT_RE = re.compile(
    r"^(?:feat|fix|test|infra|docs|refactor|chore|ci|build|perf)"
    r"(?:\([a-z0-9-]+\))?: .+ \(#\d+\)$"
)
PARTIAL_COMMIT_RE = re.compile(
    r"^(?P<type>feat|feature|fix|test|infra|docs|refactor|chore|ci|build|perf)"
    r"(?P<scope>\([a-z0-9-]+\))?: (?P<summary>.+)$"
)
CONTEXT_TOKEN_RE = re.compile(r"\(#\d+\)")
TYPE_ALIASES = {"feature": "feat"}


class FormatError(RuntimeError):
    pass


def current_branch() -> str:
    result = subprocess.run(
        ["git", "branch", "--show-current"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise FormatError("unable to read the current Git branch")
    branch = result.stdout.strip()
    if branch:
        return branch
    return rebase_branch()


def rebase_branch() -> str:
    """Return the branch being rebased, or an empty string outside a rebase.

    rebase 중에는 HEAD가 detached라 `git branch --show-current`가 빈 값을 준다.
    이때 원래 브랜치 이름을 읽지 못하면 충돌을 해결한 rebase를 끝낼 수 없다.
    git이 rebase 상태 디렉터리에 남기는 head-name을 대신 읽는다.
    """
    result = subprocess.run(
        ["git", "rev-parse", "--git-dir"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        return ""

    git_dir = (ROOT / result.stdout.strip()).resolve()
    # rebase-merge는 interactive와 merge backend, rebase-apply는 am backend가 쓴다.
    for relative in ("rebase-merge/head-name", "rebase-apply/head-name"):
        head_name = git_dir / relative
        if head_name.is_file():
            return head_name.read_text(encoding="utf-8").strip().removeprefix("refs/heads/")
    return ""


def format_subject(subject: str, branch: str) -> str:
    branch_match = BRANCH_RE.fullmatch(branch)
    if not branch_match:
        raise FormatError(
            "branch must match <type>/gh-<ISSUE>-<slug>"
        )

    normalized = subject.strip()
    if not normalized or normalized.startswith("#"):
        raise FormatError("commit summary is empty")
    if normalized.startswith(("fixup! ", "squash! ")):
        raise FormatError(
            "fixup/squash commits are not auto-formatted; use an approved "
            "full message or document the hook bypass"
        )
    if FULL_COMMIT_RE.fullmatch(normalized):
        return normalized
    if CONTEXT_TOKEN_RE.search(normalized):
        raise FormatError(
            "partially specified Issue context is ambiguous; either use "
            "a short summary or a complete conventional message"
        )

    partial_match = PARTIAL_COMMIT_RE.fullmatch(normalized)
    if partial_match:
        commit_type = partial_match.group("type")
        scope = partial_match.group("scope") or ""
        summary = partial_match.group("summary").strip()
    else:
        commit_type = branch_match.group("type")
        scope = ""
        summary = normalized

    commit_type = TYPE_ALIASES.get(commit_type, commit_type)
    issue = branch_match.group("issue")
    return f"{commit_type}{scope}: {summary} (#{issue})"


def rewrite_message(
    path: Path,
    branch: str,
    *,
    allow_empty: bool = False,
) -> str | None:
    try:
        with path.open("r", encoding="utf-8", newline="") as message_file:
            original = message_file.read()
    except OSError as error:
        raise FormatError(f"unable to read commit message file: {error}") from error

    if not original:
        raise FormatError("commit message file is empty")

    line_match = re.match(r"(?P<subject>[^\r\n]*)(?P<ending>\r\n|\n|\r)?", original)
    if line_match is None:
        raise FormatError("unable to parse commit message")
    subject = line_match.group("subject")
    ending = line_match.group("ending") or ""
    if allow_empty and (
        not subject.strip() or subject.lstrip().startswith("#")
    ):
        return None
    formatted = format_subject(subject, branch)

    if formatted != subject:
        remainder = original[line_match.end() :]
        with path.open("w", encoding="utf-8", newline="") as message_file:
            message_file.write(formatted + ending + remainder)
    return formatted


def self_test() -> list[str]:
    branch = "feat/gh-42-order-feature"
    cases = (
        (
            "기능1 개발",
            "feat: 기능1 개발 (#42)",
        ),
        (
            "test(order): 예약 테스트 추가",
            "test(order): 예약 테스트 추가 (#42)",
        ),
        (
            "feat: 완성 메시지 (#42)",
            "feat: 완성 메시지 (#42)",
        ),
    )
    errors: list[str] = []
    for subject, expected in cases:
        actual = format_subject(subject, branch)
        if actual != expected:
            errors.append(f"expected '{expected}' but got '{actual}'")
    try:
        format_subject("기능1 개발", "main")
    except FormatError:
        pass
    else:
        errors.append("invalid branch was accepted")
    try:
        format_subject("부분 문맥 (#7)", branch)
    except FormatError:
        pass
    else:
        errors.append("partial Issue context was accepted")
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / "COMMIT_EDITMSG"
        body = "\n본문은 유지한다.\n# Git comment\n"
        path.write_text("기능1 개발" + body, encoding="utf-8")
        rewrite_message(path, branch)
        expected = "feat: 기능1 개발 (#42)" + body
        actual = path.read_text(encoding="utf-8")
        if actual != expected:
            errors.append("message body or Git comment was changed")
        path.write_text("\n# Enter a commit message\n", encoding="utf-8")
        if rewrite_message(path, branch, allow_empty=True) is not None:
            errors.append("interactive empty message was not deferred")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("message_file", nargs="?")
    parser.add_argument("--branch")
    parser.add_argument("--source", default="")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        errors = self_test()
        if errors:
            print("Commit message formatter self-test failed.")
            for error in errors:
                print(f"- {error}")
            return 1
        print("Commit message formatter self-test passed.")
        return 0

    if not args.message_file:
        parser.error("message_file is required unless --self-test is used")

    try:
        formatted = rewrite_message(
            Path(args.message_file),
            args.branch or current_branch(),
            allow_empty=args.source in ("", "template"),
        )
    except FormatError as error:
        print(f"Commit message preparation failed: {error}", file=sys.stderr)
        return 1

    if formatted is None:
        print("Commit subject preparation deferred until commit-msg.")
    else:
        print(f"Prepared commit subject: {formatted}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
