# Local repository search conditions

B is optional and must be selected explicitly for each prepared session. Merging or installing these local tools does not activate B, modify global instructions, or inject a map into ordinary sessions. This adapter prepares an explicitly supplied `A` or `B` condition. It does not allocate conditions, define a public experiment API, collect usage, or run a model during preparation. Manual A/B selection is a smoke procedure; it is not the final randomized team experiment. Independently assigned tasks need not be paired.

## Common workspace and identities

Use a clean target checkout at an explicit full commit SHA with committed `AGENTS.md` and `TASK.md`. This adapter supports read-only exploration only and always records `implementation_authorized=false`; launch includes an explicit prohibition on edits and implementation. The supplied `--task-id` identifies the exploration. Existing TASK content is hashed unchanged, and the observed branch (including detached HEAD) is recorded; its older task is not reinterpreted as implementation authorization. Any future implementation requires a matching Issue/branch/TASK contract and is unsupported by this adapter. The adapter may run from a separate tooling checkout under development; this does not relax cleanliness of the target. Both conditions must use identical tracked target files, revision, product executable, model and reasoning effort. Their shared `common` identity hashes every tracked file, including common instructions and task context. The stable configuration digest excludes run paths and timestamps; each manifest separately records local paths and creation time. Compare `config.common`, `config.product_sha256`, `config.model`, `config.effort`, `config.verified_product_version` and `config.config_entrypoints` for equality before comparing outcomes; `config.condition` and the overall `config_digest` should differ between A and B.

Product version is pinned to `codex-cli 0.158.0`. Default prepare records the executable hash without executing it. Explicit `--verify-version` runs only `--version`; launch requires this evidence and rechecks it. Changing the product bytes, adapter bytes, target contents, revision, task context, condition configuration or B index/tool invalidates preparation. An expected digest from the caller guards against accidental manifest substitution; it is not a signature or a hostile-tampering security boundary.

The manifest hashes the accessible `config.toml` and `AGENTS.md` entrypoints under `CODEX_HOME` (default `~/.codex`), records absence, and revalidates these hashes at launch without copying contents. It marks effective global settings as `unknown`. Neither hidden provider settings nor user configuration, skills, MCP servers, shell startup files, dynamic environment or account defaults are frozen by this adapter. Do not claim controlled equivalence until the operator has separately checked those conditions. Do not change or bypass baseline policies to make a comparison pass.

## Prepare without a model call

All paths below are operator-supplied local paths. Store generated index and B overlay outside A's target checkout and outside measurement records. Index output must satisfy the map tool's ignored-local-output requirements. Output directories are new, private run directories; existing output is never overwritten. Create their parent directory first; prepare requires it to exist.

```sh
mkdir -p /private-runs
python3 /tooling/scripts/repo-map/session.py prepare \
  --condition A --workspace /targets/common \
  --expected-revision <full-commit-sha> --task-id <task-id> \
  --output /private-runs/a-001 --product /product/codex \
  --model <approved-model> --effort high

python3 /tooling/scripts/repo-map/run.py generate \
  --root /targets/b --output /targets/b/build/repo-map/index.json
python3 /tooling/scripts/repo-map/session.py prepare \
  --condition B --workspace /targets/b \
  --expected-revision <full-commit-sha> --task-id <task-id> \
  --output /private-runs/b-001 --product /product/codex \
  --model <approved-model> --effort high \
  --map-tool /tooling/scripts/repo-map/run.py \
  --index /targets/b/build/repo-map/index.json
```

B preparation requires a fresh index via the map tool's `check`. Its overlay contains absolute, shell-quoted query references and bounded search instructions, never the full index. A gets no overlay or index arguments. B first narrows candidates with file/symbol search, reads necessary sources and authoritative documents, and widens as needed. Existing LSP may be used; no service installation or arbitrary reading quota is required. Syntax-only map facts are not resolved dependencies or a call graph.

## Explicit ordinary fresh launch

After separate live authorization, prepare with `--verify-version`, inspect the manifest and copy its `config_digest` to an independently retained launch instruction. Prepare itself never launches a task. The following explicit command starts one new `codex exec`, uses JSON output, disables update checks, fixes read-only sandbox mode and sends the task (plus B overlay only for B) on standard input. It never resumes, retries, or automatically invokes a collector. Product output remains attached to the terminal; it is not copied into the manifest. A launch-attempt marker is written before invocation; reuse of that run is refused even if invocation fails. Prepare a new run after investigating a failure.

```sh
python3 /tooling/scripts/repo-map/session.py launch \
  --output /private-runs/a-001 --expected-config <config-digest> \
  --task-file /private-inputs/task.txt
```

This ordinary launch is not a measured collector session. No real model call is part of offline tests, which use a synthetic executable only.

## Future measured workflow

Freeze the exact commands, task, executable/configuration evidence, readable filesystem roots, collector linkage and new live budget for approval. Then use this ordering: neutral initial fresh exec → record exact native session link → collector-ready confirmation → resume that same linked session with the task and B-only overlay if assigned B. The neutral initial request must not contain the task or B treatment. The adapter does not implement this collector/resume sequence. Do not use its ordinary task launch as a substitute.

Keep evaluation oracle, previous answers, previous session artifacts and other-condition artifacts inaccessible to each tested process using independently established filesystem/process isolation. Setting the working directory alone does not confine reads; read-only sandbox mode is not an oracle-isolation guarantee. Stop the run on contamination or uncertain session linkage; record missingness rather than repairing results post hoc. Store only approved counts, timing, identifiers and hashes in measurement data, never source/index/prompt/answer contents.

## Class summary query

The same query supports `--format classes` for grouped method symbols and declared field/explicit-constructor reference types. Keep a small class limit when navigating and inspect the source evidence before assuming runtime dependencies. This syntax-only view does not resolve import ambiguity, Spring injection or method calls. Existing row queries remain the default. Regenerate the map after updating these tools, then prepare new session outputs because the index, tool and B overlay fingerprints change. Previously prepared outputs remain as historical artifacts and must not be launched as current configurations.

## Bounded operational observation

A separately approved manual Docker check completed four source-exploration sessions and eight bounded CLI calls. Operational collection and independent review passed, and both B sessions showed map-query use. Answer correctness and completeness still require human assessment. Usage is partial, initialization is excluded, and full task totals and complete cost are unavailable. Two manually selected questions and source-citation presence do not establish general token savings, quality, or B superiority.

The smoke runner and native evidence are private ignored local artifacts, not shipped product functionality. This repository does not implement randomized assignment, statistical aggregation, or the measured collector/resume runner. Docker is not required for ordinary optional map use. Further live calls need a new scoped authorization.
