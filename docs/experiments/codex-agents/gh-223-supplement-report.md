# GH-223 supplementary eight-run report

- status: `PASS` (supplementary scope; commit draft approved by the user)
- issue_number: `223`; task_id: `GH-223-INSTRUCTION-ARCHITECTURE-PHASE2`; design_id: `HARNESS-DESIGN-GH-223-001`
- batch_id: `GH-223-SUPPLEMENT-8-001`; evidence base commit: `b01812e`
- Contract: [eight-run plan](../../superpowers/plans/2026-09-12-codex-instruction-supplement-8.md)

## Outcome

The L2/test direction is reproduced: B3 used less cumulative input than B2 in both new matched repetitions, with all answers meeting the predefined checklist. The proposed L3/infra direction is not consistently reproduced: B3 used less input in the first new pair, while B2 used slightly less in the second. The infra ranking is therefore inconclusive.

This follow-up was selected after seeing the original 24 results. It is a separate outcome-informed batch, not an extension retrospectively claimed to have been preregistered. Exactly eight new parent sessions completed, with zero retries, invalid measurements or children. All separate executor checklist judgments are PASS/PASS/PASS; the [independent review](gh-223-supplement-verification.md) passed.

## New observations

| Run | First input | Cumulative input | Cached subset | Uncached input | Output | Seconds |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| sup1-r1-l2-b2 | 20,812 | 739,209 | 648,448 | 90,761 | 9,491 | 202.0 |
| sup1-r1-l2-b3 | 20,973 | 432,519 | 350,208 | 82,311 | 7,944 | 186.1 |
| sup1-r1-l3-b2 | 20,818 | 564,258 | 511,104 | 53,154 | 8,515 | 185.6 |
| sup1-r1-l3-b3 | 20,979 | 319,538 | 274,176 | 45,362 | 6,602 | 144.0 |
| sup1-r2-l2-b3 | 20,973 | 163,060 | 105,088 | 57,972 | 4,858 | 109.0 |
| sup1-r2-l2-b2 | 20,812 | 932,266 | 861,568 | 70,698 | 8,796 | 201.3 |
| sup1-r2-l3-b3 | 20,979 | 386,311 | 351,616 | 34,695 | 5,652 | 145.3 |
| sup1-r2-l3-b2 | 20,818 | 380,822 | 338,304 | 42,518 | 5,071 | 111.3 |

## Fixed direction rule

Primary metric is child-inclusive cumulative input; cache/output/time are separate descriptive metrics. The expected direction must hold in both new repetitions with equivalent checklist fulfillment. No effect-size or significance threshold was invented after observing the new outcomes.

| Task | Expected direction | New pair 1 | New pair 2 | Result |
| --- | --- | --- | --- | --- |
| L2 test | B3 < B2 | 432,519 < 739,209 | 163,060 < 932,266 | Reproduced in both new pairs |
| L3 infra | B2 < B3 | 564,258 > 319,538 | 380,822 < 386,311 | Not consistently reproduced |

The second L3 pair differs by only 5,489 input tokens (about 1.4% relative to B3); the first pair has the reverse direction. This does not support choosing B2 as a stable infra winner.

## New-only means — two observations per cell

The combined view is descriptive only; the original and follow-up batches remain separately identifiable.

| Task / candidate | Input | Cached subset | Uncached input | Output | Seconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| L2/B2 | 835,737.5 | 755,008.0 | 80,729.5 | 9,143.5 | 201.6 |
| L2/B3 | 297,789.5 | 227,648.0 | 70,141.5 | 6,401.0 | 147.6 |
| L3/B2 | 472,540.0 | 424,704.0 | 47,836.0 | 6,793.0 | 148.4 |
| L3/B3 | 352,924.5 | 312,896.0 | 40,028.5 | 6,127.0 | 144.7 |

L2 B3 relative to B2: input -64.4%, uncached -13.1%, output -30.0%, elapsed -26.8%.

L3 B3 relative to B2: input -25.3%, uncached -16.3%, output -9.8%, elapsed -2.5%.

## Original plus follow-up means — four observations per cell

The combined view is descriptive only; the original and follow-up batches remain separately identifiable.

| Task / candidate | Input | Cached subset | Uncached input | Output | Seconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| L2/B2 | 700,768.8 | 627,904.0 | 72,864.8 | 8,510.8 | 194.2 |
| L2/B3 | 241,232.8 | 190,400.0 | 50,832.8 | 5,694.2 | 128.9 |
| L3/B2 | 343,918.5 | 302,592.0 | 41,326.5 | 6,360.0 | 141.9 |
| L3/B3 | 334,317.8 | 277,152.0 | 57,165.8 | 6,346.5 | 148.5 |

L2 B3 relative to B2: input -65.6%, uncached -30.2%, output -33.1%, elapsed -33.6%.

L3 B3 relative to B2: input -2.8%, uncached +38.3%, output -0.2%, elapsed +4.7%.

## Usage and limits

The eight model processes took 1284.598 seconds (21 minutes 25 seconds) in total. Input: 3,917,983, including cached 3,440,512; uncached input: 477,471; output: 56,929. Cache is not added twice. These totals exclude collector preparation, orchestration and independent review time/usage, and exclude all original/pilot observations. No monetary saving is inferred.

- The new-only L2 input mean is lower for B3, but individual values vary widely in both candidates. The effect is confined to these read-only instruction-reporting tasks.
- Combined L3 means are close and the new pair directions disagree. No universal or statistically significant winner is established.
- No actual tests, Gradle, Terraform or infrastructure mutation were run by the evaluated sessions. Checklist success is not proof of implementation quality, safety, or identical answer depth.
- The supplementary collector uses the frozen original argv/process/runtime/usage helpers and its own exact eight-run gate. It does not modify or bypass the old 24-run sequence gate. Its own hash and the old instrumentation hashes are bound separately in the supplementary environment manifest.
- Both batches use the same exact prompts, candidate commits, gpt-5.6-sol/high, CLI0.153.4 and recorded runtime fingerprint. Complete builtin/MCP/plugin catalogs and auxiliary instruction-token attribution remain unavailable. Runtime child behavior is unobserved because child count is zero.
- Original24/full204 artifacts, source evaluator/metrics and candidate worktrees are preserved. The full204 plan remains paused. The eight-run follow-up stops here regardless of the outcome.
- Restricted raw summaries remain immutable with original UNAVAILABLE semantic judgments; separate reviewed checklist records are hash-bound. Public [run CSV](gh-223-supplement-runs.csv) and [environment manifest](gh-223-supplement-environment.json) provide non-sensitive provenance.

## Verification

Independent collector/evidence/answer/arithmetic review passed; see the [verification report](gh-223-supplement-verification.md). This supplementary measurement work uses focused Python and harness/document checks; Gradle is not repeated. New commits require approval of the concrete draft.
