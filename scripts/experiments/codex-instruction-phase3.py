#!/usr/bin/env python3
"""GH-225 bounded pilots and explicitly approved batch2; main stays review-gated.

Raw manifests, commands and rollouts are private. Stdout contains public metrics only.
This runner never resets credit, resumes, retries or commits. Batch2 has fourteen fixed sessions.
"""
import argparse
import hashlib
import importlib.util
import json
import os
import pwd
import re
from pathlib import Path
import shutil
import signal
import subprocess
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]
CANDIDATE = "8e0028b1729ad7ba8331d528ea15be4fbe9a0be0"
CONFIGS = {"sol": ("gpt-5.6-sol", "high"), "astra": ("gpt-6-astra", "medium")}
LIMITS = {"input": 8000000, "output": 160000, "usd": 40, "seconds": 5400}
PLAN = ROOT / "docs/superpowers/plans/2026-09-11-codex-instruction-token-comparison-lite.md"


def load_helper(name):
    path = Path(__file__).with_name(name + ".py")
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


P2 = load_helper("codex-instruction-phase2")
METRICS = load_helper("codex-instruction-metrics")


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def file_hash(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def usage(events):
    result = METRICS.extract_usage(events)
    for part in result.values():
        if part["status"] != "available":
            raise ValueError("USAGE_UNAVAILABLE")
        if (part["cached_input_tokens"] > part["input_tokens"] or
                part["reasoning_output_tokens"] > part["output_tokens"]):
            raise ValueError("USAGE_SUBSET_INVALID")
    return result


def cost(value, model):
    rates = (4, .4, 20) if model == "sol" else (10, 1, 50)
    return ((value["input_tokens"] - value["cached_input_tokens"]) * rates[0] +
            value["cached_input_tokens"] * rates[1] + value["output_tokens"] * rates[2]) / 1e6


def budget_reasons(value):
    return ["BUDGET_" + key.upper() for key, limit in LIMITS.items() if value.get(key, 0) >= limit]


def next_pilot(records):
    if not records:
        return 1
    if len(records) == 1 and records[0]["index"] == 1 and records[0]["status"] == "PILOT_CAPTURED":
        return 2
    raise ValueError("PILOT_ORDER_OR_PRIOR_FAILURE_OR_GATE")


def read_events(path, live=False):
    data = path.read_bytes()
    if live and data and not data.endswith(b"\n"):
        data = data.rsplit(b"\n", 1)[0] if b"\n" in data else b""
    rows = [json.loads(line) for line in data.splitlines() if line.strip()]
    if any(not isinstance(row, dict) for row in rows):
        raise ValueError("INVALID_EVENT")
    return rows


def git(cwd, *args):
    return P2._run_read_only(["git", "-C", str(cwd), *args])


def environment(cwd, private_inventory=None):
    if cwd == ROOT or git(cwd, "rev-parse", "HEAD") != CANDIDATE:
        raise ValueError("CANDIDATE_MISMATCH")
    if git(cwd, "status", "--porcelain", "--untracked-files=all"):
        raise ValueError("CANDIDATE_NOT_CLEAN")
    cli = Path(shutil.which("codex") or "").resolve()
    if not cli.is_file():
        raise ValueError("CLI_MISSING")
    home = Path(os.environ.get("CODEX_HOME", str(Path.home() / ".codex"))).resolve()
    files = [home / "config.toml", home / "plugins/installed_plugins.json"]
    files += sorted((home / "plugins").rglob("plugin.json"))
    files += sorted((home / "plugins").glob("*.json"))
    files += sorted((cwd / ".codex").rglob("*.toml"))
    inventory = {str(path): file_hash(path) for path in set(files) if path.is_file()}
    plugin_process = subprocess.run([str(cli), "plugin", "list", "--json"],
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                    stdin=subprocess.DEVNULL, env=P2.sanitized_environment(), timeout=30)
    if plugin_process.returncode:
        raise ValueError("PLUGIN_INVENTORY_UNAVAILABLE")
    plugin_inventory = json.loads(plugin_process.stdout)
    if private_inventory is not None:
        private_json(private_inventory, {"file_sha256": inventory, "plugin_list": plugin_inventory,
                                         "captured_at": time.time()})
    return {"candidate": CANDIDATE, "tree": git(cwd, "rev-parse", "HEAD^{tree}"),
            "cwd_sha256": digest(str(cwd)), "cli_sha256": file_hash(cli),
            "cli_version": P2._run_read_only([str(cli), "--version"]),
            "config_plugin_inventory_sha256": digest(inventory),
            "plugin_list_sha256": digest(plugin_inventory),
            "inventory_scope": "config.toml, plugin.json, plugins root JSON, candidate .codex TOML",
            "safe_environment_sha256": digest(P2.sanitized_environment()),
            "runner_sha256": file_hash(Path(__file__)),
            "helpers_sha256": {name: file_hash(Path(__file__).with_name(name + ".py"))
                               for name in ("codex-instruction-phase2", "codex-instruction-metrics")},
            "prompt_plan_sha256": file_hash(PLAN), "sandbox": "read-only",
            "full_delivered_catalog": "UNAVAILABLE", "equivalence": "limited_user_approved"}


def private_json(path, value):
    P2._write_restricted_json(path, value)


def order():
    pairs = [("pilot", "L2", "sol", "astra"), ("1", "L1", "sol", "astra"),
             ("1", "L2", "astra", "sol"), ("1", "L3", "sol", "astra"),
             ("2", "L3", "astra", "sol"), ("2", "L2", "sol", "astra"),
             ("2", "L1", "astra", "sol")]
    return [{"index": i + 1, "phase": phase, "cell": cell, "configuration": model}
            for i, (phase, cell, model) in enumerate(
                (phase, cell, model) for phase, cell, a, b in pairs for model in (a, b))]


def validate_raw_path(path):
    # Bind the attempt ledger to the OS account, independent of HOME/CODEX_HOME overrides.
    expected = Path(pwd.getpwuid(os.getuid()).pw_dir) / ".codex/experiments/gh-225-phase3"
    if path != expected or path.resolve() != expected:
        raise ValueError("RAW_LEDGER_PATH_MISMATCH")
    return expected


def transport_problem(stdout, stderr):
    pattern = re.compile(r"reconnect(?:ing)?|retrying|retry attempt|stream (?:disconnected|failed)|stream error", re.I)
    if pattern.search(stderr):
        return True
    for line in stdout.splitlines():
        try:
            event = json.loads(line)
        except ValueError:
            if pattern.search(line):
                return True
            continue
        if isinstance(event, dict) and event.get("type") in {"error", "turn.failed", "warning"}:
            # Inspect runtime notices only, not model answers discussing retry policy.
            if event.get("type") in {"error", "turn.failed"} or pattern.search(json.dumps(event)):
                return True
    return False


def prepare(args):
    validate_raw_path(args.raw_dir)
    raw = P2.ensure_restricted_directory(args.raw_dir, [ROOT, args.evaluation_cwd])
    if any(raw.iterdir()):
        raise ValueError("RAW_DIRECTORY_NOT_EMPTY")
    env = environment(args.evaluation_cwd, raw / "inventory-prepare.json")
    prompts, cells = P2.extract_lite_contract(PLAN)
    manifest = {"environment": env, "configurations": CONFIGS, "order": order(),
                "cells": cells, "limits": LIMITS, "single_seconds": 600,
                "evaluation_cwd": str(args.evaluation_cwd), "prepared_at": time.time()}
    manifest["manifest_sha256"] = digest(manifest)
    private_json(raw / "manifest.json", manifest)
    print(json.dumps({"status": "PREPARED", "environment": env, "order": order(),
                      "configurations": CONFIGS, "manifest_sha256": manifest["manifest_sha256"]}))


def runtime(events):
    metas = [e["payload"] for e in events if e.get("type") == "session_meta"]
    contexts = [e["payload"] for e in events if e.get("type") == "turn_context"]
    if not metas or not contexts:
        raise ValueError("RUNTIME_UNAVAILABLE")
    meta, context = metas[0], contexts[-1]
    return {"model": context.get("model"), "effort": context.get("effort"),
            "cli_version": meta.get("cli_version"), "cwd_sha256": digest(context.get("cwd")),
            "sandbox_sha256": digest(context.get("sandbox_policy")),
            "approval_policy": context.get("approval_policy"),
            "base_instructions_sha256": digest(meta.get("base_instructions")),
            "dynamic_tools_status": "observed" if "dynamic_tools" in meta else "UNAVAILABLE",
            "dynamic_tools_sha256": digest(meta["dynamic_tools"]) if "dynamic_tools" in meta else None,
            "full_delivered_catalog": "UNAVAILABLE"}


def source_capture(events, cwd):
    # Whole-source exact matches are a conservative lower bound, never complete attribution.
    return source_matches(events, P2.build_source_manifest(cwd))


def tool_payloads(events, kinds):
    seen = set()
    for event in events:
        payload = event.get("payload", {})
        if event.get("type") != "response_item" or payload.get("type") not in kinds:
            continue
        key = payload.get("id") or digest(payload)
        if key not in seen:
            seen.add(key)
            yield payload


def tool_call_count(events):
    return sum(1 for _ in tool_payloads(events, {"function_call", "custom_tool_call"}))


def source_matches(events, sources):
    found, missing, ambiguous = [], [], []
    deliveries = [("start", i, item.get("text"))
                  for i, item in enumerate(P2._start_delivery_events(events))]
    for i, payload in enumerate(tool_payloads(events, {"function_call_output", "custom_tool_call_output"})):
        output = payload.get("output")
        if isinstance(output, str):
            deliveries.append(("read", str(i) + ":0", output))
        elif isinstance(output, list):
            for block_index, block in enumerate(output):
                key = str(i) + ":" + str(block_index)
                if isinstance(block, dict) and block.get("type") in {"input_text", "text"} and isinstance(block.get("text"), str):
                    deliveries.append(("read", key, block["text"]))
                else:
                    missing.append({"delivery": key, "reason": "UNKNOWN_OUTPUT_BLOCK"})
        else:
            missing.append({"delivery": str(i), "reason": "UNKNOWN_OUTPUT_ENCODING"})
    for phase, block, text in deliveries:
        if not isinstance(text, str):
            missing.append({"delivery": block, "reason": "UNKNOWN_TEXT"})
            continue
        grouped = {}
        for source in sources:
            if phase in source.get("delivery_phases", ["start", "read"]) and source["content"]:
                grouped.setdefault(source["content"], []).append(source)
        for content, group in grouped.items():
            count = text.count(content)
            if not count:
                continue
            source = group[0]
            if len({item.get("path") for item in group}) > 1:
                ambiguous.append({"phase": phase, "delivery": block,
                                  "source_sha256": source["content_sha256"], "candidate_sources": len(group)})
                continue
            found.append({"phase": phase, "delivery": block,
                          "source_sha256": source["content_sha256"],
                          "bytes": source["bytes"], "occurrences": count})
    return {"status": "partial" if found else "unavailable", "whole_source_matches": found,
            "missing_deliveries": missing, "ambiguous_matches": ambiguous,
            "instruction_tokens": None, "missing_scope": "partial spans, wrappers, automatic injection and delivery completeness unproven"}


def totals(records):
    return {key: sum(r.get("budget", {}).get(key, 0) for r in records) for key in LIMITS}


def monitor_snapshot(raw, index, session_root, started, live):
    stdout = read_events(raw / (str(index) + "-stdout.jsonl"), live)
    thread = P2._thread_id(stdout)
    path = P2._find_rollout(thread, session_root)
    events = read_events(path, live)
    children = []
    # Only scan recent rollouts; incomplete final lines are ignored only during live monitoring.
    parents = {thread}
    candidates = [p for p in session_root.rglob("rollout-*.jsonl") if p.stat().st_mtime >= started - 2]
    pending = {p: read_events(p, live) for p in candidates}
    while True:
        added = False
        for child_path, child_events in list(pending.items()):
            meta = next((e.get("payload", {}) for e in child_events if e.get("type") == "session_meta"), {})
            if meta.get("parent_thread_id") in parents and meta.get("id") not in parents:
                parents.add(meta.get("id"))
                children.append((child_path, child_events))
                del pending[child_path]
                added = True
        if not added:
            break
    return path, events, stdout, children


def delegation_observed(events):
    return any("spawn_agent" in str(e.get("payload", {}).get("name", ""))
               for e in events if isinstance(e.get("payload"), dict))


def session_budget(events, stdout, children, model):
    parent = usage(events + stdout)
    values = [(model, parent)]
    for _, child_events in children:
        child_runtime = runtime(child_events)
        child_key = next((k for k, v in CONFIGS.items() if v[0] == child_runtime["model"]), None)
        if child_key is None:
            raise ValueError("CHILD_PRICING_UNAVAILABLE")
        values.append((child_key, usage(child_events)))
    for _, value in values:
        if value["total"].get("cache_write_input_tokens"):
            raise ValueError("PRICE_REVIEW_REQUIRED")
        if value["first"]["input_tokens"] > 272000:
            raise ValueError("LONG_CONTEXT_PRICE_REVIEW")
    for e in events + [e for _, rows in children for e in rows]:
        info = e.get("payload", {}).get("info") if isinstance(e.get("payload"), dict) else None
        if isinstance(info, dict):
            last = info.get("last_token_usage") or {}
            if last.get("input_tokens", 0) > 272000 or last.get("cache_write_input_tokens", 0):
                raise ValueError("PRICE_REVIEW_REQUIRED")
    return parent, {"input": sum(v["total"]["input_tokens"] for _, v in values),
                    "output": sum(v["total"]["output_tokens"] for _, v in values),
                    "usd": sum(cost(v["total"], k) for k, v in values)}


HISTORICAL_RESULT_SHA256 = "29cb688a13d1c543f73fa49a2a0ef0117a07a6082bc4a969806c5dce6f8a7253"


def validate_batch_path(path):
    validate_raw_path(path.parent)
    if path.name != "batch2" or path.resolve() != path:
        raise ValueError("BATCH_LEDGER_PATH_MISMATCH")
    return path


def historical_budget(raw):
    if ({p.name for p in raw.parent.glob("result-*.json")} != {"result-1.json"} or
            {p.name for p in raw.parent.glob("claim-*.json")} != {"claim-1.json"}):
        raise ValueError("HISTORICAL_ATTEMPT_SET_CHANGED")
    path = P2._restricted_input_file(raw.parent, "result-1.json")
    if file_hash(path) != HISTORICAL_RESULT_SHA256:
        raise ValueError("HISTORICAL_RESULT_CHANGED")
    return json.loads(path.read_text())["budget"]


def cumulative_budget(history, records):
    current = totals(records)
    return {key: history[key] + current[key] for key in LIMITS}


def next_batch(records, claims):
    expected = list(range(1, len(records) + 1))
    if (len(records) >= 14 or [r.get("index") for r in records] != expected or
            claims != set(expected) or any(r.get("status") != ("PILOT_CAPTURED" if r.get("index", 0) <= 2 else "RUN_CAPTURED") for r in records)):
        raise ValueError("BATCH_ORDER_OR_FAILURE_OR_CLAIM")
    return len(records) + 1


def cell_scenario(index):
    if not 1 <= index <= 14:
        raise ValueError("INDEX_NOT_APPROVED")
    return {"L1": "S1", "L2": "X2", "L3": "X1"}[order()[index - 1]["cell"]]


def validate_gate(gate, manifest_hash, pilot_hashes):
    expected = dict(status="PASS", reviewer_role="independent_verifier", usage="PASS", quality="PASS",
                    safety="PASS", environment="PASS", attribution="PARTIAL_ACCEPTED",
                    manifest_sha256=manifest_hash, pilot_result_sha256=pilot_hashes)
    if gate != expected:
        raise ValueError("PILOT_REVIEW_GATE_INVALID")


def prepare_batch2(args):
    raw = validate_batch_path(args.raw_dir)
    history = historical_budget(raw)
    # Exclusive directory creation makes this approval consumable only once, including preparation failures.
    raw.mkdir(mode=0o700)
    P2.ensure_restricted_directory(raw, [ROOT, args.evaluation_cwd])
    with P2.restricted_open(raw / "runner.py") as handle:
        handle.write(Path(__file__).read_bytes())
    env = environment(args.evaluation_cwd, raw / "inventory-prepare.json")
    _, cells = P2.extract_lite_contract(PLAN)
    manifest = {"environment": env, "configurations": CONFIGS, "order": order(),
                "cells": cells, "limits": LIMITS, "single_seconds": 600,
                "evaluation_cwd": str(args.evaluation_cwd), "prepared_at": time.time(),
                "batch": "batch2", "authorized_new_sessions": 14, "authorized_total_sessions": 15,
                "historical_result_sha256": HISTORICAL_RESULT_SHA256, "historical_budget": history,
                "partial_attribution": "USER_APPROVED_FOR_MAIN_AFTER_PILOT_REVIEW"}
    manifest["manifest_sha256"] = digest(manifest)
    private_json(raw / "manifest.json", manifest)
    print(json.dumps({"status": "BATCH2_PREPARED", "environment": env, "order": order(),
                      "historical_budget": history, "manifest_sha256": manifest["manifest_sha256"]}))


def inventory_drift(before_path, after_path):
    before = json.loads(before_path.read_text())
    after = json.loads(after_path.read_text())
    changed = []
    for name in sorted(set(before["file_sha256"]) | set(after["file_sha256"])):
        old, new = before["file_sha256"].get(name), after["file_sha256"].get(name)
        if old != new:
            logical = "codex-config" if Path(name).name == "config.toml" else "inventory-file-" + digest(name)[:12]
            changed.append({"logical_file": logical, "before_sha256": old, "after_sha256": new})
    return {"changed_files": changed, "plugin_list_changed": digest(before["plugin_list"]) != digest(after["plugin_list"])}


def pilot(args):
    batch = args.command == "run-next"
    (validate_batch_path if batch else validate_raw_path)(args.raw_dir)
    raw = P2.ensure_restricted_directory(args.raw_dir, [ROOT, args.evaluation_cwd])
    manifest = json.loads(P2._restricted_input_file(raw, "manifest.json").read_text())
    bound = dict(manifest)
    expected_hash = bound.pop("manifest_sha256")
    if digest(bound) != expected_hash or manifest["evaluation_cwd"] != str(args.evaluation_cwd):
        raise ValueError("MANIFEST_BINDING_MISMATCH")
    records = [json.loads(p.read_text()) for p in sorted(raw.glob("result-*.json"),
               key=lambda path: int(path.stem.split("-")[1]))]
    if batch:
        history = historical_budget(raw)
        if (manifest.get("batch") != "batch2" or manifest.get("historical_budget") != history or
                manifest.get("historical_result_sha256") != HISTORICAL_RESULT_SHA256 or
                manifest.get("order") != order() or manifest.get("limits") != LIMITS or
                manifest.get("authorized_new_sessions") != 14 or manifest.get("authorized_total_sessions") != 15 or
                file_hash(P2._restricted_input_file(raw, "runner.py")) != manifest["environment"]["runner_sha256"]):
            raise ValueError("BATCH_CONTRACT_MISMATCH")
        if any(record.get("manifest_sha256") != expected_hash for record in records):
            raise ValueError("RESULT_BINDING_MISMATCH")
        claims = {int(path.stem.split("-")[1]) for path in raw.glob("claim-*.json")}
        index = next_batch(records, claims)
        if index > 2:
            gate = json.loads(P2._restricted_input_file(raw, "pilot-review.json").read_text())
            validate_gate(gate, expected_hash, {str(i): file_hash(P2._restricted_input_file(raw, "result-" + str(i) + ".json")) for i in (1, 2)})
    else:
        index = next_pilot(records)
    if index != args.index:
        raise ValueError("ORDER_MISMATCH")
    pre_inventory = raw / ("inventory-pre-" + str(index) + ".json") if batch else None
    if environment(args.evaluation_cwd, pre_inventory) != manifest["environment"]:
        if batch:
            print(json.dumps({"status": "BLOCKED", "reason": "PRE_ENVIRONMENT_DRIFT",
                              "environment_drift": inventory_drift(raw / "inventory-prepare.json", pre_inventory)}))
            return 1
        raise ValueError("ENVIRONMENT_DRIFT")
    prior = cumulative_budget(history, records) if batch else totals(records)
    if budget_reasons(prior):
        raise ValueError("BUDGET_EXHAUSTED")
    # A permanent exclusive claim survives crashes and blocks every retry or concurrent runner.
    private_json(raw / ("claim-" + str(index) + ".json"), {"index": index, "started": time.time()})
    model = order()[index - 1]["configuration"] if batch else ("sol" if index == 1 else "astra")
    scenario = cell_scenario(index) if batch else "X2"
    prompts, _ = P2.extract_lite_contract(PLAN)
    argv = [str(Path(shutil.which("codex")).resolve()), "exec", "--json", "--model", CONFIGS[model][0],
            "--config", 'model_reasoning_effort="' + CONFIGS[model][1] + '"',
            "--config", 'approval_policy="never"',
            "--sandbox", "read-only", "--cd", str(args.evaluation_cwd), prompts[scenario]]
    private_json(raw / (str(index) + "-command.json"), {"argv": argv, "environment_sha256": manifest["environment"]["safe_environment_sha256"]})
    started = time.time()
    result = {"index": index, "configuration": model, "scenario": scenario,
              "run_id": ("batch2-" if batch else "original-") + str(index).zfill(2),
              "cell": order()[index - 1]["cell"] if batch else "L2",
              "model": CONFIGS[model][0], "effort": CONFIGS[model][1],
              "prompt_sha256": hashlib.sha256(prompts[scenario].encode()).hexdigest(),
              "phase": order()[index - 1]["phase"] if batch else "pilot", "status": "ERROR", "manifest_sha256": expected_hash,
              "quality": "UNREVIEWED", "retries_by_runner": 0,
              "transport_retries": "UNAVAILABLE",
              "transport_retry_policy": "CLI defaults unverified; no deliberate retries; abort on observed runtime notice", "budget_completeness": "UNAVAILABLE", "budget": {}}
    process = None
    stop = None
    try:
        with P2.restricted_open(raw / (str(index) + "-stdout.jsonl")) as out, P2.restricted_open(raw / (str(index) + "-stderr.log")) as err:
            process = subprocess.Popen(argv, stdout=out, stderr=err, stdin=subprocess.DEVNULL,
                                       env=P2.sanitized_environment(), start_new_session=True)
            while process.poll() is None:
                if transport_problem((raw / (str(index) + "-stdout.jsonl")).read_text(errors="replace"),
                                     (raw / (str(index) + "-stderr.log")).read_text(errors="replace")):
                    result["transport_retries"] = "RETRY_OR_STREAM_FAILURE_OBSERVED"
                    stop = "TRANSPORT_NOTICE_ABORT"
                    break
                elapsed = time.time() - started
                if elapsed >= 600 or prior["seconds"] + elapsed >= 5400:
                    stop = "TIME_LIMIT"
                    break
                try:
                    _, events, stdout, children = monitor_snapshot(raw, index, args.session_root, started, True)
                    if children or delegation_observed(events):
                        stop = "UNEXPECTED_DELEGATION"
                        break
                    _, current = session_budget(events, stdout, children, model)
                    current["seconds"] = elapsed
                    result["budget"] = current
                    reasons = budget_reasons({k: prior[k] + current[k] for k in LIMITS})
                    if reasons:
                        stop = reasons[0]
                        break
                except ValueError as exc:
                    if str(exc) not in {"USAGE_UNAVAILABLE", "exactly one thread.started event is required",
                                       "restricted rollout discovery did not find exactly one session", "RUNTIME_UNAVAILABLE"}:
                        raise
                    if elapsed >= 120:
                        stop = "LIVE_USAGE_UNAVAILABLE"
                        break
                time.sleep(1)
            if stop:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
            else:
                process.wait()
        if transport_problem((raw / (str(index) + "-stdout.jsonl")).read_text(errors="replace"),
                             (raw / (str(index) + "-stderr.log")).read_text(errors="replace")):
            result["transport_retries"] = "RETRY_OR_STREAM_FAILURE_OBSERVED"
            stop = "TRANSPORT_NOTICE_ABORT"
        path, events, stdout, children = monitor_snapshot(raw, index, args.session_root, started, False)
        for n, (source, _) in enumerate([(path, events)] + children):
            with P2.restricted_open(raw / (str(index) + "-rollout-" + str(n) + ".jsonl")) as handle:
                handle.write(source.read_bytes())
        parent, current = session_budget(events, stdout, children, model)
        current["seconds"] = time.time() - started
        result.update(budget=current, usage=parent, runtime=runtime(events),
                      child_sessions=[{"runtime": runtime(rows), "usage": usage(rows)} for _, rows in children],
                      child_calls=len(children), instruction=source_capture(events, args.evaluation_cwd),
                      command_calls=tool_call_count(events),
                      command_calls_scope="outer function/custom tool invocations; not nested shell command count",
                      exit_code=process.returncode)
        if children or delegation_observed(events):
            raise ValueError("UNEXPECTED_DELEGATION")
        rt = result["runtime"]
        sandbox = next((e.get("payload", {}).get("sandbox_policy") for e in reversed(events)
                        if e.get("type") == "turn_context"), None)
        if not isinstance(sandbox, dict) or sandbox.get("type") != "read-only" or rt["approval_policy"] != "never":
            raise ValueError("RUNTIME_PERMISSIONS_MISMATCH")
        if rt["cli_version"] not in manifest["environment"]["cli_version"]:
            raise ValueError("RUNTIME_CLI_MISMATCH")
        if (rt["model"], rt["effort"]) != CONFIGS[model] or rt["cwd_sha256"] != manifest["environment"]["cwd_sha256"]:
            raise ValueError("RUNTIME_MISMATCH")
        if records:
            for key in ("cli_version", "cwd_sha256", "sandbox_sha256", "approval_policy", "dynamic_tools_status", "dynamic_tools_sha256"):
                if rt[key] != records[0]["runtime"][key]:
                    raise ValueError("OBSERVED_RUNTIME_DRIFT")
        if batch and index > 2:
            baseline = records[0 if model == "sol" else 1]["runtime"]
            if rt != baseline:
                raise ValueError("MODEL_PILOT_RUNTIME_DRIFT")
        if environment(args.evaluation_cwd, raw / ("inventory-post-" + str(index) + ".json")) != manifest["environment"]:
            result["environment_drift"] = inventory_drift(raw / "inventory-prepare.json", raw / ("inventory-post-" + str(index) + ".json"))
            raise ValueError("POST_ENVIRONMENT_DRIFT")
        if stop or process.returncode != 0:
            raise ValueError(stop or "PROCESS_FAILED")
        if budget_reasons({k: prior[k] + current[k] for k in LIMITS}):
            raise ValueError("BUDGET_REACHED_AT_EXIT")
        result["budget_completeness"] = "OBSERVED_PARENT_NO_CHILDREN"
        result["status"] = "RUN_CAPTURED" if batch and index > 2 else "PILOT_CAPTURED"
        result["next_gate"] = "INDEPENDENT_ATTRIBUTION_AND_QUALITY_REVIEW"
    except BaseException as exc:
        # Never expose arbitrary exception text: JSON errors can contain raw event content.
        result["error_type"] = type(exc).__name__
        result["error_code"] = str(exc) if str(exc).replace("_", "").isalnum() and str(exc).isupper() else "CAPTURE_FAILED"
    finally:
        if process is not None and process.poll() is None:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
        result["budget"]["seconds"] = time.time() - started
        if batch:
            result["cumulative_budget_including_history"] = {key: prior[key] + result["budget"].get(key, 0) for key in LIMITS}
        result["artifact_sha256"] = {path.name: file_hash(path) for path in sorted(raw.glob(str(index) + "-*")) if path.is_file()}
        if batch:
            result["historical_result_sha256"] = HISTORICAL_RESULT_SHA256
            if index > 2:
                result["pilot_review_sha256"] = file_hash(raw / "pilot-review.json")
        private_json(raw / ("result-" + str(index) + ".json"), result)
    print(json.dumps(result, sort_keys=True))
    return 0 if result["status"] in {"PILOT_CAPTURED", "RUN_CAPTURED"} else 1


def run_self_test():
    class ContractTests(unittest.TestCase):
        def test_cache_and_duplicate_cumulative_usage(self):
            u = dict(input_tokens=100, cached_input_tokens=60, output_tokens=10,
                     reasoning_output_tokens=3)
            e = {'type': 'event_msg', 'payload': {'type': 'token_count', 'info': {
                'last_token_usage': u, 'total_token_usage': u}}}
            result = usage([e, e])
            self.assertEqual(result['total']['input_tokens'], 100)
            self.assertAlmostEqual(cost(result['total'], 'sol'), .000384)
            self.assertEqual(result['first']['cached_input_tokens'], 60)
            u['cached_input_tokens'] = 101
            with self.assertRaises(ValueError):
                usage([e])

        def test_missing_usage_blocks_instead_of_zero(self):
            with self.assertRaises(ValueError):
                usage([])

        def test_budget_stops_at_each_limit_and_unknown(self):
            for field, value in [('input', 8000000), ('output', 160000),
                                 ('usd', 40), ('seconds', 5400)]:
                self.assertTrue(budget_reasons({field: value}))
            self.assertFalse(budget_reasons({}))

        def test_cache_write_requires_price_review(self):
            u = dict(input_tokens=100, cached_input_tokens=60, output_tokens=10,
                     reasoning_output_tokens=3, cache_write_input_tokens=5)
            e = {'type': 'event_msg', 'payload': {'type': 'token_count', 'info': {
                'last_token_usage': dict(u, cache_write_input_tokens=0), 'total_token_usage': u}}}
            with self.assertRaises(ValueError):
                session_budget([e], [], [], 'sol')

        def test_child_usage_accounted_with_own_price(self):
            u = dict(input_tokens=100, cached_input_tokens=60, output_tokens=10,
                     reasoning_output_tokens=3)
            e = {'type': 'event_msg', 'payload': {'type': 'token_count', 'info': {
                'last_token_usage': u, 'total_token_usage': u}}}
            child = [{'type': 'session_meta', 'payload': {}},
                     {'type': 'turn_context', 'payload': {'model': 'gpt-6-astra', 'effort': 'medium'}}, e]
            _, budget = session_budget([e], [], [(None, child)], 'sol')
            self.assertEqual(budget['input'], 200)
            self.assertEqual(budget['output'], 20)
            self.assertAlmostEqual(budget['usd'], .001344)
            child[1]['payload']['model'] = 'unknown'
            with self.assertRaises(ValueError):
                session_budget([e], [], [(None, child)], 'sol')

        def test_live_partial_line_and_final_error(self):
            import tempfile
            with tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'events.jsonl'
                path.write_text('{"type":"ok"}\n{"type":')
                self.assertEqual(read_events(path, True), [{'type': 'ok'}])
                with self.assertRaises(ValueError):
                    read_events(path, False)

        def test_unknown_dynamic_omission_is_unavailable(self):
            evidence = runtime([{'type': 'session_meta', 'payload': {'cli_version': '0.154.0-alpha.6.2'}},
                                {'type': 'turn_context', 'payload': {'model': 'gpt-5.6-sol'}}])
            self.assertEqual(evidence['dynamic_tools_status'], 'UNAVAILABLE')
            self.assertIsNone(evidence['dynamic_tools_sha256'])

        def test_source_ambiguity_and_truncation_never_exact(self):
            source = {'content': 'instruction', 'content_sha256': 'abc', 'bytes': 11}
            events = [{'type': 'response_item', 'payload': {'type': 'message', 'role': 'user',
                       'content': [{'type': 'input_text', 'text': 'instruction'}]}}]
            result = source_matches(events, [source, dict(source)])
            self.assertEqual(result['status'], 'partial')
            self.assertIsNone(result['instruction_tokens'])
            events[0]['payload']['content'][0]['text'] = 'instru... output truncated ...'
            self.assertEqual(source_matches(events, [source])['status'], 'unavailable')

        def test_actual_custom_shape_preserves_28_blocks(self):
            events = []
            for i, size in enumerate([2, 2, 2, 9, 2, 3, 2, 2, 2, 2]):
                events.extend([
                    {'type': 'response_item', 'payload': {'type': 'custom_tool_call',
                     'id': 'call-' + str(i), 'call_id': str(i), 'name': 'exec', 'input': 'read'}},
                    {'type': 'response_item', 'payload': {'type': 'custom_tool_call_output',
                     'id': 'output-' + str(i), 'call_id': str(i),
                     'output': [{'type': 'input_text', 'text': 'instruction'} for _ in range(size)]}}])
            source = {'content': 'instruction', 'content_sha256': 'abc', 'bytes': 11,
                      'delivery_phases': ['read'], 'path': 'AGENTS.md'}
            result = source_matches(events, [source])
            self.assertEqual(len(result['whole_source_matches']), 28)
            self.assertEqual(result['status'], 'partial')
            self.assertEqual(tool_call_count(events + [events[0]]), 10)

        def test_unknown_blocks_and_ambiguous_sources_are_missing(self):
            events = [{'type': 'response_item', 'payload': {'type': 'function_call_output',
                      'call_id': 'a', 'output': [{'type': 'input_text', 'text': 'left'},
                      {'type': 'input_text', 'text': 'right'}, {'type': 'image', 'data': 'x'}]}}]
            source = {'content': 'leftright', 'content_sha256': 'abc', 'bytes': 9,
                      'delivery_phases': ['read']}
            result = source_matches(events, [source])
            self.assertFalse(result['whole_source_matches'])
            self.assertTrue(result.get('missing_deliveries'))
            events[0]['payload']['output'] = 'leftright'
            result = source_matches(events, [dict(source, path='a'), dict(source, path='b')])
            self.assertFalse(result['whole_source_matches'])
            self.assertTrue(result.get('ambiguous_matches'))

        def test_reanalysis_keeps_original_failure_separate(self):
            original = {'status': 'ERROR', 'error_code': 'POST_ENVIRONMENT_DRIFT', 'command_calls': 0}
            before = json.dumps(original, sort_keys=True)
            result = derive_metrics([], [], original)
            self.assertEqual(result['original_status'], 'ERROR')
            self.assertEqual(result['original_error_code'], 'POST_ENVIRONMENT_DRIFT')
            self.assertEqual(result['status'], 'DERIVED_ONLY_NOT_NEW_EXECUTION')
            self.assertEqual(json.dumps(original, sort_keys=True), before)

        def test_batch_sequence_fails_closed(self):
            self.assertEqual(next_batch([], set()), 1)
            self.assertEqual(next_batch([{'index': 1, 'status': 'PILOT_CAPTURED'}], {1}), 2)
            for rows, claims in [([], {1}), ([{'index': 1, 'status': 'ERROR'}], {1}),
                                 ([{'index': 2, 'status': 'PILOT_CAPTURED'}], {2})]:
                with self.assertRaises(ValueError):
                    next_batch(rows, claims)
            records = [{'index': i, 'status': 'PILOT_CAPTURED' if i <= 2 else 'RUN_CAPTURED'} for i in range(1, 14)]
            self.assertEqual(next_batch(records, set(range(1, 14))), 14)
            with self.assertRaises(ValueError):
                next_batch(records + [{'index': 14, 'status': 'RUN_CAPTURED'}], set(range(1, 15)))
            self.assertEqual(cell_scenario(3), 'S1')
            self.assertEqual(cell_scenario(7), 'X1')
            self.assertEqual(cell_scenario(11), 'X2')

        def test_historical_budget_never_resets(self):
            value = cumulative_budget({'input': 314416, 'output': 5464, 'usd': .3430464, 'seconds': 121.3678877},
                                      [{'budget': {'input': 7685584, 'output': 1, 'usd': 1, 'seconds': 2}}])
            self.assertEqual(value['input'], 8000000)
            self.assertEqual(value['output'], 5465)
            self.assertTrue(budget_reasons(value))

        def test_hash_gate_rejects_unbound_pilots(self):
            gate = dict(status='PASS', reviewer_role='independent_verifier', usage='PASS', quality='PASS',
                        safety='PASS', environment='PASS', attribution='PARTIAL_ACCEPTED',
                        manifest_sha256='manifest', pilot_result_sha256={'1': 'a', '2': 'b'})
            validate_gate(gate, 'manifest', {'1': 'a', '2': 'b'})
            for key, value in [('manifest_sha256', 'other'), ('quality', 'UNREVIEWED'),
                               ('pilot_result_sha256', {'1': 'old', '2': 'b'})]:
                with self.assertRaises(ValueError):
                    validate_gate(dict(gate, **{key: value}), 'manifest', {'1': 'a', '2': 'b'})

        def test_other_raw_directory_cannot_reset_ledger(self):
            with self.assertRaises(ValueError):
                validate_raw_path(Path('/tmp/gh225-alternate'))
            self.assertEqual(validate_raw_path(Path.home() / '.codex/experiments/gh-225-phase3'),
                             Path.home() / '.codex/experiments/gh-225-phase3')

        def test_transport_retry_notices_stop(self):
            self.assertTrue(transport_problem('Reconnecting... 1/5', ''))
            self.assertTrue(transport_problem('', 'stream disconnected before completion'))
            self.assertFalse(transport_problem('{"type":"thread.started"}', ''))
            self.assertFalse(transport_problem('{"type":"item.completed","text":"explain retry policy"}', ''))

        def test_only_next_pilot_once(self):
            self.assertEqual(next_pilot([]), 1)
            self.assertEqual(next_pilot([{'index': 1, 'status': 'PILOT_CAPTURED'}]), 2)
            for records in [[{'index': 1, 'status': 'ERROR'}],
                            [{'index': 2, 'status': 'PILOT_CAPTURED'}],
                            [{'index': 1, 'status': 'PILOT_CAPTURED'},
                             {'index': 2, 'status': 'PILOT_CAPTURED'}]]:
                with self.assertRaises(ValueError):
                    next_pilot(records)

    result = unittest.TextTestRunner().run(unittest.defaultTestLoader.loadTestsFromTestCase(ContractTests))
    return 0 if result.wasSuccessful() else 1


def derive_metrics(events, sources, original):
    return {"status": "DERIVED_ONLY_NOT_NEW_EXECUTION", "original_status": original.get("status"),
            "original_error_code": original.get("error_code"),
            "original_command_calls": original.get("command_calls"),
            "command_calls": tool_call_count(events),
            "command_calls_scope": "outer function/custom tool invocations; not nested shell command count",
            "instruction": source_matches(events, sources)}


def reanalyze(args):
    # Read existing artifacts only. Never call prepare, pilot, environment or Codex CLI here.
    raw = validate_raw_path(args.raw_dir)
    names = ["manifest.json", "result-" + str(args.index) + ".json",
             "runner-pilot" + str(args.index) + ".py", str(args.index) + "-rollout-0.jsonl"]
    paths = {name: P2._restricted_input_file(raw, name) for name in names}
    hashes = {name: file_hash(path) for name, path in paths.items()}
    manifest = json.loads(paths[names[0]].read_text())
    original = json.loads(paths[names[1]].read_text())
    bound = dict(manifest)
    expected = bound.pop("manifest_sha256")
    if digest(bound) != expected or original.get("manifest_sha256") != expected:
        raise ValueError("MANIFEST_BINDING_MISMATCH")
    if hashes[names[2]] != manifest["environment"]["runner_sha256"]:
        raise ValueError("ORIGINAL_RUNNER_BINDING_MISMATCH")
    cwd = args.evaluation_cwd
    if (str(cwd) != manifest["evaluation_cwd"] or git(cwd, "rev-parse", "HEAD") != CANDIDATE or
            git(cwd, "rev-parse", "HEAD^{tree}") != manifest["environment"]["tree"] or
            git(cwd, "status", "--porcelain", "--untracked-files=all")):
        raise ValueError("SOURCE_CHECKOUT_MISMATCH")
    events = read_events(paths[names[3]])
    result = derive_metrics(events, P2.build_source_manifest(cwd), original)
    result.update(analysis_runner_sha256=file_hash(Path(__file__)), artifact_sha256=hashes,
                  original_manifest_sha256=expected, index=args.index)
    if any(file_hash(path) != hashes[name] for name, path in paths.items()):
        raise ValueError("ARTIFACT_CHANGED_DURING_READ")
    print(json.dumps(result, sort_keys=True))
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    commands = parser.add_subparsers(dest="command")
    for name in ("prepare", "pilot", "reanalyze", "prepare-batch2", "run-next"):
        sub = commands.add_parser(name)
        sub.add_argument("--evaluation-cwd", type=lambda p: Path(p).expanduser().resolve(), required=True)
        sub.add_argument("--raw-dir", type=lambda p: Path(p).expanduser().absolute(), required=True)
        if name in {"pilot", "reanalyze", "run-next"}:
            sub.add_argument("--index", type=int, choices=range(1, 15) if name == "run-next" else (1, 2), required=True)
        if name in {"pilot", "run-next"}:
            sub.add_argument("--session-root", type=Path, default=P2._session_root())
    args = parser.parse_args()
    if args.self_test:
        return run_self_test()
    if args.command == "prepare":
        prepare(args)
        return 0
    if args.command == "prepare-batch2":
        prepare_batch2(args)
        return 0
    if args.command in {"pilot", "run-next"}:
        return pilot(args)
    if args.command == "reanalyze":
        return reanalyze(args)
    parser.error("choose prepare, pilot or reanalyze")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError, RuntimeError):
        print(json.dumps({"status": "BLOCKED", "reason": "PREFLIGHT_FAILED_NO_MODEL_CALL_OR_REPEAT_REFUSED"}))
        raise SystemExit(1)
