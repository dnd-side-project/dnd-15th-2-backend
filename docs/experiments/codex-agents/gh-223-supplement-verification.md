# GH-223 supplementary eight-run independent verification

- status: `PASS`
- issue_number: `223`
- task_id: `GH-223-INSTRUCTION-ARCHITECTURE-PHASE2`
- design_id: `HARNESS-DESIGN-GH-223-001`
- batch_id: `GH-223-SUPPLEMENT-8-001`
- evidence_base_commit: `b01812e`
- review_scope: supplementary collector and exact eight-run result only

## Independent result

No important finding was found in the approved supplementary scope. The collector, eight observations, checklist judgments, arithmetic, and report decision follow the frozen contract.

- The separate collector calls the unchanged original argv builder, process runner, JSONL and runtime parsers, usage extractor, descendant discovery, child merger, restricted writer, and sanitized `shell=false` process path. It enforces its own exact eight-row prefix/order and bindings without changing or weakening the original 24-row gate.
- The supplementary manifest freezes the collector, plan, original evaluator, metrics, original lite environment/order, prompts, candidate snapshots, runtime fingerprint, and per-row argv hashes. All eight raw summaries carry the same frozen hashes and the expected read-only root launch provenance.
- The finalized CSV contains exactly B2/B3 × L2/L3 × two new repetitions in the approved order. All eight rows match their summary hash, prompt, candidate commit, model `gpt-5.6-sol`, effort `high`, CLI `0.153.4`, runtime status `COMPARED`, and chronological execution order.
- First and final usage fields match the underlying session token events for all eight runs. Summary tool counts match the rollout records; all runs have zero descendants and no duplicate child usage.
- Each immutable summary retains three `UNAVAILABLE` checklist values. Each separate checklist record is bound to its summary hash. Independent inspection of all eight final answers and command traces supports `PASS/PASS/PASS`; no session executed tests, Gradle, Terraform, AWS, or a mutation command.
- The aggregate is `COMPLETE`, contains eight runs and no problems, and has SHA-256 `a4605a2ba241deb50f657ffdeae2216a15752fe02aa4540fed08efa8960ba46a`. The public CSV SHA-256 is `7aa99e3571df95895dbdc744edbc692519d40821d2f3ff58ff23f4a7bb12ffbd`.
- New-only totals recompute to 3,917,983 input, 3,440,512 cached input, 477,471 uncached input, 56,929 output, and 1,284.598 seconds. All new-only and combined four-observation means and percentage differences in the report match independent recomputation.
- The fixed rule is applied correctly. L2 repeats the expected B3 < B2 direction in both new pairs. L3 does not consistently repeat B2 < B3: the first pair reverses it, while the second supports it by only 5,489 input tokens. The report therefore treats L3 as inconclusive.
- Preservation evidence confirms the original 24-run evaluator, metrics, manifests, CSV, full artifacts, original checkout, and B0–B3 candidate worktrees remain unchanged or clean as applicable.

## Repository checks

The coordinator’s current-change evidence records these focused checks as passing:

- `./harness check`
- `npm run hooks:validate`
- `git diff --check`

Gradle was not run, as required by the approved supplementary plan. The independent reviewer did not repeat these broad checks.

## Limits

- This is an outcome-informed follow-up selected after the original 24 observations. It is a separate eight-run batch, not a retrospectively preregistered extension.
- Each new cell has two observations; the combined view has four. Results are descriptive and do not establish significance, a universal winner, monetary savings, implementation quality, or a safety guarantee.
- The evaluated scope contains only read-only L2 and L3 instruction-reporting tasks for B2 and B3. The full 204-session and actual-change smoke plans remain paused.
- Complete builtin, MCP, and plugin catalogs and auxiliary instruction-token attribution remain unavailable. No evaluated run spawned a child, so child-role behavior remains unobserved.
- Input and cache vary substantially between repetitions. The follow-up ends at eight sessions regardless of its outcome; no retry or additional sample was taken.

## Result contract

```text
status: PASS
issue_number: 223
task_id: GH-223-INSTRUCTION-ARCHITECTURE-PHASE2
design_id: HARNESS-DESIGN-GH-223-001
changed_files: TASK.md; supplement plan; supplement collector; supplement environment; supplement CSV; supplement report; supplement verification
executed_checks: collector/source equivalence review; exact-eight order and hash audit; raw first/final usage and runtime comparison; parent/child/tool accounting; eight-answer checklist and command-trace review; new-only and combined arithmetic recomputation; preservation and coordinator-check evidence review
passed_checks: all scoped checks
failed_checks: none
blocked_checks: none within the approved supplementary scope
assumptions: restricted raw evidence remains available with its reviewed hashes and permissions until handoff
risks: outcome-informed selection; two new observations per cell; variable cache/input; unavailable auxiliary catalogs and attribution; no child sample; full safety/smoke evaluation paused; Gradle not run by plan
required_human_decisions: approve the concrete supplementary result commit draft if these artifacts should be committed; push, PR, and merge remain separate decisions
```
