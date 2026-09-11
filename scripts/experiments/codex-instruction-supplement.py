#!/usr/bin/env python3
"""Collect the exact GH-223 outcome-informed supplementary eight-run batch."""

import argparse
import csv
from datetime import datetime, timezone
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import stat
import sys
import time


REPO_ROOT = Path(__file__).resolve().parents[2]
DOCS = REPO_ROOT / "docs/experiments/codex-agents"
ORIGINAL_RUNNER = Path(__file__).with_name("codex-instruction-phase2.py")
METRICS = Path(__file__).with_name("codex-instruction-metrics.py")
ORIGINAL_ENVIRONMENT = DOCS / "gh-223-lite-environment.json"
ORIGINAL_ORDER = DOCS / "gh-223-lite-execution-order.csv"
ORIGINAL_PLAN = (REPO_ROOT / "docs/superpowers/plans/"
                 "2026-09-11-codex-instruction-token-comparison-lite.md")
SUPPLEMENT_PLAN = (REPO_ROOT / "docs/superpowers/plans/"
                   "2026-09-12-codex-instruction-supplement-8.md")
SUPPLEMENT_ENVIRONMENT = DOCS / "gh-223-supplement-environment.json"
FIXTURES = DOCS / "gh-223-fixtures.json"
SPEC = (REPO_ROOT / "docs/superpowers/specs/"
        "2026-09-11-codex-instruction-architecture-phase2-design.md")
EVALUATION_DESIGN = DOCS / "gh-223-evaluation-design.md"
TOKENIZER_EVIDENCE = (REPO_ROOT / ".superpowers/sdd/"
                      "2026-09-11-codex-instruction-architecture-phase2/"
                      "scratch/task-7-tokenizer-evidence.json")
BATCH_ID = "GH-223-SUPPLEMENT-8-001"
SCHEMA_VERSION = "gh-223-supplement-1"
EXPECTED_MODEL = "gpt-5.6-sol"
EXPECTED_EFFORT = "high"
EXPECTED_CLI = "0.153.4"

SEQUENCE = (
    {"sequence": 1, "run_id": "sup1-r1-l2-b2", "repetition": 1,
     "cell": "L2", "candidate": "B2"},
    {"sequence": 2, "run_id": "sup1-r1-l2-b3", "repetition": 1,
     "cell": "L2", "candidate": "B3"},
    {"sequence": 3, "run_id": "sup1-r1-l3-b2", "repetition": 1,
     "cell": "L3", "candidate": "B2"},
    {"sequence": 4, "run_id": "sup1-r1-l3-b3", "repetition": 1,
     "cell": "L3", "candidate": "B3"},
    {"sequence": 5, "run_id": "sup1-r2-l2-b3", "repetition": 2,
     "cell": "L2", "candidate": "B3"},
    {"sequence": 6, "run_id": "sup1-r2-l2-b2", "repetition": 2,
     "cell": "L2", "candidate": "B2"},
    {"sequence": 7, "run_id": "sup1-r2-l3-b3", "repetition": 2,
     "cell": "L3", "candidate": "B3"},
    {"sequence": 8, "run_id": "sup1-r2-l3-b2", "repetition": 2,
     "cell": "L3", "candidate": "B2"},
)


def _load_original():
    spec = importlib.util.spec_from_file_location(
        "codex_instruction_phase2_frozen", ORIGINAL_RUNNER)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


ORIGINAL = _load_original()


def sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha256_file(path):
    return sha256_bytes(path.read_bytes())


def canonical_sha256(value):
    return sha256_bytes(json.dumps(
        value, ensure_ascii=False, sort_keys=True,
        separators=(",", ":")).encode("utf-8"))


def static_paths():
    return {
        "original_runner_sha256": ORIGINAL_RUNNER,
        "metrics_sha256": METRICS,
        "original_environment_sha256": ORIGINAL_ENVIRONMENT,
        "original_order_sha256": ORIGINAL_ORDER,
        "original_lite_plan_sha256": ORIGINAL_PLAN,
        "supplement_plan_sha256": SUPPLEMENT_PLAN,
        "collector_sha256": Path(__file__),
        "fixtures_sha256": FIXTURES,
        "spec_sha256": SPEC,
        "evaluation_design_sha256": EVALUATION_DESIGN,
        "tokenizer_evidence_sha256": TOKENIZER_EVIDENCE,
    }


def current_static_hashes():
    return {name: sha256_file(path) for name, path in static_paths().items()}


def order_contract_problems(rows):
    problems = []
    if len(rows) != len(SEQUENCE):
        problems.append("SUPPLEMENT_ORDER_LENGTH_MISMATCH")
    run_ids = [row.get("run_id") for row in rows]
    if len(set(run_ids)) != len(run_ids):
        problems.append("SUPPLEMENT_ORDER_DUPLICATE_RUN_ID")
    for index, expected in enumerate(SEQUENCE):
        actual = ({key: rows[index].get(key) for key in expected}
                  if index < len(rows) else None)
        if actual != expected:
            problems.append("SUPPLEMENT_ORDER_ROW_MISMATCH:{:02d}".format(index + 1))
    return sorted(set(problems))


def completed_sequence_problems(observed_ids):
    expected = [row["run_id"] for row in SEQUENCE]
    problems = []
    if len(set(observed_ids)) != len(observed_ids):
        problems.append("SUPPLEMENT_COMPLETED_DUPLICATE_RUN_ID")
    unknown = sorted(set(observed_ids) - set(expected))
    problems.extend("SUPPLEMENT_UNKNOWN_RUN:" + value for value in unknown)
    observed_set = set(observed_ids)
    completed = sum(run_id in observed_set for run_id in expected)
    if observed_set != set(expected[:completed]):
        problems.append("SUPPLEMENT_COMPLETED_SEQUENCE_MISMATCH")
    return sorted(set(problems))


def summary_usage_problems(summary, metrics):
    problems = []
    usage = summary.get("usage")
    if not isinstance(usage, dict):
        return ["SUPPLEMENT_USAGE_MISSING"]
    for phase in ("first", "total"):
        value = usage.get(phase)
        if not isinstance(value, dict) or value.get("status") != "available":
            problems.append("SUPPLEMENT_{}_USAGE_MISSING".format(phase.upper()))
    combined = summary.get("combined_total_usage")
    if not isinstance(combined, dict) or combined.get("status") != "available":
        problems.append("SUPPLEMENT_COMBINED_USAGE_MISSING")
    if not problems:
        problems.extend(metrics.validate_run_usage(summary))
    return sorted(set(problems))


def manifest_problems(manifest):
    problems = []
    if manifest.get("schema_version") != SCHEMA_VERSION:
        problems.append("SUPPLEMENT_SCHEMA_MISMATCH")
    if manifest.get("batch_id") != BATCH_ID:
        problems.append("SUPPLEMENT_BATCH_MISMATCH")
    problems.extend(order_contract_problems(manifest.get("sequence", [])))
    hashes = manifest.get("frozen_sha256")
    if not isinstance(hashes, dict):
        problems.append("SUPPLEMENT_FROZEN_HASHES_MISSING")
    else:
        for name, current in current_static_hashes().items():
            if hashes.get(name) != current:
                problems.append("SUPPLEMENT_STATIC_HASH_MISMATCH:" + name)
    if manifest.get("model") != EXPECTED_MODEL:
        problems.append("SUPPLEMENT_MODEL_MISMATCH")
    if manifest.get("reasoning_effort") != EXPECTED_EFFORT:
        problems.append("SUPPLEMENT_EFFORT_MISMATCH")
    if manifest.get("codex_cli") != EXPECTED_CLI:
        problems.append("SUPPLEMENT_CLI_MISMATCH")
    if manifest.get("source_profile") != "lite":
        problems.append("SUPPLEMENT_PROFILE_MISMATCH")
    return sorted(set(problems))


def original_manifest_and_problems():
    manifest = json.loads(ORIGINAL_ENVIRONMENT.read_text(encoding="utf-8"))
    problems = ORIGINAL.static_manifest_problems(
        manifest, SPEC, FIXTURES, EVALUATION_DESIGN, ORIGINAL_ORDER,
        profile=ORIGINAL.LITE_PROFILE, lite_plan_path=ORIGINAL_PLAN)
    if manifest.get("model") != EXPECTED_MODEL:
        problems.append("ORIGINAL_MODEL_MISMATCH")
    if manifest.get("reasoning_effort") != EXPECTED_EFFORT:
        problems.append("ORIGINAL_EFFORT_MISMATCH")
    codex_cli = manifest.get("codex_cli")
    if not isinstance(codex_cli, dict) or codex_cli.get("version") != EXPECTED_CLI:
        problems.append("ORIGINAL_CLI_MISMATCH")
    problems.extend(ORIGINAL.runtime_start_problems(
        manifest.get("runtime_evidence", {}), pilot=False,
        profile=ORIGINAL.LITE_PROFILE))
    return manifest, sorted(set(problems))


def candidate_parent_default():
    return ORIGINAL._candidate_parent_default(REPO_ROOT)


def tokenizer_path_default():
    return ORIGINAL._tokenizer_path_default(REPO_ROOT)


def row_with_source(row, original_manifest, candidate_parent):
    cell = original_manifest["cells"][row["cell"]]
    candidate = ORIGINAL._manifest_candidate(original_manifest, row["candidate"])
    candidate_root = ORIGINAL._candidate_root(candidate, candidate_parent)
    prompt = original_manifest["prompts"][cell["scenario"]]["text"]
    argv = ORIGINAL.build_codex_argv(
        candidate_root / cell["cwd"], cell["sandbox"], prompt)
    return {
        **row,
        "scenario": cell["scenario"],
        "cwd": cell["cwd"],
        "sandbox": cell["sandbox"],
        "prompt_sha256": cell["prompt_sha256"],
        "candidate_commit": candidate["candidate_commit"],
        "candidate_git_tree": candidate["git_tree"],
        "launch_argv_sha256": canonical_sha256(argv),
    }


def build_manifest(candidate_parent, tokenizer_pythonpath):
    original_manifest, problems = original_manifest_and_problems()
    tokenizer = ORIGINAL.activate_tokenizer(tokenizer_pythonpath, TOKENIZER_EVIDENCE)
    if tokenizer != original_manifest.get("tokenizer"):
        problems.append("TOKENIZER_ENVIRONMENT_MISMATCH")
    candidates = []
    for candidate_id in ("B2", "B3"):
        candidate = ORIGINAL._manifest_candidate(original_manifest, candidate_id)
        candidate_root = ORIGINAL._candidate_root(candidate, candidate_parent)
        candidate_problems, _, _ = ORIGINAL._validate_candidate_snapshot(
            candidate_root, candidate, require_clean=True)
        problems.extend(candidate_id + ":" + value for value in candidate_problems)
        frozen_candidate = {key: candidate[key] for key in (
            "id", "directory_name", "branch", "candidate_commit", "git_tree",
            "tracked_manifest_sha256", "fixture_file_manifest_sha256")}
        frozen_candidate["instruction_sources_sha256"] = candidate[
            "instruction_sources"]["sha256"]
        frozen_candidate["instruction_source_count"] = candidate[
            "instruction_sources"]["source_count"]
        candidates.append(frozen_candidate)
    rows = [row_with_source(dict(row), original_manifest, candidate_parent)
            for row in SEQUENCE]
    for row in rows:
        if row["sandbox"] != "read-only" or row["cwd"] != ".":
            problems.append("SUPPLEMENT_CELL_NOT_READ_ONLY_ROOT:" + row["run_id"])
    payload = {
        "schema_version": SCHEMA_VERSION,
        "batch_id": BATCH_ID,
        "status": "READY" if not problems else "BLOCKED",
        "outcome_informed_followup": True,
        "planned_parent_sessions": 8,
        "source_profile": "lite",
        "model": EXPECTED_MODEL,
        "reasoning_effort": EXPECTED_EFFORT,
        "codex_cli": EXPECTED_CLI,
        "frozen_sha256": current_static_hashes(),
        "original_comparison_environment_sha256": original_manifest[
            "comparison_environment_sha256"],
        "original_comparison_environment_core_sha256": original_manifest[
            "comparison_environment_core_sha256"],
        "original_runtime_evidence_sha256": canonical_sha256(
            original_manifest["runtime_evidence"]),
        "tokenizer": tokenizer,
        "candidates": candidates,
        "sequence": rows,
        "launch_equivalence": {
            "argv_builder": "codex-instruction-phase2.py:build_codex_argv",
            "process_runner": "codex-instruction-phase2.py:run_process",
            "stdout_parser": "codex-instruction-phase2.py:_load_jsonl",
            "runtime_parser": "codex-instruction-phase2.py:runtime_evidence_from_events",
            "usage_parser": "codex-instruction-metrics.py:extract_usage",
            "child_merger": "codex-instruction-metrics.py:merge_children",
            "stdin": "DEVNULL",
            "shell": False,
            "environment": "sanitized_allowlist",
        },
        "problems": sorted(set(problems)),
    }
    return payload


def verify(environment, candidate_parent, tokenizer_pythonpath):
    manifest_bytes = environment.read_bytes()
    manifest = json.loads(manifest_bytes.decode("utf-8"))
    problems = manifest_problems(manifest)
    original_manifest, original_problems = original_manifest_and_problems()
    problems.extend(original_problems)
    tokenizer = ORIGINAL.activate_tokenizer(tokenizer_pythonpath, TOKENIZER_EVIDENCE)
    if tokenizer != manifest.get("tokenizer") or tokenizer != original_manifest.get("tokenizer"):
        problems.append("TOKENIZER_ENVIRONMENT_MISMATCH")
    expected_rows = [row_with_source(dict(row), original_manifest, candidate_parent)
                     for row in SEQUENCE]
    if manifest.get("sequence") != expected_rows:
        problems.append("SUPPLEMENT_RESOLVED_SEQUENCE_MISMATCH")
    for candidate_id in ("B2", "B3"):
        candidate = ORIGINAL._manifest_candidate(original_manifest, candidate_id)
        candidate_root = ORIGINAL._candidate_root(candidate, candidate_parent)
        candidate_problems, _, _ = ORIGINAL._validate_candidate_snapshot(
            candidate_root, candidate, require_clean=True)
        problems.extend(candidate_id + ":" + value for value in candidate_problems)
    if manifest.get("status") != "READY" or manifest.get("problems") != []:
        problems.append("SUPPLEMENT_MANIFEST_NOT_READY")
    return manifest, original_manifest, manifest_bytes, sorted(set(problems))


def discover_summary_ids(raw_dir):
    return [path.name[:-len(".summary.json")]
            for path in raw_dir.glob("sup1-*.summary.json")]


def run_next(args):
    manifest, original_manifest, manifest_bytes, problems = verify(
        args.environment, args.candidate_parent, args.tokenizer_pythonpath)
    if problems:
        raise RuntimeError("supplement preflight failed: " + ",".join(problems))
    raw_resolved = ORIGINAL.ensure_restricted_directory(
        args.raw_dir, [REPO_ROOT, args.candidate_parent])
    observed = discover_summary_ids(args.raw_dir)
    sequence_problems = completed_sequence_problems(observed)
    if sequence_problems:
        raise RuntimeError("supplement sequence failed: " + ",".join(sequence_problems))
    if len(observed) >= len(SEQUENCE):
        raise RuntimeError("supplement batch is already complete")
    row = manifest["sequence"][len(observed)]
    if args.run_id != row["run_id"]:
        raise RuntimeError("supplement next run mismatch")
    candidate = ORIGINAL._manifest_candidate(original_manifest, row["candidate"])
    cell = original_manifest["cells"][row["cell"]]
    candidate_root = ORIGINAL._candidate_root(candidate, args.candidate_parent)
    before_problems, before, sources = ORIGINAL._validate_candidate_snapshot(
        candidate_root, candidate, require_clean=True)
    if before_problems:
        raise RuntimeError("candidate preflight failed: " + ",".join(before_problems))
    prompt = original_manifest["prompts"][cell["scenario"]]["text"]
    argv = ORIGINAL.build_codex_argv(
        candidate_root / cell["cwd"], cell["sandbox"], prompt)
    if canonical_sha256(argv) != row["launch_argv_sha256"]:
        raise RuntimeError("launch argv binding mismatch")
    hashes_before = current_static_hashes()
    started_epoch = time.time()
    process = ORIGINAL.run_process(
        argv, args.raw_dir, row["run_id"],
        forbidden_roots=[REPO_ROOT, args.candidate_parent, candidate_root])
    stdout_events = ORIGINAL._load_jsonl(process["stdout_path"])
    thread_id = ORIGINAL._thread_id(stdout_events)
    rollout_path = ORIGINAL._find_rollout(thread_id, args.session_root)
    rollout_events = ORIGINAL._load_jsonl(rollout_path)
    actual_runtime = ORIGINAL.runtime_evidence_from_events(
        rollout_events, profile=ORIGINAL.LITE_PROFILE)
    runtime_problems = ORIGINAL._runtime_matches(
        original_manifest["runtime_evidence"], actual_runtime,
        allow_capture=False, profile=ORIGINAL.LITE_PROFILE)
    metrics = ORIGINAL._metrics_module()
    parent_usage = metrics.extract_usage(rollout_events + stdout_events)
    children = []
    child_measurements = []
    child_runtime = []
    all_tool_events = list(rollout_events)
    child_problems = []
    expected_roles = original_manifest["runtime_evidence"].get(
        "child_role_fingerprints", {})
    for child_id, parent_id, _, child_events in ORIGINAL._descendant_rollouts(
            thread_id, args.session_root, started_epoch):
        evidence = ORIGINAL.runtime_evidence_from_events(
            child_events, profile=ORIGINAL.LITE_PROFILE)
        role = ORIGINAL.child_role_from_events(child_events)
        if role is None:
            child_problems.append("CHILD_ROLE_UNAVAILABLE")
        expected = expected_roles.get(role)
        if expected is None:
            child_problems.append("CHILD_ROLE_FINGERPRINT_MISSING")
        else:
            child_problems.extend(
                "CHILD_{}:{}".format(role, value)
                for value in ORIGINAL._fingerprint_matches(
                    expected, evidence, profile=ORIGINAL.LITE_PROFILE))
        child_runtime.append({"role": role, "fingerprint": evidence})
        children.append({
            "thread_id": child_id,
            "parent_thread_id": parent_id,
            "usage": metrics.extract_usage(child_events),
            "elapsed_seconds": None,
        })
        child_attributed = ORIGINAL.annotate_source_hints(child_events, sources)
        child_measurements.append(ORIGINAL._instruction_measurement(
            child_attributed, sources, metrics))
        all_tool_events.extend(child_events)
    merged = metrics.merge_children({
        "thread_id": thread_id,
        "usage": parent_usage,
        "elapsed_seconds": process["elapsed_seconds"],
    }, children)
    usage_shape = {
        "thread_id": thread_id,
        "usage": parent_usage,
        "elapsed_seconds": process["elapsed_seconds"],
        "child_calls": merged["child_calls"],
        "child_thread_ids": merged["child_thread_ids"],
        "child_sessions": children,
        "combined_total_usage": merged["combined_total_usage"],
    }
    usage_problems = metrics.validate_run_usage(usage_shape)
    parent_attributed = ORIGINAL.annotate_source_hints(rollout_events, sources)
    instruction = ORIGINAL.combine_instruction_measurements(
        ORIGINAL._instruction_measurement(parent_attributed, sources, metrics),
        child_measurements)
    tool_calls = metrics.count_tool_calls(all_tool_events)
    after_problems, after, _ = ORIGINAL._validate_candidate_snapshot(
        candidate_root, candidate, require_clean=True)
    changes = ORIGINAL._tracked_changes(candidate_root)
    if changes:
        after_problems.append("READ_ONLY_MUTATION")
    if before["head"] != after["head"] or before["tree"] != after["tree"]:
        after_problems.append("CANDIDATE_GIT_IDENTITY_CHANGED")
    if args.environment.read_bytes() != manifest_bytes:
        after_problems.append("SUPPLEMENT_ENVIRONMENT_CHANGED")
    if current_static_hashes() != hashes_before:
        after_problems.append("SUPPLEMENT_STATIC_INPUT_CHANGED")
    all_problems = sorted(set(runtime_problems + child_problems
                              + usage_problems + after_problems))
    if process["return_code"] != 0:
        all_problems.append("CODEX_EXIT_NONZERO")
    summary = {
        "schema_version": SCHEMA_VERSION,
        "batch_id": BATCH_ID,
        "profile": "lite",
        "run_id": row["run_id"],
        "run_kind": "outcome_informed_supplement",
        "order_sequence": row["sequence"],
        "repetition": row["repetition"],
        "cell": row["cell"],
        "candidate": row["candidate"],
        "scenario": row["scenario"],
        "cwd": row["cwd"],
        "sandbox": row["sandbox"],
        "prompt_hash": row["prompt_sha256"],
        "candidate_commit": candidate["candidate_commit"],
        "candidate_git_tree": candidate["git_tree"],
        "candidate_tracked_manifest_sha256": candidate[
            "tracked_manifest_sha256"],
        "candidate_fixture_file_manifest_sha256": candidate[
            "fixture_file_manifest_sha256"],
        "original_environment_sha256": sha256_file(ORIGINAL_ENVIRONMENT),
        "supplement_environment_sha256": sha256_bytes(manifest_bytes),
        "frozen_sha256": hashes_before,
        "launch_argv_sha256": canonical_sha256(argv),
        "launch_equivalence": manifest["launch_equivalence"],
        "model": EXPECTED_MODEL,
        "effort": EXPECTED_EFFORT,
        "version": EXPECTED_CLI,
        "started_at": datetime.fromtimestamp(
            started_epoch, timezone.utc).isoformat(),
        "thread_id": thread_id,
        "usage": parent_usage,
        "combined_total_usage": merged["combined_total_usage"],
        "tool_calls": tool_calls,
        "elapsed_seconds": process["elapsed_seconds"],
        "child_calls": merged["child_calls"],
        "child_thread_ids": merged["child_thread_ids"],
        "child_sessions": children,
        "child_elapsed_seconds": merged["child_elapsed_seconds"],
        "instruction": instruction,
        "runtime_evidence": actual_runtime,
        "child_runtime_evidence": child_runtime,
        "runtime_evidence_status": "COMPARED",
        "answer_checklist": [
            {"item": item, "result": "UNAVAILABLE"}
            for item in cell["answer_checklist"]
        ],
        "valid_run": not all_problems,
        "invalid_reason": all_problems,
        "execution_status": "EXECUTED",
    }
    ORIGINAL._write_restricted_json(
        args.raw_dir / (row["run_id"] + ".summary.json"), summary)
    print(json.dumps({
        "run_id": row["run_id"],
        "valid_run": summary["valid_run"],
        "invalid_reason": summary["invalid_reason"],
        "runtime_evidence_status": summary["runtime_evidence_status"],
        "raw_directory_mode": format(stat.S_IMODE(raw_resolved.stat().st_mode), "04o"),
    }, sort_keys=True))
    return 0 if summary["valid_run"] else 2


def record_checklist(args):
    ORIGINAL.ensure_restricted_directory(
        args.raw_dir, [REPO_ROOT, args.candidate_parent])
    expected = {row["run_id"]: row for row in SEQUENCE}
    if args.run_id not in expected:
        raise ValueError("unknown supplement run id")
    summary_path = args.raw_dir / (args.run_id + ".summary.json")
    summary_bytes = summary_path.read_bytes()
    summary = json.loads(summary_bytes.decode("utf-8"))
    if summary.get("run_id") != args.run_id:
        raise ValueError("summary run id mismatch")
    if len(args.results) != len(summary.get("answer_checklist", [])):
        raise ValueError("checklist result count mismatch")
    evidence = {
        "schema_version": SCHEMA_VERSION,
        "batch_id": BATCH_ID,
        "run_id": args.run_id,
        "summary_sha256": sha256_bytes(summary_bytes),
        "review_source": "raw_final_answer_and_tool_record",
        "items": [
            {"item": item["item"], "result": result}
            for item, result in zip(summary["answer_checklist"], args.results)
        ],
    }
    ORIGINAL._write_restricted_json(
        args.raw_dir / (args.run_id + ".checklist.json"), evidence)
    print(json.dumps({"run_id": args.run_id,
                      "results": args.results}, sort_keys=True))
    return 0


CSV_FIELDS = (
    "sequence", "run_id", "repetition", "cell", "candidate", "scenario",
    "prompt_sha256", "model", "reasoning_effort", "codex_version",
    "candidate_commit", "started_at", "elapsed_seconds", "first_input_tokens",
    "first_cached_input_tokens", "first_output_tokens",
    "first_reasoning_output_tokens", "total_input_tokens",
    "total_cached_input_tokens", "total_output_tokens",
    "total_reasoning_output_tokens", "combined_input_tokens",
    "combined_cached_input_tokens", "combined_output_tokens",
    "combined_reasoning_output_tokens", "child_calls", "tool_calls",
    "instruction_status", "R_start_bytes", "R_read_bytes", "checklist_1",
    "checklist_2", "checklist_3", "checklist_review_status", "valid_run",
    "runtime_evidence_status", "summary_sha256", "notes",
)


def aggregate(args):
    manifest, _, _, problems = verify(
        args.environment, args.candidate_parent, args.tokenizer_pythonpath)
    observed = discover_summary_ids(args.raw_dir)
    problems.extend(completed_sequence_problems(observed))
    expected_ids = [row["run_id"] for row in SEQUENCE]
    if set(observed) != set(expected_ids):
        problems.append("SUPPLEMENT_BATCH_INCOMPLETE")
    metrics = ORIGINAL._metrics_module()
    rows = []
    totals = {
        "elapsed_seconds": 0.0, "child_calls": 0, "tool_calls": 0,
        "first_input_tokens": 0, "first_cached_input_tokens": 0,
        "first_output_tokens": 0, "first_reasoning_output_tokens": 0,
        "total_input_tokens": 0, "total_cached_input_tokens": 0,
        "total_output_tokens": 0, "total_reasoning_output_tokens": 0,
        "combined_input_tokens": 0, "combined_cached_input_tokens": 0,
        "combined_output_tokens": 0, "combined_reasoning_output_tokens": 0,
    }
    for expected in manifest.get("sequence", []):
        run_id = expected["run_id"]
        summary_path = args.raw_dir / (run_id + ".summary.json")
        checklist_path = args.raw_dir / (run_id + ".checklist.json")
        if not summary_path.is_file() or not checklist_path.is_file():
            problems.append("SUPPLEMENT_EVIDENCE_MISSING:" + run_id)
            continue
        summary_bytes = summary_path.read_bytes()
        summary = json.loads(summary_bytes.decode("utf-8"))
        checklist = json.loads(checklist_path.read_text(encoding="utf-8"))
        summary_hash = sha256_bytes(summary_bytes)
        for key in ("run_id", "candidate", "cell", "repetition"):
            if summary.get(key) != expected[key]:
                problems.append("SUPPLEMENT_SUMMARY_BINDING_MISMATCH:{}:{}".format(
                    run_id, key))
        problems.extend(run_id + ":" + value
                        for value in summary_usage_problems(summary, metrics))
        if not summary.get("valid_run") or summary.get("invalid_reason") != []:
            problems.append("SUPPLEMENT_RUN_INVALID:" + run_id)
        if summary.get("runtime_evidence_status") != "COMPARED":
            problems.append("SUPPLEMENT_RUNTIME_NOT_COMPARED:" + run_id)
        if checklist.get("summary_sha256") != summary_hash:
            problems.append("SUPPLEMENT_CHECKLIST_HASH_MISMATCH:" + run_id)
        results = [item.get("result") for item in checklist.get("items", [])]
        if len(results) != 3 or any(value not in {"PASS", "FAIL"}
                                    for value in results):
            problems.append("SUPPLEMENT_CHECKLIST_INVALID:" + run_id)
            continue
        first = summary["usage"]["first"]
        total = summary["usage"]["total"]
        combined = summary["combined_total_usage"]
        row = {
            "sequence": expected["sequence"], "run_id": run_id,
            "repetition": expected["repetition"], "cell": expected["cell"],
            "candidate": expected["candidate"], "scenario": expected["scenario"],
            "prompt_sha256": expected["prompt_sha256"], "model": summary["model"],
            "reasoning_effort": summary["effort"], "codex_version": summary["version"],
            "candidate_commit": summary["candidate_commit"],
            "started_at": summary["started_at"],
            "elapsed_seconds": summary["elapsed_seconds"],
            "first_input_tokens": first["input_tokens"],
            "first_cached_input_tokens": first["cached_input_tokens"],
            "first_output_tokens": first["output_tokens"],
            "first_reasoning_output_tokens": first["reasoning_output_tokens"],
            "total_input_tokens": total["input_tokens"],
            "total_cached_input_tokens": total["cached_input_tokens"],
            "total_output_tokens": total["output_tokens"],
            "total_reasoning_output_tokens": total["reasoning_output_tokens"],
            "combined_input_tokens": combined["input_tokens"],
            "combined_cached_input_tokens": combined["cached_input_tokens"],
            "combined_output_tokens": combined["output_tokens"],
            "combined_reasoning_output_tokens": combined["reasoning_output_tokens"],
            "child_calls": summary["child_calls"],
            "tool_calls": summary["tool_calls"]["logical_total"],
            "instruction_status": summary["instruction"]["status"],
            "R_start_bytes": summary["instruction"]["R_start_bytes"],
            "R_read_bytes": summary["instruction"]["R_read_bytes"],
            "checklist_1": results[0], "checklist_2": results[1],
            "checklist_3": results[2],
            "checklist_review_status": "REVIEWED_FROM_RAW_FINAL_AND_TOOLS",
            "valid_run": str(summary["valid_run"]).lower(),
            "runtime_evidence_status": summary["runtime_evidence_status"],
            "summary_sha256": summary_hash,
            "notes": ("outcome_informed_followup;"
                      "summary_answer_checklist_retained_UNAVAILABLE;"
                      "instruction_attribution_{}".format(
                          summary["instruction"]["status"])),
        }
        rows.append(row)
        totals["elapsed_seconds"] += summary["elapsed_seconds"]
        totals["child_calls"] += summary["child_calls"]
        totals["tool_calls"] += summary["tool_calls"]["logical_total"]
        for prefix, usage in (("first", first), ("total", total),
                              ("combined", combined)):
            for target, source in (("input_tokens", "input_tokens"),
                                   ("cached_input_tokens", "cached_input_tokens"),
                                   ("output_tokens", "output_tokens"),
                                   ("reasoning_output_tokens", "reasoning_output_tokens")):
                totals[prefix + "_" + target] += usage[source]
    problems = sorted(set(problems))
    aggregate_value = {
        "schema_version": SCHEMA_VERSION,
        "batch_id": BATCH_ID,
        "execution_status": "COMPLETE" if not problems else "BLOCKED",
        "run_count": len(rows),
        "problems": problems,
        "totals": totals,
        "environment_sha256": sha256_file(args.environment),
        "summary_sha256": {row["run_id"]: row["summary_sha256"] for row in rows},
    }
    ORIGINAL._write_restricted_json(args.aggregate_output, aggregate_value)
    output = io.StringIO()
    writer = csv.DictWriter(output, fieldnames=CSV_FIELDS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    with ORIGINAL.restricted_open(args.csv_output) as handle:
        handle.write(output.getvalue().encode("utf-8"))
    print(json.dumps({
        "execution_status": aggregate_value["execution_status"],
        "run_count": len(rows), "problems": problems,
    }, sort_keys=True))
    return 0 if not problems else 2


def self_test():
    exact = [dict(row) for row in SEQUENCE]
    assert order_contract_problems(exact) == []
    assert "SUPPLEMENT_ORDER_LENGTH_MISMATCH" in order_contract_problems(exact[:-1])
    duplicate = [dict(row) for row in exact]
    duplicate[-1] = dict(duplicate[0])
    assert "SUPPLEMENT_ORDER_DUPLICATE_RUN_ID" in order_contract_problems(duplicate)
    out_of_order = [dict(row) for row in exact]
    out_of_order[0], out_of_order[1] = out_of_order[1], out_of_order[0]
    assert any(value.startswith("SUPPLEMENT_ORDER_ROW_MISMATCH")
               for value in order_contract_problems(out_of_order))
    assert completed_sequence_problems([row["run_id"] for row in SEQUENCE[:3]]) == []
    assert "SUPPLEMENT_COMPLETED_SEQUENCE_MISMATCH" in completed_sequence_problems(
        [SEQUENCE[0]["run_id"], SEQUENCE[2]["run_id"]])
    assert "SUPPLEMENT_COMPLETED_DUPLICATE_RUN_ID" in completed_sequence_problems(
        [SEQUENCE[0]["run_id"], SEQUENCE[0]["run_id"]])
    assert summary_usage_problems({}, object()) == ["SUPPLEMENT_USAGE_MISSING"]
    fake = {
        "schema_version": SCHEMA_VERSION, "batch_id": BATCH_ID,
        "sequence": exact, "frozen_sha256": current_static_hashes(),
        "model": EXPECTED_MODEL, "reasoning_effort": EXPECTED_EFFORT,
        "codex_cli": EXPECTED_CLI, "source_profile": "lite",
    }
    assert manifest_problems(fake) == []
    fake["frozen_sha256"] = dict(fake["frozen_sha256"])
    fake["frozen_sha256"]["original_environment_sha256"] = "0" * 64
    assert "SUPPLEMENT_STATIC_HASH_MISMATCH:original_environment_sha256" in (
        manifest_problems(fake))
    print(json.dumps({
        "status": "PASS",
        "checks": ["exact8", "missing", "duplicate", "out_of_order",
                   "missing_usage", "environment_drift"],
    }, sort_keys=True))
    return 0


def write_json_stdout(value):
    print(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    subparsers = parser.add_subparsers(dest="command")
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--environment", type=Path, default=SUPPLEMENT_ENVIRONMENT)
    common.add_argument("--candidate-parent", type=Path,
                        default=candidate_parent_default())
    common.add_argument("--tokenizer-pythonpath", type=Path,
                        default=tokenizer_path_default())
    prepare_parser = subparsers.add_parser("prepare", parents=[common])
    prepare_parser.add_argument("--output", type=Path)
    verify_parser = subparsers.add_parser("verify", parents=[common])
    run_parser = subparsers.add_parser("run-next", parents=[common])
    run_parser.add_argument("--raw-dir", type=Path, required=True)
    run_parser.add_argument("--session-root", type=Path,
                            default=ORIGINAL._session_root())
    run_parser.add_argument("--run-id", required=True)
    checklist_parser = subparsers.add_parser("record-checklist", parents=[common])
    checklist_parser.add_argument("--raw-dir", type=Path, required=True)
    checklist_parser.add_argument("--run-id", required=True)
    checklist_parser.add_argument("--results", nargs=3, required=True,
                                  choices=("PASS", "FAIL"))
    aggregate_parser = subparsers.add_parser("aggregate", parents=[common])
    aggregate_parser.add_argument("--raw-dir", type=Path, required=True)
    aggregate_parser.add_argument("--aggregate-output", type=Path, required=True)
    aggregate_parser.add_argument("--csv-output", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.self_test:
            return self_test()
        if args.command == "prepare":
            payload = build_manifest(args.candidate_parent,
                                     args.tokenizer_pythonpath)
            if args.output:
                args.output.write_text(json.dumps(
                    payload, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
                    encoding="utf-8")
            else:
                write_json_stdout(payload)
            return 0 if payload["status"] == "READY" else 2
        if args.command == "verify":
            _, _, _, problems = verify(
                args.environment, args.candidate_parent,
                args.tokenizer_pythonpath)
            print(json.dumps({"status": "PASS" if not problems else "BLOCKED",
                              "problems": problems}, sort_keys=True))
            return 0 if not problems else 2
        if args.command == "run-next":
            return run_next(args)
        if args.command == "record-checklist":
            return record_checklist(args)
        if args.command == "aggregate":
            return aggregate(args)
        parser.error("a subcommand is required")
    except (OSError, KeyError, TypeError, csv.Error, json.JSONDecodeError,
            RuntimeError, ValueError) as exc:
        print(json.dumps({"status": "BLOCKED", "error": str(exc)}, sort_keys=True),
              file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
