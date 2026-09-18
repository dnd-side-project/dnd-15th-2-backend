#!/usr/bin/env python3
"""Require a merged infrastructure PR that an owner other than its author approved.

The approval evidence lives on the real infrastructure PR, not on a throwaway
PR kept open for the apply. An earlier design required an open PR whose head
carried the approval, which forced a rebase (and therefore a fresh approval)
before every apply and was recreated three times when it was merged by mistake
(#249/#253/#257, #281). Tying the evidence to the merged PR keeps the control
and removes that loop.

Checks, all required:

* the PR is merged into the repository's default infrastructure branch
* at least one named reviewer approved that PR's head commit
* the approver is not the PR author
* the commit being applied is exactly that PR's merge commit

GitHub does not let a PR author approve their own PR, so any one of the named
reviewers is sufficient (AGENTS.md 4.8, decided 2026-09-17).
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


def latest_reviews(reviews: list) -> dict[str, dict]:
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
    return latest


def verify(pull: dict, reviews: list, required: list[str], commit_sha: str,
           base_branch: str) -> tuple[int, str]:
    if not pull.get("merged"):
        return 1, "Apply requires a merged infrastructure PR."
    if ((pull.get("base") or {}).get("ref")) != base_branch:
        return 1, f"The PR must target {base_branch}."

    merge_commit = pull.get("merge_commit_sha") or ""
    if not merge_commit:
        return 1, "The PR has no merge commit."
    if merge_commit != commit_sha:
        return 1, "The requested commit is not that PR's merge commit."

    head_sha = ((pull.get("head") or {}).get("sha")) or ""
    if not head_sha:
        return 1, "The PR has no head commit."

    author = (((pull.get("user") or {}).get("login")) or "").lower()
    latest = latest_reviews(reviews)
    for name in required:
        review = latest.get(name.lower())
        if not review or review.get("state") != "APPROVED":
            continue
        if review.get("commit_id") != head_sha:
            continue
        # A PR author cannot approve their own PR through GitHub, but a review
        # left before authorship changed (or by an automation acting as the
        # author) must not count as independent review.
        if name.lower() == author:
            continue
        return 0, f"Merged PR approved by {name}, who is not its author."

    return 1, (
        "No approval of the merged PR head from a reviewer other than its "
        "author, among: " + ", ".join(required)
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True)
    parser.add_argument("--pr-number", required=True, type=int)
    parser.add_argument("--required-reviewers", nargs="+", required=True)
    parser.add_argument("--commit-sha", required=True)
    parser.add_argument("--base-branch", default="main")
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

    code, message = verify(
        pull, reviews, args.required_reviewers, args.commit_sha, args.base_branch
    )
    print(message, file=sys.stderr if code else sys.stdout)
    return code


if __name__ == "__main__":
    sys.exit(main())
