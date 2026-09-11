#!/usr/bin/env python3
"""Phase 2 instruction evaluation metrics."""

import argparse
import hashlib
import json
import re
from statistics import mean, median


USAGE_FIELDS = (
    "input_tokens",
    "cached_input_tokens",
    "output_tokens",
    "reasoning_output_tokens",
)
TRUNCATION_MARKERS = (
    "... output truncated ...",
    "… output truncated …",
    "truncated after",
    "output was truncated",
    "warning: truncated output (original token count:",
)


def _sha256(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def structural_cost(start_tokens, delivered_reads):
    if start_tokens < 0 or any(value < 0 for value in delivered_reads):
        raise ValueError("negative metric")
    return start_tokens + sum(delivered_reads)


def selection_score(cell_runs, normal_cells):
    if (len(normal_cells) != 9
            or any(cell not in cell_runs or len(cell_runs[cell]) != 3
                   for cell in normal_cells)):
        raise ValueError("incomplete sample")
    values = [value for cell in normal_cells for value in cell_runs[cell]]
    if any(value is None for value in values):
        raise ValueError("unavailable metric")
    if any(value < 0 for value in values):
        raise ValueError("negative metric")
    return mean(median(cell_runs[cell]) for cell in normal_cells)


def _unavailable(reason, source):
    return {
        "status": "unavailable",
        "source": source,
        "reason": reason,
        **{field: None for field in USAGE_FIELDS},
    }


def _normalize_usage(value, source):
    if not isinstance(value, dict):
        return _unavailable("usage_object_missing", source)
    normalized = {"status": "available", "source": source}
    for field in USAGE_FIELDS:
        metric = value.get(field)
        if not isinstance(metric, int) or isinstance(metric, bool) or metric < 0:
            return _unavailable("invalid_" + field, source)
        normalized[field] = metric
    cache_write = value.get("cache_write_input_tokens")
    total = value.get("total_tokens")
    normalized["cache_write_input_tokens"] = (
        cache_write if isinstance(cache_write, int) and cache_write >= 0 else None
    )
    normalized["total_tokens"] = (
        total if isinstance(total, int) and total >= 0 else None
    )
    return normalized


def extract_usage(events):
    """Select first per-turn and final cumulative usage without summing events."""
    session_pairs = []
    stdout_totals = []
    for event in events:
        if not isinstance(event, dict):
            continue
        payload = event.get("payload")
        if (event.get("type") == "event_msg" and isinstance(payload, dict)
                and payload.get("type") == "token_count"):
            info = payload.get("info")
            if isinstance(info, dict):
                session_pairs.append((info.get("last_token_usage"),
                                      info.get("total_token_usage")))
        if event.get("type") == "turn.completed":
            stdout_totals.append(event.get("usage"))

    if session_pairs:
        first = _normalize_usage(session_pairs[0][0], "session_token_count.last_token_usage")
        total = _normalize_usage(session_pairs[-1][1], "session_token_count.total_token_usage")
        return {"first": first, "total": total}
    if stdout_totals:
        return {
            "first": _unavailable("stdout_has_no_first_call_usage", "stdout_jsonl"),
            "total": _normalize_usage(stdout_totals[-1], "stdout_turn.completed.usage"),
        }
    return {
        "first": _unavailable("usage_event_missing", "none"),
        "total": _unavailable("usage_event_missing", "none"),
    }


def _has_truncation_metadata(value):
    if not isinstance(value, dict):
        return False
    for key in ("truncated", "is_truncated", "output_truncated"):
        if value.get(key) is True:
            return True
    if isinstance(value.get("original_token_count"), int):
        return True
    metadata = value.get("metadata")
    return _has_truncation_metadata(metadata) if isinstance(metadata, dict) else False


def _output_text(event):
    if not isinstance(event, dict):
        return None, None, False
    if event.get("type") == "item.completed":
        item = event.get("item")
        if isinstance(item, dict) and item.get("type") in {
                "command_execution", "mcp_tool_call", "web_search"}:
            for key in ("aggregated_output", "output", "result"):
                if isinstance(item.get(key), str):
                    return item[key], item.get("id"), _has_truncation_metadata(item)
    if event.get("type") == "response_item":
        payload = event.get("payload")
        if isinstance(payload, dict) and payload.get("type") in {
                "custom_tool_call_output", "function_call_output"}:
            output = payload.get("output")
            if isinstance(output, str):
                try:
                    decoded = json.loads(output)
                except json.JSONDecodeError:
                    decoded = None
                if isinstance(decoded, dict) and isinstance(decoded.get("output"), str):
                    return (decoded["output"], payload.get("call_id") or payload.get("id"),
                            _has_truncation_metadata(decoded) or _has_truncation_metadata(payload))
                return (output, payload.get("call_id") or payload.get("id"),
                        _has_truncation_metadata(payload))
            if isinstance(output, list):
                parts = []
                for part in output:
                    if isinstance(part, str):
                        parts.append(part)
                    elif isinstance(part, dict) and isinstance(part.get("text"), str):
                        parts.append(part["text"])
                    else:
                        return None, payload.get("call_id") or payload.get("id"), False
                return ("".join(parts), payload.get("call_id") or payload.get("id"),
                        _has_truncation_metadata(payload))
    if event.get("type") == "delivered_text":
        return event.get("text"), event.get("call_id"), _has_truncation_metadata(event)
    return None, None, False


def _token_count(text, token_counter):
    if token_counter is not None:
        value = token_counter(text)
        if not isinstance(value, int) or isinstance(value, bool) or value < 0:
            raise ValueError("invalid token count")
        return value
    try:
        import tiktoken
        return len(tiktoken.get_encoding("o200k_base").encode(text))
    except (ImportError, KeyError, OSError):
        return None


def _byte_offset(text, character_offset):
    return len(text[:character_offset].encode("utf-8"))


def _unavailable_read(read_ordinal, call_id, reason):
    return {
        "path": None,
        "start": None,
        "end": None,
        "content_sha256": None,
        "bytes": None,
        "estimated_tokens": None,
        "role": None,
        "read_ordinal": read_ordinal,
        "classification": "unavailable",
        "call_id": call_id,
        "reason": reason,
    }


def _bucket_row(text, role, read_ordinal, call_id, token_counter):
    return {
        "path": None,
        "start": None,
        "end": None,
        "content_sha256": _sha256(text),
        "bytes": len(text.encode("utf-8")),
        "estimated_tokens": _token_count(text, token_counter),
        "role": role,
        "read_ordinal": read_ordinal,
        "classification": role,
        "call_id": call_id,
        "reason": None,
    }


def _source_row(source, matched_text, start_character, read_ordinal, call_id,
                token_counter):
    base_offset = source.get("base_offset", 0)
    start = base_offset + _byte_offset(source["content"], start_character)
    end = base_offset + _byte_offset(source["content"], start_character + len(matched_text))
    return {
        "path": source["path"],
        "start": start,
        "end": end,
        "content_sha256": _sha256(matched_text),
        "bytes": len(matched_text.encode("utf-8")),
        "estimated_tokens": _token_count(matched_text, token_counter),
        "role": source["role"],
        "read_ordinal": read_ordinal,
        "classification": source["role"],
        "call_id": call_id,
        "reason": None,
    }


def _strip_verified_line_prefixes(delivered):
    body = []
    wrappers = []
    for line in delivered.splitlines(keepends=True):
        match = re.match(r"^(\s*\d+[\t:])", line)
        if match is None:
            return None, None
        wrappers.append(match.group(1))
        body.append(line[match.end():])
    return "".join(body), "".join(wrappers)


def attribute_reads(events, source_manifest, token_counter=None):
    """Attribute exact delivered UTF-8 text ranges to a unique source."""
    sources = []
    for source in source_manifest:
        content = source.get("content")
        path = source.get("path")
        role = source.get("role")
        if not all(isinstance(value, str) for value in (content, path, role)):
            raise ValueError("invalid source manifest")
        declared_sha = source.get("content_sha256")
        actual_sha = _sha256(content)
        if declared_sha is not None and declared_sha != actual_sha:
            raise ValueError("source hash mismatch: " + path)
        base_offset = source.get("base_offset", 0)
        if not isinstance(base_offset, int) or isinstance(base_offset, bool) or base_offset < 0:
            raise ValueError("invalid source base offset: " + path)
        sources.append({**source, "content_sha256": actual_sha})

    rows = []
    ordinal = 0
    for event in events:
        delivered, call_id, truncated = _output_text(event)
        if delivered is None or delivered == "":
            continue
        ordinal += 1
        lowered = delivered.lower()
        if truncated or any(marker in lowered for marker in TRUNCATION_MARKERS):
            rows.append(_unavailable_read(ordinal, call_id, "truncated_output"))
            continue

        if event.get("attribution_scope") == "global_only":
            rows.append(_bucket_row(delivered, "global_prefix", ordinal, call_id,
                                    token_counter))
            continue

        hint = event.get("source_hint") if isinstance(event, dict) else None
        wrapper_from_transform = ""
        match_text = delivered
        if isinstance(hint, dict) and hint.get("line_prefix") is True:
            match_text, wrapper_from_transform = _strip_verified_line_prefixes(delivered)
            if match_text is None:
                rows.append(_unavailable_read(ordinal, call_id, "line_prefix_not_recoverable"))
                continue

        matches = []
        for source in sources:
            start_character = source["content"].find(match_text)
            while start_character >= 0:
                matches.append((source, start_character, match_text, "exact"))
                start_character = source["content"].find(match_text, start_character + 1)

        if len(matches) != 1:
            if isinstance(hint, dict) and isinstance(hint.get("path"), str):
                hinted = [match for match in matches if match[0]["path"] == hint["path"]]
                start_hint = hint.get("start")
                end_hint = hint.get("end")
                if isinstance(start_hint, int):
                    hinted = [match for match in hinted
                              if _byte_offset(match[0]["content"], match[1]) == start_hint]
                if isinstance(end_hint, int):
                    hinted = [match for match in hinted
                              if _byte_offset(match[0]["content"], match[1] + len(match[2])) == end_hint]
                matches = hinted

        if len(matches) == 1:
            if wrapper_from_transform:
                rows.append(_bucket_row(wrapper_from_transform, "wrapper", ordinal, call_id,
                                        token_counter))
            source, start_character, matched_text, _ = matches[0]
            rows.append(_source_row(source, matched_text, start_character, ordinal, call_id,
                                    token_counter))
            continue

        if matches:
            rows.append(_unavailable_read(ordinal, call_id, "source_match_ambiguous"))
            continue

        embedded = []
        for source in sources:
            if isinstance(hint, dict) and isinstance(hint.get("path"), str):
                if source["path"] != hint["path"]:
                    continue
            position = delivered.find(source["content"])
            while position >= 0 and source["content"]:
                embedded.append((position, position + len(source["content"]), source))
                position = delivered.find(source["content"], position + 1)
        embedded.sort(key=lambda value: (value[0], -(value[1] - value[0]), value[2]["path"]))
        overlap = any(current[0] < previous[1]
                      for previous, current in zip(embedded, embedded[1:]))
        if not embedded and event.get("attribution_scope") == "non_instruction":
            rows.append(_bucket_row(delivered, "non_instruction", ordinal, call_id,
                                    token_counter))
            continue
        if not embedded or overlap:
            rows.append(_unavailable_read(
                ordinal, call_id,
                "source_match_missing" if not embedded else "source_match_overlapping",
            ))
            continue
        cursor = 0
        bucket_role = event.get("bucket_role", "wrapper")
        for start_in_delivery, end_in_delivery, source in embedded:
            if cursor < start_in_delivery:
                rows.append(_bucket_row(delivered[cursor:start_in_delivery], bucket_role,
                                        ordinal, call_id, token_counter))
            rows.append(_source_row(source, source["content"], 0, ordinal, call_id,
                                    token_counter))
            cursor = end_in_delivery
        if cursor < len(delivered):
            rows.append(_bucket_row(delivered[cursor:], bucket_role, ordinal, call_id,
                                    token_counter))
    return rows


def _usage_total(run):
    usage = run.get("usage") if isinstance(run, dict) else None
    total = usage.get("total") if isinstance(usage, dict) else None
    return total if isinstance(total, dict) else _unavailable("usage_missing", "merge")


def merge_children(parent, children):
    """Merge each unique descendant session exactly once."""
    parent_id = parent.get("thread_id")
    unique = {}
    for child in children:
        child_id = child.get("thread_id") if isinstance(child, dict) else None
        if not isinstance(child_id, str) or not child_id or child_id == parent_id:
            continue
        prior = unique.get(child_id)
        if prior is not None and prior != child:
            raise ValueError("conflicting child session: " + child_id)
        unique[child_id] = child

    parent_total = _usage_total(parent)
    totals = [parent_total] + [_usage_total(child) for child in unique.values()]
    if any(total.get("status") != "available" for total in totals):
        combined = _unavailable("parent_or_child_usage_unavailable", "merged_sessions")
    else:
        combined = {"status": "available", "source": "merged_sessions"}
        for field in USAGE_FIELDS:
            combined[field] = sum(total[field] for total in totals)
        for optional in ("cache_write_input_tokens", "total_tokens"):
            values = [total.get(optional) for total in totals]
            combined[optional] = sum(values) if all(isinstance(value, int) for value in values) else None

    elapsed_values = [child.get("elapsed_seconds") for child in unique.values()]
    child_elapsed = (sum(elapsed_values)
                     if elapsed_values and all(
                         isinstance(value, (int, float)) and not isinstance(value, bool)
                         and value >= 0 for value in elapsed_values)
                     else None)
    return {
        **parent,
        "child_calls": len(unique),
        "child_thread_ids": sorted(unique),
        "combined_total_usage": combined,
        "child_elapsed_seconds": child_elapsed,
    }


def count_tool_calls(events):
    physical = {}
    physical_by_name = {}
    physical_ids = set()
    implicit_wrapper_ids = set()
    wrappers = []
    for event in events:
        if not isinstance(event, dict):
            continue
        if event.get("type") == "response_item" and isinstance(event.get("payload"), dict):
            payload = event["payload"]
            call_type = payload.get("type")
            if call_type in {"function_call", "custom_tool_call"}:
                call_id = payload.get("call_id") or payload.get("id")
                identity = call_id or json.dumps(payload, sort_keys=True, ensure_ascii=False)
                if identity not in physical_ids:
                    physical_ids.add(identity)
                    physical[call_type] = physical.get(call_type, 0) + 1
                    name = payload.get("name")
                    if isinstance(name, str):
                        physical_by_name[name] = physical_by_name.get(name, 0) + 1
                        if name in {"functions.exec", "multi_tool_use.parallel"}:
                            implicit_wrapper_ids.add(identity)
        if event.get("type") == "logical_tool_call" and event.get("wrapper") is True:
            wrappers.append(event)
    wrapped_children = {
        child for wrapper in wrappers for child in wrapper.get("children", [])
        if isinstance(child, str)
    }
    wrapper_only = sum(1 for wrapper in wrappers if not wrapper.get("children"))
    explicit_wrapper_ids = {
        wrapper.get("call_id") for wrapper in wrappers
        if isinstance(wrapper.get("call_id"), str)
    }
    unresolved_wrappers = implicit_wrapper_ids - explicit_wrapper_ids
    logical_total = (None if unresolved_wrappers else
                     len(physical_ids - wrapped_children - implicit_wrapper_ids)
                     + len(wrapped_children) + wrapper_only)
    return {
        "physical_by_type": dict(sorted(physical.items())),
        "physical_by_name": dict(sorted(physical_by_name.items())),
        "physical_total": len(physical_ids),
        "logical_total": logical_total,
        "logical_status": "unavailable" if unresolved_wrappers else "available",
        "wrapper_calls": len(explicit_wrapper_ids | implicit_wrapper_ids),
    }


def candidate_is_eligible(runs):
    if not runs or any(run.get("valid_run") is not True for run in runs):
        return False
    for run in runs:
        if run.get("hard_gate_pass") is not True:
            return False
        if run.get("routing_pass") is not True:
            return False
        if run.get("false_block") is not False:
            return False
        if run.get("quality_pass") is not True:
            return False
    return True


def run_self_test():
    assert structural_cost(100, [20, 20]) == 140
    try:
        structural_cost(-1, [])
    except ValueError as exc:
        assert str(exc) == "negative metric"
    else:
        raise AssertionError("negative start metric was accepted")

    samples = {str(index): [10, 20, 30] for index in range(9)}
    assert selection_score(samples, list(samples)) == 20
    try:
        selection_score(samples, list(samples)[:-1])
    except ValueError as exc:
        assert str(exc) == "incomplete sample"
    else:
        raise AssertionError("incomplete selection sample was accepted")

    stdout_events = [{
        "type": "turn.completed",
        "usage": {"input_tokens": 70, "cached_input_tokens": 0,
                  "output_tokens": 9, "reasoning_output_tokens": 2},
    }]
    stdout_usage = extract_usage(stdout_events)
    assert stdout_usage["first"]["status"] == "unavailable"
    assert stdout_usage["total"]["input_tokens"] == 70
    assert stdout_usage["total"]["cached_input_tokens"] == 0

    session_events = [
        {"type": "event_msg", "payload": {"type": "token_count", "info": {
            "last_token_usage": {"input_tokens": 11, "cached_input_tokens": 3,
                                 "output_tokens": 2, "reasoning_output_tokens": 1},
            "total_token_usage": {"input_tokens": 11, "cached_input_tokens": 3,
                                  "output_tokens": 2, "reasoning_output_tokens": 1},
        }}},
        {"type": "event_msg", "payload": {"type": "token_count", "info": {
            "last_token_usage": {"input_tokens": 7, "cached_input_tokens": 0,
                                 "output_tokens": 4, "reasoning_output_tokens": 0},
            "total_token_usage": {"input_tokens": 18, "cached_input_tokens": 3,
                                  "output_tokens": 6, "reasoning_output_tokens": 1},
        }}},
    ]
    session_usage = extract_usage(session_events)
    assert session_usage["first"]["input_tokens"] == 11
    assert session_usage["total"]["input_tokens"] == 18

    source_text = "alpha\nbeta\ngamma\n"
    manifest = [{"path": "AGENTS.md", "content": source_text, "role": "repository_instruction"}]
    read_events = [
        {"type": "item.completed", "item": {"id": "read-1", "type": "command_execution",
                                               "aggregated_output": source_text}},
        {"type": "item.completed", "item": {"id": "read-2", "type": "command_execution",
                                               "aggregated_output": "beta\n"}},
        {"type": "response_item", "payload": {"id": "read-3", "type": "function_call_output",
                                                 "output": source_text}},
        {"type": "response_item", "payload": {"id": "read-4", "type": "function_call_output",
                                                 "output": json.dumps({
                                                     "output": "gamma\n", "exit_code": 0,
                                                 })}},
    ]
    rows = attribute_reads(read_events, manifest, token_counter=lambda text: len(text.split()))
    available = [row for row in rows if row["classification"] == "repository_instruction"]
    assert [(row["start"], row["end"], row["read_ordinal"]) for row in available] == [
        (0, len(source_text.encode("utf-8")), 1),
        (len("alpha\n".encode("utf-8")), len("alpha\nbeta\n".encode("utf-8")), 2),
        (0, len(source_text.encode("utf-8")), 3),
        (len("alpha\nbeta\n".encode("utf-8")), len(source_text.encode("utf-8")), 4),
    ]
    assert available[0]["content_sha256"] == _sha256(source_text)

    ambiguous_manifest = manifest + [
        {"path": "copy.md", "content": source_text, "role": "repository_instruction"}
    ]
    assert attribute_reads(read_events[:1], ambiguous_manifest)[0]["classification"] == "unavailable"
    truncated = [{"type": "item.completed", "item": {
        "id": "truncated", "type": "command_execution",
        "aggregated_output": "alpha\n... output truncated ...\n",
    }}]
    assert attribute_reads(truncated, manifest)[0]["classification"] == "unavailable"
    observed_truncation = [{"type": "response_item", "payload": {
        "type": "function_call_output", "call_id": "observed",
        "output": json.dumps({
            "output": "Warning: truncated output (original token count: 99)\n" + source_text,
            "original_token_count": 99,
        }),
    }}]
    assert attribute_reads(observed_truncation, manifest) == [
        _unavailable_read(1, "observed", "truncated_output")]
    metadata_truncation = [{"type": "item.completed", "item": {
        "id": "metadata", "type": "command_execution",
        "aggregated_output": source_text, "truncated": True,
    }}]
    assert attribute_reads(metadata_truncation, manifest)[0]["reason"] == "truncated_output"

    wrapped = [{"type": "delivered_text", "call_id": "wrapped",
                "text": "tool header\n" + source_text + "tool footer\n",
                "bucket_role": "wrapper"}]
    wrapped_rows = attribute_reads(
        wrapped, manifest, token_counter=lambda text: len(text.split()))
    assert [row["classification"] for row in wrapped_rows] == [
        "wrapper", "repository_instruction", "wrapper"]
    assert sum(row["bytes"] for row in wrapped_rows) == len(
        wrapped[0]["text"].encode("utf-8"))

    numbered = [{"type": "delivered_text", "call_id": "numbered",
                 "text": "     1\talpha\n     2\tbeta\n",
                 "source_hint": {"path": "AGENTS.md", "line_prefix": True}}]
    numbered_rows = attribute_reads(
        numbered, manifest, token_counter=lambda text: len(text.split()))
    assert any(row["classification"] == "repository_instruction"
               and row["start"] == 0
               and row["end"] == len("alpha\nbeta\n".encode("utf-8"))
               for row in numbered_rows)
    assert any(row["classification"] == "wrapper" for row in numbered_rows)

    unrelated = [{"type": "delivered_text", "call_id": "status",
                  "text": "branch is clean\n", "attribution_scope": "non_instruction"}]
    assert attribute_reads(unrelated, manifest)[0]["classification"] == "non_instruction"
    global_only = [{"type": "delivered_text", "call_id": "base",
                    "text": "global base only", "bucket_role": "global_prefix",
                    "attribution_scope": "global_only"}]
    assert attribute_reads(global_only, manifest)[0]["classification"] == "global_prefix"
    unresolved_start = [{"type": "delivered_text", "call_id": "developer",
                         "text": "partial alpha", "bucket_role": "global_prefix"}]
    assert attribute_reads(unresolved_start, manifest)[0]["classification"] == "unavailable"

    fragment_manifest = [{
        "path": "SKILL.md", "content": "catalog description",
        "content_sha256": _sha256("catalog description"),
        "role": "skill_catalog_metadata", "base_offset": 7,
    }]
    fragment_row = attribute_reads(
        [{"type": "delivered_text", "text": "catalog description"}],
        fragment_manifest, token_counter=lambda text: 2)[0]
    assert fragment_row["start"] == 7
    assert fragment_row["end"] == 7 + len("catalog description".encode("utf-8"))

    parent = {"thread_id": "parent", "usage": session_usage,
              "children": ["child-a"], "elapsed_seconds": 4}
    child = {"thread_id": "child-a", "parent_thread_id": "parent",
             "usage": {"first": {"status": "available", "input_tokens": 5,
                                    "cached_input_tokens": 0, "output_tokens": 1,
                                    "reasoning_output_tokens": 0},
                       "total": {"status": "available", "input_tokens": 5,
                                  "cached_input_tokens": 0, "output_tokens": 1,
                                  "reasoning_output_tokens": 0}},
             "elapsed_seconds": 2}
    merged = merge_children(parent, [child, dict(child)])
    assert merged["child_calls"] == 1
    assert merged["combined_total_usage"]["input_tokens"] == 23
    assert merged["child_elapsed_seconds"] == 2
    child_without_elapsed = dict(child)
    child_without_elapsed["elapsed_seconds"] = None
    assert merge_children(parent, [child_without_elapsed])["child_elapsed_seconds"] is None
    assert merge_children(parent, [])["child_elapsed_seconds"] is None

    calls = count_tool_calls([
        {"type": "response_item", "payload": {"type": "function_call", "call_id": "f1",
                                                 "name": "exec_command"}},
        {"type": "response_item", "payload": {"type": "custom_tool_call", "call_id": "c1",
                                                 "name": "apply_patch"}},
        {"type": "logical_tool_call", "call_id": "wrapper", "wrapper": True,
         "children": ["f1", "c1"]},
    ])
    assert calls["physical_by_type"] == {"custom_tool_call": 1, "function_call": 1}
    assert calls["logical_total"] == 2
    assert calls["wrapper_calls"] == 1
    unresolved_wrapper = count_tool_calls([
        {"type": "response_item", "payload": {"type": "custom_tool_call",
                                                 "call_id": "wrapper-only",
                                                 "name": "functions.exec"}},
    ])
    assert unresolved_wrapper["logical_status"] == "unavailable"
    assert unresolved_wrapper["logical_total"] is None

    assert candidate_is_eligible([
        {"valid_run": True, "hard_gate_pass": True, "routing_pass": True,
         "false_block": False, "quality_pass": True},
    ]) is True
    assert candidate_is_eligible([
        {"valid_run": True, "hard_gate_pass": False, "routing_pass": True,
         "false_block": False, "quality_pass": True},
    ]) is False
    assert candidate_is_eligible([
        {"valid_run": True, "hard_gate_pass": True, "routing_pass": True,
         "false_block": False, "quality_pass": True},
        {"valid_run": False, "hard_gate_pass": False, "routing_pass": True,
         "false_block": False, "quality_pass": True},
    ]) is False
    assert candidate_is_eligible([
        {"valid_run": True, "hard_gate_pass": None, "routing_pass": True,
         "false_block": False, "quality_pass": True},
    ]) is False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        run_self_test()
        print("codex-instruction-metrics self-test: PASS")


if __name__ == "__main__":
    main()
