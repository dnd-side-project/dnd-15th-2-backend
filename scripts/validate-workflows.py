#!/usr/bin/env python3
"""Perform static safety checks for repository GitHub workflows."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW_DIR = ROOT / ".github" / "workflows"
TERRAFORM_PLAN_RE = re.compile(r"\bterraform(?:\s+\S+)*\s+plan\b")
TERRAFORM_APPLY_RE = re.compile(r"\bterraform(?:\s+\S+)*\s+apply\b")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="*")
    args = parser.parse_args()
    paths = [ROOT / path for path in args.paths] if args.paths else sorted(
        [*WORKFLOW_DIR.glob("*.yml"), *WORKFLOW_DIR.glob("*.yaml")]
    )
    errors: list[str] = []
    for path in paths:
        if not path.exists():
            errors.append(f"{path.relative_to(ROOT)}: missing")
            continue
        text = path.read_text(encoding="utf-8")
        relative = path.relative_to(ROOT)
        for marker in ("name:", "on:", "permissions:", "jobs:"):
            if marker not in text:
                errors.append(f"{relative}: missing {marker}")
        if "\t" in text:
            errors.append(f"{relative}: tab character is not allowed")
        if "terraform destroy" in text:
            errors.append(f"{relative}: terraform destroy is prohibited")
        if "continue-on-error: true" in text:
            errors.append(f"{relative}: safety checks may not ignore errors")

        if path.name == "infrastructure-apply.yml":
            trigger = text.split("permissions:", maxsplit=1)[0]
            required = (
                "workflow_dispatch:",
                "INFRA_APPLY_ENABLED",
                "environment: infrastructure-apply",
                "confirm-infra-apply.py",
                "verify-infra-approvals.py",
                "--required-reviewers Byuntil tkv00",
                "mask-aws-account-id: true",
            )
            for marker in required:
                if marker not in text:
                    errors.append(f"{relative}: missing gate {marker}")
            if "pull_request:" in trigger or "push:" in trigger:
                errors.append(f"{relative}: apply must be workflow_dispatch only")
            if not TERRAFORM_PLAN_RE.search(text):
                errors.append(f"{relative}: missing gated Terraform plan")
            if not TERRAFORM_APPLY_RE.search(text):
                errors.append(f"{relative}: missing gated Terraform apply")
        elif TERRAFORM_APPLY_RE.search(text):
            errors.append(f"{relative}: apply exists outside the gated workflow")

        if path.name == "jira-sync.yml":
            required = (
                "function branchSlug(value, issueNumber)",
                'branchName = `${prefix}/${childKey}-gh-${issue.number}-${slug}`;',
                'return slug || `issue-${issueNumber}`;',
            )
            for marker in required:
                if marker not in text:
                    errors.append(
                        f"{relative}: missing dynamic Jira/GitHub branch marker"
                    )
        if path.name == "label-policy.yml":
            required = (
                "pull_request_target:",
                "classify-issue:",
                "classify-pull-request:",
                "type: feature",
                "status: needs-triage",
            )
            for marker in required:
                if marker not in text:
                    errors.append(f"{relative}: missing label gate {marker}")
            if "actions/checkout" in text.split(
                "classify-pull-request:", maxsplit=1
            )[1]:
                errors.append(
                    f"{relative}: pull_request_target job must not checkout PR code"
                )

    if errors:
        print("Workflow validation failed.")
        for error in errors:
            print(f"- {error}")
        return 1
    print(f"Workflow validation passed: {len(paths)} files inspected.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
