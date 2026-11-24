#!/usr/bin/env python3
"""TEST-PLAN-GH-386-TEST-GIT-ENV UNIT-005/006."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from typing import TypedDict
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("run_hook", Path(__file__).with_name("run-hook.py"))
HOOK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HOOK)
LOCATION_KEYS = ("GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE")
PROBE = """\
import json
import os
import subprocess
import sys
from pathlib import Path

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

record = {
    "script": Path(__file__).name,
    "cwd": str(Path.cwd().resolve()),
    "environment": dict(os.environ),
    "toplevel": git("rev-parse", "--show-toplevel"),
    "head": git("rev-parse", "HEAD"),
    "staged": git("diff", "--cached", "--name-only").splitlines(),
    "command_config": git("config", "fixture.command"),
    "parameter_config": git("config", "fixture.parameter"),
    "global_config": git("config", "fixture.global"),
    "system_config": git("config", "fixture.system"),
}
with open(os.environ["PROBE_OUTPUT"], "a", encoding="utf-8") as output:
    output.write(json.dumps(record) + "\\n")
sys.exit(int(os.environ.get("PROBE_EXIT", "0")))
"""


class ProbeRecord(TypedDict):
    script: str
    cwd: str
    environment: dict[str, str]
    toplevel: str
    head: str
    staged: list[str]
    command_config: str
    parameter_config: str
    global_config: str
    system_config: str


class RunHookTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="qello-hook-")
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name).resolve()
        home = self.base / "home"
        home.mkdir()
        global_config = self.base / "global.config"
        global_config.write_text("[fixture]\n\tglobal = global-value\n", encoding="utf-8")
        system_config = self.base / "system.config"
        system_config.write_text("[fixture]\n\tsystem = system-value\n", encoding="utf-8")
        self.clean_env = {
            key: value for key, value in os.environ.items()
            if not key.startswith("GIT_")
        }
        self.clean_env.update({
            "GIT_MASTER": "1",
            "GIT_PAGER": "cat",
            "HOME": str(home),
            "XDG_CONFIG_HOME": str(home),
            "GIT_CONFIG_GLOBAL": str(global_config),
            "GIT_CONFIG_SYSTEM": str(system_config),
            "GIT_CONFIG_NOSYSTEM": "0",
            "GIT_AUTHOR_NAME": "Fixture",
            "GIT_AUTHOR_EMAIL": "fixture@example.invalid",
            "GIT_COMMITTER_NAME": "Fixture",
            "GIT_COMMITTER_EMAIL": "fixture@example.invalid",
        })
        self.repository = self.base / "repository"
        self.sentinel = self.base / "sentinel"
        for repository in (self.repository, self.sentinel):
            repository.mkdir()
            self.git(repository, "init", "--initial-branch=main")
            (repository / "tracked.txt").write_text(repository.name + "\n", encoding="utf-8")
            self.git(repository, "add", "tracked.txt")
            self.git(repository, "-c", "core.hooksPath=" + str(self.base / "no-hooks"),
                     "-c", "maintenance.auto=false", "-c", "gc.auto=0",
                     "-c", "commit.gpgsign=false", "commit", "-m", "fixture")
        self.root = self.base / "linked"
        self.git(self.repository, "worktree", "add", "-b", "fixture-linked", str(self.root))
        self.head = self.git(self.root, "rev-parse", "HEAD")
        self.assertNotEqual(self.head, self.git(self.sentinel, "rev-parse", "HEAD"))
        scripts = self.root / "scripts"
        scripts.mkdir()
        for name in ("harness.py", "validate-conventions.py", "preflight.py",
                     "validate-workflows.py", "format-commit-msg.py"):
            (scripts / name).write_text(PROBE, encoding="utf-8")
        self.output = self.base / "probe.jsonl"
        self.environment = self.clean_env | {
            "GIT_CONFIG_COUNT": "1",
            "GIT_CONFIG_KEY_0": "fixture.command",
            "GIT_CONFIG_VALUE_0": "command-value",
            "GIT_CONFIG_PARAMETERS": "'fixture.parameter=parameter-value'",
            "GIT_OPTIONAL_LOCKS": "0",
            "GIT_TERMINAL_PROMPT": "0",
            "GIT_SSH_COMMAND": "fixture-ssh-command",
            "QELLO_HOOK_FIXTURE": "non-git-value",
            "PROBE_OUTPUT": str(self.output),
        }
        self.sentinel_before = self.snapshot()
        self.addCleanup(self.assert_sentinel_unchanged)

    def git(self, cwd: Path, *arguments: str, env: dict[str, str] | None = None) -> str:
        result = subprocess.run(
            ["git", *arguments], cwd=cwd, env=self.clean_env if env is None else env,
            capture_output=True, text=True, check=False, timeout=20,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.strip()

    def snapshot(self) -> dict[str, str]:
        metadata = self.sentinel / ".git"
        return {
            "bare": self.git(self.sentinel, "rev-parse", "--is-bare-repository"),
            "head": self.git(self.sentinel, "rev-parse", "HEAD"),
            "refs": self.git(self.sentinel, "show-ref"),
            **{
                str(path.relative_to(metadata)): hashlib.sha256(path.read_bytes()).hexdigest()
                for path in sorted(metadata.rglob("*")) if path.is_file()
            },
        }

    def assert_sentinel_unchanged(self) -> None:
        self.assertEqual(self.sentinel_before["bare"], "false")
        after = self.snapshot()
        self.assertEqual(after, self.sentinel_before)
        digest = hashlib.sha256(json.dumps(after, sort_keys=True).encode()).hexdigest()
        print(f"[sentinel] {self.id()} bare=false config/HEAD/index/refs/objects=unchanged sha256={digest}")

    def records(self) -> list[ProbeRecord]:
        return [json.loads(line) for line in self.output.read_text(encoding="utf-8").splitlines()]

    def assert_probe(self, record: ProbeRecord, expected_env: dict[str, str]) -> None:
        self.assertEqual(record["cwd"], str(self.root))
        self.assertEqual(record["toplevel"], str(self.root))
        self.assertEqual(record["head"], self.head)
        self.assertEqual(record["command_config"], "command-value")
        self.assertEqual(record["parameter_config"], "parameter-value")
        self.assertEqual(record["global_config"], "global-value")
        self.assertEqual(record["system_config"], "system-value")
        for key, value in expected_env.items():
            self.assertEqual(record["environment"].get(key), value, key)

    def test_unit_005_pre_push_rediscovers_linked_worktree_and_preserves_config(self) -> None:
        contamination = {
            "GIT_DIR": str(self.sentinel / ".git"),
            "GIT_WORK_TREE": str(self.sentinel),
            "GIT_COMMON_DIR": str(self.sentinel / ".git"),
            "GIT_INDEX_FILE": str(self.sentinel / ".git" / "index"),
        }
        for path in contamination.values():
            self.assertTrue(Path(path).is_relative_to(self.base))
        inherited = self.environment | contamination
        with patch.object(HOOK, "ROOT", self.root), patch.dict(os.environ, inherited, clear=True):
            HOOK.pre_push()
            self.assertEqual(dict(os.environ), inherited)
        records = self.records()
        self.assertEqual(len(records), 1)
        self.assertEqual(records[0]["script"], "harness.py")
        self.assert_probe(records[0], self.environment)
        for key in LOCATION_KEYS:
            self.assertNotIn(key, records[0]["environment"])
        self.assertEqual(records[0]["staged"], [])

    def test_unit_005_pre_push_propagates_child_failure(self) -> None:
        with patch.object(HOOK, "ROOT", self.root), patch.dict(
            os.environ, self.environment | {"PROBE_EXIT": "23"}, clear=True,
        ):
            with self.assertRaises(SystemExit) as failure:
                HOOK.pre_push()
        self.assertEqual(failure.exception.code, 23)
        self.assert_probe(self.records()[0], self.environment)

    def test_unit_006_other_hooks_preserve_locations_config_and_alternate_index(self) -> None:
        (self.root / "default.txt").write_text("default index\n", encoding="utf-8")
        self.git(self.root, "add", "default.txt")
        alternate_index = self.base / "alternate.index"
        inherited = self.environment | {
            "GIT_DIR": self.git(self.root, "rev-parse", "--absolute-git-dir"),
            "GIT_WORK_TREE": str(self.root),
            "GIT_COMMON_DIR": str(self.repository / ".git"),
            "GIT_INDEX_FILE": str(alternate_index),
        }
        for key in LOCATION_KEYS:
            self.assertTrue(Path(inherited[key]).is_relative_to(self.base))
        self.git(self.root, "read-tree", "HEAD", env=inherited)
        workflow = self.root / ".github" / "workflows" / "alternate.yml"
        workflow.parent.mkdir(parents=True)
        workflow.write_text("name: alternate-index\n", encoding="utf-8")
        self.git(self.root, "add", ".github/workflows/alternate.yml", env=inherited)
        self.assertEqual(self.git(self.root, "diff", "--cached", "--name-only"), "default.txt")
        message = self.base / "message"
        message.write_text("fixture\n", encoding="utf-8")
        hooks = (
            ("pre-commit", lambda: HOOK.pre_commit(),
             ["validate-conventions.py", "preflight.py", "validate-workflows.py"]),
            ("prepare-commit-msg", lambda: HOOK.prepare_commit_msg(str(message), "message"),
             ["format-commit-msg.py"]),
            ("commit-msg", lambda: HOOK.commit_msg(str(message)),
             ["format-commit-msg.py", "validate-conventions.py"]),
        )
        for name, invoke, expected_scripts in hooks:
            with self.subTest(hook=name):
                self.output.unlink(missing_ok=True)
                with patch.object(HOOK, "ROOT", self.root), patch.dict(
                    os.environ, inherited, clear=True,
                ):
                    invoke()
                    self.assertEqual(dict(os.environ), inherited)
                records = self.records()
                self.assertEqual([record["script"] for record in records], expected_scripts)
                for record in records:
                    self.assert_probe(record, inherited)
                    self.assertEqual(record["staged"], [".github/workflows/alternate.yml"])


if __name__ == "__main__":
    unittest.main()
