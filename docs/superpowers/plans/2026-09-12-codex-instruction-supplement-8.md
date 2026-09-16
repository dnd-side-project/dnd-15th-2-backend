# GH-223 Supplementary Eight-Run Evaluation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development to execute the bounded runner/execution task, with one grouped final independent review. Do not add per-run reviewers.

**Goal:** Check whether B3's L2 advantage and B2's L3 advantage persist in eight new observations.
**Architecture:** Preserve the completed lite evaluator, 24-run manifests, raw records and full paused plan. A separate supplementary collector reuses frozen model launch, runtime fingerprint and usage parsing functions without modifying or bypassing the existing 24-run CLI gate. It enforces its own exact eight-row contract and separate batch/run identifiers.
**Tech Stack:** Existing Python evaluator/metrics, Codex CLI 0.153.4, gpt-5.6-sol/high.
**Spec:** Existing lite plan `2026-09-11-codex-instruction-token-comparison-lite.md` exact L2/L3 prompts and checklists; user-approved supplementary proposal (B2/B3 × L2/L3 × two additional repetitions).

## Authorization and constraints

- User approved proceeding with the proposed eight-run follow-up after seeing the initial 24 results. This is outcome-informed follow-up, not a retrospectively preregistered extension of the initial study.
- Issue223; task GH-223-INSTRUCTION-ARCHITECTURE-PHASE2; design HARNESS-DESIGN-GH-223-001; branch chore/gh-223-instruction-architecture-phase2.
- Exactly eight new parent sessions. No pilot, automatic retry, sample expansion, configuration/model updates, Gradle, actual tests, Terraform, AWS or application changes.
- Original24 summaries, manifests, scripts, candidate checkouts and full204 plan remain unchanged. New supplementary IDs and raw directory; never label old runs as new or pad prior-run evidence to satisfy a sequence gate.
- Raw outside all repos, directory0700/files0600. Public outputs only non-sensitive numbers, logical IDs, judgments and hashes.
- Reuse exact prompts, candidate roots and model/effort/CLI. Validate frozen static environment and runtime fingerprint before/after calls. If changed, stop and report BLOCKED; do not weaken validators.
- Missing required first/final usage or child accounting, unknown child runtime role, runtime mismatch, mutation or execution failure stops the batch; retain all observations, no rerun.
- New collector is an execution artifact, not a candidate instruction change. Record both its own hash and frozen evaluator/metrics hashes in a separate supplemental manifest. Do not relabel the old environment as incorporating the new collector.
- Raw first/final usage, elapsed, tools, descendants and runtime checks must be collected equivalently. Auxiliary instruction attribution may remain explicitly unavailable. Keep semantic checklist review separate from immutable raw summaries.
- User approval is required before any new commit. Current authorization permits the eight-run execution after the contract is frozen by SHA-256, without an additional commit gate before execution. No push/PR/merge.

## Frozen sequence

| Sequence | Supplement ID | Repetition | Task | Candidate |
| --- | --- | --- | --- | --- |
| 1 | sup1-r1-l2-b2 | 1 | L2 | B2 |
| 2 | sup1-r1-l2-b3 | 1 | L2 | B3 |
| 3 | sup1-r1-l3-b2 | 1 | L3 | B2 |
| 4 | sup1-r1-l3-b3 | 1 | L3 | B3 |
| 5 | sup1-r2-l2-b3 | 2 | L2 | B3 |
| 6 | sup1-r2-l2-b2 | 2 | L2 | B2 |
| 7 | sup1-r2-l3-b3 | 2 | L3 | B3 |
| 8 | sup1-r2-l3-b2 | 2 | L3 | B2 |

## Decision rule (fixed before new calls)

Primary descriptive metric: child-inclusive cumulative input tokens. Report cached and uncached input, output and elapsed separately; never infer a price-weighted cost or mixed score.

For L2 the expected direction is B3 < B2; for L3 it is B2 < B3. Mark the direction as repeated only if both new within-repetition comparisons have that direction and every answer meets the same checklist. A tie, reversed pair, invalid/missing run, or unequal checklist fulfillment means the proposed direction is not consistently reproduced. Report magnitudes without inventing a significance or minimum effect threshold.

Show new observations separately, followed by a clearly labeled descriptive four-observation-per-candidate/task view combining original and follow-up. Do not claim statistical significance, safety, actual implementation quality, or universal superiority. Eight new sessions end the follow-up regardless of the result.

## Task S1: Separate collector and execution

**Owner:** executor; root coordinates. You are not alone; preserve other edits.
**Files:** Create `scripts/experiments/codex-instruction-supplement.py`, `docs/experiments/codex-agents/gh-223-supplement-environment.json`, `docs/experiments/codex-agents/gh-223-supplement-runs.csv`; ignored execution ledger and restricted raw outputs.
**Interfaces:** Existing unchanged evaluator functions, old lite manifest and prompts; produce exactly eight hash-bound supplementary records with runtime/usage checks.

- [ ] Inspect reusable launch/parse/runtime helpers and implement a minimal separate collector with strict batch order and no retries. Never monkeypatch the original sequence checks or call the pilot mode as a shortcut.
- [ ] Perform focused offline checks for exact8 order, duplicate/missing/out-of-order rejection, missing usage and environment drift. No real model call as a test.
- [ ] Freeze new plan/collector/manifest hashes and the existing evaluator, metrics, order, environment and candidates before first run. Record exact launch equivalence.
- [ ] Execute eight sequentially in frozen order, matching original raw capture and measured usage. Review checklist against each final answer and trace without another model session.
- [ ] Validate all eight and export CSV; preserve incomplete evidence if stopped.

## Task S2: Report and grouped review

**Owner:** root report; independent verifier one grouped final review.
**Files:** Create `docs/experiments/codex-agents/gh-223-supplement-report.md`, `docs/experiments/codex-agents/gh-223-supplement-verification.md`; update `TASK.md` status.

- [ ] Report observed usage and decision-rule outcomes, new-only and combined descriptive views, total time/tokens and limits.
- [ ] One independent review of collector equivalence, raw usage, hash/order checks, answer judgments and arithmetic. Target10 minutes; fix only important findings without automatic model reruns.
- [ ] Run relevant Python checks, harness/document checks and diff check. Do not rerun Gradle for this supplementary measurement work.
- [ ] Present concrete purpose-separated commit draft for user approval. Preserve old evidence and paused full plan.
