#!/usr/bin/env python3
"""Prepare and run the approved GH-223 Phase 2 evaluation."""

import argparse
import csv
import hashlib
import importlib.util
import json
import os
import platform
import re
import stat
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path


EXPECTED_MODEL = "gpt-5.6-sol"
EXPECTED_EFFORT = "high"
EXPECTED_CLI_VERSION = "0.153.4"
SCHEMA_VERSION = 2
HEX_SHA256 = re.compile(r"^[0-9a-f]{64}$")
FULL_PROFILE = "full"
LITE_PROFILE = "lite"
CELL_LAYOUT = {
    "01": ("S1", ".", "read-only"),
    "02": ("S2", ".", "read-only"),
    "03": ("S3", ".", "read-only"),
    "04": ("S4", ".", "read-only"),
    "05": ("S5", ".", "read-only"),
    "06": ("S6", ".", "read-only"),
    "07": ("S7", ".", "read-only"),
    "08": ("S1", "infra", "read-only"),
    "09": ("S1", "src/test", "read-only"),
    "10": ("S1", "src/integrationTest", "read-only"),
    "11": ("X1", ".", "read-only"),
    "12": ("X2", ".", "read-only"),
    "13": ("N1", ".", "read-only"),
    "14": ("N2", ".", "read-only"),
    "15": ("N3", ".", "read-only"),
    "16": ("M1", ".", "workspace-write"),
    "17": ("M2", ".", "workspace-write"),
}
CANDIDATE_ROTATION = {
    1: ("B0", "B1", "B2", "B3"),
    2: ("B1", "B2", "B3", "B0"),
    3: ("B2", "B3", "B0", "B1"),
}
LITE_CANDIDATE_ROTATION = {
    1: ("B0", "B1", "B2", "B3"),
    2: ("B1", "B2", "B3", "B0"),
}
NORMAL_CELLS = ("01", "02", "08", "09", "10", "11", "12", "16", "17")
SMOKE_ALLOWLIST = {
    "16": {
        "docs/experiments/codex-agents/smoke/intake-guide.md",
        "docs/experiments/codex-agents/smoke/document-report.md",
    },
    "17": {
        "src/test/java/com/dnd/qello/feed/service/AccountEligibilityGateInstructionSmokeTest.java",
        "docs/experiments/codex-agents/smoke/test-report.md",
    },
}
SAFE_ENVIRONMENT_KEYS = {
    "PATH", "HOME", "CODEX_HOME", "LANG", "LC_ALL", "LC_CTYPE", "TMPDIR",
    "SSL_CERT_FILE", "SSL_CERT_DIR",
}
RUNTIME_FINGERPRINT_KEYS = {
    "base_instructions_sha256", "tool_catalog_sha256", "plugin_catalog_sha256",
    "resolved_model", "resolved_reasoning_effort", "cli_version",
}
LITE_RUNTIME_FINGERPRINT_KEYS = {
    "base_instructions_sha256", "dynamic_tools_sha256",
    "dynamic_tool_namespace_projection_sha256", "dynamic_tools_source",
    "dynamic_tools_serialization", "dynamic_tools_scope",
    "full_tool_catalog_status", "plugin_catalog_status",
    "resolved_model", "resolved_reasoning_effort", "cli_version",
}


def _repo_root():
    return Path(__file__).resolve().parents[2]


def _sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def _sha256_file(path):
    return _sha256_bytes(path.read_bytes())


def _canonical_sha256(value):
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True,
                         separators=(",", ":")).encode("utf-8")
    return _sha256_bytes(encoded)


def _tokenizer_path_default(repo_root):
    pointer = (repo_root / ".superpowers/sdd/2026-09-11-codex-instruction-architecture-phase2/"
                           "scratch/task-7-tokenizer-path.txt")
    if not pointer.is_file():
        return None
    value = pointer.read_text(encoding="utf-8").strip()
    return Path(value) if value else None


def activate_tokenizer(tokenizer_pythonpath, evidence_path):
    if tokenizer_pythonpath is None or evidence_path is None:
        raise RuntimeError("isolated tokenizer path and evidence are required")
    resolved = tokenizer_pythonpath.expanduser().resolve()
    if not resolved.is_dir():
        raise RuntimeError("isolated tokenizer directory is missing")
    if str(resolved) not in sys.path:
        sys.path.insert(0, str(resolved))
    import tiktoken
    evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
    if evidence.get("package") != "tiktoken" or evidence.get("version") != tiktoken.__version__:
        raise RuntimeError("tokenizer version evidence mismatch")
    if evidence.get("encoding") != "o200k_base":
        raise RuntimeError("tokenizer encoding evidence mismatch")
    encoding = tiktoken.get_encoding("o200k_base")
    if encoding.name != "o200k_base":
        raise RuntimeError("o200k_base failed to load")
    if evidence.get("python_version") != platform.python_version():
        raise RuntimeError("tokenizer Python version evidence mismatch")
    allowlisted = {
        key: evidence[key] for key in (
            "package", "version", "encoding", "sample_tokens",
            "mergeable_ranks_sha256", "special_tokens_sha256", "pattern_sha256",
            "python_version",
        ) if key in evidence
    }
    allowlisted["runtime_load"] = "PASS"
    allowlisted["evidence_sha256"] = _sha256_file(evidence_path)
    return allowlisted


def _run_read_only(argv, cwd=None):
    completed = subprocess.run(
        argv, cwd=cwd, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, check=False, text=True,
    )
    if completed.returncode != 0:
        raise RuntimeError("read-only command failed: " + " ".join(argv[:3]))
    return completed.stdout.rstrip("\n")


def build_codex_argv(candidate_cwd, sandbox, prompt, verification_cwd=None):
    if sandbox not in {"read-only", "workspace-write"}:
        raise ValueError("sandbox must be read-only or workspace-write")
    if sandbox == "workspace-write" and verification_cwd is None:
        raise ValueError("workspace-write requires verification_cwd")
    if sandbox == "read-only" and verification_cwd is not None:
        raise ValueError("read-only does not accept verification_cwd")
    argv = [
        "codex", "exec", "--json", "--model", EXPECTED_MODEL,
        "--config", 'model_reasoning_effort="high"',
        "--sandbox", sandbox, "--cd", str(candidate_cwd),
    ]
    if sandbox == "workspace-write":
        argv += ["--add-dir", str(verification_cwd)]
    argv.append(prompt)
    return argv


def sanitized_environment(environment=None):
    source = os.environ if environment is None else environment
    return {key: value for key, value in source.items()
            if key in SAFE_ENVIRONMENT_KEYS and isinstance(value, str)}


def _is_within(path, root):
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


def ensure_restricted_directory(path, forbidden_roots):
    resolved = path.expanduser().resolve()
    for forbidden in forbidden_roots:
        root = Path(forbidden).expanduser().resolve()
        if resolved == root or _is_within(resolved, root) or _is_within(root, resolved):
            raise ValueError("raw directory must be outside repository and worktrees")
    if path.exists() and path.is_symlink():
        raise ValueError("raw directory must not be a symlink")
    path.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(path, 0o700)
    if stat.S_IMODE(path.stat().st_mode) != 0o700:
        raise PermissionError("raw directory mode is not 0700")
    return resolved


def restricted_open(path):
    descriptor = os.open(str(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    return os.fdopen(descriptor, "wb")


def _restricted_input_file(base, relative):
    if not isinstance(relative, str) or not relative or Path(relative).is_absolute():
        raise ValueError("restricted artifact path must be relative")
    resolved = (base / relative).resolve()
    if not _is_within(resolved, base) or resolved.is_symlink() or not resolved.is_file():
        raise ValueError("restricted artifact is outside its package")
    if stat.S_IMODE(resolved.stat().st_mode) != 0o600:
        raise PermissionError("restricted artifact mode is not 0600")
    return resolved


def _write_restricted_json(path, value):
    handle = restricted_open(path)
    try:
        handle.write((json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2)
                      + "\n").encode("utf-8"))
    finally:
        handle.close()


def run_process(argv, raw_dir, run_id, popen_factory=subprocess.Popen, environment=None,
                forbidden_roots=None):
    if not isinstance(argv, list) or not all(isinstance(value, str) for value in argv):
        raise TypeError("argv must be a list of strings")
    ensure_restricted_directory(raw_dir, forbidden_roots or [])
    stdout_path = raw_dir / (run_id + ".stdout.jsonl")
    stderr_path = raw_dir / (run_id + ".stderr.txt")
    stdout_handle = restricted_open(stdout_path)
    stderr_handle = restricted_open(stderr_path)
    started = time.monotonic()
    try:
        process = popen_factory(
            argv, stdin=subprocess.DEVNULL, stdout=stdout_handle, stderr=stderr_handle,
            env=sanitized_environment(environment), shell=False,
        )
        process.communicate()
        return_code = process.returncode
    finally:
        stdout_handle.close()
        stderr_handle.close()
    return {
        "return_code": return_code,
        "elapsed_seconds": time.monotonic() - started,
        "stdout_path": stdout_path,
        "stderr_path": stderr_path,
    }


def extract_exact_prompts(spec_path):
    text = spec_path.read_text(encoding="utf-8")
    matches = re.findall(
        r"^### (S[1-7]|X[12]|N[1-3]|M[12])\s+[^\n]*\n\n```text\n(.*?)\n```",
        text, flags=re.MULTILINE | re.DOTALL,
    )
    prompts = {}
    for name, prompt in matches:
        if name in prompts:
            raise ValueError("duplicate prompt: " + name)
        prompts[name] = prompt
    expected = {"S1", "S2", "S3", "S4", "S5", "S6", "S7",
                "X1", "X2", "N1", "N2", "N3", "M1", "M2"}
    if set(prompts) != expected:
        raise ValueError("exact prompt set mismatch")
    return prompts


def extract_lite_contract(plan_path):
    text = plan_path.read_text(encoding="utf-8")
    expected_sources = {"L1": "S1", "L2": "X2", "L3": "X1"}
    prompts = {}
    cells = {}
    for cell, expected_source in expected_sources.items():
        section_match = re.search(
            r"^### {} —[^\n]*\n(.*?)(?=^### L[123] —|^pilot은|\Z)".format(cell),
            text, flags=re.MULTILINE | re.DOTALL)
        if section_match is None:
            raise ValueError("lite contract section missing: " + cell)
        section = section_match.group(1)
        source_match = re.search(r"^- Source scenario: (\S+)$", section, re.MULTILINE)
        cwd_match = re.search(r"^- cwd: `([^`]+)`$", section, re.MULTILINE)
        sandbox_match = re.search(r"^- sandbox: `([^`]+)`$", section, re.MULTILINE)
        prompt_match = re.search(r"```text\n(.*?)\n```", section, re.DOTALL)
        if not all((source_match, cwd_match, sandbox_match, prompt_match)):
            raise ValueError("lite contract fields missing: " + cell)
        source = source_match.group(1)
        if source != expected_source:
            raise ValueError("lite source scenario mismatch: " + cell)
        prompt = prompt_match.group(1)
        checklist_text = section[prompt_match.end():]
        checklist = re.findall(r"^- (.+)$", checklist_text, re.MULTILINE)
        if not checklist:
            raise ValueError("lite checklist missing: " + cell)
        prompts[source] = prompt
        cells[cell] = {
            "scenario": source,
            "cwd": cwd_match.group(1),
            "sandbox": sandbox_match.group(1),
            "prompt_sha256": _sha256_bytes(prompt.encode("utf-8")),
            "answer_checklist": checklist,
        }
    return prompts, cells


def cell_manifest(prompts):
    return {
        cell: {
            "scenario": scenario,
            "cwd": cwd,
            "sandbox": sandbox,
            "prompt_sha256": _sha256_bytes(prompts[scenario].encode("utf-8")),
        }
        for cell, (scenario, cwd, sandbox) in CELL_LAYOUT.items()
    }


def write_execution_order(path, cells, profile=FULL_PROFILE):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow([
            "sequence", "run_id", "repetition", "cell", "candidate", "scenario",
            "cwd", "sandbox", "prompt_sha256", "status",
        ])
        for row in expected_execution_rows(cells, profile=profile):
            writer.writerow([row[field] for field in (
                "sequence", "run_id", "repetition", "cell", "candidate", "scenario",
                "cwd", "sandbox", "prompt_sha256", "status",
            )])
    os.replace(temporary, path)


def expected_execution_rows(cells, profile=FULL_PROFILE):
    if profile not in {FULL_PROFILE, LITE_PROFILE}:
        raise ValueError("unknown evaluation profile")
    rotations = (CANDIDATE_ROTATION if profile == FULL_PROFILE
                 else LITE_CANDIDATE_ROTATION)
    rows = []
    sequence = 0
    for repetition in rotations:
        for cell in sorted(cells):
            for candidate in rotations[repetition]:
                sequence += 1
                entry = cells[cell]
                rows.append({
                    "sequence": str(sequence),
                    "run_id": ("r{}-c{}-{}".format(repetition, cell, candidate.lower())
                               if profile == FULL_PROFILE else
                               "r{}-{}-{}".format(
                                   repetition, cell.lower(), candidate.lower())),
                    "repetition": str(repetition),
                    "cell": cell,
                    "candidate": candidate,
                    "scenario": entry["scenario"],
                    "cwd": entry["cwd"],
                    "sandbox": entry["sandbox"],
                    "prompt_sha256": entry["prompt_sha256"],
                    "status": "PLANNED",
                })
    return rows


def profile_manifest_problems(manifest, expected_profile):
    actual = manifest.get("profile", FULL_PROFILE)
    return [] if actual == expected_profile else ["INPUT_PROFILE_MISMATCH"]


def execution_order_problems(rows, cells, profile):
    problems = []
    run_ids = [row.get("run_id") for row in rows if isinstance(row, dict)]
    if len(run_ids) != len(set(run_ids)):
        problems.append("EXECUTION_ORDER_DUPLICATE_RUN_ID")
    for row in rows:
        if not isinstance(row, dict):
            continue
        cell = cells.get(row.get("cell"))
        if cell is not None and row.get("prompt_sha256") != cell.get("prompt_sha256"):
            problems.append("EXECUTION_ORDER_PROMPT_HASH_MISMATCH")
    if rows != expected_execution_rows(cells, profile=profile):
        problems.append("EXECUTION_ORDER_MISMATCH")
    return sorted(set(problems))


def pilot_run_id(candidate, cell, profile=FULL_PROFILE):
    if profile == LITE_PROFILE:
        return "pilot-lite-{}-{}".format(candidate.lower(), cell.lower())
    return "pilot-{}-c{}".format(candidate.lower(), cell)


def expected_pilot_rows(cells, profile=FULL_PROFILE):
    rows = []
    for candidate, cell, run_id in sorted(_expected_pilots(profile), key=lambda row: row[2]):
        entry = cells[cell]
        rows.append({
            "run_id": run_id,
            "candidate": candidate,
            "cell": cell,
            "scenario": entry["scenario"],
            "cwd": entry["cwd"],
            "sandbox": entry["sandbox"],
            "prompt_sha256": entry["prompt_sha256"],
            "status": "PILOT_PLANNED",
        })
    return rows


def protect_profile_outputs(profile, environment_output, order_output, docs_root):
    if profile != LITE_PROFILE:
        return
    full_paths = {
        (docs_root / "gh-223-environment.json").resolve(),
        (docs_root / "gh-223-execution-order.csv").resolve(),
    }
    if environment_output.resolve() in full_paths or order_output.resolve() in full_paths:
        raise ValueError("lite profile must not overwrite full artifacts")


def validate_runtime_fingerprint(evidence, profile=FULL_PROFILE):
    if not evidence:
        return ["RUNTIME_EVIDENCE_MISSING"]
    problems = []
    hash_keys = (["base_instructions_sha256", "tool_catalog_sha256",
                  "plugin_catalog_sha256"] if profile == FULL_PROFILE else
                 ["base_instructions_sha256", "dynamic_tools_sha256",
                  "dynamic_tool_namespace_projection_sha256"])
    for key in hash_keys:
        if not isinstance(evidence.get(key), str) or not HEX_SHA256.fullmatch(evidence[key]):
            problems.append("RUNTIME_HASH_INVALID:" + key)
    if evidence.get("resolved_model") != EXPECTED_MODEL:
        problems.append("RUNTIME_MODEL_MISMATCH")
    if evidence.get("resolved_reasoning_effort") != EXPECTED_EFFORT:
        problems.append("RUNTIME_EFFORT_MISMATCH")
    cli_version = evidence.get("cli_version")
    if cli_version != EXPECTED_CLI_VERSION:
        problems.append("RUNTIME_CLI_MISMATCH")
    if profile == LITE_PROFILE:
        serialization = evidence.get("dynamic_tools_serialization")
        if serialization == "malformed":
            problems.append("RUNTIME_DYNAMIC_TOOLS_MALFORMED")
        elif serialization == "unsupported_cli_serialization":
            problems.append("RUNTIME_DYNAMIC_TOOLS_SERIALIZATION_UNSUPPORTED")
        elif serialization not in {"empty_omitted_for_cli_0.153.4", "explicit_list"}:
            problems.append("RUNTIME_DYNAMIC_TOOLS_SERIALIZATION_INVALID")
        if evidence.get("dynamic_tools_source") != "session_meta.dynamic_tools":
            problems.append("RUNTIME_DYNAMIC_TOOLS_SOURCE_MISMATCH")
        if evidence.get("dynamic_tools_scope") != "dynamic_supplement_only":
            problems.append("RUNTIME_DYNAMIC_TOOLS_SCOPE_MISMATCH")
        if evidence.get("full_tool_catalog_status") != (
                "unavailable_from_serialized_rollout"):
            problems.append("RUNTIME_FULL_TOOL_CATALOG_STATUS_INVALID")
        if evidence.get("plugin_catalog_status") != (
                "unavailable_from_serialized_rollout"):
            problems.append("RUNTIME_PLUGIN_CATALOG_STATUS_INVALID")
        if serialization == "empty_omitted_for_cli_0.153.4":
            empty_hash = _canonical_sha256([])
            if evidence.get("dynamic_tools_sha256") != empty_hash:
                problems.append("RUNTIME_DYNAMIC_TOOLS_EMPTY_HASH_MISMATCH")
            if evidence.get("dynamic_tool_namespace_projection_sha256") != empty_hash:
                problems.append("RUNTIME_DYNAMIC_TOOL_PROJECTION_EMPTY_HASH_MISMATCH")
    return problems


def _expected_pilots(profile):
    if profile == LITE_PROFILE:
        return {("B0", "L1", "pilot-lite-b0-l1"),
                ("B3", "L2", "pilot-lite-b3-l2")}
    return {("B0", "01", "pilot-b0-c01"),
            ("B3", "12", "pilot-b3-c12")}


def validate_runtime_evidence(evidence, allow_capture, profile=FULL_PROFILE):
    if not evidence or evidence.get("status") == "PENDING_APPROVED_PILOT_PAIR":
        return [] if allow_capture else ["RUNTIME_EVIDENCE_MISSING"]
    if evidence.get("status") != "APPROVED_PILOT_PAIR":
        return ["RUNTIME_PROMOTION_REQUIRED"]
    problems = profile_manifest_problems(evidence, profile)
    problems.extend(validate_runtime_fingerprint(
        evidence.get("parent_fingerprint"), profile=profile))
    bindings = evidence.get("pilot_bindings")
    expected_pilots = _expected_pilots(profile)
    observed = set()
    if not isinstance(bindings, list) or len(bindings) != 2:
        problems.append("RUNTIME_PILOT_PAIR_INVALID")
    else:
        for binding in bindings:
            if not isinstance(binding, dict):
                problems.append("RUNTIME_PILOT_BINDING_INVALID")
                continue
            observed.add((binding.get("candidate"), binding.get("cell"),
                          binding.get("run_id")))
            for key in ("summary_sha256", "raw_evidence_sha256",
                        "measurement_review_sha256"):
                if not isinstance(binding.get(key), str) or not HEX_SHA256.fullmatch(binding[key]):
                    problems.append("RUNTIME_PILOT_HASH_INVALID:" + key)
        if observed != expected_pilots:
            problems.append("RUNTIME_PILOT_PAIR_INVALID")
    children = evidence.get("child_role_fingerprints")
    if not isinstance(children, dict):
        problems.append("RUNTIME_CHILD_ROLE_FINGERPRINTS_INVALID")
    else:
        for role, fingerprint in children.items():
            if not isinstance(role, str) or not role:
                problems.append("RUNTIME_CHILD_ROLE_INVALID")
            problems.extend("CHILD_ROLE_{}:{}".format(role, problem)
                            for problem in validate_runtime_fingerprint(
                                fingerprint, profile=profile))
    review_hash_key = ("coordinator_review_sha256" if profile == LITE_PROFILE
                       else "independent_review_sha256")
    for key in (review_hash_key, "pilot_environment_manifest_sha256",
                "comparison_environment_core_sha256"):
        if not isinstance(evidence.get(key), str) or not HEX_SHA256.fullmatch(evidence[key]):
            problems.append("RUNTIME_BINDING_HASH_INVALID:" + key)
    instrumentation = evidence.get("instrumentation")
    source_documents = evidence.get("source_documents")
    if not isinstance(instrumentation, dict) or not isinstance(source_documents, dict):
        problems.append("RUNTIME_REVISION_BINDING_INVALID")
    else:
        for namespace, values in (("instrumentation", instrumentation),
                                  ("source_documents", source_documents)):
            for key, value in values.items():
                if not isinstance(key, str) or not isinstance(value, str) or not HEX_SHA256.fullmatch(value):
                    problems.append("RUNTIME_REVISION_HASH_INVALID:" + namespace)
    return sorted(set(problems))


def runtime_start_problems(evidence, pilot, profile=FULL_PROFILE):
    if evidence.get("status") == "PENDING_APPROVED_PILOT_PAIR" or not evidence:
        return [] if pilot else ["RUNTIME_EVIDENCE_MISSING"]
    return validate_runtime_evidence(evidence, allow_capture=False, profile=profile)


def approved_pilot_cell(candidate, cell, profile=FULL_PROFILE):
    return (candidate.upper(), cell, pilot_run_id(candidate, cell, profile)) in (
        _expected_pilots(profile))


def read_runtime_evidence(path, profile=FULL_PROFILE):
    if path is None:
        return {}
    if path.expanduser().is_symlink():
        raise ValueError("runtime evidence package must not be a symlink")
    resolved_package = path.expanduser().resolve()
    if stat.S_IMODE(resolved_package.parent.stat().st_mode) != 0o700:
        raise PermissionError("runtime evidence package directory mode is not 0700")
    if stat.S_IMODE(resolved_package.stat().st_mode) != 0o600:
        raise PermissionError("runtime evidence package mode is not 0600")
    value = json.loads(resolved_package.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("runtime evidence must be an object")
    if value.get("status") != "APPROVED_PILOT_PAIR":
        raise ValueError("runtime evidence requires an approved pilot pair")
    if profile_manifest_problems(value, profile):
        raise ValueError("runtime evidence profile mismatch")
    artifacts = value.get("pilot_artifacts")
    review_name = "coordinator_review" if profile == LITE_PROFILE else "independent_review"
    review_entry = value.get(review_name)
    if not isinstance(artifacts, list) or len(artifacts) != 2 or not isinstance(review_entry, dict):
        raise ValueError("runtime evidence promotion package is incomplete")

    base = resolved_package.parent
    loaded = []
    for artifact in artifacts:
        if not isinstance(artifact, dict):
            raise ValueError("runtime pilot artifact entry must be an object")
        summary_path = _restricted_input_file(base, artifact.get("summary_file"))
        evidence_path = _restricted_input_file(base, artifact.get("evidence_file"))
        summary_bytes = summary_path.read_bytes()
        evidence_bytes = evidence_path.read_bytes()
        if _sha256_bytes(summary_bytes) != artifact.get("summary_sha256"):
            raise ValueError("pilot summary hash mismatch")
        if _sha256_bytes(evidence_bytes) != artifact.get("raw_evidence_sha256"):
            raise ValueError("pilot raw evidence hash mismatch")
        loaded.append({**artifact,
                       "summary": json.loads(summary_bytes.decode("utf-8")),
                       "evidence": json.loads(evidence_bytes.decode("utf-8"))})

    review_path = _restricted_input_file(base, review_entry.get("file"))
    review_bytes = review_path.read_bytes()
    if _sha256_bytes(review_bytes) != review_entry.get("sha256"):
        raise ValueError("pilot review hash mismatch")
    review = json.loads(review_bytes.decode("utf-8"))
    return {"status": "PROMOTION_PACKAGE_LOADED", "artifacts": loaded,
            "review": review, "review_sha256": _sha256_bytes(review_bytes)}


def _git_snapshot(path):
    return {
        "head": _run_read_only(["git", "-C", str(path), "rev-parse", "HEAD"]),
        "tree": _run_read_only(["git", "-C", str(path), "rev-parse", "HEAD^{tree}"]),
        "branch": _run_read_only(["git", "-C", str(path), "branch", "--show-current"]),
        "status": _run_read_only(["git", "-C", str(path), "status", "--short"]),
    }


def _tracked_manifest_sha256(path):
    listing = _run_read_only(["git", "-C", str(path), "ls-tree", "-r", "HEAD"])
    return _sha256_bytes((listing + "\n").encode("utf-8"))


def _instruction_role(relative):
    if relative in {"AGENTS.md", "TASK.md", "CLAUDE.md"} or relative.endswith("/AGENTS.md"):
        return "repository_instruction"
    if relative.startswith(".agents/skills/") and relative.endswith(".md"):
        return "skill_instruction"
    if relative.startswith("agents/") and relative.endswith(".md"):
        return "role_instruction"
    if relative in {"docs/harness/instruction-links.json",
                    "scripts/validate-instruction-links.py",
                    "scripts/validate-task-contract.py",
                    "scripts/validate-conventions.py",
                    "scripts/run-hook.py",
                    ".github/workflows/harness-policy.yml"}:
        return "policy_enforcement"
    return None


def _decode_yaml_scalar(raw):
    value = raw.strip()
    if len(value) >= 2 and value[0] == value[-1] == '"':
        try:
            decoded = json.loads(value)
        except json.JSONDecodeError as exc:
            raise ValueError("unsupported double-quoted YAML description") from exc
        if not isinstance(decoded, str):
            raise ValueError("YAML description must decode to text")
        return decoded, "decoded_yaml_scalar"
    if len(value) >= 2 and value[0] == value[-1] == "'":
        return value[1:-1].replace("''", "'"), "decoded_yaml_scalar"
    return value, "source_bytes"


def build_source_manifest(candidate_root):
    tracked = _run_read_only(["git", "-C", str(candidate_root), "ls-files"]).splitlines()
    sources = []
    for relative in tracked:
        role = _instruction_role(relative)
        if role is None:
            continue
        path = candidate_root / relative
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        sources.append({
            "path": relative,
            "content": content,
            "content_sha256": _sha256_bytes(content.encode("utf-8")),
            "bytes": len(content.encode("utf-8")),
            "role": role,
            "delivery_phases": ["read", "start"] if relative.endswith("AGENTS.md")
                               else ["read"],
            "base_offset": 0,
        })
        if (relative.startswith(".agents/skills/") and relative.endswith("/SKILL.md")
                and content.startswith("---\n")):
            closing = content.find("\n---\n", 4)
            frontmatter = content[4:closing] if closing >= 0 else ""
            description = re.search(r"^description:\s*(.+)$", frontmatter, re.MULTILINE)
            if description is not None:
                raw_value = description.group(1).strip()
                value, offset_basis = _decode_yaml_scalar(raw_value)
                character_offset = content.find(raw_value, 4, closing)
                if value and character_offset >= 0:
                    sources.append({
                        "path": relative,
                        "content": value,
                        "content_sha256": _sha256_bytes(value.encode("utf-8")),
                        "bytes": len(value.encode("utf-8")),
                        "role": "skill_catalog_metadata",
                        "delivery_phases": ["start"],
                        "base_offset": (len(content[:character_offset].encode("utf-8"))
                                        if offset_basis == "source_bytes" else 0),
                        "offset_basis": offset_basis,
                    })
    return sources


def _source_manifest_public(sources):
    rows = [{key: source[key] for key in (
        "path", "content_sha256", "bytes", "role", "delivery_phases", "base_offset",
        "offset_basis") if key in source}
            for source in sources]
    return {
        "source_count": len(rows),
        "sha256": _canonical_sha256(rows),
        "sources": rows,
    }


def _candidate_parent_default(repo_root):
    return repo_root.parent.parent


def _candidate_root(candidate, parent):
    return parent / candidate["directory_name"]


def _verify_common_files(root, common_files):
    problems = []
    for relative, expected in common_files.items():
        path = root / relative
        if not path.is_file():
            problems.append("COMMON_FILE_MISSING:" + relative)
            continue
        data = path.read_bytes()
        if len(data) != expected["bytes"] or _sha256_bytes(data) != expected["sha256"]:
            problems.append("COMMON_FILE_HASH_MISMATCH:" + relative)
    return problems


def _verify_file_manifest(root, file_manifest):
    problems = []
    for relative, expected in file_manifest.items():
        path = root / relative
        if not path.is_file():
            problems.append("CANDIDATE_FILE_MISSING:" + relative)
            continue
        data = path.read_bytes()
        if len(data) != expected["bytes"] or _sha256_bytes(data) != expected["sha256"]:
            problems.append("CANDIDATE_FILE_HASH_MISMATCH:" + relative)
    return problems


def cumulative_candidate_files(candidates):
    cumulative = {}
    current = {}
    for candidate in candidates:
        current = dict(current)
        current.update(candidate.get("files") or candidate.get("changed_files") or {})
        cumulative[candidate["id"]] = current
    return cumulative


def _file_state(root, relative):
    path = root / relative
    if path.is_symlink():
        return {"state": "symlink"}
    if not path.exists():
        return {"state": "absent"}
    if not path.is_file():
        return {"state": "non_file"}
    data = path.read_bytes()
    return {"state": "file", "bytes": len(data), "sha256": _sha256_bytes(data)}


def _checkout_state_manifest(root):
    return {relative: _file_state(root, relative)
            for relative in sorted(_tracked_changes(root))}


def read_smoke_manifest(path):
    if path is None:
        return {}
    if path.expanduser().is_symlink():
        raise ValueError("smoke package must not be a symlink")
    resolved_package = path.expanduser().resolve()
    if stat.S_IMODE(resolved_package.parent.stat().st_mode) != 0o700:
        raise PermissionError("smoke package directory mode is not 0700")
    if stat.S_IMODE(resolved_package.stat().st_mode) != 0o600:
        raise PermissionError("smoke package mode is not 0600")
    value = json.loads(resolved_package.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or value.get("status") != "APPROVED":
        raise ValueError("smoke manifest must be approved")
    review_entry = value.get("independent_review")
    if not isinstance(review_entry, dict):
        raise ValueError("smoke manifest independent review is missing")
    base = resolved_package.parent
    review_path = _restricted_input_file(base, review_entry.get("file"))
    review_bytes = review_path.read_bytes()
    if _sha256_bytes(review_bytes) != review_entry.get("sha256"):
        raise ValueError("smoke independent review hash mismatch")
    review = json.loads(review_bytes.decode("utf-8"))
    if review.get("status") != "PASS":
        raise ValueError("smoke independent review is not PASS")
    return {**value, "_package_root": str(base), "_review": review,
            "_review_sha256": _sha256_bytes(review_bytes)}


def _normalize_smoke_environment(package, candidates, candidate_parent):
    if not package:
        return {"status": "PENDING_TASK9",
                "required": "approved evaluation-owned starting fixture and checkout pair"}
    if package.get("evaluation_owned") is not True:
        raise ValueError("smoke checkouts must be evaluation-owned")
    entries = package.get("checkouts")
    if not isinstance(entries, dict) or set(entries) != {"B0", "B1", "B2", "B3"}:
        raise ValueError("smoke manifest must bind all candidates")
    package_root = Path(package["_package_root"])
    canonical_roots = {_candidate_root(candidate, candidate_parent).resolve()
                       for candidate in candidates}
    all_checkout_paths = set()
    normalized = {}
    state_bindings = {}
    for candidate in candidates:
        candidate_id = candidate["id"]
        entry = entries[candidate_id]
        if not isinstance(entry, dict):
            raise ValueError("smoke checkout entry must be an object")
        candidate_root = (package_root / entry.get("candidate_checkout", "")).resolve()
        verification_root = (package_root / entry.get("verification_checkout", "")).resolve()
        if candidate_root == verification_root or candidate_root in canonical_roots or (
                verification_root in canonical_roots):
            raise ValueError("smoke checkout separation failed")
        if candidate_root in all_checkout_paths or verification_root in all_checkout_paths:
            raise ValueError("smoke checkout is reused")
        all_checkout_paths.update({candidate_root, verification_root})
        candidate_problems, _, _ = _validate_candidate_snapshot(
            candidate_root, candidate, require_clean=False)
        verification_problems, _, _ = _validate_candidate_snapshot(
            verification_root, candidate, require_clean=False)
        if candidate_problems or verification_problems:
            raise ValueError("smoke checkout revision mismatch: " + candidate_id)
        candidate_state = _checkout_state_manifest(candidate_root)
        verification_state = _checkout_state_manifest(verification_root)
        if candidate_state != entry.get("candidate_starting_files"):
            raise ValueError("smoke candidate starting fixture mismatch: " + candidate_id)
        if verification_state != entry.get("verification_starting_files"):
            raise ValueError("smoke verification starting fixture mismatch: " + candidate_id)
        state_bindings[candidate_id] = {
            "candidate_starting_files": candidate_state,
            "verification_starting_files": verification_state,
        }
        normalized[candidate_id] = {
            "candidate_checkout_identity_sha256": _sha256_bytes(
                str(candidate_root).encode("utf-8")),
            "verification_checkout_identity_sha256": _sha256_bytes(
                str(verification_root).encode("utf-8")),
            "candidate_starting_files": candidate_state,
            "verification_starting_files": verification_state,
            "starting_fixture_sha256": _canonical_sha256(state_bindings[candidate_id]),
        }
    fixture_revision = _canonical_sha256(state_bindings)
    if fixture_revision != package.get("fixture_revision_sha256"):
        raise ValueError("smoke fixture revision hash mismatch")
    review = package["_review"]
    if (review.get("fixture_revision_sha256") != fixture_revision
            or review.get("reviewed_candidates") != ["B0", "B1", "B2", "B3"]):
        raise ValueError("smoke independent review binding mismatch")
    return {
        "status": "APPROVED",
        "evaluation_owned": True,
        "fixture_revision_sha256": fixture_revision,
        "independent_review_sha256": package["_review_sha256"],
        "checkouts": normalized,
        "candidate_model_diff_allowlist": {
            cell: sorted(paths) for cell, paths in sorted(SMOKE_ALLOWLIST.items())
        },
        "verification_model_diff_allowlist": {
            cell: sorted({"docs/api/openapi.json"} | paths)
            for cell, paths in sorted(SMOKE_ALLOWLIST.items())
        },
    }


def validate_smoke_environment(smoke, allow_pending):
    if not isinstance(smoke, dict):
        return ["SMOKE_ENVIRONMENT_INVALID"]
    if smoke.get("status") == "PENDING_TASK9":
        return [] if allow_pending else ["SMOKE_ENVIRONMENT_NOT_APPROVED"]
    if smoke.get("status") != "APPROVED" or smoke.get("evaluation_owned") is not True:
        return ["SMOKE_ENVIRONMENT_INVALID"]
    problems = []
    checkouts = smoke.get("checkouts")
    if not isinstance(checkouts, dict) or set(checkouts) != {"B0", "B1", "B2", "B3"}:
        problems.append("SMOKE_CHECKOUT_SET_INVALID")
        return problems
    identities = []
    state_bindings = {}
    for candidate_id, entry in checkouts.items():
        if not isinstance(entry, dict):
            problems.append("SMOKE_CHECKOUT_ENTRY_INVALID:" + candidate_id)
            continue
        for key in ("candidate_checkout_identity_sha256",
                    "verification_checkout_identity_sha256"):
            value = entry.get(key)
            if not isinstance(value, str) or not HEX_SHA256.fullmatch(value):
                problems.append("SMOKE_CHECKOUT_IDENTITY_INVALID:{}:{}".format(
                    candidate_id, key))
            else:
                identities.append(value)
        state = {
            "candidate_starting_files": entry.get("candidate_starting_files"),
            "verification_starting_files": entry.get("verification_starting_files"),
        }
        if not all(isinstance(value, dict) for value in state.values()):
            problems.append("SMOKE_STARTING_STATE_INVALID:" + candidate_id)
            continue
        if entry.get("starting_fixture_sha256") != _canonical_sha256(state):
            problems.append("SMOKE_STARTING_FIXTURE_HASH_INVALID:" + candidate_id)
        state_bindings[candidate_id] = state
    if len(identities) != len(set(identities)):
        problems.append("SMOKE_CHECKOUT_IDENTITY_REUSED")
    if smoke.get("fixture_revision_sha256") != _canonical_sha256(state_bindings):
        problems.append("SMOKE_FIXTURE_REVISION_INVALID")
    if not isinstance(smoke.get("independent_review_sha256"), str) or not HEX_SHA256.fullmatch(
            smoke["independent_review_sha256"]):
        problems.append("SMOKE_REVIEW_HASH_INVALID")
    expected_candidate_allowlist = {
        cell: sorted(paths) for cell, paths in sorted(SMOKE_ALLOWLIST.items())
    }
    expected_verification_allowlist = {
        cell: sorted({"docs/api/openapi.json"} | paths)
        for cell, paths in sorted(SMOKE_ALLOWLIST.items())
    }
    if smoke.get("candidate_model_diff_allowlist") != expected_candidate_allowlist:
        problems.append("SMOKE_CANDIDATE_ALLOWLIST_INVALID")
    if smoke.get("verification_model_diff_allowlist") != expected_verification_allowlist:
        problems.append("SMOKE_VERIFICATION_ALLOWLIST_INVALID")
    return sorted(set(problems))


def _instruction_metric_problems(instruction, require_available, label):
    if not isinstance(instruction, dict):
        return [label + ":INSTRUCTION_SCHEMA"]
    status = instruction.get("status")
    if status == "unavailable":
        problems = []
        if instruction.get("C_structure_tokens") is not None:
            problems.append(label + ":UNAVAILABLE_INSTRUCTION_HAS_SCORE")
        if require_available:
            problems.append(label + ":SELECTION_INSTRUCTION_UNAVAILABLE")
        return problems
    if status != "available":
        return [label + ":INSTRUCTION_STATUS_INVALID"]
    fields = ("R_start_bytes", "R_start_tokens", "R_read_bytes", "R_read_tokens",
              "C_structure_tokens", "unique_read_tokens")
    problems = []
    for field in fields:
        value = instruction.get(field)
        if not isinstance(value, int) or isinstance(value, bool) or value < 0:
            problems.append(label + ":INSTRUCTION_FIELD_INVALID:" + field)
    if not problems and instruction["C_structure_tokens"] != (
            instruction["R_start_tokens"] + instruction["R_read_tokens"]):
        problems.append(label + ":INSTRUCTION_SCORE_INCONSISTENT")
    return problems


def _pilot_usage_available(usage):
    if not isinstance(usage, dict):
        return False
    for phase in ("first", "total"):
        value = usage.get(phase)
        if not isinstance(value, dict) or value.get("status") != "available":
            return False
        for field in ("input_tokens", "cached_input_tokens", "output_tokens",
                      "reasoning_output_tokens"):
            metric = value.get(field)
            if not isinstance(metric, int) or isinstance(metric, bool) or metric < 0:
                return False
    return True


def _promote_runtime_evidence(package, binding, profile=FULL_PROFILE):
    if not package:
        required = ("approved B0/L1 and B3/L2 lite pilot artifacts"
                    if profile == LITE_PROFILE else
                    "approved B0/01 and B3/12 pilot artifacts")
        return {"profile": profile, "status": "PENDING_APPROVED_PILOT_PAIR",
                "required": required}
    review = package["review"]
    if review.get("status") != "PASS":
        raise ValueError("runtime pilot review is not PASS")
    expected = _expected_pilots(profile)
    observed = set()
    bindings = []
    parent_fingerprints = []
    child_roles = {}
    environment_hashes = set()
    measurement_reviews = review.get("pilot_measurement_reviews")
    if not isinstance(measurement_reviews, dict):
        raise ValueError("pilot measurement review is missing")
    for artifact in package["artifacts"]:
        summary = artifact["summary"]
        evidence = artifact["evidence"]
        identity = (artifact.get("candidate"), artifact.get("cell"), artifact.get("run_id"))
        observed.add(identity)
        if identity != (summary.get("candidate"), summary.get("cell"), summary.get("run_id")):
            raise ValueError("pilot artifact identity mismatch")
        if (summary.get("run_kind") != "pilot" or summary.get("valid_run") is not True
                or summary.get("invalid_reason") != []
                or summary.get("runtime_evidence_status") != "CAPTURED_PENDING_CONFIRMATION"):
            raise ValueError("pilot summary is not a valid captured pilot")
        if summary.get("profile", FULL_PROFILE) != profile:
            raise ValueError("pilot summary profile mismatch")
        if summary.get("runtime_evidence") != evidence:
            raise ValueError("pilot summary and raw evidence differ")
        instruction_problems = _instruction_metric_problems(
            summary.get("instruction"), require_available=profile == FULL_PROFILE,
            label=summary.get("run_id", "pilot"))
        if instruction_problems:
            raise ValueError("pilot instruction measurement is inconsistent")
        if _metrics_module().validate_run_usage(summary):
            raise ValueError("pilot parent or child usage is unavailable or inconsistent")
        measurement_review = measurement_reviews.get(summary.get("run_id"))
        required_review_matches = [
            "first_input_tokens_direct_match", "first_cached_input_tokens_direct_match",
            "final_total_usage_direct_match",
            "tool_child_attribution_direct_match", "elapsed_direct_match",
        ]
        if profile == FULL_PROFILE:
            required_review_matches.append("instruction_attribution_direct_match")
        if (not isinstance(measurement_review, dict)
                or measurement_review.get("status") != "PASS"
                or measurement_review.get("instruction_sha256") != _canonical_sha256(
                    summary["instruction"])
                or measurement_review.get("usage_sha256") != _canonical_sha256(summary["usage"])
                or any(measurement_review.get(key) is not True
                       for key in required_review_matches)):
            raise ValueError("pilot direct measurement review is incomplete")
        fingerprint_problems = validate_runtime_fingerprint(evidence, profile=profile)
        if fingerprint_problems:
            raise ValueError("pilot runtime fingerprint invalid")
        if summary.get("instrumentation") != binding["instrumentation"]:
            raise ValueError("pilot instrumentation revision mismatch")
        if summary.get("source_documents") != binding["source_documents"]:
            raise ValueError("pilot source revision mismatch")
        if (summary.get("comparison_environment_core_hash")
                != binding["comparison_environment_core_sha256"]):
            raise ValueError("pilot comparison environment mismatch")
        environment_hashes.add(summary.get("environment_manifest_hash"))
        parent_fingerprints.append(evidence)
        for child in summary.get("child_runtime_evidence", []):
            if not isinstance(child, dict) or not isinstance(child.get("role"), str):
                raise ValueError("pilot child role evidence invalid")
            fingerprint = child.get("fingerprint")
            if validate_runtime_fingerprint(fingerprint, profile=profile):
                raise ValueError("pilot child fingerprint invalid")
            prior = child_roles.get(child["role"])
            if prior is not None and prior != fingerprint:
                raise ValueError("pilot child role fingerprint is inconsistent")
            child_roles[child["role"]] = fingerprint
        pilot_binding = {key: artifact[key] for key in (
            "candidate", "cell", "run_id", "summary_sha256", "raw_evidence_sha256")}
        pilot_binding["measurement_review_sha256"] = _canonical_sha256(measurement_review)
        bindings.append(pilot_binding)
    if observed != expected or len(environment_hashes) != 1:
        raise ValueError("approved pilot pair binding mismatch")
    if parent_fingerprints[0] != parent_fingerprints[1]:
        raise ValueError("pilot parent fingerprints differ")
    expected_summary_hashes = sorted(binding["summary_sha256"] for binding in bindings)
    expected_evidence_hashes = sorted(binding["raw_evidence_sha256"] for binding in bindings)
    if (review.get("pilot_summary_sha256") != expected_summary_hashes
            or review.get("pilot_raw_evidence_sha256") != expected_evidence_hashes):
        raise ValueError("pilot review artifact binding mismatch")
    promoted = {
        "profile": profile,
        "status": "APPROVED_PILOT_PAIR",
        "parent_fingerprint": parent_fingerprints[0],
        "child_role_fingerprints": dict(sorted(child_roles.items())),
        "pilot_bindings": sorted(bindings, key=lambda row: row["run_id"]),
        "pilot_environment_manifest_sha256": next(iter(environment_hashes)),
        "comparison_environment_core_sha256": binding["comparison_environment_core_sha256"],
        "instrumentation": binding["instrumentation"],
        "source_documents": binding["source_documents"],
    }
    promoted[("coordinator_review_sha256" if profile == LITE_PROFILE
              else "independent_review_sha256")] = package["review_sha256"]
    problems = validate_runtime_evidence(
        promoted, allow_capture=False, profile=profile)
    if problems:
        raise ValueError("promoted runtime evidence is invalid")
    return promoted


def build_environment_manifest(repo_root, fixtures_path, spec_path, evaluation_design_path,
                               candidate_parent, runtime_evidence, smoke_package, tokenizer,
                               profile=FULL_PROFILE, lite_plan_path=None):
    fixtures = json.loads(fixtures_path.read_text(encoding="utf-8"))
    if profile == LITE_PROFILE:
        if lite_plan_path is None:
            raise ValueError("lite profile requires its approved plan")
        prompts, cells = extract_lite_contract(lite_plan_path)
    elif profile == FULL_PROFILE:
        prompts = extract_exact_prompts(spec_path)
        cells = cell_manifest(prompts)
    else:
        raise ValueError("unknown evaluation profile")
    cli_output = _run_read_only(["codex", "--version"])
    cli_version = cli_output.removeprefix("codex-cli ")
    if cli_version != EXPECTED_CLI_VERSION:
        raise ValueError("Codex CLI version mismatch")

    candidates = []
    problems = []
    cumulative_files = cumulative_candidate_files(fixtures["candidates"])
    for candidate in fixtures["candidates"]:
        root = _candidate_root(candidate, candidate_parent)
        snapshot = _git_snapshot(root)
        candidate_files = cumulative_files[candidate["id"]]
        if snapshot["head"] != candidate["candidate_commit"]:
            problems.append("CANDIDATE_HEAD_MISMATCH:" + candidate["id"])
        if snapshot["branch"] != candidate["branch"]:
            problems.append("CANDIDATE_BRANCH_MISMATCH:" + candidate["id"])
        if snapshot["status"]:
            problems.append("CANDIDATE_NOT_CLEAN:" + candidate["id"])
        inherited_common = {relative: expected
                            for relative, expected in fixtures["common_files"].items()
                            if relative not in candidate_files}
        problems.extend(problem + ":" + candidate["id"]
                        for problem in _verify_common_files(root, inherited_common))
        problems.extend(problem + ":" + candidate["id"]
                        for problem in _verify_file_manifest(root, candidate_files))
        sources = build_source_manifest(root)
        candidates.append({
            "id": candidate["id"],
            "branch": candidate["branch"],
            "directory_name": candidate["directory_name"],
            "candidate_commit": candidate["candidate_commit"],
            "git_tree": snapshot["tree"],
            "tracked_manifest_sha256": _tracked_manifest_sha256(root),
            "instruction_sources": _source_manifest_public(sources),
            "fixture_file_manifest_sha256": _canonical_sha256(candidate_files),
        })

    prompt_rows = {
        name: {"text": prompt,
               "sha256": _sha256_bytes(prompt.encode("utf-8"))}
        for name, prompt in sorted(prompts.items())
    }
    source_documents = {
        "fixtures_sha256": _sha256_file(fixtures_path),
        "spec_sha256": _sha256_file(spec_path),
        "evaluation_design_sha256": _sha256_file(evaluation_design_path),
    }
    if profile == LITE_PROFILE:
        source_documents["lite_plan_sha256"] = _sha256_file(lite_plan_path)
    instrumentation = {
        "runner_sha256": _sha256_file(Path(__file__)),
        "metrics_sha256": _sha256_file(Path(__file__).with_name("codex-instruction-metrics.py")),
    }
    pilot_runs = expected_pilot_rows(cells, profile=profile)
    core = {
        "profile": profile,
        "model": EXPECTED_MODEL,
        "reasoning_effort": EXPECTED_EFFORT,
        "codex_cli": {"version": cli_version, "raw": cli_output},
        "tokenizer": tokenizer,
        "prompts": prompt_rows,
        "cells": cells,
        "pilot_runs": pilot_runs,
        "instrumentation": instrumentation,
        "source_documents": source_documents,
        "common_fixture_sha256": _sha256_file(fixtures_path),
        "common_files_sha256": _canonical_sha256(fixtures["common_files"]),
        "candidates": candidates,
    }
    comparison_core_sha256 = _canonical_sha256(core)
    promoted_runtime = _promote_runtime_evidence(runtime_evidence, {
        "instrumentation": instrumentation,
        "source_documents": source_documents,
        "comparison_environment_core_sha256": comparison_core_sha256,
    }, profile=profile)
    if profile == FULL_PROFILE:
        smoke_environment = _normalize_smoke_environment(
            smoke_package, candidates, candidate_parent)
        problems.extend(validate_smoke_environment(smoke_environment, allow_pending=True))
    else:
        smoke_environment = {"status": "NOT_APPLICABLE_LITE_READ_ONLY"}
    evidence_problems = validate_runtime_evidence(
        promoted_runtime, allow_capture=True, profile=profile)
    problems.extend(evidence_problems)
    status = ("PREPARED_RUNTIME_EVIDENCE_FIXED"
              if promoted_runtime.get("status") == "APPROVED_PILOT_PAIR"
              else "PREPARED_RUNTIME_UNVERIFIED")
    if problems:
        status = "BLOCKED"
    manifest = {
        "schema_version": SCHEMA_VERSION,
        "profile": profile,
        "issue_number": fixtures["issue_number"],
        "task_id": fixtures["task_id"],
        "design_id": fixtures["design_id"],
        "status": status,
        "execution_status": "NOT_STARTED",
        "planned_sessions": 204 if profile == FULL_PROFILE else 24,
        "model": EXPECTED_MODEL,
        "reasoning_effort": EXPECTED_EFFORT,
        "codex_cli": {"version": cli_version, "raw": cli_output},
        "sandbox_modes": (["read-only", "workspace-write"]
                          if profile == FULL_PROFILE else ["read-only"]),
        "common_commit": fixtures["common_commit"],
        "common_fixture_sha256": _sha256_file(fixtures_path),
        "common_files_sha256": _canonical_sha256(fixtures["common_files"]),
        "source_documents": source_documents,
        "python": {"version": platform.python_version(),
                   "implementation": platform.python_implementation()},
        "instrumentation": instrumentation,
        "tokenizer": tokenizer,
        "runtime_evidence": promoted_runtime,
        "smoke_environment": smoke_environment,
        "prompts": prompt_rows,
        "cells": cells,
        "pilot_runs": pilot_runs,
        "normal_selection_cells": (list(NORMAL_CELLS)
                                   if profile == FULL_PROFILE else []),
        "candidates": candidates,
        "problems": problems,
        "privacy": {
            "raw_logs": "external_restricted_directory_only",
            "raw_paths_in_public_outputs": False,
            "credential_environment_forwarded": False,
        },
    }
    manifest["comparison_environment_core_sha256"] = comparison_core_sha256
    manifest["comparison_environment_sha256"] = _canonical_sha256({
        "profile": manifest["profile"],
        "model": manifest["model"],
        "reasoning_effort": manifest["reasoning_effort"],
        "codex_cli": manifest["codex_cli"],
        "tokenizer": manifest["tokenizer"],
        "runtime_evidence": manifest["runtime_evidence"],
        "prompts": manifest["prompts"],
        "instrumentation": manifest["instrumentation"],
        "source_documents": manifest["source_documents"],
        "cells": manifest["cells"],
        "pilot_runs": manifest["pilot_runs"],
        "candidates": manifest["candidates"],
        "common_fixture_sha256": manifest["common_fixture_sha256"],
        "common_files_sha256": manifest["common_files_sha256"],
        "smoke_environment": manifest["smoke_environment"],
    })
    return manifest


def _atomic_write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
                         encoding="utf-8")
    os.replace(temporary, path)


def _comparison_core_payload(manifest):
    return {key: manifest.get(key) for key in (
        "profile", "model", "reasoning_effort", "codex_cli", "tokenizer", "prompts", "cells",
        "pilot_runs",
        "instrumentation", "source_documents", "common_fixture_sha256",
        "common_files_sha256", "candidates",
    )}


def _comparison_payload(manifest):
    return {key: manifest.get(key) for key in (
        "profile", "model", "reasoning_effort", "codex_cli", "tokenizer", "runtime_evidence",
        "prompts", "instrumentation", "source_documents", "cells", "pilot_runs", "candidates",
        "common_fixture_sha256", "common_files_sha256", "smoke_environment",
    )}


def _read_order(path):
    with path.open("r", encoding="utf-8", newline="") as handle:
        content = handle.read()
    return content, list(csv.DictReader(content.splitlines()))


def static_manifest_problems(manifest, spec_path, fixtures_path, evaluation_design_path,
                             order_path, profile=FULL_PROFILE, lite_plan_path=None):
    problems = profile_manifest_problems(manifest, profile)
    if manifest.get("schema_version") != SCHEMA_VERSION:
        problems.append("MANIFEST_SCHEMA_VERSION_MISMATCH")
    try:
        if profile == LITE_PROFILE:
            if lite_plan_path is None:
                raise ValueError("lite plan missing")
            prompts, expected_cells = extract_lite_contract(lite_plan_path)
        else:
            prompts = extract_exact_prompts(spec_path)
            expected_cells = cell_manifest(prompts)
        fixtures = json.loads(fixtures_path.read_text(encoding="utf-8"))
    except (KeyError, TypeError, ValueError, json.JSONDecodeError):
        return ["APPROVED_SOURCE_INVALID"]
    expected_prompts = {
        name: {"text": prompt, "sha256": _sha256_bytes(prompt.encode("utf-8"))}
        for name, prompt in sorted(prompts.items())
    }
    expected_documents = {
        "fixtures_sha256": _sha256_file(fixtures_path),
        "spec_sha256": _sha256_file(spec_path),
        "evaluation_design_sha256": _sha256_file(evaluation_design_path),
    }
    if profile == LITE_PROFILE:
        expected_documents["lite_plan_sha256"] = _sha256_file(lite_plan_path)
    if manifest.get("source_documents") != expected_documents:
        problems.append("SOURCE_DOCUMENT_HASH_MISMATCH")
    if manifest.get("prompts") != expected_prompts:
        problems.append("APPROVED_PROMPT_MISMATCH")
    if manifest.get("cells") != expected_cells:
        problems.append("APPROVED_CELL_LAYOUT_MISMATCH")
    if manifest.get("pilot_runs") != expected_pilot_rows(expected_cells, profile=profile):
        problems.append("APPROVED_PILOT_LAYOUT_MISMATCH")
    expected_selection_cells = list(NORMAL_CELLS) if profile == FULL_PROFILE else []
    if manifest.get("normal_selection_cells") != expected_selection_cells:
        problems.append("NORMAL_SELECTION_CELLS_MISMATCH")
    expected_sessions = 204 if profile == FULL_PROFILE else 24
    if (manifest.get("planned_sessions") != expected_sessions
            or manifest.get("model") != EXPECTED_MODEL
            or manifest.get("reasoning_effort") != EXPECTED_EFFORT
            or manifest.get("codex_cli", {}).get("version") != EXPECTED_CLI_VERSION):
        problems.append("COMPARISON_CONSTANT_MISMATCH")
    for key in ("issue_number", "task_id", "design_id", "common_commit"):
        if manifest.get(key) != fixtures.get(key):
            problems.append("FIXTURE_BINDING_MISMATCH:" + key)
    if manifest.get("common_fixture_sha256") != _sha256_file(fixtures_path):
        problems.append("COMMON_FIXTURE_HASH_MISMATCH")
    if manifest.get("common_files_sha256") != _canonical_sha256(fixtures.get("common_files")):
        problems.append("COMMON_FILES_HASH_MISMATCH")
    fixture_candidates = {candidate["id"]: candidate for candidate in fixtures["candidates"]}
    manifest_candidates = manifest.get("candidates")
    if not isinstance(manifest_candidates, list) or {
            candidate.get("id") for candidate in manifest_candidates if isinstance(candidate, dict)
            } != set(fixture_candidates):
        problems.append("CANDIDATE_SET_MISMATCH")
    else:
        cumulative = cumulative_candidate_files(fixtures["candidates"])
        for candidate in manifest_candidates:
            expected = fixture_candidates[candidate["id"]]
            for key in ("branch", "directory_name", "candidate_commit"):
                if candidate.get(key) != expected.get(key):
                    problems.append("CANDIDATE_FIXTURE_MISMATCH:{}:{}".format(
                        candidate["id"], key))
            if candidate.get("fixture_file_manifest_sha256") != _canonical_sha256(
                    cumulative[candidate["id"]]):
                problems.append("CANDIDATE_FIXTURE_MANIFEST_MISMATCH:" + candidate["id"])
    instrumentation = manifest.get("instrumentation", {})
    current_instrumentation = {
        "runner_sha256": _sha256_file(Path(__file__)),
        "metrics_sha256": _sha256_file(Path(__file__).with_name(
            "codex-instruction-metrics.py")),
    }
    if instrumentation != current_instrumentation:
        problems.append("INSTRUMENTATION_HASH_MISMATCH")
    if manifest.get("comparison_environment_core_sha256") != _canonical_sha256(
            _comparison_core_payload(manifest)):
        problems.append("COMPARISON_CORE_HASH_MISMATCH")
    if manifest.get("comparison_environment_sha256") != _canonical_sha256(
            _comparison_payload(manifest)):
        problems.append("COMPARISON_ENVIRONMENT_HASH_MISMATCH")
    if profile == FULL_PROFILE:
        problems.extend(validate_smoke_environment(
            manifest.get("smoke_environment"), allow_pending=True))
    elif manifest.get("smoke_environment") != {"status": "NOT_APPLICABLE_LITE_READ_ONLY"}:
        problems.append("LITE_SMOKE_ENVIRONMENT_MISMATCH")
    runtime = manifest.get("runtime_evidence", {})
    if runtime.get("status") == "APPROVED_PILOT_PAIR":
        problems.extend(validate_runtime_evidence(
            runtime, allow_capture=False, profile=profile))
        if runtime.get("instrumentation") != manifest.get("instrumentation"):
            problems.append("RUNTIME_INSTRUMENTATION_BINDING_MISMATCH")
        if runtime.get("source_documents") != manifest.get("source_documents"):
            problems.append("RUNTIME_SOURCE_BINDING_MISMATCH")
        if runtime.get("comparison_environment_core_sha256") != manifest.get(
                "comparison_environment_core_sha256"):
            problems.append("RUNTIME_COMPARISON_BINDING_MISMATCH")
    try:
        content, rows = _read_order(order_path)
    except (OSError, csv.Error):
        problems.append("EXECUTION_ORDER_UNREADABLE")
    else:
        if "\r\n" in content:
            problems.append("EXECUTION_ORDER_NOT_LF")
        problems.extend(execution_order_problems(rows, expected_cells, profile))
    return sorted(set(problems))


def prepare_command(args):
    repo_root = _repo_root()
    protect_profile_outputs(
        args.profile, args.environment_output, args.order_output,
        repo_root / "docs/experiments/codex-agents")
    evidence = read_runtime_evidence(args.runtime_evidence, profile=args.profile)
    smoke_package = (read_smoke_manifest(args.smoke_manifest)
                     if args.profile == FULL_PROFILE else {})
    tokenizer = activate_tokenizer(args.tokenizer_pythonpath, args.tokenizer_evidence)
    manifest = build_environment_manifest(
        repo_root, args.fixtures, args.spec, args.evaluation_design,
        args.candidate_parent, evidence,
        smoke_package, tokenizer, profile=args.profile,
        lite_plan_path=args.lite_plan)
    _atomic_write_json(args.environment_output, manifest)
    write_execution_order(args.order_output, manifest["cells"], profile=args.profile)
    print(json.dumps({"status": manifest["status"],
                      "planned_sessions": manifest["planned_sessions"],
                      "problems": manifest["problems"]}, sort_keys=True))
    return 0 if manifest["status"] != "BLOCKED" else 2


def _load_jsonl(path):
    events = []
    with path.open("r", encoding="utf-8") as handle:
        for number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError("invalid JSONL at line {}".format(number)) from exc
            if not isinstance(value, dict):
                raise ValueError("JSONL event must be an object")
            events.append(value)
    return events


def _thread_id(stdout_events):
    values = [event.get("thread_id") for event in stdout_events
              if event.get("type") == "thread.started"]
    values = [value for value in values if isinstance(value, str) and value]
    if len(values) != 1:
        raise ValueError("exactly one thread.started event is required")
    return values[0]


def _session_root():
    codex_home = os.environ.get("CODEX_HOME")
    return Path(codex_home).expanduser() / "sessions" if codex_home else Path.home() / ".codex/sessions"


def _find_rollout(thread_id, session_root):
    matches = list(session_root.rglob("rollout-*{}*.jsonl".format(thread_id)))
    if len(matches) != 1:
        raise ValueError("restricted rollout discovery did not find exactly one session")
    return matches[0]


def _dynamic_tool_projection(tools):
    return [{key: tool.get(key) for key in ("type", "name", "description")}
            for tool in tools]


def _canonical_dynamic_tool_function(tool):
    return (isinstance(tool, dict)
            and tool.get("type") == "function"
            and isinstance(tool.get("name"), str)
            and isinstance(tool.get("description"), str)
            and "inputSchema" in tool
            and ("deferLoading" not in tool
                 or isinstance(tool.get("deferLoading"), bool)))


def _canonical_dynamic_tool(tool):
    if _canonical_dynamic_tool_function(tool):
        return True
    return (isinstance(tool, dict)
            and tool.get("type") == "namespace"
            and isinstance(tool.get("name"), str)
            and isinstance(tool.get("description"), str)
            and isinstance(tool.get("tools"), list)
            and all(_canonical_dynamic_tool_function(nested)
                    for nested in tool["tools"]))


def runtime_evidence_from_events(events, profile=FULL_PROFILE):
    metas = [event["payload"] for event in events
             if event.get("type") == "session_meta" and isinstance(event.get("payload"), dict)]
    contexts = [event["payload"] for event in events
                if event.get("type") == "turn_context" and isinstance(event.get("payload"), dict)]
    if not metas or not contexts:
        return {}
    meta = metas[0]
    context = contexts[-1]
    base = meta.get("base_instructions")
    cli_version = meta.get("cli_version")
    if profile == FULL_PROFILE:
        tools = meta.get("dynamic_tools")
        if not isinstance(base, dict) or not isinstance(tools, list):
            return {}
        plugin_catalog = []
        for tool_namespace in tools:
            if not isinstance(tool_namespace, dict):
                continue
            plugin_catalog.append({key: tool_namespace.get(key)
                                   for key in ("type", "name", "description")})
        return {
            "base_instructions_sha256": _canonical_sha256(base),
            "tool_catalog_sha256": _canonical_sha256(tools),
            "plugin_catalog_sha256": _canonical_sha256(plugin_catalog),
            "resolved_model": context.get("model"),
            "resolved_reasoning_effort": context.get("effort"),
            "cli_version": cli_version,
        }
    if profile != LITE_PROFILE:
        raise ValueError("unknown evaluation profile")

    tools_present = "dynamic_tools" in meta
    tools = meta.get("dynamic_tools")
    if not tools_present and cli_version == EXPECTED_CLI_VERSION:
        tools = []
        serialization = "empty_omitted_for_cli_0.153.4"
    elif (isinstance(tools, list) and tools
          and all(_canonical_dynamic_tool(tool) for tool in tools)):
        serialization = "explicit_list"
    else:
        tools = None
        serialization = ("unsupported_cli_serialization"
                         if not tools_present else "malformed")
    projection = _dynamic_tool_projection(tools) if tools is not None else None
    return {
        "base_instructions_sha256": (_canonical_sha256(base)
                                     if isinstance(base, dict) else None),
        "dynamic_tools_sha256": (_canonical_sha256(tools)
                                 if tools is not None else None),
        "dynamic_tool_namespace_projection_sha256": (
            _canonical_sha256(projection) if projection is not None else None),
        "dynamic_tools_source": "session_meta.dynamic_tools",
        "dynamic_tools_serialization": serialization,
        "dynamic_tools_scope": "dynamic_supplement_only",
        "full_tool_catalog_status": "unavailable_from_serialized_rollout",
        "plugin_catalog_status": "unavailable_from_serialized_rollout",
        "resolved_model": context.get("model"),
        "resolved_reasoning_effort": context.get("effort"),
        "cli_version": cli_version,
    }


def child_role_from_events(events):
    metas = [event.get("payload") for event in events
             if event.get("type") == "session_meta" and isinstance(event.get("payload"), dict)]
    if not metas:
        return None
    meta = metas[0]
    source = meta.get("source")
    if isinstance(source, dict):
        subagent = source.get("subagent")
        if isinstance(subagent, dict):
            spawn = subagent.get("thread_spawn")
            for container in (spawn, subagent):
                if isinstance(container, dict):
                    for key in ("agent_role", "agent_type", "role"):
                        if isinstance(container.get(key), str) and container[key]:
                            return container[key]
            if isinstance(spawn, dict) and "agent_role" in spawn:
                return "default"
    for key in ("agent_type", "agent_role"):
        if isinstance(meta.get(key), str) and meta[key]:
            return meta[key]
    return None


def _descendant_rollouts(parent_thread_id, session_root, not_before):
    discovered = []
    parents = {parent_thread_id}
    seen = set()
    candidates = [path for path in session_root.rglob("rollout-*.jsonl")
                  if path.stat().st_mtime >= not_before - 2]
    while True:
        changed = False
        for path in candidates:
            if path in seen:
                continue
            events = _load_jsonl(path)
            metas = [event.get("payload") for event in events
                     if event.get("type") == "session_meta"]
            meta = metas[0] if metas and isinstance(metas[0], dict) else {}
            thread_id = meta.get("id") or meta.get("session_id")
            if meta.get("parent_thread_id") in parents and isinstance(thread_id, str):
                seen.add(path)
                parents.add(thread_id)
                discovered.append((thread_id, meta.get("parent_thread_id"), path, events))
                changed = True
        if not changed:
            break
    return discovered


def _metrics_module():
    path = Path(__file__).with_name("codex-instruction-metrics.py")
    spec = importlib.util.spec_from_file_location("codex_instruction_metrics", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _tracked_changes(root):
    output = _run_read_only(["git", "-C", str(root), "status", "--porcelain=v1",
                             "--untracked-files=all"])
    changes = set()
    for line in output.splitlines():
        relative = line[3:]
        if " -> " in relative:
            changes.update(relative.split(" -> ", 1))
        elif relative:
            changes.add(relative)
    return changes


def _manifest_candidate(manifest, candidate_id):
    matches = [candidate for candidate in manifest["candidates"]
               if candidate["id"].lower() == candidate_id.lower()]
    if len(matches) != 1:
        raise ValueError("unknown candidate")
    return matches[0]


def _validate_candidate_snapshot(root, candidate, require_clean):
    snapshot = _git_snapshot(root)
    problems = []
    if snapshot["head"] != candidate["candidate_commit"]:
        problems.append("CANDIDATE_HEAD_MISMATCH")
    if snapshot["tree"] != candidate["git_tree"]:
        problems.append("CANDIDATE_TREE_MISMATCH")
    if _tracked_manifest_sha256(root) != candidate["tracked_manifest_sha256"]:
        problems.append("CANDIDATE_TRACKED_MANIFEST_MISMATCH")
    sources = build_source_manifest(root)
    if _source_manifest_public(sources)["sha256"] != candidate["instruction_sources"]["sha256"]:
        problems.append("CANDIDATE_INSTRUCTION_HASH_MISMATCH")
    if require_clean and snapshot["status"]:
        problems.append("CANDIDATE_NOT_CLEAN")
    return problems, snapshot, sources


def _fingerprint_matches(expected, actual, profile=FULL_PROFILE):
    problems = validate_runtime_fingerprint(actual, profile=profile)
    if isinstance(expected, dict):
        keys = (RUNTIME_FINGERPRINT_KEYS if profile == FULL_PROFILE
                else LITE_RUNTIME_FINGERPRINT_KEYS)
        for key in keys:
            if key in expected and expected[key] != actual.get(key):
                problems.append("RUNTIME_EVIDENCE_MISMATCH:" + key)
    return sorted(set(problems))


def _runtime_matches(expected, actual, allow_capture, profile=FULL_PROFILE):
    if not expected or expected.get("status") == "PENDING_APPROVED_PILOT_PAIR":
        problems = validate_runtime_fingerprint(actual, profile=profile)
        if not allow_capture:
            problems.append("RUNTIME_EVIDENCE_MISSING")
        return sorted(set(problems))
    return _fingerprint_matches(
        expected.get("parent_fingerprint"), actual, profile=profile)


def _call_text(payload):
    value = payload.get("arguments") if payload.get("type") == "function_call" else payload.get("input")
    if isinstance(value, str):
        return value
    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False, sort_keys=True)
    return ""


def _hint_from_command(command, sources):
    paths = []
    for source in sources:
        relative = source["path"]
        if relative in command and relative not in paths:
            paths.append(relative)
    if paths:
        maximum = max(len(path) for path in paths)
        paths = [path for path in paths if len(path) == maximum]
    if len(paths) != 1:
        return None
    line_prefix = bool(
        re.search(r"\bnl\b", command)
        or (re.search(r"\brg\b", command)
            and re.search(r"(^|\s)(-n|--line-number)(\s|$)", command))
    )
    return {"path": paths[0], "line_prefix": line_prefix}


def annotate_source_hints(events, sources):
    call_hints = {}
    annotated = []
    for event in events:
        current = dict(event)
        payload = current.get("payload")
        if current.get("type") == "response_item" and isinstance(payload, dict):
            payload_type = payload.get("type")
            call_id = payload.get("call_id") or payload.get("id")
            if payload_type in {"function_call", "custom_tool_call"}:
                hint = _hint_from_command(_call_text(payload), sources)
                if hint is not None and isinstance(call_id, str):
                    call_hints[call_id] = hint
            elif payload_type in {"function_call_output", "custom_tool_call_output"}:
                hint = call_hints.get(call_id)
                if hint is not None:
                    current["source_hint"] = hint
                    current["attribution_scope"] = "instruction_candidate"
                else:
                    current["attribution_scope"] = "non_instruction"
        if current.get("type") == "item.completed" and isinstance(current.get("item"), dict):
            command = current["item"].get("command", "")
            hint = _hint_from_command(command if isinstance(command, str) else "", sources)
            if hint is not None:
                current["source_hint"] = hint
                current["attribution_scope"] = "instruction_candidate"
            else:
                current["attribution_scope"] = "non_instruction"
        annotated.append(current)
    return annotated


def _start_delivery_events(events):
    deliveries = []
    for event in events:
        if event.get("type") == "session_meta" and isinstance(event.get("payload"), dict):
            base = event["payload"].get("base_instructions")
            if isinstance(base, dict) and isinstance(base.get("text"), str):
                deliveries.append({
                    "type": "delivered_text", "call_id": "session_base",
                    "text": base["text"], "bucket_role": "global_prefix",
                    "attribution_scope": "global_only",
                })
        if event.get("type") == "response_item" and isinstance(event.get("payload"), dict):
            payload = event["payload"]
            if payload.get("type") != "message" or payload.get("role") not in {"developer", "user"}:
                continue
            content = payload.get("content")
            if not isinstance(content, list):
                continue
            text_parts = [part.get("text") for part in content
                          if isinstance(part, dict) and part.get("type") == "input_text"
                          and isinstance(part.get("text"), str)]
            if text_parts:
                delivery = {
                    "type": "delivered_text",
                    "call_id": "start_message_{}".format(len(deliveries) + 1),
                    "text": "".join(text_parts), "bucket_role": "global_prefix",
                }
                if payload.get("role") == "user":
                    delivery["attribution_scope"] = "global_only"
                deliveries.append(delivery)
    return deliveries


def _instruction_measurement(events, sources, metrics):
    start_sources = [source for source in sources if "start" in source["delivery_phases"]]
    read_sources = [source for source in sources if "read" in source["delivery_phases"]]
    start_rows = metrics.attribute_reads(
        _start_delivery_events(events), start_sources)
    read_rows = metrics.attribute_reads(events, read_sources)
    rows = start_rows + read_rows
    unavailable = [row for row in rows if row["classification"] == "unavailable"]
    structural_roles = {
        "repository_instruction", "skill_catalog_metadata", "skill_instruction",
        "role_instruction", "policy_enforcement",
    }
    start_structure = [row for row in start_rows if row["classification"] in structural_roles]
    read_structure = [row for row in read_rows if row["classification"] in structural_roles]
    start_tokens = [row["estimated_tokens"] for row in start_structure]
    read_tokens = [row["estimated_tokens"] for row in read_structure]
    if unavailable or not start_structure or any(
            value is None for value in start_tokens + read_tokens):
        structure_tokens = None
        status = "unavailable"
    else:
        structure_tokens = metrics.structural_cost(sum(start_tokens), read_tokens)
        status = "available"
    return {
        "status": status,
        "R_start_bytes": sum(row["bytes"] for row in start_structure),
        "R_start_tokens": sum(start_tokens) if start_tokens and all(
            value is not None for value in start_tokens) else None,
        "R_read_bytes": sum(row["bytes"] for row in read_structure),
        "R_read_tokens": sum(read_tokens) if all(
            value is not None for value in read_tokens) else None,
        "C_structure_tokens": structure_tokens,
        "unique_read_tokens": (sum({row["content_sha256"]: row["estimated_tokens"]
                                     for row in read_structure}.values())
                               if all(row["estimated_tokens"] is not None
                                      for row in read_structure) else None),
        "buckets": {
            role: {
                "bytes": sum(row["bytes"] for row in rows
                             if row["classification"] == role and row["bytes"] is not None),
                "estimated_tokens": (sum(row["estimated_tokens"] for row in rows
                                         if row["classification"] == role)
                                     if all(row["estimated_tokens"] is not None for row in rows
                                            if row["classification"] == role) else None),
            }
            for role in ("global_prefix", "wrapper", "non_instruction")
        },
        "source_rows": rows,
    }


def combine_instruction_measurements(parent, children):
    measurements = [parent] + list(children)
    available = all(measurement.get("status") == "available"
                    for measurement in measurements)
    combined = {
        "status": "available" if available else "unavailable",
        "R_start_bytes": sum(measurement.get("R_start_bytes", 0)
                             for measurement in measurements),
        "R_read_bytes": sum(measurement.get("R_read_bytes", 0)
                            for measurement in measurements),
    }
    for key in ("R_start_tokens", "R_read_tokens", "C_structure_tokens",
                "unique_read_tokens"):
        values = [measurement.get(key) for measurement in measurements]
        combined[key] = sum(values) if available and all(
            isinstance(value, int) and not isinstance(value, bool) and value >= 0
            for value in values) else None
    combined["buckets"] = {}
    for role in ("global_prefix", "wrapper", "non_instruction"):
        byte_values = [measurement.get("buckets", {}).get(role, {}).get("bytes", 0)
                       for measurement in measurements]
        token_values = [measurement.get("buckets", {}).get(role, {}).get("estimated_tokens")
                        for measurement in measurements]
        combined["buckets"][role] = {
            "bytes": sum(byte_values),
            "estimated_tokens": (sum(token_values)
                                 if all(isinstance(value, int) for value in token_values)
                                 else None),
        }
    source_rows = []
    for index, measurement in enumerate(measurements):
        scope = "parent" if index == 0 else "child-{}".format(index)
        source_rows.extend({**row, "session_scope": scope}
                           for row in measurement.get("source_rows", []))
    combined["source_rows"] = source_rows
    return combined


def _evaluation_summary_run_ids(results_dir):
    if not results_dir.exists():
        return set()
    observed = set()
    for path in sorted(results_dir.glob("*.summary.json")):
        value = json.loads(path.read_text(encoding="utf-8"))
        if value.get("run_kind") != "evaluation":
            continue
        run_id = value.get("run_id")
        if not isinstance(run_id, str) or run_id in observed:
            raise ValueError("duplicate or invalid evaluation summary run id")
        observed.add(run_id)
    return observed


def planned_run_preflight(order_path, manifest, candidate_id, cell, repetition,
                          results_dir, profile=FULL_PROFILE):
    _, rows = _read_order(order_path)
    expected_rows = expected_execution_rows(manifest["cells"], profile=profile)
    if rows != expected_rows:
        return ["EXECUTION_ORDER_MISMATCH"], None
    run_id = ("r{}-c{}-{}".format(repetition, cell, candidate_id.lower())
              if profile == FULL_PROFILE else
              "r{}-{}-{}".format(repetition, cell.lower(), candidate_id.lower()))
    matches = [row for row in rows if row["run_id"] == run_id]
    if len(matches) != 1:
        return ["PLANNED_RUN_BINDING_MISSING"], None
    row = matches[0]
    sequence = int(row["sequence"])
    observed = _evaluation_summary_run_ids(results_dir)
    expected_prior = {candidate["run_id"] for candidate in rows[:sequence - 1]}
    problems = []
    if run_id in observed:
        problems.append("PLANNED_RUN_ALREADY_EXECUTED")
    if observed != expected_prior:
        problems.append("PLANNED_RUN_SEQUENCE_MISMATCH")
    return problems, row


def _smoke_run_preflight(manifest, candidate, candidate_root, verification_root):
    smoke = manifest.get("smoke_environment", {})
    if smoke.get("status") != "APPROVED":
        return ["SMOKE_ENVIRONMENT_NOT_APPROVED"], None
    entry = smoke.get("checkouts", {}).get(candidate["id"])
    if not isinstance(entry, dict):
        return ["SMOKE_CHECKOUT_BINDING_MISSING"], None
    problems = []
    if candidate_root.resolve() == verification_root.resolve():
        problems.append("SMOKE_CHECKOUT_SEPARATION_FAILED")
    if _sha256_bytes(str(candidate_root.resolve()).encode("utf-8")) != (
            entry.get("candidate_checkout_identity_sha256")):
        problems.append("SMOKE_CANDIDATE_OWNERSHIP_MISMATCH")
    if _sha256_bytes(str(verification_root.resolve()).encode("utf-8")) != (
            entry.get("verification_checkout_identity_sha256")):
        problems.append("SMOKE_VERIFICATION_OWNERSHIP_MISMATCH")
    candidate_state = _checkout_state_manifest(candidate_root)
    verification_state = _checkout_state_manifest(verification_root)
    if candidate_state != entry.get("candidate_starting_files"):
        problems.append("SMOKE_CANDIDATE_STARTING_FIXTURE_MISMATCH")
    if verification_state != entry.get("verification_starting_files"):
        problems.append("SMOKE_VERIFICATION_STARTING_FIXTURE_MISMATCH")
    if _canonical_sha256({
            "candidate_starting_files": candidate_state,
            "verification_starting_files": verification_state,
            }) != entry.get("starting_fixture_sha256"):
        problems.append("SMOKE_STARTING_FIXTURE_HASH_MISMATCH")
    return sorted(set(problems)), {
        "contract": entry,
        "candidate_state": candidate_state,
        "verification_state": verification_state,
    }


def run_cell_command(args, pilot):
    manifest_bytes_before = args.environment.read_bytes()
    manifest = json.loads(manifest_bytes_before.decode("utf-8"))
    static_problems = static_manifest_problems(
        manifest, args.spec, args.fixtures, args.evaluation_design, args.order,
        profile=args.profile, lite_plan_path=args.lite_plan)
    if static_problems:
        raise RuntimeError("immutable input preflight failed: " + ",".join(static_problems))
    tokenizer = activate_tokenizer(args.tokenizer_pythonpath, args.tokenizer_evidence)
    if tokenizer != manifest.get("tokenizer"):
        raise RuntimeError("tokenizer environment mismatch")
    candidate = _manifest_candidate(manifest, args.candidate)
    cell = manifest["cells"].get(args.cell)
    if cell is None:
        raise ValueError("unknown cell")
    if pilot and (args.repetition != 0):
        raise ValueError("pilot repetition must be 0")
    if pilot and not approved_pilot_cell(candidate["id"], args.cell, args.profile):
        raise ValueError("pilot is restricted to the approved profile pair")
    allowed_repetitions = ((1, 2, 3) if args.profile == FULL_PROFILE else (1, 2))
    if not pilot and args.repetition not in allowed_repetitions:
        raise ValueError("run-cell repetition is outside the selected profile")
    planned_row = None
    if not pilot:
        order_problems, planned_row = planned_run_preflight(
            args.order, manifest, candidate["id"], args.cell, args.repetition,
            args.raw_dir, profile=args.profile)
        if order_problems:
            raise RuntimeError("planned run preflight failed: " + ",".join(order_problems))
    if cell["sandbox"] == "workspace-write" and args.verification_cwd is None:
        raise ValueError("smoke cell requires --verification-cwd")
    start_problems = runtime_start_problems(
        manifest.get("runtime_evidence", {}), pilot, profile=args.profile)
    if start_problems:
        raise RuntimeError("runtime preflight failed: " + ",".join(start_problems))
    if args.capture_runtime_evidence is not None:
        raw_resolved = args.raw_dir.expanduser().resolve()
        evidence_resolved = args.capture_runtime_evidence.expanduser().resolve()
        if evidence_resolved.parent != raw_resolved:
            raise ValueError("captured runtime evidence must stay in raw directory")

    canonical_root = _candidate_root(candidate, args.candidate_parent)
    candidate_root = args.candidate_cwd or canonical_root
    if cell["sandbox"] == "workspace-write" and candidate_root.resolve() == canonical_root.resolve():
        raise ValueError("smoke must not mutate the immutable candidate worktree")
    problems, before, sources = _validate_candidate_snapshot(
        candidate_root, candidate, require_clean=cell["sandbox"] == "read-only")
    if problems:
        raise RuntimeError("candidate preflight failed: " + ",".join(problems))
    verification_before = None
    smoke_before = None
    if args.verification_cwd is not None:
        verification_problems, _, _ = _validate_candidate_snapshot(
            args.verification_cwd, candidate, require_clean=False)
        if verification_problems:
            raise RuntimeError("verification checkout preflight failed")
        verification_before = _git_snapshot(args.verification_cwd)
    if cell["sandbox"] == "workspace-write":
        if (candidate_root.resolve() == canonical_root.resolve()
                or args.verification_cwd.resolve() == canonical_root.resolve()
                or candidate_root.resolve() == args.verification_cwd.resolve()):
            raise ValueError("smoke must not mutate the immutable candidate worktree")
        smoke_problems, smoke_before = _smoke_run_preflight(
            manifest, candidate, candidate_root, args.verification_cwd)
        if smoke_problems:
            raise RuntimeError("smoke environment preflight failed: "
                               + ",".join(smoke_problems))

    environment_hash_before = _sha256_bytes(manifest_bytes_before)
    instrumentation_before = {
        "runner_sha256": _sha256_file(Path(__file__)),
        "metrics_sha256": _sha256_file(Path(__file__).with_name("codex-instruction-metrics.py")),
    }
    if instrumentation_before != manifest.get("instrumentation"):
        raise RuntimeError("instrumentation revision preflight failed")
    prompt = manifest["prompts"][cell["scenario"]]["text"]
    if _sha256_bytes(prompt.encode("utf-8")) != cell["prompt_sha256"]:
        raise RuntimeError("prompt hash mismatch")
    work_cwd = candidate_root / cell["cwd"]
    argv = build_codex_argv(work_cwd, cell["sandbox"], prompt, args.verification_cwd)
    run_id = (pilot_run_id(candidate["id"], args.cell, args.profile)
              if pilot else planned_row["run_id"])
    started_epoch = time.time()
    process = run_process(
        argv, args.raw_dir, run_id, forbidden_roots=[
            _repo_root(), args.candidate_parent, candidate_root,
        ]
        + ([args.verification_cwd] if args.verification_cwd else []),
    )
    stdout_events = _load_jsonl(process["stdout_path"])
    thread_id = _thread_id(stdout_events)
    rollout_path = _find_rollout(thread_id, args.session_root)
    rollout_events = _load_jsonl(rollout_path)
    actual_evidence = runtime_evidence_from_events(
        rollout_events, profile=args.profile)
    runtime_problems = _runtime_matches(
        manifest.get("runtime_evidence", {}), actual_evidence,
        allow_capture=pilot, profile=args.profile)

    metrics = _metrics_module()
    parent_usage = metrics.extract_usage(rollout_events + stdout_events)
    children = []
    child_measurements = []
    child_runtime_evidence = []
    all_tool_events = list(rollout_events)
    child_evidence_problems = []
    expected_child_roles = manifest.get("runtime_evidence", {}).get(
        "child_role_fingerprints", {})
    for child_id, parent_id, _, child_events in _descendant_rollouts(
            thread_id, args.session_root, started_epoch):
        child_evidence = runtime_evidence_from_events(
            child_events, profile=args.profile)
        child_role = child_role_from_events(child_events)
        if child_role is None:
            child_evidence_problems.append("CHILD_ROLE_UNAVAILABLE")
        if pilot and manifest.get("runtime_evidence", {}).get(
                "status") == "PENDING_APPROVED_PILOT_PAIR":
            child_evidence_problems.extend(validate_runtime_fingerprint(
                child_evidence, profile=args.profile))
        else:
            expected_child = expected_child_roles.get(child_role)
            if expected_child is None:
                child_evidence_problems.append("CHILD_ROLE_FINGERPRINT_MISSING")
            else:
                child_evidence_problems.extend(
                    "CHILD_{}:{}".format(child_role, problem)
                    for problem in _fingerprint_matches(
                        expected_child, child_evidence, profile=args.profile))
        child_runtime_evidence.append({"role": child_role, "fingerprint": child_evidence})
        children.append({
            "thread_id": child_id,
            "parent_thread_id": parent_id,
            "usage": metrics.extract_usage(child_events),
            "elapsed_seconds": None,
        })
        child_attributed = annotate_source_hints(child_events, sources)
        child_measurements.append(_instruction_measurement(child_attributed, sources, metrics))
        all_tool_events.extend(child_events)
    merged = metrics.merge_children({
        "thread_id": thread_id, "usage": parent_usage,
        "elapsed_seconds": process["elapsed_seconds"],
    }, children)
    usage_problems = metrics.validate_run_usage({
        "thread_id": thread_id,
        "usage": parent_usage,
        "elapsed_seconds": process["elapsed_seconds"],
        "child_calls": merged["child_calls"],
        "child_thread_ids": merged["child_thread_ids"],
        "child_sessions": children,
        "combined_total_usage": merged["combined_total_usage"],
    })
    attributed_events = annotate_source_hints(rollout_events, sources)
    parent_instruction = _instruction_measurement(attributed_events, sources, metrics)
    instruction = combine_instruction_measurements(parent_instruction, child_measurements)
    tool_calls = metrics.count_tool_calls(all_tool_events)

    after_problems, after, _ = _validate_candidate_snapshot(
        candidate_root, candidate, require_clean=cell["sandbox"] == "read-only")
    changes = _tracked_changes(candidate_root)
    candidate_after_state = None
    verification_after_state = None
    if cell["sandbox"] == "read-only" and changes:
        after_problems.append("READ_ONLY_MUTATION")
    if cell["sandbox"] == "workspace-write":
        candidate_after_state = {
            relative: _file_state(candidate_root, relative)
            for relative in sorted(set(smoke_before["candidate_state"]) | changes)
        }
        model_changes = {
            relative for relative in candidate_after_state
            if smoke_before["candidate_state"].get(relative, {"state": "absent"})
            != candidate_after_state[relative]
        }
        unexpected = model_changes - SMOKE_ALLOWLIST[args.cell]
        if unexpected:
            after_problems.append("SMOKE_FILE_SCOPE_VIOLATION")
    if before["head"] != after["head"] or before["tree"] != after["tree"]:
        after_problems.append("CANDIDATE_GIT_IDENTITY_CHANGED")
    if args.verification_cwd is not None:
        verification_after_problems, verification_after, _ = _validate_candidate_snapshot(
            args.verification_cwd, candidate, require_clean=False)
        after_problems.extend("VERIFICATION_" + problem
                              for problem in verification_after_problems)
        verification_changes = _tracked_changes(args.verification_cwd)
        verification_before_state = (smoke_before["verification_state"]
                                     if smoke_before is not None else {})
        verification_after_state = {
            relative: _file_state(args.verification_cwd, relative)
            for relative in sorted(set(verification_before_state) | verification_changes)
        }
        verification_model_changes = {
            relative for relative in verification_after_state
            if verification_before_state.get(relative, {"state": "absent"})
            != verification_after_state[relative]
        }
        if verification_model_changes - ({"docs/api/openapi.json"}
                                         | SMOKE_ALLOWLIST.get(args.cell, set())):
            after_problems.append("VERIFICATION_FILE_SCOPE_VIOLATION")
        if (verification_before["head"] != verification_after["head"]
                or verification_before["tree"] != verification_after["tree"]):
            after_problems.append("VERIFICATION_GIT_IDENTITY_CHANGED")
    if args.environment.read_bytes() != manifest_bytes_before:
        after_problems.append("ENVIRONMENT_MANIFEST_CHANGED")
    instrumentation_after = {
        "runner_sha256": _sha256_file(Path(__file__)),
        "metrics_sha256": _sha256_file(Path(__file__).with_name("codex-instruction-metrics.py")),
    }
    if instrumentation_after != instrumentation_before:
        after_problems.append("INSTRUMENTATION_CHANGED")

    all_problems = sorted(set(runtime_problems + child_evidence_problems
                              + usage_problems + after_problems))
    if process["return_code"] != 0:
        all_problems.append("CODEX_EXIT_NONZERO")
    summary = {
        "schema_version": SCHEMA_VERSION,
        "profile": args.profile,
        "run_id": run_id,
        "run_kind": "pilot" if pilot else "evaluation",
        "candidate": candidate["id"],
        "cell": args.cell,
        "scenario": cell["scenario"],
        "cwd": cell["cwd"],
        "sandbox": cell["sandbox"],
        "repetition": args.repetition,
        "candidate_commit": candidate["candidate_commit"],
        "candidate_git_tree": candidate["git_tree"],
        "candidate_tracked_manifest_sha256": candidate["tracked_manifest_sha256"],
        "candidate_fixture_file_manifest_sha256": candidate[
            "fixture_file_manifest_sha256"],
        "common_fixture_hash": manifest["common_fixture_sha256"],
        "common_files_hash": manifest["common_files_sha256"],
        "prompt_hash": cell["prompt_sha256"],
        "environment_manifest_hash": environment_hash_before,
        "comparison_environment_hash": manifest["comparison_environment_sha256"],
        "comparison_environment_core_hash": manifest["comparison_environment_core_sha256"],
        "source_documents": manifest["source_documents"],
        "instrumentation": manifest["instrumentation"],
        "order_sha256": _sha256_file(args.order),
        "order_sequence": int(planned_row["sequence"]) if planned_row is not None else None,
        "model": EXPECTED_MODEL,
        "effort": EXPECTED_EFFORT,
        "version": EXPECTED_CLI_VERSION,
        "started_at": datetime.fromtimestamp(started_epoch, timezone.utc).isoformat(),
        "usage": parent_usage,
        "thread_id": thread_id,
        "combined_total_usage": merged["combined_total_usage"],
        "tool_calls": tool_calls,
        "elapsed_seconds": process["elapsed_seconds"],
        "child_calls": merged["child_calls"],
        "child_thread_ids": merged["child_thread_ids"],
        "child_sessions": children,
        "child_elapsed_seconds": merged["child_elapsed_seconds"],
        "instruction": instruction,
        "runtime_evidence": actual_evidence,
        "child_runtime_evidence": child_runtime_evidence,
        "runtime_evidence_status": "CAPTURED_PENDING_CONFIRMATION" if (
            pilot and manifest.get("runtime_evidence", {}).get(
                "status") == "PENDING_APPROVED_PILOT_PAIR")
            else "COMPARED",
        "hard_gate_pass": None,
        "routing_pass": None,
        "false_block": None,
        "quality_pass": None,
        "valid_run": not all_problems,
        "invalid_reason": all_problems,
        "execution_status": "EXECUTED",
    }
    if args.profile == LITE_PROFILE:
        summary["answer_checklist"] = [
            {"item": item, "result": "UNAVAILABLE"}
            for item in cell["answer_checklist"]
        ]
    if smoke_before is not None:
        candidate_model_files = {
            relative: candidate_after_state[relative]
            for relative in sorted(model_changes)
        }
        verification_copy_files = {
            relative: verification_after_state.get(relative, {"state": "absent"})
            for relative in sorted(model_changes)
        }
        candidate_content_sha256 = _canonical_sha256(candidate_model_files)
        verification_content_sha256 = _canonical_sha256(verification_copy_files)
        summary["smoke_execution"] = {
            "starting_fixture_sha256": smoke_before["contract"]["starting_fixture_sha256"],
            "candidate_model_diff_sha256": _canonical_sha256({
                "before": smoke_before["candidate_state"], "after": candidate_after_state}),
            "verification_copy_diff_sha256": _canonical_sha256({
                "before": smoke_before["verification_state"],
                "after": verification_after_state}),
            "candidate_model_files": candidate_model_files,
            "verification_copy_files": verification_copy_files,
            "candidate_content_sha256": candidate_content_sha256,
            "verification_copy_content_sha256": verification_content_sha256,
            "copy_content_match": (bool(candidate_model_files)
                                   and candidate_model_files == verification_copy_files),
            "verification_result": {
                "status": "PENDING_INDEPENDENT_VERIFICATION",
                "verified_copy_content_sha256": None,
                "evidence_sha256": None,
                "independent_review_sha256": None,
                "executed_checks": [],
                "passed_checks": [],
                "failed_checks": [],
                "blocked_checks": [],
            },
        }
    summary_path = args.raw_dir / (run_id + ".summary.json")
    _write_restricted_json(summary_path, summary)
    if pilot and args.capture_runtime_evidence is not None:
        _write_restricted_json(args.capture_runtime_evidence, actual_evidence)
    print(json.dumps({
        "run_id": run_id,
        "valid_run": summary["valid_run"],
        "invalid_reason": summary["invalid_reason"],
        "runtime_evidence_status": summary["runtime_evidence_status"],
    }, sort_keys=True))
    return 0 if summary["valid_run"] else 2


def _smoke_summary_problems(smoke_execution, expected_row, manifest, candidate):
    label = "SMOKE:" + expected_row["run_id"]
    if not isinstance(smoke_execution, dict):
        return [label + ":EVIDENCE_MISSING"]
    problems = []
    contract = manifest.get("smoke_environment", {}).get(
        "checkouts", {}).get(candidate["id"], {})
    if smoke_execution.get("starting_fixture_sha256") != contract.get(
            "starting_fixture_sha256"):
        problems.append(label + ":STARTING_FIXTURE_MISMATCH")
    for key in ("candidate_model_diff_sha256", "verification_copy_diff_sha256",
                "candidate_content_sha256", "verification_copy_content_sha256"):
        value = smoke_execution.get(key)
        if not isinstance(value, str) or not HEX_SHA256.fullmatch(value):
            problems.append(label + ":HASH_INVALID:" + key)
    candidate_files = smoke_execution.get("candidate_model_files")
    verification_files = smoke_execution.get("verification_copy_files")
    allowed = SMOKE_ALLOWLIST[expected_row["cell"]]
    if (not isinstance(candidate_files, dict) or not candidate_files
            or not set(candidate_files).issubset(allowed)):
        problems.append(label + ":CANDIDATE_MODEL_FILES_INVALID")
    elif any(not isinstance(state, dict) or state.get("state") != "file"
             for state in candidate_files.values()):
        problems.append(label + ":CANDIDATE_MODEL_FILE_STATE_INVALID")
    if verification_files != candidate_files:
        problems.append(label + ":VERIFICATION_COPY_CONTENT_MISMATCH")
    if isinstance(candidate_files, dict):
        content_sha256 = _canonical_sha256(candidate_files)
        if smoke_execution.get("candidate_content_sha256") != content_sha256:
            problems.append(label + ":CANDIDATE_CONTENT_HASH_MISMATCH")
        if smoke_execution.get("verification_copy_content_sha256") != content_sha256:
            problems.append(label + ":VERIFICATION_CONTENT_HASH_MISMATCH")
    else:
        content_sha256 = None
    if smoke_execution.get("copy_content_match") is not True:
        problems.append(label + ":COPY_NOT_CONFIRMED")
    result = smoke_execution.get("verification_result")
    if not isinstance(result, dict) or result.get("status") != "PASS":
        problems.append(label + ":VERIFICATION_RESULT_MISSING")
    else:
        if result.get("verified_copy_content_sha256") != content_sha256:
            problems.append(label + ":VERIFIED_COPY_HASH_MISMATCH")
        for key in ("evidence_sha256", "independent_review_sha256"):
            value = result.get(key)
            if not isinstance(value, str) or not HEX_SHA256.fullmatch(value):
                problems.append(label + ":VERIFICATION_HASH_INVALID:" + key)
        executed = result.get("executed_checks")
        if (not isinstance(executed, list) or not executed
                or any(not isinstance(check, str) or not check for check in executed)
                or result.get("passed_checks") != executed
                or result.get("failed_checks") != [] or result.get("blocked_checks") != []):
            problems.append(label + ":VERIFICATION_CHECKS_INVALID")
    return problems


def _load_smoke_verification_evidence(directory):
    if directory is None:
        return {}
    resolved = directory.expanduser().resolve()
    if not resolved.is_dir() or stat.S_IMODE(resolved.stat().st_mode) != 0o700:
        raise PermissionError("smoke verification evidence directory mode is not 0700")
    evidence = {}
    for path in sorted(resolved.glob("*.smoke-verification.json")):
        if path.is_symlink() or stat.S_IMODE(path.stat().st_mode) != 0o600:
            raise PermissionError("smoke verification evidence file mode is not 0600")
        value = json.loads(path.read_text(encoding="utf-8"))
        run_id = value.get("run_id") if isinstance(value, dict) else None
        if not isinstance(run_id, str) or run_id in evidence:
            raise ValueError("smoke verification evidence run id is invalid")
        evidence[run_id] = value
    return evidence


def _smoke_evidence_set_problems(evidence, order_rows):
    expected = {
        row["run_id"] for row in order_rows
        if row.get("cell") in SMOKE_ALLOWLIST
    }
    observed = set(evidence) if isinstance(evidence, dict) else set()
    if observed != expected:
        return ["SMOKE_EVIDENCE_EXACT_RUN_SET_MISMATCH"]
    return []


def _apply_smoke_verification_evidence(run, evidence):
    original = run.get("smoke_execution")
    if not isinstance(original, dict) or not isinstance(evidence, dict):
        return run, ["SMOKE_EVIDENCE_BASE_MISSING:" + str(run.get("run_id"))]
    problems = []
    for key in ("run_id", "candidate", "cell"):
        if evidence.get(key) != run.get(key):
            problems.append("SMOKE_EVIDENCE_IDENTITY_MISMATCH:{}:{}".format(
                run.get("run_id"), key))
    for key in ("starting_fixture_sha256", "candidate_model_diff_sha256",
                "candidate_model_files", "candidate_content_sha256"):
        if evidence.get(key) != original.get(key):
            problems.append("SMOKE_EVIDENCE_CANDIDATE_BINDING_MISMATCH:{}:{}".format(
                run.get("run_id"), key))
    allowed = {
        "verification_copy_diff_sha256", "verification_copy_files",
        "verification_copy_content_sha256", "copy_content_match", "verification_result",
    }
    merged = {**original, **{key: evidence[key] for key in allowed if key in evidence}}
    return {**run, "smoke_execution": merged}, problems


def _summary_problems(run, expected_row, manifest, candidate):
    problems = []
    profile = manifest.get("profile", FULL_PROFILE)
    exact = {
        "schema_version": SCHEMA_VERSION,
        "profile": profile,
        "run_id": expected_row["run_id"],
        "run_kind": "evaluation",
        "candidate": expected_row["candidate"],
        "cell": expected_row["cell"],
        "scenario": expected_row["scenario"],
        "cwd": expected_row["cwd"],
        "sandbox": expected_row["sandbox"],
        "repetition": int(expected_row["repetition"]),
        "candidate_commit": candidate["candidate_commit"],
        "candidate_git_tree": candidate["git_tree"],
        "candidate_tracked_manifest_sha256": candidate["tracked_manifest_sha256"],
        "candidate_fixture_file_manifest_sha256": candidate[
            "fixture_file_manifest_sha256"],
        "common_fixture_hash": manifest["common_fixture_sha256"],
        "common_files_hash": manifest["common_files_sha256"],
        "prompt_hash": expected_row["prompt_sha256"],
        "model": manifest["model"],
        "effort": manifest["reasoning_effort"],
        "version": manifest["codex_cli"]["version"],
        "comparison_environment_hash": manifest["comparison_environment_sha256"],
        "comparison_environment_core_hash": manifest["comparison_environment_core_sha256"],
        "source_documents": manifest["source_documents"],
        "instrumentation": manifest["instrumentation"],
        "order_sequence": int(expected_row["sequence"]),
    }
    for key, expected in exact.items():
        if run.get(key) != expected:
            problems.append("SUMMARY_PROVENANCE_MISMATCH:{}:{}".format(
                expected_row["run_id"], key))
    if run.get("environment_manifest_hash") != manifest["_file_sha256"]:
        problems.append("SUMMARY_PROVENANCE_MISMATCH:{}:environment_manifest_hash".format(
            expected_row["run_id"]))
    if run.get("order_sha256") != manifest["_order_sha256"]:
        problems.append("SUMMARY_PROVENANCE_MISMATCH:{}:order_sha256".format(
            expected_row["run_id"]))
    invalid_reason = run.get("invalid_reason")
    if (not isinstance(invalid_reason, list)
            or any(not isinstance(reason, str) or not reason for reason in invalid_reason)):
        problems.append("SUMMARY_INVALID_REASON_SCHEMA:" + expected_row["run_id"])
    valid_run = run.get("valid_run")
    if not isinstance(valid_run, bool):
        problems.append("SUMMARY_VALID_RUN_SCHEMA:" + expected_row["run_id"])
    elif valid_run != (invalid_reason == []):
        problems.append("SUMMARY_VALIDITY_INVARIANT:" + expected_row["run_id"])
    if valid_run is not True:
        problems.append("SUMMARY_INVALID_RUN:" + expected_row["run_id"])
    if run.get("execution_status") != "EXECUTED":
        problems.append("SUMMARY_EXECUTION_STATUS_INVALID:" + expected_row["run_id"])
    metrics = _metrics_module()
    problems.extend("SUMMARY:{}:{}".format(expected_row["run_id"], problem)
                    for problem in metrics.validate_run_usage(run))
    if profile == FULL_PROFILE:
        for key in ("hard_gate_pass", "routing_pass", "false_block", "quality_pass"):
            if not isinstance(run.get(key), bool):
                problems.append("SUMMARY_GATE_SCHEMA:{}:{}".format(
                    expected_row["run_id"], key))
    problems.extend(_instruction_metric_problems(
        run.get("instruction"), (profile == FULL_PROFILE
                                 and expected_row["cell"] in NORMAL_CELLS),
        "SUMMARY:" + expected_row["run_id"]))
    if profile == LITE_PROFILE:
        observed_checklist = run.get("answer_checklist")
        expected_items = manifest["cells"][expected_row["cell"]]["answer_checklist"]
        if (not isinstance(observed_checklist, list)
                or [entry.get("item") for entry in observed_checklist
                    if isinstance(entry, dict)] != expected_items
                or any(entry.get("result") not in {"PASS", "FAIL", "UNAVAILABLE"}
                       for entry in observed_checklist if isinstance(entry, dict))
                or any(not isinstance(entry, dict) for entry in observed_checklist)):
            problems.append("SUMMARY_ANSWER_CHECKLIST_MISMATCH:" + expected_row["run_id"])
    if profile == FULL_PROFILE and expected_row["cell"] in SMOKE_ALLOWLIST:
        problems.extend(_smoke_summary_problems(
            run.get("smoke_execution"), expected_row, manifest, candidate))
    return problems


def aggregate_command(args):
    metrics = _metrics_module()
    manifest_bytes = args.environment.read_bytes()
    manifest = json.loads(manifest_bytes.decode("utf-8"))
    static_problems = static_manifest_problems(
        manifest, args.spec, args.fixtures, args.evaluation_design, args.order,
        profile=args.profile, lite_plan_path=args.lite_plan)
    _, order_rows = _read_order(args.order)
    manifest["_file_sha256"] = _sha256_bytes(manifest_bytes)
    manifest["_order_sha256"] = _sha256_file(args.order)
    by_id = {}
    problems = list(static_problems)
    problems.extend(validate_runtime_evidence(
        manifest.get("runtime_evidence"), allow_capture=False,
        profile=args.profile))
    if args.profile == FULL_PROFILE:
        problems.extend(validate_smoke_environment(
            manifest.get("smoke_environment"), allow_pending=False))
        smoke_evidence = _load_smoke_verification_evidence(args.smoke_evidence_dir)
        problems.extend(_smoke_evidence_set_problems(smoke_evidence, order_rows))
    else:
        smoke_evidence = {}
    for path in sorted(args.results_dir.glob("*.summary.json")):
        value = json.loads(path.read_text(encoding="utf-8"))
        if value.get("run_kind") != "evaluation":
            continue
        run_id = value.get("run_id")
        if not isinstance(run_id, str) or run_id in by_id:
            problems.append("SUMMARY_RUN_ID_DUPLICATE_OR_INVALID")
            continue
        if run_id in smoke_evidence:
            value, evidence_problems = _apply_smoke_verification_evidence(
                value, smoke_evidence[run_id])
            problems.extend(evidence_problems)
        by_id[run_id] = value
    expected_ids = {row["run_id"] for row in order_rows}
    if set(by_id) != expected_ids:
        problems.append("SUMMARY_EXACT_RUN_SET_MISMATCH")
    unknown_smoke_evidence = set(smoke_evidence) - expected_ids
    if unknown_smoke_evidence:
        problems.append("SMOKE_EVIDENCE_UNKNOWN_RUN_ID")
    candidate_map = {candidate["id"]: candidate for candidate in manifest["candidates"]}
    for row in order_rows:
        run = by_id.get(row["run_id"])
        if run is None:
            continue
        problems.extend(_summary_problems(run, row, manifest, candidate_map[row["candidate"]]))

    candidates = {}
    for candidate_id in ("B0", "B1", "B2", "B3"):
        candidate_runs = [by_id[row["run_id"]] for row in order_rows
                          if row["candidate"] == candidate_id and row["run_id"] in by_id]
        score = None
        if args.profile == FULL_PROFILE:
            cell_runs = {
                cell: [run.get("instruction", {}).get("C_structure_tokens")
                       for run in candidate_runs if run.get("cell") == cell]
                for cell in NORMAL_CELLS
            }
            try:
                score = metrics.selection_score(cell_runs, list(NORMAL_CELLS))
            except (TypeError, ValueError):
                score = None
        expected_candidate_runs = 51 if args.profile == FULL_PROFILE else 6
        complete = (len(candidate_runs) == expected_candidate_runs and not problems
                    and all(run.get("valid_run") is True for run in candidate_runs))
        eligible = (complete and metrics.candidate_is_eligible(candidate_runs)
                    and score is not None) if args.profile == FULL_PROFILE else None
        candidates[candidate_id] = {
            "run_count": len(candidate_runs),
            "complete": complete,
            "eligible": eligible,
            "selection_score": score if complete else None,
            "invalid_runs": sum(run.get("valid_run") is not True for run in candidate_runs),
        }
    expected_runs = 204 if args.profile == FULL_PROFILE else 24
    complete = not problems and len(by_id) == expected_runs and all(
        candidate["complete"] for candidate in candidates.values())
    output = {
        "schema_version": SCHEMA_VERSION,
        "profile": args.profile,
        "execution_status": "COMPLETE" if complete else "BLOCKED",
        "run_count": len(by_id),
        "problems": sorted(set(problems)),
        "candidates": candidates,
    }
    _atomic_write_json(args.output, output)
    print(json.dumps({"execution_status": output["execution_status"],
                      "run_count": len(by_id), "problems": output["problems"]}, sort_keys=True))
    return 0 if complete else 2


def verify_command(args):
    manifest = json.loads(args.environment.read_text(encoding="utf-8"))
    problems = static_manifest_problems(
        manifest, args.spec, args.fixtures, args.evaluation_design, args.order,
        profile=args.profile, lite_plan_path=args.lite_plan)
    try:
        tokenizer = activate_tokenizer(args.tokenizer_pythonpath, args.tokenizer_evidence)
        if tokenizer != manifest.get("tokenizer"):
            problems.append("TOKENIZER_ENVIRONMENT_MISMATCH")
    except RuntimeError:
        problems.append("TOKENIZER_UNAVAILABLE")
    runtime = manifest.get("runtime_evidence", {})
    if runtime.get("status") == "PENDING_APPROVED_PILOT_PAIR":
        if not args.allow_runtime_unverified:
            problems.append("RUNTIME_EVIDENCE_MISSING")
    else:
        problems.extend(validate_runtime_evidence(
            runtime, allow_capture=False, profile=args.profile))
        if runtime.get("instrumentation") != manifest.get("instrumentation"):
            problems.append("RUNTIME_INSTRUMENTATION_BINDING_MISMATCH")
        if runtime.get("source_documents") != manifest.get("source_documents"):
            problems.append("RUNTIME_SOURCE_BINDING_MISMATCH")
        if runtime.get("comparison_environment_core_sha256") != manifest.get(
                "comparison_environment_core_sha256"):
            problems.append("RUNTIME_COMPARISON_BINDING_MISMATCH")
    for candidate in manifest.get("candidates", []):
        root = _candidate_root(candidate, args.candidate_parent)
        candidate_problems, _, _ = _validate_candidate_snapshot(root, candidate, True)
        problems.extend(problem + ":" + candidate["id"] for problem in candidate_problems)
    status = "PASS" if not problems else "BLOCKED"
    print(json.dumps({"status": status, "problems": sorted(set(problems))}, sort_keys=True))
    return 0 if not problems else 2


def run_self_test():
    argv = build_codex_argv(Path("/candidate"), "read-only", "prompt")
    assert argv == [
        "codex", "exec", "--json", "--model", "gpt-5.6-sol",
        "--config", 'model_reasoning_effort="high"',
        "--sandbox", "read-only", "--cd", "/candidate", "prompt",
    ]
    workspace_argv = build_codex_argv(
        Path("/candidate"), "workspace-write", "prompt", Path("/verification"))
    assert workspace_argv[-3:] == ["--add-dir", "/verification", "prompt"]

    safe = sanitized_environment({
        "PATH": "/bin", "HOME": "/safe-home", "LANG": "en_US.UTF-8",
        "AWS_ACCESS_KEY_ID": "secret", "OPENAI_API_KEY": "secret",
        "CODEX_API_KEY": "secret", "DATABASE_URL": "secret",
    })
    assert safe == {"PATH": "/bin", "HOME": "/safe-home", "LANG": "en_US.UTF-8"}

    with tempfile.TemporaryDirectory() as temp:
        raw_dir = Path(temp) / "raw"
        ensure_restricted_directory(raw_dir, forbidden_roots=[])
        assert stat.S_IMODE(raw_dir.stat().st_mode) == 0o700
        raw_file = restricted_open(raw_dir / "run.jsonl")
        raw_file.write(b"{}\n")
        raw_file.close()
        assert stat.S_IMODE((raw_dir / "run.jsonl").stat().st_mode) == 0o600

        order = Path(temp) / "order.csv"
        synthetic_cells = {
            "{:02d}".format(index): {"scenario": "S1", "cwd": ".",
                                      "sandbox": "read-only",
                                      "prompt_sha256": "a" * 64}
            for index in range(1, 18)
        }
        write_execution_order(order, synthetic_cells)
        data = order.read_bytes()
        assert data.count(b"\n") == 205
        assert b"\r\n" not in data
        assert b",PLANNED\n" in data
        synthetic_rows = expected_execution_rows(synthetic_cells)
        smoke_run_ids = {
            row["run_id"] for row in synthetic_rows
            if row["cell"] in SMOKE_ALLOWLIST
        }
        assert len(smoke_run_ids) == 24
        assert _smoke_evidence_set_problems({}, synthetic_rows) == [
            "SMOKE_EVIDENCE_EXACT_RUN_SET_MISMATCH"]
        assert _smoke_evidence_set_problems(
            {run_id: {} for run_id in smoke_run_ids}, synthetic_rows) == []

        lite_cells = {
            "L1": {"scenario": "S1", "cwd": ".", "sandbox": "read-only",
                   "prompt_sha256": "1" * 64},
            "L2": {"scenario": "X2", "cwd": ".", "sandbox": "read-only",
                   "prompt_sha256": "2" * 64},
            "L3": {"scenario": "X1", "cwd": ".", "sandbox": "read-only",
                   "prompt_sha256": "3" * 64},
        }
        lite_rows = expected_execution_rows(lite_cells, profile="lite")
        assert len(lite_rows) == 24
        assert len({row["run_id"] for row in lite_rows}) == 24
        assert [row["candidate"] for row in lite_rows[:4]] == ["B0", "B1", "B2", "B3"]
        assert [row["candidate"] for row in lite_rows[12:16]] == ["B1", "B2", "B3", "B0"]
        assert approved_pilot_cell("B0", "L1", profile="lite") is True
        assert approved_pilot_cell("B3", "L2", profile="lite") is True
        assert pilot_run_id("B0", "L1", profile="lite") == "pilot-lite-b0-l1"
        assert pilot_run_id("B3", "L2", profile="lite") == "pilot-lite-b3-l2"
        lite_pilots = expected_pilot_rows(lite_cells, profile="lite")
        assert [row["run_id"] for row in lite_pilots] == [
            "pilot-lite-b0-l1", "pilot-lite-b3-l2"]
        assert {row["run_id"] for row in lite_pilots}.isdisjoint(
            row["run_id"] for row in lite_rows)
        assert profile_manifest_problems({"profile": "lite"}, "full") == [
            "INPUT_PROFILE_MISMATCH"]
        assert execution_order_problems(lite_rows, lite_cells, "lite") == []
        assert "EXECUTION_ORDER_MISMATCH" in execution_order_problems(
            lite_rows[:-1], lite_cells, "lite")
        duplicate_rows = list(lite_rows)
        duplicate_rows[-1] = dict(duplicate_rows[0])
        assert "EXECUTION_ORDER_DUPLICATE_RUN_ID" in execution_order_problems(
            duplicate_rows, lite_cells, "lite")
        reordered_rows = list(lite_rows)
        reordered_rows[0], reordered_rows[1] = reordered_rows[1], reordered_rows[0]
        assert "EXECUTION_ORDER_MISMATCH" in execution_order_problems(
            reordered_rows, lite_cells, "lite")
        stale_hash_rows = [dict(row) for row in lite_rows]
        stale_hash_rows[0]["prompt_sha256"] = "f" * 64
        assert "EXECUTION_ORDER_PROMPT_HASH_MISMATCH" in execution_order_problems(
            stale_hash_rows, lite_cells, "lite")
        try:
            protect_profile_outputs("lite", Path("gh-223-environment.json"),
                                    Path("gh-223-lite-execution-order.csv"), Path("."))
        except ValueError as exc:
            assert str(exc) == "lite profile must not overwrite full artifacts"
        else:
            raise AssertionError("lite profile accepted a full output path")

    missing = validate_runtime_evidence({}, allow_capture=False)
    assert "RUNTIME_EVIDENCE_MISSING" in missing
    evidence = {
        "base_instructions_sha256": "a" * 64,
        "tool_catalog_sha256": "b" * 64,
        "plugin_catalog_sha256": "c" * 64,
        "resolved_model": EXPECTED_MODEL,
        "resolved_reasoning_effort": EXPECTED_EFFORT,
        "cli_version": EXPECTED_CLI_VERSION,
    }
    assert validate_runtime_fingerprint(evidence) == []
    assert "RUNTIME_PROMOTION_REQUIRED" in validate_runtime_evidence(
        evidence, allow_capture=False)
    wrong_model = dict(evidence, resolved_model="other")
    assert "RUNTIME_MODEL_MISMATCH" in validate_runtime_fingerprint(wrong_model)
    wrong_effort = dict(evidence, resolved_reasoning_effort="medium")
    assert "RUNTIME_EFFORT_MISMATCH" in validate_runtime_fingerprint(wrong_effort)
    pending = {"status": "PENDING_APPROVED_PILOT_PAIR"}
    assert runtime_start_problems(pending, pilot=False) == ["RUNTIME_EVIDENCE_MISSING"]
    assert runtime_start_problems(pending, pilot=True) == []
    assert approved_pilot_cell("B0", "01") is True
    assert approved_pilot_cell("B3", "12") is True
    assert approved_pilot_cell("B1", "01") is False

    promoted = {
        "status": "APPROVED_PILOT_PAIR",
        "parent_fingerprint": evidence,
        "child_role_fingerprints": {"worker": evidence},
        "pilot_bindings": [
            {"candidate": "B0", "cell": "01", "run_id": "pilot-b0-c01",
             "summary_sha256": "1" * 64, "raw_evidence_sha256": "2" * 64,
             "measurement_review_sha256": "a" * 64},
            {"candidate": "B3", "cell": "12", "run_id": "pilot-b3-c12",
             "summary_sha256": "3" * 64, "raw_evidence_sha256": "4" * 64,
             "measurement_review_sha256": "b" * 64},
        ],
        "independent_review_sha256": "5" * 64,
        "pilot_environment_manifest_sha256": "6" * 64,
        "comparison_environment_core_sha256": "7" * 64,
        "instrumentation": {"runner_sha256": "8" * 64, "metrics_sha256": "9" * 64},
        "source_documents": {"spec_sha256": "a" * 64},
    }
    assert validate_runtime_evidence(promoted, allow_capture=False) == []
    assert _fingerprint_matches(evidence, dict(evidence, tool_catalog_sha256="0" * 64))

    actual_lite_shape = [
        {"type": "session_meta", "payload": {
            "base_instructions": {"text": "base", "provenance": {
                "type": "model", "model": EXPECTED_MODEL}},
            "cli_version": EXPECTED_CLI_VERSION,
        }},
        {"type": "turn_context", "payload": {
            "model": EXPECTED_MODEL, "effort": EXPECTED_EFFORT,
        }},
    ]
    lite_fingerprint = runtime_evidence_from_events(
        actual_lite_shape, profile=LITE_PROFILE)
    assert lite_fingerprint == {
        "base_instructions_sha256": _canonical_sha256(
            actual_lite_shape[0]["payload"]["base_instructions"]),
        "dynamic_tools_sha256": _canonical_sha256([]),
        "dynamic_tool_namespace_projection_sha256": _canonical_sha256([]),
        "dynamic_tools_source": "session_meta.dynamic_tools",
        "dynamic_tools_serialization": "empty_omitted_for_cli_0.153.4",
        "dynamic_tools_scope": "dynamic_supplement_only",
        "full_tool_catalog_status": "unavailable_from_serialized_rollout",
        "plugin_catalog_status": "unavailable_from_serialized_rollout",
        "resolved_model": EXPECTED_MODEL,
        "resolved_reasoning_effort": EXPECTED_EFFORT,
        "cli_version": EXPECTED_CLI_VERSION,
    }
    assert validate_runtime_fingerprint(lite_fingerprint, profile=LITE_PROFILE) == []
    assert runtime_evidence_from_events(actual_lite_shape, profile=FULL_PROFILE) == {}

    nonempty_tools = [
        {"type": "function", "name": "direct", "description": "synthetic",
         "inputSchema": {"type": "object"}, "deferLoading": True},
        {"type": "namespace", "name": "bounded", "description": "synthetic",
         "tools": [
             {"type": "function", "name": "nested",
              "description": "synthetic", "inputSchema": None},
         ]},
    ]
    nonempty_events = json.loads(json.dumps(actual_lite_shape))
    nonempty_events[0]["payload"]["dynamic_tools"] = nonempty_tools
    nonempty_fingerprint = runtime_evidence_from_events(
        nonempty_events, profile=LITE_PROFILE)
    assert nonempty_fingerprint["dynamic_tools_sha256"] == _canonical_sha256(
        nonempty_tools)
    assert nonempty_fingerprint["dynamic_tools_serialization"] == "explicit_list"
    assert runtime_evidence_from_events(nonempty_events, profile=FULL_PROFILE) == {
        "base_instructions_sha256": _canonical_sha256(
            nonempty_events[0]["payload"]["base_instructions"]),
        "tool_catalog_sha256": _canonical_sha256(nonempty_tools),
        "plugin_catalog_sha256": _canonical_sha256(
            _dynamic_tool_projection(nonempty_tools)),
        "resolved_model": EXPECTED_MODEL,
        "resolved_reasoning_effort": EXPECTED_EFFORT,
        "cli_version": EXPECTED_CLI_VERSION,
    }

    malformed_events = json.loads(json.dumps(actual_lite_shape))
    malformed_events[0]["payload"]["dynamic_tools"] = None
    malformed_fingerprint = runtime_evidence_from_events(
        malformed_events, profile=LITE_PROFILE)
    assert "RUNTIME_DYNAMIC_TOOLS_MALFORMED" in validate_runtime_fingerprint(
        malformed_fingerprint, profile=LITE_PROFILE)
    explicit_empty_events = json.loads(json.dumps(actual_lite_shape))
    explicit_empty_events[0]["payload"]["dynamic_tools"] = []
    explicit_empty_fingerprint = runtime_evidence_from_events(
        explicit_empty_events, profile=LITE_PROFILE)
    assert "RUNTIME_DYNAMIC_TOOLS_MALFORMED" in validate_runtime_fingerprint(
        explicit_empty_fingerprint, profile=LITE_PROFILE)
    for invalid_tools in (
            [{"type": "function"}],
            [{"type": "namespace", "name": 7, "description": None,
              "tools": "bad"}],
            [{"type": "namespace", "name": "bounded",
              "description": "synthetic",
              "tools": [{"type": "namespace", "name": "nested",
                         "description": "invalid", "tools": []}]}],
            [{"type": "function", "name": "direct",
              "description": "synthetic", "inputSchema": {},
              "deferLoading": "yes"}],
    ):
        invalid_events = json.loads(json.dumps(actual_lite_shape))
        invalid_events[0]["payload"]["dynamic_tools"] = invalid_tools
        invalid_fingerprint = runtime_evidence_from_events(
            invalid_events, profile=LITE_PROFILE)
        assert "RUNTIME_DYNAMIC_TOOLS_MALFORMED" in validate_runtime_fingerprint(
            invalid_fingerprint, profile=LITE_PROFILE)
    unknown_version_events = json.loads(json.dumps(actual_lite_shape))
    unknown_version_events[0]["payload"]["cli_version"] = "unknown"
    unknown_fingerprint = runtime_evidence_from_events(
        unknown_version_events, profile=LITE_PROFILE)
    assert "RUNTIME_DYNAMIC_TOOLS_SERIALIZATION_UNSUPPORTED" in (
        validate_runtime_fingerprint(unknown_fingerprint, profile=LITE_PROFILE))
    for missing_key, problem in (
            ("base_instructions_sha256", "RUNTIME_HASH_INVALID:base_instructions_sha256"),
            ("resolved_model", "RUNTIME_MODEL_MISMATCH"),
            ("resolved_reasoning_effort", "RUNTIME_EFFORT_MISMATCH"),
            ("cli_version", "RUNTIME_CLI_MISMATCH")):
        incomplete = dict(lite_fingerprint)
        incomplete.pop(missing_key)
        assert problem in validate_runtime_fingerprint(incomplete, profile=LITE_PROFILE)

    scope_mismatch = dict(lite_fingerprint, dynamic_tools_scope="complete_catalog")
    assert "RUNTIME_DYNAMIC_TOOLS_SCOPE_MISMATCH" in validate_runtime_fingerprint(
        scope_mismatch, profile=LITE_PROFILE)
    assert _fingerprint_matches(
        lite_fingerprint, scope_mismatch, profile=LITE_PROFILE)

    lite_promoted = {
        "profile": LITE_PROFILE,
        "status": "APPROVED_PILOT_PAIR",
        "parent_fingerprint": lite_fingerprint,
        "child_role_fingerprints": {"worker": lite_fingerprint},
        "pilot_bindings": [
            {"candidate": "B0", "cell": "L1", "run_id": "pilot-lite-b0-l1",
             "summary_sha256": "1" * 64, "raw_evidence_sha256": "2" * 64,
             "measurement_review_sha256": "a" * 64},
            {"candidate": "B3", "cell": "L2", "run_id": "pilot-lite-b3-l2",
             "summary_sha256": "3" * 64, "raw_evidence_sha256": "4" * 64,
             "measurement_review_sha256": "b" * 64},
        ],
        "coordinator_review_sha256": "5" * 64,
        "pilot_environment_manifest_sha256": "6" * 64,
        "comparison_environment_core_sha256": "7" * 64,
        "instrumentation": {"runner_sha256": "8" * 64, "metrics_sha256": "9" * 64},
        "source_documents": {"lite_plan_sha256": "a" * 64},
    }
    assert validate_runtime_evidence(
        lite_promoted, allow_capture=False, profile=LITE_PROFILE) == []

    child_events = [{"type": "session_meta", "payload": {
        "source": {"subagent": {"thread_spawn": {"agent_type": "worker"}}}}}]
    assert child_role_from_events(child_events) == "worker"
    assert _decode_yaml_scalar('"\\uacc4\\uce21"') == ("계측", "decoded_yaml_scalar")
    for policy_path in ("scripts/validate-conventions.py", "scripts/run-hook.py",
                        ".github/workflows/harness-policy.yml"):
        assert _instruction_role(policy_path) == "policy_enforcement"

    sources = [{"path": "AGENTS.md", "content": "alpha\nbeta\n",
                "content_sha256": "a" * 64, "bytes": 11,
                "role": "repository_instruction"}]
    annotated = annotate_source_hints([
        {"type": "response_item", "payload": {
            "type": "function_call", "call_id": "read", "name": "exec_command",
            "arguments": '{"cmd":"nl -ba AGENTS.md"}',
        }},
        {"type": "response_item", "payload": {
            "type": "function_call_output", "call_id": "read",
            "output": "     1\\talpha\\n     2\\tbeta\\n",
        }},
    ], sources)
    assert annotated[-1]["source_hint"] == {"path": "AGENTS.md", "line_prefix": True}
    metrics = _metrics_module()
    start_rows = metrics.attribute_reads(_start_delivery_events([
        {"type": "session_meta", "payload": {
            "base_instructions": {"text": "global base only"}}},
        {"type": "response_item", "payload": {"type": "message", "role": "developer",
            "content": [{"type": "input_text", "text": "prefix\nalpha\nbeta\nsuffix\n"}]}},
    ]), [{key: value for key, value in {**sources[0], "delivery_phases": ["start"]}.items()
          if key != "content_sha256"}],
        token_counter=lambda text: len(text))
    assert [row["classification"] for row in start_rows] == [
        "global_prefix", "global_prefix", "repository_instruction", "global_prefix"]

    lineage = cumulative_candidate_files([
        {"id": "B0", "files": {"AGENTS.md": {"bytes": 1, "sha256": "a"}}},
        {"id": "B1", "files": {"ref.md": {"bytes": 2, "sha256": "b"}}},
        {"id": "B2", "changed_files": {"AGENTS.md": {"bytes": 3, "sha256": "c"}}},
    ])
    assert lineage["B1"] == {
        "AGENTS.md": {"bytes": 1, "sha256": "a"},
        "ref.md": {"bytes": 2, "sha256": "b"},
    }
    assert lineage["B2"]["AGENTS.md"] == {"bytes": 3, "sha256": "c"}

    measurement = {
        "status": "available", "R_start_bytes": 10, "R_start_tokens": 4,
        "R_read_bytes": 6, "R_read_tokens": 2, "C_structure_tokens": 6,
        "unique_read_tokens": 2, "buckets": {}, "source_rows": [],
    }
    combined = combine_instruction_measurements(measurement, [dict(measurement)])
    assert combined["R_start_tokens"] == 8
    assert combined["R_read_tokens"] == 4
    assert combined["C_structure_tokens"] == 12

    class FakeProcess:
        returncode = 0

        def communicate(self):
            return None

    calls = []

    def fake_popen(argv, **kwargs):
        calls.append((argv, kwargs))
        kwargs["stdout"].write(b'{"type":"thread.started","thread_id":"synthetic"}\n')
        kwargs["stdout"].write(b'{"type":"turn.completed","usage":{"input_tokens":1,'
                               b'"cached_input_tokens":0,"output_tokens":1,'
                               b'"reasoning_output_tokens":0}}\n')
        kwargs["stdout"].flush()
        return FakeProcess()

    with tempfile.TemporaryDirectory() as temp:
        raw_dir = Path(temp) / "raw"
        run_process(
            ["codex", "exec", "--json", "prompt"], raw_dir, "synthetic",
            popen_factory=fake_popen, environment={"PATH": "/bin"})
        assert calls[0][0] == ["codex", "exec", "--json", "prompt"]
        assert calls[0][1]["stdin"] is subprocess.DEVNULL
        assert calls[0][1]["shell"] is False
        assert stat.S_IMODE((raw_dir / "synthetic.stdout.jsonl").stat().st_mode) == 0o600


def _resolve_profile_arguments(args, docs, lite_plan_default):
    if not hasattr(args, "profile"):
        return
    args.lite_plan = args.lite_plan or lite_plan_default
    environment_name = ("gh-223-lite-environment.json"
                        if args.profile == LITE_PROFILE else "gh-223-environment.json")
    order_name = ("gh-223-lite-execution-order.csv"
                  if args.profile == LITE_PROFILE else "gh-223-execution-order.csv")
    if hasattr(args, "environment_output") and args.environment_output is None:
        args.environment_output = docs / environment_name
    if hasattr(args, "order_output") and args.order_output is None:
        args.order_output = docs / order_name
    if hasattr(args, "environment") and args.environment is None:
        args.environment = docs / environment_name
    if hasattr(args, "order") and args.order is None:
        args.order = docs / order_name


def main():
    repo_root = _repo_root()
    docs = repo_root / "docs/experiments/codex-agents"
    scratch = repo_root / ".superpowers/sdd/2026-09-11-codex-instruction-architecture-phase2/scratch"
    tokenizer_evidence_default = scratch / "task-7-tokenizer-evidence.json"
    tokenizer_path_default = _tokenizer_path_default(repo_root)
    lite_plan_default = (repo_root / "docs/superpowers/plans/"
                         "2026-09-11-codex-instruction-token-comparison-lite.md")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    subparsers = parser.add_subparsers(dest="command")

    prepare = subparsers.add_parser("prepare", help="write the environment and planned order")
    prepare.add_argument("--profile", choices=(FULL_PROFILE, LITE_PROFILE),
                         default=FULL_PROFILE)
    prepare.add_argument("--lite-plan", type=Path)
    prepare.add_argument("--fixtures", type=Path, default=docs / "gh-223-fixtures.json")
    prepare.add_argument(
        "--spec", type=Path,
        default=repo_root / "docs/superpowers/specs/"
                            "2026-09-11-codex-instruction-architecture-phase2-design.md")
    prepare.add_argument("--evaluation-design", type=Path,
                         default=docs / "gh-223-evaluation-design.md")
    prepare.add_argument("--candidate-parent", type=Path,
                         default=_candidate_parent_default(repo_root))
    prepare.add_argument("--runtime-evidence", type=Path)
    prepare.add_argument("--smoke-manifest", type=Path)
    prepare.add_argument("--tokenizer-pythonpath", type=Path, default=tokenizer_path_default)
    prepare.add_argument("--tokenizer-evidence", type=Path,
                         default=tokenizer_evidence_default)
    prepare.add_argument("--environment-output", type=Path,
                         default=None)
    prepare.add_argument("--order-output", type=Path,
                         default=None)
    prepare.set_defaults(handler=prepare_command)

    def add_run_arguments(command, is_pilot):
        command.add_argument("--profile", choices=(FULL_PROFILE, LITE_PROFILE),
                             default=FULL_PROFILE)
        command.add_argument("--lite-plan", type=Path)
        command.add_argument("--environment", type=Path,
                             default=None)
        command.add_argument("--order", type=Path,
                             default=None)
        command.add_argument("--fixtures", type=Path, default=docs / "gh-223-fixtures.json")
        command.add_argument(
            "--spec", type=Path,
            default=repo_root / "docs/superpowers/specs/"
                                "2026-09-11-codex-instruction-architecture-phase2-design.md")
        command.add_argument("--evaluation-design", type=Path,
                             default=docs / "gh-223-evaluation-design.md")
        command.add_argument("--candidate", required=True,
                             choices=("b0", "b1", "b2", "b3", "B0", "B1", "B2", "B3"))
        command.add_argument("--cell", required=True,
                             choices=tuple(CELL_LAYOUT) + ("L1", "L2", "L3"))
        command.add_argument("--candidate-parent", type=Path,
                             default=_candidate_parent_default(repo_root))
        command.add_argument("--candidate-cwd", type=Path)
        command.add_argument("--verification-cwd", type=Path)
        command.add_argument("--raw-dir", type=Path, required=True)
        command.add_argument("--session-root", type=Path, default=_session_root())
        command.add_argument("--tokenizer-pythonpath", type=Path,
                             default=tokenizer_path_default)
        command.add_argument("--tokenizer-evidence", type=Path,
                             default=tokenizer_evidence_default)
        if is_pilot:
            command.add_argument("--repetition", type=int, default=0)
            command.add_argument("--capture-runtime-evidence", type=Path)
            command.set_defaults(handler=lambda args: run_cell_command(args, pilot=True))
        else:
            command.add_argument("--repetition", type=int, required=True, choices=(1, 2, 3))
            command.set_defaults(capture_runtime_evidence=None)
            command.set_defaults(handler=lambda args: run_cell_command(args, pilot=False))

    pilot = subparsers.add_parser("pilot", help="run one approved instrumentation pilot")
    add_run_arguments(pilot, True)
    run_cell = subparsers.add_parser("run-cell", help="run one planned evaluation cell")
    add_run_arguments(run_cell, False)

    aggregate = subparsers.add_parser("aggregate", help="aggregate restricted run summaries")
    aggregate.add_argument("--profile", choices=(FULL_PROFILE, LITE_PROFILE),
                           default=FULL_PROFILE)
    aggregate.add_argument("--lite-plan", type=Path)
    aggregate.add_argument("--results-dir", type=Path, required=True)
    aggregate.add_argument("--output", type=Path, required=True)
    aggregate.add_argument(
        "--smoke-evidence-dir", type=Path,
        help="restricted Task 9 verification evidence directory")
    aggregate.add_argument("--environment", type=Path,
                           default=None)
    aggregate.add_argument("--order", type=Path,
                           default=None)
    aggregate.add_argument("--fixtures", type=Path, default=docs / "gh-223-fixtures.json")
    aggregate.add_argument(
        "--spec", type=Path,
        default=repo_root / "docs/superpowers/specs/"
                            "2026-09-11-codex-instruction-architecture-phase2-design.md")
    aggregate.add_argument("--evaluation-design", type=Path,
                           default=docs / "gh-223-evaluation-design.md")
    aggregate.set_defaults(handler=aggregate_command)

    verify = subparsers.add_parser("verify", help="verify immutable prepared inputs")
    verify.add_argument("--profile", choices=(FULL_PROFILE, LITE_PROFILE),
                        default=FULL_PROFILE)
    verify.add_argument("--lite-plan", type=Path)
    verify.add_argument("--environment", type=Path,
                        default=None)
    verify.add_argument("--order", type=Path,
                        default=None)
    verify.add_argument("--fixtures", type=Path, default=docs / "gh-223-fixtures.json")
    verify.add_argument(
        "--spec", type=Path,
        default=repo_root / "docs/superpowers/specs/"
                            "2026-09-11-codex-instruction-architecture-phase2-design.md")
    verify.add_argument("--evaluation-design", type=Path,
                        default=docs / "gh-223-evaluation-design.md")
    verify.add_argument("--candidate-parent", type=Path,
                        default=_candidate_parent_default(repo_root))
    verify.add_argument("--allow-runtime-unverified", action="store_true",
                        help="verify Task 7 static artifacts before the approved pilot")
    verify.add_argument("--tokenizer-pythonpath", type=Path,
                        default=tokenizer_path_default)
    verify.add_argument("--tokenizer-evidence", type=Path,
                        default=tokenizer_evidence_default)
    verify.set_defaults(handler=verify_command)
    args = parser.parse_args()
    if args.self_test:
        run_self_test()
        print("codex-instruction-phase2 self-test: PASS")
        return 0
    if args.command is None:
        parser.error("a subcommand is required")
    _resolve_profile_arguments(args, docs, lite_plan_default)
    try:
        return args.handler(args)
    except (OSError, KeyError, TypeError, csv.Error, json.JSONDecodeError,
            RuntimeError, ValueError) as exc:
        print(json.dumps({"status": "BLOCKED", "error": str(exc)}, sort_keys=True),
              file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
