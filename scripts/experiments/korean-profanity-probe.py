#!/usr/bin/env python3
"""Measure how OpenAI moderation judges Korean profanity samples (GH-336).

Each sample is normalized the same way as UnicodeTextNormalizer (NFKC, hidden
character removal, whitespace collapse, strip) and sent on its own to
/v1/moderations, matching the production request shape. The script writes one
result row per sample and prints miss and false-positive rates per group.

    python3 scripts/experiments/korean-profanity-probe.py \\
        --samples docs/experiments/korean-profanity/gh-336-samples.csv \\
        --out docs/experiments/korean-profanity/gh-336-results.csv

Sample text may contain \\uXXXX escapes so that invisible characters such as
zero-width spaces stay reviewable in the committed CSV.

NFKC rewrites Hangul compatibility jamo (U+3131..) into conjoining jamo
(U+1100..), so an initial-consonant abbreviation reaches the provider as
different code points than the user typed. When normalization changes a
sample, the un-normalized text is also sent and recorded in raw_* columns.

The API key comes from OPENAI_API_KEY. When the variable is unset, only the
OPENAI_API_KEY line of the repository root .env is read. The key is never
printed or written, and error bodies are not echoed because OpenAI includes a
partially masked key in authentication errors.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENDPOINT = "https://api.openai.com/v1/moderations"
DEFAULT_MODEL = "omni-moderation-latest"
KEY_NAME = "OPENAI_API_KEY"
CATEGORIES = (
    "harassment",
    "harassment/threatening",
    "hate",
    "hate/threatening",
    "illicit",
    "illicit/violent",
    "self-harm",
    "self-harm/intent",
    "self-harm/instructions",
    "sexual",
    "sexual/minors",
    "violence",
    "violence/graphic",
)
GROUPS = (
    "targeted",
    "untargeted",
    "evasion",
    "fp_candidate",
    "normal",
    # Drawn by korean-profanity-aihub.py; these samples stay outside the repository.
    "aihub_abuse",
    "aihub_none",
)
SAMPLE_FIELDS = ("id", "group", "subtype", "expected", "text")
MAX_ATTEMPTS = 5
# Java \s without UNICODE_CHARACTER_CLASS: these Cc characters survive removal.
JAVA_ASCII_SPACE = frozenset(" \t\n\x0b\f\r")
ESCAPE_RE = re.compile(r"\\u([0-9a-fA-F]{4})")
WHITESPACE_RE = re.compile(r"\s+")


def decode_escapes(text: str) -> str:
    return ESCAPE_RE.sub(lambda match: chr(int(match.group(1), 16)), text)


def normalize(text: str) -> str:
    """Mirror UnicodeTextNormalizer normalization-v1."""
    compatibility = unicodedata.normalize("NFKC", text)
    visible = "".join(
        ch
        for ch in compatibility
        if ch in JAVA_ASCII_SPACE or unicodedata.category(ch) not in ("Cc", "Cf")
    )
    return WHITESPACE_RE.sub(" ", visible).strip()


def load_samples(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        missing = set(SAMPLE_FIELDS) - set(reader.fieldnames or ())
        if missing:
            raise SystemExit(f"samples: missing columns {sorted(missing)}")
        samples = list(reader)
    seen: set[str] = set()
    for row in samples:
        if row["id"] in seen:
            raise SystemExit(f"samples: duplicate id {row['id']}")
        seen.add(row["id"])
        if row["group"] not in GROUPS:
            raise SystemExit(f"samples: {row['id']} has unknown group {row['group']!r}")
        if row["expected"] not in ("block", "allow"):
            raise SystemExit(f"samples: {row['id']} has unknown expected {row['expected']!r}")
        if not normalize(decode_escapes(row["text"])):
            raise SystemExit(f"samples: {row['id']} is empty after normalization")
    return samples


def read_key() -> str | None:
    key = os.environ.get(KEY_NAME, "").strip()
    if key:
        return key
    env_file = ROOT / ".env"
    if not env_file.is_file():
        return None
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line.startswith("export "):
            line = line[len("export ") :].strip()
        name, sep, value = line.partition("=")
        if sep and name.strip() == KEY_NAME:
            value = value.strip().strip("'\"")
            return value or None
    return None


def moderate(key: str, model: str, text: str) -> dict:
    body = json.dumps({"model": model, "input": text}).encode("utf-8")
    for attempt in range(1, MAX_ATTEMPTS + 1):
        request = urllib.request.Request(
            ENDPOINT,
            data=body,
            method="POST",
            headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            retryable = error.code == 429 or error.code >= 500
            if not retryable or attempt == MAX_ATTEMPTS:
                raise SystemExit(f"moderation request failed: HTTP {error.code}") from None
            retry_after = error.headers.get("Retry-After", "")
            delay = float(retry_after) if retry_after.isdigit() else 2.0**attempt
            print(f"  HTTP {error.code}, retrying in {delay:.0f}s", file=sys.stderr)
            time.sleep(delay)
        except urllib.error.URLError as error:
            if attempt == MAX_ATTEMPTS:
                raise SystemExit(f"moderation request failed: {type(error.reason).__name__}") from None
            time.sleep(2.0**attempt)
    raise AssertionError("unreachable")


def outcome(expected: str, flagged: bool) -> str:
    if expected == "block":
        return "caught" if flagged else "missed"
    return "false_positive" if flagged else "passed"


def summarize(rows: list[dict[str, object]]) -> None:
    print()
    print("| group | samples | flagged | miss rate | false positive rate |")
    print("| --- | ---: | ---: | ---: | ---: |")
    for group in GROUPS:
        members = [row for row in rows if row["group"] == group]
        if not members:
            continue
        flagged = sum(1 for row in members if row["flagged"])
        missed = sum(1 for row in members if row["outcome"] == "missed")
        false_positive = sum(1 for row in members if row["outcome"] == "false_positive")
        blockable = sum(1 for row in members if row["expected"] == "block")
        allowable = len(members) - blockable
        miss_rate = f"{missed / blockable:.1%}" if blockable else "-"
        fp_rate = f"{false_positive / allowable:.1%}" if allowable else "-"
        print(f"| {group} | {len(members)} | {flagged} | {miss_rate} | {fp_rate} |")
    changed = [row for row in rows if row["normalization_changed"]]
    differs = [row["id"] for row in changed if row["raw_flagged"] != row["flagged"]]
    print(f"\nnormalization changed {len(changed)} samples; flagged differs from raw on {differs}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--samples", type=Path, required=True)
    parser.add_argument("--out", type=Path)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--pause", type=float, default=0.2, help="seconds between requests")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="validate samples and key presence without calling the API",
    )
    args = parser.parse_args()

    samples = load_samples(args.samples)
    key = read_key()
    counts = {group: sum(1 for row in samples if row["group"] == group) for group in GROUPS}
    print(f"samples: {len(samples)} {counts}")
    print(f"{KEY_NAME}: {'present' if key else 'missing'}")
    if args.dry_run:
        return 0
    if not key:
        raise SystemExit(f"{KEY_NAME} is not set in the environment or .env")
    if args.out is None:
        raise SystemExit("--out is required unless --dry-run is given")

    rows: list[dict[str, object]] = []
    for index, sample in enumerate(samples, start=1):
        raw = decode_escapes(sample["text"])
        text = normalize(raw)
        measured_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
        response = moderate(key, args.model, text)
        result = response["results"][0]
        scores = result["category_scores"]
        flagged_categories = [name for name in CATEGORIES if result["categories"].get(name)]
        top = max(CATEGORIES, key=lambda name: scores.get(name, 0.0))
        raw_flagged: object = ""
        raw_top_score = ""
        if text != raw:
            time.sleep(args.pause)
            raw_result = moderate(key, args.model, raw)["results"][0]
            raw_flagged = raw_result["flagged"]
            raw_top_score = f"{max(raw_result['category_scores'].values()):.6f}"
        row: dict[str, object] = {
            "id": sample["id"],
            "group": sample["group"],
            "subtype": sample["subtype"],
            "expected": sample["expected"],
            "text": sample["text"],
            "flagged": result["flagged"],
            "outcome": outcome(sample["expected"], result["flagged"]),
            "flagged_categories": ";".join(flagged_categories),
            "top_category": top,
            "top_score": f"{scores.get(top, 0.0):.6f}",
            "normalization_changed": text != raw,
            "raw_flagged": raw_flagged,
            "raw_top_score": raw_top_score,
            "model": response.get("model", ""),
            "measured_at": measured_at,
        }
        row.update({name: f"{scores.get(name, 0.0):.6f}" for name in CATEGORIES})
        rows.append(row)
        print(f"[{index}/{len(samples)}] {sample['id']} flagged={result['flagged']}")
        time.sleep(args.pause)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]), lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    models = sorted({str(row["model"]) for row in rows})
    print(f"\nwrote {len(rows)} rows to {args.out}; models: {models}")
    summarize(rows)
    return 0


if __name__ == "__main__":
    sys.exit(main())
