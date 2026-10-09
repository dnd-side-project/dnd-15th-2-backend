#!/usr/bin/env python3
"""Sample AI Hub text-ethics sentences and simulate rule v2 terms (GH-336).

The AI Hub dataset (텍스트 윤리검증 데이터) cannot be redistributed, so every
file this script writes must stay outside the repository. Only aggregate
numbers go into the GH-336 report.

    # Draw a fixed-seed sample in the korean-profanity-probe.py samples format.
    python3 scripts/experiments/korean-profanity-aihub.py sample \\
        --labels <dir>/talksets-train-6.json --out <outside-repo>/aihub-samples.csv

    # Rule-only hit rates over every ABUSE and IMMORAL_NONE sentence.
    python3 scripts/experiments/korean-profanity-aihub.py simulate \\
        --labels <dir>/talksets-train-6.json

    # OpenAI flagged OR rule hit, per group, for any probe results file.
    python3 scripts/experiments/korean-profanity-aihub.py simulate \\
        --results docs/experiments/korean-profanity/gh-336-results.csv

Rule matching mirrors ResourceLocalRuleEngine: NFKC, lower case, drop every
character that is not a letter or digit, then substring match.
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TERM_TIERS = (
    (
        "base",
        (
            "씨발", "시발", "씨팔", "씨바", "씌발", "씨빨", "좆", "좃", "존나", "졸라",
            "병신", "븅신", "개새끼", "개쉑", "지랄", "썅", "염병", "느금마", "니애미",
        ),
    ),
    ("choseong", ("ㅅㅂ", "ㅆㅂ", "ㅂㅅ", "ㄱㅅㄲ", "ㅈㄴ", "ㅈㄹ")),
    ("latin", ("sibal", "tlqkf", "qudtls", "byungsin")),
)
ESCAPE_RE = re.compile(r"\\u([0-9a-fA-F]{4})")


def fold(text: str) -> str:
    compatibility = unicodedata.normalize("NFKC", text).lower()
    return "".join(ch for ch in compatibility if ch.isalnum())


def tier_terms(tiers: int) -> list[str]:
    return [fold(term) for _, terms in TERM_TIERS[:tiers] for term in terms]


def rule_hit(text: str, terms: list[str]) -> bool:
    folded = fold(ESCAPE_RE.sub(lambda m: chr(int(m.group(1), 16)), text))
    return any(term in folded for term in terms)


def load_sentences(path: Path) -> list[dict]:
    if ROOT in path.resolve().parents:
        raise SystemExit("AI Hub data must stay outside the repository")
    talksets = json.loads(path.read_text(encoding="utf-8"))
    return [sentence for talkset in talksets for sentence in talkset["sentences"]]


def split(sentences: list[dict]) -> tuple[list[dict], list[dict]]:
    abuse = [s for s in sentences if "ABUSE" in s["types"]]
    none = [s for s in sentences if s["types"] == ["IMMORAL_NONE"]]
    return abuse, none


def sample(args: argparse.Namespace) -> int:
    if ROOT in args.out.resolve().parents:
        raise SystemExit("--out must be outside the repository")
    abuse, none = split(load_sentences(args.labels))
    rng = random.Random(args.seed)
    picked_abuse = rng.sample(abuse, args.per_group)
    picked_none = rng.sample(none, args.per_group)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(("id", "group", "subtype", "expected", "text"))
        for s in picked_abuse:
            subtype = "abuse_only" if s["types"] == ["ABUSE"] else "abuse_with_other"
            writer.writerow((s["id"], "aihub_abuse", subtype, "block", s["text"]))
        for s in picked_none:
            writer.writerow((s["id"], "aihub_none", "immoral_none", "allow", s["text"]))
    print(f"population: ABUSE {len(abuse)}, IMMORAL_NONE {len(none)}")
    print(f"wrote {2 * args.per_group} samples (seed {args.seed}) to {args.out}")
    return 0


def simulate_labels(path: Path) -> None:
    abuse, none = split(load_sentences(path))
    abuse_only = [s for s in abuse if s["types"] == ["ABUSE"]]
    print("| terms | ABUSE hit | ABUSE-only hit | IMMORAL_NONE hit |")
    print("| --- | ---: | ---: | ---: |")
    for tiers in range(1, len(TERM_TIERS) + 1):
        terms = tier_terms(tiers)
        rates = []
        for population in (abuse, abuse_only, none):
            hits = sum(1 for s in population if rule_hit(s["text"], terms))
            rates.append(f"{hits}/{len(population)} ({hits / len(population):.1%})")
        label = " + ".join(name for name, _ in TERM_TIERS[:tiers])
        print(f"| {label} | {' | '.join(rates)} |")
    terms = tier_terms(len(TERM_TIERS))
    by_term = {}
    for term in terms:
        hits = sum(1 for s in none if term in fold(s["text"]))
        if hits:
            by_term[term] = hits
    print(f"\nIMMORAL_NONE hits by term: {dict(sorted(by_term.items(), key=lambda kv: -kv[1]))}")


def simulate_results(path: Path) -> None:
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    groups = list(dict.fromkeys(row["group"] for row in rows))
    print("| terms | " + " | ".join(groups) + " |")
    print("| --- |" + " ---: |" * len(groups))
    for tiers in range(0, len(TERM_TIERS) + 1):
        terms = tier_terms(tiers)
        cells = []
        for group in groups:
            members = [row for row in rows if row["group"] == group]
            wrong = 0
            for row in members:
                final = row["flagged"] == "True" or rule_hit(row["text"], terms)
                wrong += final != (row["expected"] == "block")
            cells.append(f"{wrong}/{len(members)}")
        label = "openai only" if tiers == 0 else " + ".join(n for n, _ in TERM_TIERS[:tiers])
        print(f"| {label} | {' | '.join(cells)} |")
    print("\ncells count misses for block groups and false positives for allow groups")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    sample_parser = commands.add_parser("sample")
    sample_parser.add_argument("--labels", type=Path, required=True)
    sample_parser.add_argument("--out", type=Path, required=True)
    sample_parser.add_argument("--per-group", type=int, default=300)
    sample_parser.add_argument("--seed", type=int, default=336)
    simulate_parser = commands.add_parser("simulate")
    simulate_parser.add_argument("--labels", type=Path)
    simulate_parser.add_argument("--results", type=Path)
    args = parser.parse_args()

    if args.command == "sample":
        return sample(args)
    if not args.labels and not args.results:
        parser.error("simulate needs --labels or --results")
    if args.labels:
        simulate_labels(args.labels)
    if args.results:
        simulate_results(args.results)
    return 0


if __name__ == "__main__":
    sys.exit(main())
