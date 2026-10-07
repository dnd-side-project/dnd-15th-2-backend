#!/usr/bin/env python3
"""Compare the two sides of a CI Benchmark run (GH-330).

Download every artifact of one run first:

    gh run download <run-id> -D <dir>

then run this script on <dir>. It checks that both sides ran the same tests and
compares task durations from the Gradle --profile report with a one-sided
Mann-Whitney U test (alternative: the head side is faster).
"""

from __future__ import annotations

import argparse
import html
import re
import statistics
import sys
import tempfile
import xml.etree.ElementTree as ET
from math import comb
from pathlib import Path

TASKS = (":integrationTest", ":test")
SIDES = ("base", "head")
ALPHA = 0.05
ROW_RE = re.compile(
    r'<td class="indentPath">(?P<task>[^<]+)</td>\s*<td class="numeric">(?P<duration>[^<]+)</td>'
)
TOTAL_RE = re.compile(
    r"<td>Total Build Time</td>\s*<td class=\"numeric\">(?P<duration>[^<]+)</td>"
)
DURATION_RE = re.compile(r"^(?:(\d+)h)?(?:(\d+)m)?(?:([\d.]+)s)?$")
CACHE_RE = re.compile(
    r"DefaultContextCache@(?P<id>\w+) size = (?P<size>\d+), maxSize = \d+, "
    r"parentContextCount = \d+, hitCount = \d+, missCount = (?P<miss>\d+)"
)
GC_PAUSE_RE = re.compile(
    r"\[gc\s*\] GC\(\d+\) Pause .*? (?P<before>\d+)(?P<bu>[KMG])->\d+[KMG]"
    r"\((?P<cap>\d+)(?P<cu>[KMG])\) (?P<ms>[\d.]+)ms"
)
UNIT_MB = {"K": 1 / 1024, "M": 1.0, "G": 1024.0}


def parse_duration(text: str) -> float:
    """Gradle profile durations look like 0.123s, 1m2.345s or 1h2m3.456s."""
    match = DURATION_RE.match(text.strip())
    if not match or not any(match.groups()):
        raise ValueError(f"unknown duration: {text!r}")
    hours, minutes, seconds = match.groups()
    return int(hours or 0) * 3600 + int(minutes or 0) * 60 + float(seconds or 0)


def read_meta(artifact: Path) -> dict[str, str]:
    meta = {}
    path = artifact / "benchmark" / "meta.txt"
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            key, _, value = line.partition("=")
            meta[key] = value
    match = re.fullmatch(r"benchmark-(base|head)-(\d+)", artifact.name)
    if match:
        meta.setdefault("side", match.group(1))
        meta.setdefault("attempt", match.group(2))
    return meta


def read_profile(artifact: Path) -> dict[str, float]:
    reports = sorted((artifact / "reports" / "profile").glob("profile-*.html"))
    if not reports:
        return {}
    text = reports[-1].read_text(encoding="utf-8")
    durations = {html.unescape(m["task"]): parse_duration(m["duration"]) for m in ROW_RE.finditer(text)}
    total = TOTAL_RE.search(text)
    if total:
        durations["total"] = parse_duration(total["duration"])
    return durations


def read_junit(artifact: Path, task: str) -> dict:
    counts = {"tests": 0, "skipped": 0, "failures": 0, "errors": 0}
    names: set[str] = set()
    caches: dict[str, tuple[int, int]] = {}
    for path in sorted((artifact / "test-results" / task.lstrip(":")).glob("*.xml")):
        suite = ET.parse(path).getroot()
        for key in counts:
            counts[key] += int(suite.get(key, 0))
        for case in suite.iter("testcase"):
            names.add(f"{case.get('classname')}#{case.get('name')}")
        out = suite.findtext("system-out") or ""
        for match in CACHE_RE.finditer(out):
            size, miss = int(match["size"]), int(match["miss"])
            old = caches.get(match["id"], (0, 0))
            caches[match["id"]] = (max(old[0], size), max(old[1], miss))
    return {
        "counts": counts,
        "names": names,
        # missCount is cumulative per JVM, so the largest value per cache is the number of contexts loaded.
        "contexts": sum(miss for _, miss in caches.values()) if caches else None,
        "max_cache_size": max((size for size, _ in caches.values()), default=None),
    }


def read_gc(artifact: Path) -> dict | None:
    pauses = []
    for path in sorted((artifact / "gc").glob("*.log")):
        for match in GC_PAUSE_RE.finditer(path.read_text(encoding="utf-8", errors="replace")):
            pauses.append((
                int(match["before"]) * UNIT_MB[match["bu"]],
                int(match["cap"]) * UNIT_MB[match["cu"]],
                float(match["ms"]),
            ))
    if not pauses:
        return None
    return {
        "peak_used_mb": max(p[0] for p in pauses),
        "max_capacity_mb": max(p[1] for p in pauses),
        "pause_ms": sum(p[2] for p in pauses),
        "pauses": len(pauses),
    }


def load(root: Path) -> list[dict]:
    jobs = []
    for artifact in sorted(p for p in root.iterdir() if p.is_dir()):
        meta = read_meta(artifact)
        if meta.get("side") not in SIDES:
            continue
        jobs.append({
            "artifact": artifact.name,
            "side": meta["side"],
            "sha": meta.get("sha", "unknown"),
            "profile": read_profile(artifact),
            "junit": {task: read_junit(artifact, task) for task in TASKS},
            "gc": read_gc(artifact),
        })
    return jobs


def mann_whitney_less(head: list[float], base: list[float]) -> float:
    """Exact one-sided p-value that head is stochastically smaller than base (mid-ranks for ties)."""
    pooled = sorted([(v, 0) for v in head] + [(v, 1) for v in base])
    doubled_ranks = [0] * len(pooled)
    i = 0
    while i < len(pooled):
        j = i
        while j + 1 < len(pooled) and pooled[j + 1][0] == pooled[i][0]:
            j += 1
        for k in range(i, j + 1):
            doubled_ranks[k] = i + j + 2  # twice the mid-rank, keeps sums integral
        i = j + 1
    observed = sum(r for r, (_, group) in zip(doubled_ranks, pooled) if group == 0)
    n = len(head)
    # ways[k][s]: subsets of size k whose doubled rank sum is s
    ways = [dict() for _ in range(n + 1)]
    ways[0][0] = 1
    for rank in doubled_ranks:
        for k in range(n - 1, -1, -1):
            for s, count in ways[k].items():
                ways[k + 1][s + rank] = ways[k + 1].get(s + rank, 0) + count
    extreme = sum(count for s, count in ways[n].items() if s <= observed)
    return extreme / comb(len(pooled), n)


def shift_interval(head: list[float], base: list[float]) -> tuple[float, float, float]:
    """Hodges-Lehmann shift (head - base) with an approximate 95% distribution-free interval."""
    diffs = sorted(h - b for h in head for b in base)
    n, m = len(head), len(base)
    estimate = statistics.median(diffs)
    # smallest k with P(U < k) > alpha/2 under the null, from the exact U distribution without ties
    ways = [dict() for _ in range(n + 1)]
    ways[0][0] = 1
    for rank in range(1, n + m + 1):
        for k in range(n - 1, -1, -1):
            for s, count in ways[k].items():
                ways[k + 1][s + rank] = ways[k + 1].get(s + rank, 0) + count
    offset = n * (n + 1) // 2
    total = comb(n + m, n)
    cumulative, k = 0, 0
    while True:
        cumulative += ways[n].get(k + offset, 0)
        if cumulative / total > ALPHA / 2:
            break
        k += 1
    k = max(k, 1)
    return estimate, diffs[k - 1], diffs[len(diffs) - k]


def summarize(jobs: list[dict]) -> int:
    by_side = {side: [j for j in jobs if j["side"] == side] for side in SIDES}
    print("== Runs")
    for side in SIDES:
        shas = sorted({j["sha"] for j in by_side[side]})
        print(f"{side}: {len(by_side[side])} jobs, commit {', '.join(s[:12] for s in shas) or '-'}")
    if not all(by_side.values()):
        print("Both sides need at least one job.")
        return 1

    print("\n== Test consistency")
    consistent = True
    for task in TASKS:
        counts = {tuple(sorted(j["junit"][task]["counts"].items())) for j in jobs}
        names = {frozenset(j["junit"][task]["names"]) for j in jobs}
        failed = sum(j["junit"][task]["counts"]["failures"] + j["junit"][task]["counts"]["errors"] for j in jobs)
        ok = len(counts) == 1 and len(names) == 1 and failed == 0
        consistent &= ok
        sample = dict(next(iter(counts))) if len(counts) == 1 else "differs between jobs"
        print(f"{task}: {'same' if ok else 'MISMATCH'} {sample} failures+errors={failed}")

    print("\n== Durations (seconds, from --profile)")
    print("task | base median [min-max] | head median [min-max] | shift head-base [95% interval] | one-sided p")
    verdicts = {}
    for task in (*TASKS, "total"):
        series = {side: [j["profile"][task] for j in by_side[side] if task in j["profile"]] for side in SIDES}
        if not all(series.values()):
            print(f"{task} | no data")
            continue
        base, head = series["base"], series["head"]
        p = mann_whitney_less(head, base)
        shift, low, high = shift_interval(head, base)
        verdicts[task] = p
        print(f"{task} | {statistics.median(base):.1f} [{min(base):.1f}-{max(base):.1f}] n={len(base)} | "
              f"{statistics.median(head):.1f} [{min(head):.1f}-{max(head):.1f}] n={len(head)} | "
              f"{shift:+.1f} [{low:+.1f}, {high:+.1f}] | {p:.4f}")

    print("\n== Integration test JVM")
    for side in SIDES:
        contexts = [j["junit"][":integrationTest"]["contexts"] for j in by_side[side]]
        gcs = [j["gc"] for j in by_side[side] if j["gc"]]
        context_text = statistics.median(c for c in contexts if c is not None) if any(c is not None for c in contexts) else "no cache log"
        if gcs:
            peak = statistics.median(g["peak_used_mb"] for g in gcs)
            cap = max(g["max_capacity_mb"] for g in gcs)
            pause = statistics.median(g["pause_ms"] for g in gcs) / 1000
            gc_text = f"peak heap {peak:.0f} MB of {cap:.0f} MB, gc pauses {pause:.1f} s (medians)"
        else:
            gc_text = "no gc log"
        print(f"{side}: contexts loaded {context_text} (median), {gc_text}")

    print("\n== Verdict")
    if not consistent:
        print("Tests differ between jobs; durations are not comparable.")
        return 1
    for task, p in verdicts.items():
        result = "head is faster" if p < ALPHA else "no difference shown"
        print(f"{task}: {result} (one-sided p={p:.4f}, alpha={ALPHA})")
    return 0


def _write_fixture(root: Path, side: str, attempt: int, seconds: float) -> None:
    artifact = root / f"benchmark-{side}-{attempt}"
    (artifact / "benchmark").mkdir(parents=True)
    (artifact / "benchmark" / "meta.txt").write_text(f"side={side}\nattempt={attempt}\nsha={side * 10}\n")
    (artifact / "reports" / "profile").mkdir(parents=True)
    minutes, rest = divmod(seconds, 60)
    (artifact / "reports" / "profile" / "profile-2026-10-08-00-00-00.html").write_text(
        '<tr>\n<td>Total Build Time</td>\n<td class="numeric">13m5.000s</td>\n</tr>\n'
        f'<tr>\n<td class="indentPath">:integrationTest</td>\n<td class="numeric">{int(minutes)}m{rest:.3f}s</td>\n<td></td>\n</tr>\n'
        '<tr>\n<td class="indentPath">:test</td>\n<td class="numeric">25.900s</td>\n<td></td>\n</tr>\n'
    )
    for task in ("test", "integrationTest"):
        (artifact / "test-results" / task).mkdir(parents=True)
        (artifact / "test-results" / task / "TEST-a.xml").write_text(
            '<testsuite tests="2" skipped="0" failures="0" errors="0">'
            '<testcase classname="A" name="one"/><testcase classname="A" name="two"/>'
            "<system-out>Spring test ApplicationContext cache statistics: [DefaultContextCache@1a "
            "size = 1, maxSize = 32, parentContextCount = 0, hitCount = 3, missCount = 95, failureCount = 0]"
            "</system-out></testsuite>"
        )
    (artifact / "gc").mkdir()
    (artifact / "gc" / "integrationTest-1.log").write_text(
        "[2026-10-08T00:00:00.000+0000][1.0s][info][gc          ] GC(0) Pause Young (Normal) "
        "(G1 Evacuation Pause) 245M->54M(512M) 8.123ms\n"
    )


def self_test() -> list[str]:
    errors = []
    if parse_duration("1m2.500s") != 62.5 or parse_duration("1h0m1s") != 3601 or parse_duration("0s") != 0:
        errors.append("duration parsing")
    # all three head values below all base values: exact p = 1 / C(6, 3)
    if abs(mann_whitney_less([1, 2, 3], [4, 5, 6]) - 1 / 20) > 1e-12:
        errors.append("exact p for complete separation")
    if abs(mann_whitney_less([4, 5, 6], [1, 2, 3]) - 1.0) > 1e-12:
        errors.append("exact p for the opposite direction")
    if mann_whitney_less([1, 1, 1], [1, 1, 1]) != 1.0:
        errors.append("ties must not show a difference")
    estimate, low, high = shift_interval([10.0] * 10, [20.0] * 10)
    if (estimate, low, high) != (-10.0, -10.0, -10.0):
        errors.append("shift interval for constant samples")
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        for attempt in range(1, 11):
            _write_fixture(root, "base", attempt, 690 + attempt)
            _write_fixture(root, "head", attempt, 400 + attempt)
        jobs = load(root)
        if len(jobs) != 20 or jobs[0]["junit"][":integrationTest"]["contexts"] != 95:
            errors.append("fixture loading")
        if jobs[0]["gc"]["peak_used_mb"] != 245 or jobs[0]["profile"][":integrationTest"] != 691:
            errors.append("gc or profile parsing")
        head = [j["profile"][":integrationTest"] for j in jobs if j["side"] == "head"]
        base = [j["profile"][":integrationTest"] for j in jobs if j["side"] == "base"]
        if mann_whitney_less(head, base) >= ALPHA:
            errors.append("clear speedup not detected")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("directory", nargs="?", type=Path, help="output of gh run download -D")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        errors = self_test()
        for error in errors:
            print(f"self-test failed: {error}")
        print("self-test passed" if not errors else "")
        return 1 if errors else 0
    if not args.directory or not args.directory.is_dir():
        parser.error("directory is required")
    return summarize(load(args.directory))


if __name__ == "__main__":
    sys.exit(main())
