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
