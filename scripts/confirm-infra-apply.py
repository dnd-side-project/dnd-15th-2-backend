#!/usr/bin/env python3
"""Validate the explicit phrase and safe Terraform path for an apply request."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pr-number", required=True, type=int)
    parser.add_argument("--confirmation", required=True)
    parser.add_argument("--terraform-dir", required=True)
    args = parser.parse_args()

    if args.pr_number <= 0:
        print("PR number must be positive.", file=sys.stderr)
        return 1
    if args.confirmation != f"APPLY PR-{args.pr_number}":
        print("Confirmation phrase did not match.", file=sys.stderr)
        return 1

    requested = Path(args.terraform_dir)
    if requested.is_absolute() or ".." in requested.parts:
        print("Terraform directory must be repository-relative.", file=sys.stderr)
        return 1
    resolved = (ROOT / requested).resolve()
    try:
        relative = resolved.relative_to(ROOT)
    except ValueError:
        print("Terraform directory resolves outside the repository.", file=sys.stderr)
        return 1
    if not relative.parts or relative.parts[0] != "infra":
        print("Terraform directory must be under infra/.", file=sys.stderr)
        return 1
    if not resolved.is_dir() or not any(resolved.glob("*.tf")):
        print("Terraform root module was not found.", file=sys.stderr)
        return 1
    print("Explicit infrastructure apply confirmation validated.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
