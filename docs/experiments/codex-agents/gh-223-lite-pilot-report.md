# GH-223 lite pilot report

## Result

- status: `BLOCKED`
- issue_number: `223`
- task_id: `GH-223-INSTRUCTION-ARCHITECTURE-PHASE2`
- design_id: `HARNESS-DESIGN-GH-223-001`
- plan_id: `GH-223-TOKEN-COMPARISON-LITE-001`
- completed_parent_sessions: `1`
- planned_parent_sessions: `2`
- valid_pilot_sessions: `0`
- child_sessions: `0`

The B0/L1 pilot executed once with the approved read-only prompt, but the frozen evaluator marked the run invalid with `RUNTIME_EVIDENCE_MISSING`. The stop rule was applied immediately. B0 was not retried, B3/L2 was not started, and no comparison session ran.

## Frozen inputs

- plan/tooling commits: `a30e199`, `9aaba06`, `621e2b4`
- coordinator head: `621e2b426afe4259f6f95fa271befab80b5317d2`
- requested runtime: `gpt-5.6-sol`, reasoning effort `high`, Codex CLI `0.153.4`
- runner SHA-256: `6b6b3adc8a19c7ec132a4fe2383298d808a68644471e5ab122886cc0dfb00ea4`
- metrics SHA-256: `03a4f2c057696a2f1db23878185325cb82404ebba4620d589405929d18b7e97e`
- lite environment file SHA-256: `d9bf24e6eaa45979f51b365f0877ae5fa5a0659016ba4bd26bbff4e8d842e0a9`
- lite execution order SHA-256: `2c0050d7f0247c7e010c89b99b4a05b5ed3d7611fffc2e5167828e2bc3f11308`
- B0 candidate commit: `256a0dbcb7798904fa9ed4e44c2556016b5f56bd`
- sandbox/input mode: `read-only`; prompt passed through the existing argv; stdin closed with EOF

The coordinator directly checked the restricted rollout and confirmed `gpt-5.6-sol/high`. The complete runtime fingerprint is still unavailable for the reason below.

## B0/L1 observation

| Measurement | First | Final parent | Child-inclusive total |
| --- | ---: | ---: | ---: |
| input tokens | 23,555 | 532,485 | 532,485 |
| cached input tokens | 0 | 472,704 | 472,704 |
| output tokens | 415 | 5,634 | 5,634 |
| reasoning output tokens | 138 | 2,684 | 2,684 |
| total tokens | 23,970 | 538,119 | 538,119 |

- usage status: `available`; coordinator direct comparison found the rollout and summary values equal
- elapsed: `151.03765462501906` seconds
- child calls: `0`; child elapsed: not applicable
- evaluator status: `invalid`
- invalid reason: `RUNTIME_EVIDENCE_MISSING`
- answer checklist: `UNAVAILABLE`
- instruction attribution: `UNAVAILABLE`; measured start bytes `1,753` and additional read bytes `1,862`, while structure/read token fields remained `null`

Cached input is included in input tokens and is not added again to the total.

## Instrumentation failure

The rollout contains one `session_meta` event and one `turn_context` event. `base_instructions`, model, effort, and CLI metadata are present, but `session_meta` does not contain the `dynamic_tools` array required by the frozen `runtime_evidence_from_events` implementation. The extractor consequently returned an empty runtime evidence object, so tool/plugin catalog hashes and the complete required runtime fingerprint could not be produced.

This is a measurement failure. The observed usage is retained as immutable failure evidence and is not eligible for candidate comparison or promotion.

## Restricted evidence

Raw stdout, stderr, rollout-derived evidence, and the summary remain outside repositories in a directory with mode `0700`; evaluator-created files have mode `0600`. The private path is intentionally omitted from this report.

- stdout SHA-256: `3a07ad1c81e313a1e8695f5dfb6b4cfcd3039ee053b14f4ea747fd4231db4d40`
- stderr SHA-256: `1aa26269eb1cc57f86b235a03cda53c004edb5b1e9fc99d4da4f00843293d721`
- summary SHA-256: `4e6f14373b39fa7347ce2cc5407f068174d4dfff3b6ed5bcaf378c67245ec38f`
- captured runtime evidence SHA-256: `ca3d163bab055381827226140568f3bef7eaac187cebd76878e0b63e9e442356`

## Unverified scope and risk

- B3/L2 and the required two-pilot pair remain unexecuted.
- The remaining 24-session time/token range is not estimated from one invalid pilot.
- No B0~B3 token or answer comparison, cost winner, safety conclusion, or full-environment validation is available.
- Candidate, evaluator, application, test, Terraform, AWS, and DB source/state were not changed by the pilot. This report is the only repository artifact added by Task 9 execution.
- Resuming requires a reviewed evaluator change that supports the observed rollout schema and explicit authorization for a new pilot attempt. Automatic retry and sample expansion remain prohibited.

## Approved retry 1 — Task 9 measurement result

This section records the subsequent explicitly approved two-attempt retry. The original BLOCKED attempt above remains historical failure evidence and is excluded from promotion and comparison.

- status: `PASS` for the Task 9 lite measurement gate; Task 10 has not started
- issue_number: `223`; task_id: `GH-223-INSTRUCTION-ARCHITECTURE-PHASE2`; design_id: `HARNESS-DESIGN-GH-223-001`
- approved commits: `2deae42` (failure report), `719db51` (reviewed adapter), `3c6e67b` (instrumentation fingerprint)
- runtime: `gpt-5.6-sol/high`, Codex CLI `0.153.4`, read-only
- new attempts: `2`; valid: `2`; children: `0`; automatic retries: `0`
- actual parent attempts to date: `3` (one historical invalid attempt and two valid new attempts)

| Measurement | B0 / L1 | B3 / L2 |
| --- | ---: | ---: |
| Elapsed seconds | 146.001 | 142.420 |
| First input tokens | 23,555 | 20,973 |
| First cached input tokens | 6,528 | 0 |
| Final input tokens | 274,092 | 253,725 |
| Final cached input tokens | 225,536 | 184,192 |
| Final output tokens | 5,831 | 5,899 |
| Tool calls, physical and logical | 6 | 8 |
| Child calls | 0 | 0 |

The pair took 288.421 seconds (4 minutes 48 seconds), with 527,817 input tokens including 409,728 cached tokens, and 11,730 output tokens. Uncached input was 118,089 tokens. The prompts differ, so these two observations do not establish a candidate cost ranking or equal answer quality.

### Direct review and evidence binding

The coordinator independently compared first and final usage with raw token-count events, checked tool-call counts and absence of descendants, and confirmed that child-inclusive usage equals parent usage. Process elapsed values contain the rollout timestamp envelopes (144.672 and 140.229 seconds); startup and shutdown overhead is retained. Both runtime fingerprints match. Usage validation returned no problems for either run.

- B0 summary SHA-256: `ff387d5b0afbfc68aca2a1b001fbb826d99172d9e8fe2d9153a512cc0c77f61b`
- B3 summary SHA-256: `ef1b8964e711db7e865765931a08b03b8d0b0e83bf17c0b948420dc8d24df137`
- runtime evidence SHA-256, identical for both: `3354fefb0faca771354adaf53de524235f0800e2e72a19e6c782db3f1b7047bb`
- coordinator review SHA-256: `cee6e3c64a471fe46f8bc322152ccfdf9d311bcfc5b8bee83539499c87622ff5`
- promoted lite environment file SHA-256: `c37190afd3ea0503e7ad2fc2afb7440412d4daab2748da622b8f655965b0e0c9`
- comparison environment SHA-256: `cb454bb5be85b2172aea07136b3692b5bb5d86952681a1a461d3426baddd70f6`
- execution order SHA-256, unchanged: `2c0050d7f0247c7e010c89b99b4a05b5ed3d7611fffc2e5167828e2bc3f11308`

Original raw files remain outside repositories with restricted permissions. The separate coordinator review and promotion package bind the unchanged summaries to the lite manifest. `APPROVED_PILOT_PAIR` records the measurement review specified in Task 9; it does not replace the human commit gate.

### Estimate for the remaining 24 comparisons

Multiplying the two observed parent durations by 24 gives approximately 57–58 minutes of model runtime. Multiplying the observed input range gives 6.09–6.58 million input tokens. At the pair average, the projection is 6,333,804 input tokens, including 4,916,736 cached tokens, plus 140,760 output tokens. Cached tokens must not be added a second time.

These are rough extrapolations, not confidence intervals or budget guarantees: only two different prompts were observed, L3 is unobserved, cache behavior can vary, and neither pilot spawned children. Allow roughly 1–2 hours for planning; final review and repository checks add time. Future children can increase usage and elapsed time. No monetary estimate or additional sample authorization is inferred.

### Checks, limits, and next gate

- executed_checks / passed_checks: direct raw usage and tool attribution review for both pilots; lite runtime promotion; strict `python3 scripts/experiments/codex-instruction-phase2.py verify --profile lite` (`PASS`, no problems)
- failed_checks: none for this retry; the historical invalid attempt remains recorded above
- blocked_checks: no required Task 9 measurement checks; auxiliary instruction-token attribution and complete tool/plugin catalogs remain `UNAVAILABLE`
- assumptions: the forecast extrapolates two observed parent sessions; it does not predict L3 or child behavior
- risks: only the serialized dynamic supplement is fingerprinted; builtin/MCP/plugin catalog completeness is not claimed. Child-role fingerprints are empty; an unobserved child role must follow the evaluator stop rule. Answer quality, safety, and candidate superiority are not established by the measurement pilot.
- changed_files: this report and `gh-223-lite-environment.json`; full-plan artifacts and candidate worktrees are preserved
- deferred_checks: Gradle and the full final validation bundle remain scheduled for Task 12 under the approved lite plan
- required_human_decisions: approve the concrete pilot-evidence commit draft before the frozen 24-session comparison starts
