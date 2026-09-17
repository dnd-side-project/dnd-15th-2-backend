#!/usr/bin/env python3
"""Require at least one named reviewer to approve the exact current PR head.

GitHub does not let a PR author approve their own PR, so requiring every
named reviewer would always fail for whichever one opened the PR. Any one
of the named reviewers approving the exact head commit is sufficient
(AGENTS.md 4.8, decided 2026-09-17).
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request


def request_json(url: str, token: str) -> object:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "qello-infrastructure-approval-gate",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True)
    parser.add_argument("--pr-number", required=True, type=int)
    parser.add_argument("--required-reviewers", nargs="+", required=True)
    args = parser.parse_args()
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        print("GitHub workflow token is unavailable.", file=sys.stderr)
        return 2

    base = f"https://api.github.com/repos/{args.repository}/pulls/{args.pr_number}"
    try:
        pull = request_json(base, token)
        reviews = request_json(f"{base}/reviews?per_page=100", token)
    except (urllib.error.URLError, urllib.error.HTTPError, json.JSONDecodeError):
        print("Unable to verify approvals.", file=sys.stderr)
        return 2
    if not isinstance(pull, dict) or not isinstance(reviews, list):
        print("Unexpected GitHub response.", file=sys.stderr)
        return 2
    head_sha = ((pull.get("head") or {}).get("sha")) or ""
    if pull.get("state") != "open" or pull.get("draft") is True or not head_sha:
        print("Apply requires an open, non-draft PR.", file=sys.stderr)
        return 1

    latest: dict[str, dict] = {}
    for review in reviews:
        login = ((review.get("user") or {}).get("login")) or ""
        if not login:
            continue
        previous = latest.get(login.lower())
        if previous is None or (review.get("submitted_at") or "") >= (
            previous.get("submitted_at") or ""
        ):
            latest[login.lower()] = review

    approved_by = []
    for required in args.required_reviewers:
        review = latest.get(required.lower())
        if (
            review
            and review.get("state") == "APPROVED"
            and review.get("commit_id") == head_sha
        ):
            approved_by.append(required)
    if not approved_by:
        print(
            "Exact-head approval is missing from all of: "
            + ", ".join(args.required_reviewers),
            file=sys.stderr,
        )
        return 1
    print(f"Required reviewer approved the exact PR head: {approved_by[0]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
