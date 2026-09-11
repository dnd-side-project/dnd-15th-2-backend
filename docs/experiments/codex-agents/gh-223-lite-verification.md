# GH-223 lite comparison independent verification

- status: `PASS`
- issue_number: `223`
- task_id: `GH-223-INSTRUCTION-ARCHITECTURE-PHASE2`
- design_id: `HARNESS-DESIGN-GH-223-001`
- plan_id: `GH-223-TOKEN-COMPARISON-LITE-001`
- evidence_base_commit: `87cd509`
- review_scope: active lite plan Tasks 10–12

## Independent result

The 24 comparison observations are complete and internally consistent with the frozen order, provenance, restricted raw summaries, underlying session usage events, and the published report. The review found no important discrepancy within the approved lite scope.

- The CSV contains the exact 3 tasks × 4 candidates × 2 repetitions in frozen order. All 24 rows use `gpt-5.6-sol/high`, Codex CLI `0.153.4`, the expected candidate commit and prompt hash, and runtime evidence status `COMPARED`.
- All 24 CSV summary hashes match their immutable restricted summaries. The aggregate is `COMPLETE` with 24 runs and no recorded problems; its SHA-256 is `1cc137e347b0df8c39123e268e9b60b64fb02b4f08037ab5c96723f343280607`.
- First and final input, cached input, output, and reasoning-output fields match the underlying session token events for every run. The published totals recompute to 8,685,408 input, 7,438,208 cached input, 1,247,200 uncached input, 149,349 output, 8,834,757 input plus output, and 3,806.055 seconds.
- Exactly 24 parent sessions completed with 0 retries and 0 child sessions. Tool counts match the raw rollout records, and no duplicate child accounting is present.
- The original evaluator summary checklist values remain `UNAVAILABLE` for all 24 runs. The separate CSV review fields are bound to each summary hash. Independent inspection of every final answer and command trace supports all three predefined checklist items for all 24 runs.
- The command traces are read-only. No comparison session ran Gradle, tests, Terraform, or AWS mutation commands, and the candidate worktrees remained clean.
- The report’s task means, ranges, equal-task candidate means, and percentage comparisons match independent recomputation. In this sample, B3 versus B0 is −43.5% input, −17.7% uncached input, −8.5% output, and −23.0% elapsed time. The report makes no monetary claim.
- The original full environment and order hashes remain unchanged, and the original checkout and B0–B3 worktrees were clean in the preservation evidence.

## Repository validation evidence

The independent reviewer inspected the coordinator’s retained logs rather than repeating the broad checks. Each recorded command exited 0:

- `./harness check`
- `./harness pr-ready --project-tests`
- `npm run hooks:validate`
- `git diff --check`

The Gradle portion completed in about six seconds with 13 of 14 tasks up-to-date, so this evidence is not a forced test rerun.

## Limits

- The full 204-session evaluation and actual-change smoke remain paused. This lite result does not replace them.
- Each task/candidate has only two repetitions. The descriptive differences do not establish statistical significance, a universal optimum, implementation quality, or a safety guarantee.
- Complete builtin, MCP, and plugin catalogs and auxiliary instruction attribution are unavailable. The runtime fingerprint covers only the recorded base instructions and serialized dynamic supplement.
- No evaluated session spawned a child, so child-role behavior remains unobserved even though zero-child accounting is verified.
- L2 and L3 explicitly read nested guidance from the repository root; they do not measure automatic loading from a nested working directory.

## Result contract

```text
status: PASS
issue_number: 223
task_id: GH-223-INSTRUCTION-ARCHITECTURE-PHASE2
design_id: HARNESS-DESIGN-GH-223-001
changed_files: TASK.md; gh-223-lite-runs.csv; gh-223-lite-report.md; gh-223-lite-verification.md
executed_checks: 24-run provenance/hash audit; raw first/final usage comparison; parent/child/tool accounting; 24-answer checklist and command-trace review; arithmetic/report recomputation; preservation evidence review; retained final-gate log review
passed_checks: all scoped checks
failed_checks: none
blocked_checks: none within the approved lite scope
assumptions: retained raw evidence and final-gate logs remain available with their reviewed hashes and permissions
risks: two-repetition sampling; unavailable auxiliary instruction/tool-catalog data; no observed child session; full safety/smoke evaluation paused; Gradle tasks were not forcibly rerun
required_human_decisions: approve the concrete result commit draft if these lite artifacts should be committed; push, PR, and merge remain separate decisions
```
