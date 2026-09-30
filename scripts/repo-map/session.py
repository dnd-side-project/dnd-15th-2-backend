#!/usr/bin/env python3
"""Local, caller-selected A/B preparation. No allocator or implicit model calls."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys

VERSION = "codex-cli 0.158.0"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode()


def file_hash(path):
    return digest(path.read_bytes())


def git(root, *args):
    result = subprocess.run(["git", "-C", str(root), *args], capture_output=True)
    if result.returncode:
        raise ValueError("Git workspace validation failed")
    return result.stdout


def snapshot(root, revision, task_id):
    if not root.is_dir() or git(root, "rev-parse", "--show-toplevel").decode().strip() != str(root):
        raise ValueError("workspace must be a Git root")
    if not re.fullmatch(r"[0-9a-f]{40}", revision) or git(root, "rev-parse", "HEAD").decode().strip() != revision:
        raise ValueError("expected revision must match full HEAD")
    if git(root, "status", "--porcelain", "--untracked-files=all"):
        raise ValueError("target workspace must be clean")
    paths = sorted(p.decode() for p in git(root, "ls-files", "-z").split(b"\0") if p)
    if not {"AGENTS.md", "TASK.md"}.issubset(paths):
        raise ValueError("tracked AGENTS.md and TASK.md are required")
    hashes = {}
    for name in paths:
        path = root / name
        if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(root):
            raise ValueError("tracked files must be regular confined files")
        hashes[name] = file_hash(path)
    return {"revision": revision, "task_id": task_id, "tracked_files_sha256": digest(canonical(hashes)),
            "task_context_sha256": hashes["TASK.md"], "instructions_sha256": hashes["AGENTS.md"]}


def config_entrypoints():
    home = Path(os.environ.get("CODEX_HOME", str(Path.home() / ".codex"))).expanduser()
    return {name: file_hash(home / name) if (home / name).is_file() else "absent"
            for name in ("config.toml", "AGENTS.md")}


def overlay_for(run):
    command = shlex.join([sys.executable, run["map_tool"], "query", "--root", run["workspace"],
                          "--output", run["index"], "--symbol", "SYMBOL", "--limit", "20"])
    return ("Additional search procedure for condition B. Preserve all mandatory repository policies.\n"
            "Use rg/files/symbol search to narrow candidates before reading necessary sources and authoritative documents.\n"
            "Use an existing LSP when useful; do not install one. Widen searches as needed; no reading quota applies.\n"
            "The map is syntax-only, not a resolved dependency or call graph. Never inject the full map.\n"
            "Run this bounded query, replacing SYMBOL with the search term:\n" + command + "\n"
            "For grouped class symbols and declared field/constructor dependency types, add --format classes.\n"
            "These declared references do not resolve imports, runtime injection or method calls.\n")


def configuration(run):
    root = Path(run["workspace"])
    common = snapshot(root, run["revision"], run["task_id"])
    product = Path(run["product"])
    if not product.is_file() or not os.access(product, os.X_OK):
        raise ValueError("product must be an executable file")
    product_hash = file_hash(product)
    version = "unverified"
    if run["verify_version"]:
        result = subprocess.run([str(product), "--version"], capture_output=True, text=True, timeout=15)
        if result.returncode or result.stdout.strip() != VERSION:
            raise ValueError("product version must be codex-cli 0.158.0")
        version = VERSION
    config = {"schema": 1, "condition": run["condition"], "common": common,
              "product_sha256": product_hash, "expected_product_version": VERSION,
              "verified_product_version": version, "model": run["model"], "effort": run["effort"],
              "effective_global_settings": "unknown", "adapter_sha256": file_hash(Path(__file__)),
              "sandbox": "read-only", "json_output": True, "update_check": False,
              "implementation_authorized": False, "config_entrypoints": config_entrypoints()}
    if run["condition"] == "B":
        tool, index = Path(run["map_tool"]), Path(run["index"])
        if not tool.is_file() or not index.is_file():
            raise ValueError("B requires an existing map tool and index")
        parser = tool.with_name("RepoMap.java")
        before = {"index_sha256": file_hash(index), "map_tool_sha256": file_hash(tool),
                  "parser_sha256": file_hash(parser)}
        result = subprocess.run([sys.executable, str(tool), "check", "--root", str(root),
                                 "--output", str(index)], capture_output=True, timeout=120)
        if result.returncode:
            raise ValueError("B map freshness check failed")
        after = {"index_sha256": file_hash(index), "map_tool_sha256": file_hash(tool),
                 "parser_sha256": file_hash(parser)}
        if before != after:
            raise ValueError("map changed during check")
        config["map"] = before
        # Overlay paths are per-run; only the common instruction template is stable.
        neutral = dict(run, workspace="<workspace>", map_tool="<map-tool>", index="<index>")
        config["overlay_template_sha256"] = digest(overlay_for(neutral).encode())
    if common != snapshot(root, run["revision"], run["task_id"]) or product_hash != file_hash(product):
        raise ValueError("inputs changed during preparation")
    return config


def prepare(args):
    root, output = Path(args.workspace).resolve(), Path(args.output).absolute()
    if output.exists() or output.is_symlink():
        raise ValueError("output already exists")
    if output.resolve().is_relative_to(root):
        raise ValueError("run output must be outside the target workspace")
    if args.condition == "A" and (args.index or args.map_tool):
        raise ValueError("A must not receive B map inputs")
    if args.condition == "B" and not (args.index and args.map_tool):
        raise ValueError("B requires --index and --map-tool")
    run = {"workspace": str(root), "product": str(Path(args.product).resolve()),
           "revision": args.expected_revision, "task_id": args.task_id, "condition": args.condition,
           "model": args.model, "effort": args.effort, "verify_version": args.verify_version,
           "observed_branch": git(root, "branch", "--show-current").decode().strip() or "detached"}
    if not args.model.strip() or not args.task_id.strip():
        raise ValueError("model and task ID must not be empty")
    if args.condition == "B":
        run.update(index=str(Path(args.index).resolve()), map_tool=str(Path(args.map_tool).resolve()))
    config = configuration(run)
    manifest = {"config": config, "config_digest": digest(canonical(config)), "run": run,
                "created_at": datetime.datetime.now(datetime.timezone.utc).isoformat()}
    output.mkdir(mode=0o700)  # Exclusive: a competing prepare cannot replace this run.
    if args.condition == "B":
        (output / "overlay.txt").write_text(overlay_for(run))
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"config_digest": manifest["config_digest"], "prepared": True}))


def launch(args):
    output = Path(args.output).resolve()
    manifest = json.loads((output / "manifest.json").read_text())
    config, run = manifest["config"], manifest["run"]
    if digest(canonical(config)) != args.expected_config or manifest["config_digest"] != args.expected_config:
        raise ValueError("configuration digest mismatch")
    if not run["verify_version"]:
        raise ValueError("launch requires preparation with --verify-version")
    if configuration(run) != config:
        raise ValueError("prepared identity changed")
    prompt = Path(args.task_file).read_text()
    if not prompt.strip():
        raise ValueError("task must not be empty")
    if run["condition"] == "B":
        overlay = overlay_for(run)
        if (output / "overlay.txt").read_text() != overlay:
            raise ValueError("B overlay changed")
        prompt = overlay + "\n" + prompt
    elif (output / "overlay.txt").exists():
        raise ValueError("A must not contain B overlay")
    prompt = "Read-only exploration only. Do not edit files or implement changes. Follow all repository policies.\n\n" + prompt
    command = [run["product"], "exec", "--json", "--sandbox", "read-only", "--model", run["model"],
               "-c", "model_reasoning_effort=" + json.dumps(run["effort"]),
               "-c", "check_for_update_on_startup=false", "-"]
    with (output / "launch-attempted").open("x") as marker:
        marker.write("fresh exec requested; no automatic retry\n")
    return subprocess.run(command, cwd=run["workspace"], input=prompt, text=True).returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    prep = sub.add_parser("prepare")
    prep.add_argument("--condition", choices=["A", "B"], required=True)
    for key in ("workspace", "expected-revision", "output", "product", "model", "task-id"):
        prep.add_argument("--" + key, required=True)
    prep.add_argument("--effort", choices=["minimal", "low", "medium", "high", "xhigh"], required=True)
    prep.add_argument("--verify-version", action="store_true")
    prep.add_argument("--index")
    prep.add_argument("--map-tool")
    start = sub.add_parser("launch")
    for key in ("output", "expected-config", "task-file"):
        start.add_argument("--" + key, required=True)
    args = parser.parse_args()
    try:
        return prepare(args) if args.command == "prepare" else launch(args)
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as exc:
        print("session: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
