#!/usr/bin/env python3
"""Local deterministic, parse-only Java map; no external dependencies."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

SCHEMA = 2
SOURCE_ROOTS = ("src/main/java", "src/test/java", "src/integrationTest/java")
HERE = Path(__file__).resolve().parent


class MapError(Exception):
    """Safe fixed diagnostic without input/source content."""


def digest(data):
    return hashlib.sha256(data).hexdigest()


def execute(args, **kwargs):
    result = subprocess.run(args, capture_output=True, timeout=120, **kwargs)
    if result.returncode:
        raise MapError("subprocess failed; diagnostics withheld")
    return result.stdout


def git(root, *args):
    return execute(["git", "-C", str(root), *args])


def no_symlinks(path):
    for entry in (path, *path.parents):
        if entry.is_symlink():
            raise MapError("symlink paths are not allowed")


def snapshot(root, java):
    names = git(root, "ls-files", "-z", "--cached", "--others", "--exclude-standard").decode().split("\0")
    sources = {}
    for source_root in SOURCE_ROOTS:
        directory = root / source_root
        no_symlinks(directory)
        # Reject even ignored links in a source tree, rather than follow ambiguous boundaries.
        if directory.exists():
            for parent, dirs, files in os.walk(directory, followlinks=False):
                for name in dirs + files:
                    if (Path(parent) / name).is_symlink():
                        raise MapError("source symlinks are not allowed")
    for name in sorted(set(names)):
        if name.endswith(".java") and any(name.startswith(prefix + "/") for prefix in SOURCE_ROOTS):
            path = root / name
            no_symlinks(path)
            if path.exists():
                sources[name] = digest(path.read_bytes())
    result = subprocess.run([str(java), "-version"], capture_output=True, timeout=30)
    version = result.stderr.decode("utf-8", "replace").strip()
    if result.returncode or not version.splitlines() or 'version "21' not in version.splitlines()[0]:
        raise MapError("JAVA_HOME must select JDK 21")
    return {"schema": SCHEMA, "tool": digest((HERE / "run.py").read_bytes() + (HERE / "RepoMap.java").read_bytes()),
            "runtime": version + "\nPython " + sys.version.split()[0], "head": git(root, "rev-parse", "HEAD").decode().strip(), "sources": sources}


def output_path(root, output):
    path = Path(output) if output else root / "build/repo-map/index.json"
    if not path.is_absolute():
        path = root / path
    no_symlinks(path)
    path = path.resolve()
    relative = path.relative_to(root).as_posix()
    # Restrict publication to the existing ignored artifact directory, even for explicit output.
    if not relative.startswith("build/repo-map/") or path.suffix != ".json":
        raise MapError("output must be a JSON file under ignored build/repo-map")
    tracked = git(root, "ls-files", "-z", "--", relative)
    ignored = subprocess.run(["git", "-C", str(root), "check-ignore", "-q", "--", relative], capture_output=True)
    if tracked or ignored.returncode:
        raise MapError("output must be ignored and untracked")
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("generate", "check", "query"))
    parser.add_argument("--root", default=".")
    parser.add_argument("--output")
    parser.add_argument("--symbol")
    parser.add_argument("--format", choices=("rows", "classes"), default="rows")
    parser.add_argument("--limit", type=int, default=20)
    args = parser.parse_args()
    root = Path(os.path.abspath(args.root))
    no_symlinks(root)
    root = root.resolve()
    if Path(git(root, "rev-parse", "--show-toplevel").decode().strip()).resolve() != root:
        raise MapError("root must be the repository root")
    output = output_path(root, args.output)
    java = Path(os.environ["JAVA_HOME"]) / "bin/java" if "JAVA_HOME" in os.environ else Path("java")
    before = snapshot(root, java)
    if args.action == "generate":
        raw = execute([str(java), str(HERE / "RepoMap.java"), str(root)],
                      input="\0".join(before["sources"]).encode())
        rows = json.loads(raw)
        for row in rows:
            source_root = next(prefix for prefix in SOURCE_ROOTS if row["file"].startswith(prefix + "/"))
            row["source_root"] = source_root
            row["source_path"] = row["file"][len(source_root) + 1:]
        rows.sort(key=lambda r: (r["file"], r["line"], r["kind"], r["type"], r["signature"]))
        if before != snapshot(root, java):
            raise MapError("inputs changed during generation")
        data = dict(before, rows=rows)
        output.parent.mkdir(parents=True, exist_ok=True)
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=output.parent, delete=False) as handle:
                temporary = Path(handle.name)
                json.dump(data, handle, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
                handle.write("\n")
                handle.flush()
                os.fsync(handle.fileno())
            no_symlinks(output)
            os.replace(temporary, output)
        finally:
            if temporary and temporary.exists():
                temporary.unlink()
        print(json.dumps({"status": "generated", "files": len(before["sources"]), "rows": len(rows)}))
        return
    data = json.loads(output.read_text(encoding="utf-8"))
    if {key: data.get(key) for key in before} != before:
        raise MapError("stale index; regenerate before use")
    if args.action == "check":
        print('{"status":"fresh"}')
        return
    if not args.symbol or not 1 <= args.limit <= 200:
        raise MapError("query requires nonempty --symbol and --limit between 1 and 200")
    term = args.symbol.casefold()
    if args.format == "classes":
        matches = []
        for row in data["rows"]:
            if "dependency_evidence" not in row:
                continue
            candidates = [row["type"], row["file"], *row["symbols"], *row["dependencies"]]
            if any(term in value.casefold() for value in candidates):
                matches.append({"file": row["file"], "path": row["source_path"],
                                "source_root": row["source_root"], "type": row["type"],
                                "kind": row["kind"], "line": row["line"], "symbols": row["symbols"],
                                "dependencies": row["dependencies"], "dependency_evidence": row["dependency_evidence"]})
        matches.sort(key=lambda group: (group["file"], group["type"], group["line"]))
    else:
        matches = [row for row in data["rows"] if any(term in row[key].casefold() for key in ("symbol", "type", "signature", "file"))]
    print(json.dumps({"total": len(matches), "truncated": len(matches) > args.limit, args.format: matches[:args.limit]}, ensure_ascii=False, separators=(",", ":")))


if __name__ == "__main__":
    try:
        main()
    except (MapError, ValueError, OSError, subprocess.SubprocessError, KeyError, TypeError) as error:
        # Do not include exception strings: compiler/OS paths or source text may be sensitive.
        message = str(error) if isinstance(error, MapError) else "operation failed; diagnostics withheld"
        print("repo-map: " + message, file=sys.stderr)
        sys.exit(2)
