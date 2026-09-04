# Test Report: TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE

> Created at: `2026-09-05T03:51:23+09:00`
> Performance measured at: `2026-09-05T03:54:13+09:00`
> GitHub Issue: `#214`
> Branch: `perf/gh-214-postgis-e3-evidence`
> Commit: `2f4e575`
> Task ID: `GH-214-POSTGIS-E3-EVIDENCE`

## 1. Executive summary

- Result: `PASS`
- Experiment conclusion: `REJECTED: statistics target 1000 did not materially change estimates/access path, while all guardrails remained equal.`
- Tested scope: PATH-A local Testcontainers PostGIS E3 experiment — performance-only `pg_stat_statements`, 10K:1K / 50K:5K / 100K:10K cardinality sweep, fixed 100K:10K statistics-target 100 vs 1000 comparison, preview/matching/recipient/policy guardrails, and this sanitized report.
- Unverified scope: production data, production statistics/cache/load, Testcontainers CPU/memory (not fixed), relations other than `user_account` and `active_user_presence` (the only ANALYZE targets), Incremental Sort nodes (probe collects only nodes literally typed `Sort`), and any production SQL/index/policy change.
- Release recommendation: no production change. This Issue does not recommend altering statistics, queries, indexes, schema, policy, or infrastructure.

`./harness test-run --id TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE` ran unit and ordinary `integrationTest` only. It does **not** execute the manual `performanceTest` suite. Focused and full `performanceTest` commands were run separately for this report.

# Performance Experiment Report

> Warning: do not copy raw query text, EXPLAIN JSON, IDs, coordinates,
> credentials, URLs, tokens, `.env` values, or complete logs into this
> report. Record only allowed aggregate fields, logical labels, and
> sanitized plan summaries.

## Contract

- Experiment ID: `GH-214-POSTGIS-E3-EVIDENCE` (PATH-A local Testcontainers experiment)
- GitHub Issue: `#214`
- Test plan ID: `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE`
- Before commit and condition: commit `2f4e575`; `default_statistics_target = 100`; `ANALYZE` on `user_account` and `active_user_presence` only; no reseed
- After commit and condition: same commit `2f4e575` and same fixture; `default_statistics_target = 1000`; same two-relation `ANALYZE`; no reseed. This is not a code-change A/B.
- Fixture seed and logical distribution:
  - Sweep (PERF-003/004/005): deterministic 10:1 account:presence scales 10,000:1,000, 50,000:5,000, 100,000:10,000 inside a 100 km disk using the GH-163 square-root radius and golden-angle formula. No `random()`.
  - E3 before/after (PERF-006/007/008/009): same 100K:10K *synthetic* scale as PERF-005, **plus** 9 guardrail accounts, 10 extra presences, and 10,004 baseline `recipient_receive_state` rows. PERF-006 is **not** PERF-005. MATCHING therefore hits a populated receive-state table in PERF-006 and an empty one in PERF-005. Equality claims are within-fixture only.
  - 10K:1K 5 km probe presence count is **2**, not the implementation-plan analytical 3. `ST_DWithin` excludes the `gs=3` boundary at exactly 5,000.000 m. The approved test plan only fixed 13 and 25. Data, formula, and radius were not changed.
- Primary metric: planner estimates (`plan rows`) and access path / partial-GiST state on the two ANALYZEd relations. Client p50/p95/p99 and `pg_stat_statements` timings are observations only, not pass/fail thresholds.
- Correctness, policy, resource, and cost guardrails: preview segment-count equality; matching order/set equality; persisted recipient logical-set equality; duplicate/missing = 0; bidirectional block, inactive, expired, fairness, and receive-capacity checks in both statistics conditions. No latency or GiST threshold.
- Success and stop criteria: every P0 guardrail equal → `SUPPORTED` or `REJECTED`. Any correctness/policy failure or uncontrolled condition → `INVALID` (`FAIL`). Statistics target 1000 not changing estimates/access path is a valid `REJECTED` result, not a reason to edit SQL or indexes.

## Environment

- Java, Spring Boot, PostgreSQL, PostGIS, Docker Desktop versions:
  - Java toolchain 21 (Temurin 21.0.12.1) for Gradle test tasks; Gradle host/daemon JVM Temurin 24.0.2
  - Spring Boot 3.5.16
  - Gradle 8.14.3
  - Database: local Testcontainers `postgis/postgis:16-3.5-alpine` forced to `linux/amd64` (emulated on host `arm64`)
  - PostgreSQL 16 / PostGIS 3.5 (image tag)
  - Docker Desktop 4.87.0; Docker Engine 29.7.2
- Host resource summary without account, address, or device identifiers:
  - Host: 10 logical CPUs, 24 GiB RAM, `arm64`
  - Docker VM: 10 CPUs, approximately 7.75 GiB RAM, linux/aarch64
- Container CPU and memory limits: Testcontainers CPU and memory limits were **not** fixed. Latency between statistics conditions is within run-to-run host noise and is not a pass/fail signal.
- JVM, Hikari, and worker settings:
  - Test profile Hikari: `maximum-pool-size=4`, `minimum-idle=1`
  - No experiment-specific JVM heap flags
  - Matching worker (PERF-008/009): batch limit 10, 60 s lease, `OutboxRetryPolicy(3, 1s)`
  - Policy fixtures (not production recommendations): `max-recipients-per-post=10`, `receive-capacity=5`
- Dirty-worktree status excluding unrelated file contents:
  - Measured commit: `2f4e575`
  - No uncommitted Java, Flyway, Terraform, workflow, or production config
  - Untracked at report time: this evidence report, the sanitized console capture, and `templates/performance-experiment-report.md`

## Method

- Warm-up count: 1 unmeasured call per combination (`query.get()` before `pg_stat_statements_reset()`)
- Measured-call count: 20 per query/radius/statistics-target combination
- Cold or warm state: warm for the measured batch (warm-up discarded; `pg_stat_statements` reset before the 20 calls). Shared-buffer hits were non-zero and shared reads were 0 on published lines. EXPLAIN ANALYZE is a separate later pass and is not interchangeable with the 20-call timings.
- Statistics target: sweep uses 100 only. E3 before/after uses 100 then 1000 on the same connection: `SET default_statistics_target` → `ANALYZE user_account, active_user_presence` → `RESET`. No other relations were re-analyzed.
- Query/radius combinations: preview and matching × policy baseline (`GLOBAL` / 0..20,100 km from `DirectionPostPolicy`) × 5 km diagnostic probe. 5 km is not an operating-policy claim.
- Representative-value rule: published numbers are the Task 6 full `./gradlew performanceTest` run (started `2026-09-05T03:54:13+09:00`). Client percentiles are from the 20 measured JDBC-inclusive calls. `pg_stat_statements` is database time for those 20 calls. The two are not interchangeable. A prior Task 5 controlled run exists but is not copied as this experiment's results. GH-163 timings are not copied.

## Results

Fresh full-suite XML: `DirectionMatchingE3PerformanceIntegrationTest` 5 tests, 0 failures, 0 errors, 96.344 s. Sanitized `experiment=` lines: `docs/reports/tests/gh-214-TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-sanitized-console.txt`.

### Cardinality sweep (target 100)

| Scale | Query | Radius | Client p50 / p95 / p99 (ms) | calls | rows | mean_exec_ms | shared_hit / read | temp r/w |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 10K:1K | PREVIEW | POLICY | 31.441 / 31.886 / 33.234 | 20 | 160 | 29.864 | 115018 / 0 | 0 / 0 |
| 10K:1K | MATCHING | POLICY | 3.276 / 3.852 / 4.004 | 20 | 2500 | 2.600 | 27780 / 0 | 0 / 0 |
| 10K:1K | PREVIEW | PROBE | 0.491 / 0.539 / 0.651 | 20 | 160 | 0.115 | 440 / 0 | 0 / 0 |
| 10K:1K | MATCHING | PROBE | 0.388 / 0.426 / 0.429 | 20 | 20 | 0.057 | 300 / 0 | 0 / 0 |
| 50K:5K | PREVIEW | POLICY | 154.492 / 163.046 / 163.828 | 20 | 160 | 154.650 | 657400 / 0 | 0 / 0 |
| 50K:5K | MATCHING | POLICY | 13.975 / 14.295 / 14.611 | 20 | 12500 | 13.546 | 157320 / 0 | 0 / 0 |
| 50K:5K | PREVIEW | PROBE | 0.846 / 0.915 / 0.915 | 20 | 160 | 0.477 | 1800 / 0 | 0 / 0 |
| 50K:5K | MATCHING | PROBE | 0.414 / 0.466 / 0.479 | 20 | 40 | 0.093 | 480 / 0 | 0 / 0 |
| 100K:10K | PREVIEW | POLICY | 314.082 / 334.828 / 457.898 | 20 | 160 | 321.023 | 1323040 / 0 | 0 / 0 |
| 100K:10K | MATCHING | POLICY | 28.056 / 28.340 / 28.372 | 20 | 25020 | 27.415 | 273080 / 0 | 0 / 0 |
| 100K:10K | PREVIEW | PROBE | 1.287 / 1.331 / 1.415 | 20 | 160 | 0.879 | 3460 / 0 | 0 / 0 |
| 100K:10K | MATCHING | PROBE | 0.513 / 0.564 / 0.576 | 20 | 80 | 0.158 | 860 / 0 | 0 / 0 |

Plan estimates, actual rows, access paths, GiST state (target relations only):

| Scale | Query | Radius | `active_user_presence` | `user_account` | GiST | Sort / spill |
| --- | --- | --- | --- | --- | --- | --- |
| 10K:1K | PREVIEW | POLICY | Seq Scan; plan 1 actual 1000 loops 1 | Index Scan `user_account_pkey`; plan 1 actual 1 loops 1000 | NOT_USED | no Sort node; no spill |
| 10K:1K | MATCHING | POLICY | Seq Scan; plan 1 actual 125 loops 1 | Index Scan `user_account_pkey`; plan 1 actual 1 loops 125 | NOT_USED | quicksort/Memory 35 KB, 125 rows; no spill |
| 10K:1K | PREVIEW | PROBE | Index Scan GiST; plan 1 actual 2 loops 1 | Index Scan `user_account_pkey`; plan 1 actual 1 loops 2 | USED | quicksort/Memory 25 KB, 8 rows; no spill |
| 10K:1K | MATCHING | PROBE | Bitmap Heap Scan GiST; plan 1 actual 1 loops 1 | Index Scan `user_account_pkey`; plan 1 actual 1 loops 1 | USED | quicksort/Memory 25 KB, 1 row; no spill |
| 50K:5K | PREVIEW | POLICY | Seq Scan; plan 1 actual 5000 loops 1 | Index Scan `uq_user_account_id_role`; plan 1 actual 1 loops 5000 | NOT_USED | no Sort node; no spill |
| 50K:5K | MATCHING | POLICY | Seq Scan; plan 1 actual 625 loops 1 | Index Scan `uq_user_account_id_role`; plan 1 actual 1 loops 625 | NOT_USED | quicksort/Memory 78 KB, 625 rows; no spill |
| 50K:5K | PREVIEW | PROBE | Bitmap Heap Scan GiST; plan 1 actual 13 loops 1 | Index Scan `uq_user_account_id_role`; plan 1 actual 1 loops 13 | USED | no Sort node; no spill |
| 50K:5K | MATCHING | PROBE | Bitmap Heap Scan GiST; plan 1 actual 2 loops 1 | Index Scan `uq_user_account_id_role`; plan 1 actual 1 loops 2 | USED | quicksort/Memory 25 KB, 2 rows; no spill |
| 100K:10K | PREVIEW | POLICY | Seq Scan; plan 1 actual 10000 loops 1 | Index Scan `uq_user_account_id_role`; plan 1 actual 1 loops 10000 | NOT_USED | no Sort node; no spill |
| 100K:10K | MATCHING | POLICY | Seq Scan; plan 1 actual 1251 loops 1 | Index Scan `uq_user_account_id_role`; plan 1 actual 1 loops 1251 | NOT_USED | quicksort/Memory 156 KB, 1251 rows; no spill |
| 100K:10K | PREVIEW | PROBE | Bitmap Heap Scan GiST; plan 1 actual 25 loops 1 | Index Scan `uq_user_account_id_role`; plan 1 actual 1 loops 25 | USED | no Sort node; no spill |
| 100K:10K | MATCHING | PROBE | Bitmap Heap Scan GiST; plan 1 actual 4 loops 1 | Index Scan `uq_user_account_id_role`; plan 1 actual 1 loops 4 | USED | quicksort/Memory 25 KB, 4 rows; no spill |

No cardinality-driven transition off Seq Scan at the policy radius. Probe remains GiST USED at every scale. 10K PREVIEW probe used Index Scan on the GiST rather than Bitmap Heap Scan; that is still GiST USED, not a policy-radius index transition. Probe presence counts: 2 / 13 / 25.

### Fixed 100K:10K statistics comparison (PERF-006)

Client p50/p95/p99 and PostgreSQL calls, total/mean execution time, rows, shared/temp blocks:

| Query / radius | Target | calls | rows | mean_exec_ms | p50 / p95 / p99 (ms) | shared_hit | temp r/w |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PREVIEW / POLICY | 100 | 20 | 160 | 317.680 | 317.016 / 330.596 / 338.654 | 1734580 | 0 / 0 |
| PREVIEW / POLICY | 1000 | 20 | 160 | 319.205 | 318.117 / 332.886 / 342.294 | 1734580 | 0 / 0 |
| MATCHING / POLICY | 100 | 20 | 25120 | 28.191 | 28.529 / 30.855 / 35.666 | 410080 | 0 / 0 |
| MATCHING / POLICY | 1000 | 20 | 25120 | 28.560 | 28.624 / 30.681 / 30.762 | 410080 | 0 / 0 |
| PREVIEW / PROBE | 100 | 20 | 160 | 1.012 | 1.423 / 1.513 / 1.563 | 5280 | 0 / 0 |
| PREVIEW / PROBE | 1000 | 20 | 160 | 0.960 | 1.298 / 1.375 / 1.387 | 5280 | 0 / 0 |
| MATCHING / PROBE | 100 | 20 | 160 | 0.196 | 0.510 / 0.566 / 0.596 | 2380 | 0 / 0 |
| MATCHING / PROBE | 1000 | 20 | 160 | 0.190 | 0.494 / 0.523 / 0.528 | 2380 | 0 / 0 |

Plan estimates, actual rows, access paths, GiST, sort/spill — **identical** at 100 and 1000 for all four combinations (`access_path_changed=false`):

| Query / radius | Access path | plan/actual (`aup` / `ua`) | GiST | Sort / spill |
| --- | --- | --- | --- | --- |
| PREVIEW / POLICY | Seq Scan / Index Scan `uq_user_account_id_role` | 1/10008 / 1/1 loops 10008 | NOT_USED | no Sort; no spill |
| MATCHING / POLICY | Seq Scan / Index Scan `uq_user_account_id_role` | 1/1259 / 1/1 loops 1259 | NOT_USED | quicksort/Memory 166 KB, 1256 rows; no spill |
| PREVIEW / PROBE | Bitmap Heap Scan GiST / Index Scan `uq_user_account_id_role` | 1/32 / 1/1 loops 32 | USED | no Sort; no spill |
| MATCHING / PROBE | Bitmap Heap Scan GiST / Index Scan `uq_user_account_id_role` | 1/11 / 1/1 loops 11 | USED | quicksort/Memory 25 KB, 8 rows; no spill |

`plan rows` stayed 1 on both ANALYZEd relations under both targets. Shared hits and temp blocks did not change. Latency deltas are smaller than previously observed same-condition run-to-run noise.

## Guardrails

All asserted in **both** statistics conditions on the guardrail fixture. Tests passed; no `INVALID`.

- Preview count equality: `previewCounts` equal at policy and at 5 km. Policy total 10,005 (8 octant segments); probe total 29.
- Matching order/set equality: `matchingOrder` and `matchingSet` equal at both radii. Policy matching 1,256 candidates; probe 8.
- Persisted recipient equality: both runs `claimed=1 outcomes=1 processed=1 recipients=10 duplicate=0 missing=0 blocked=0 full_slot=0`. Ordered list and set equal.
- Duplicate, missing, block, status, expiry, fairness, and capacity checks:
  - duplicate = 0, missing = 0 in both conditions
  - blocked / inactive / expired logical rows absent from matching at both radii
  - distance filter: outside-window row present at policy, absent at 5 km
  - fairness matching prefix `[near, full-slot, old, recent]` in both conditions (inverted distances so fairness ≠ distance)
  - persisted recipients start with `[near, old, recent]`; full-slot excluded by receive-capacity
  - recipient count = 10 = `max-recipients-per-post`

## Interpretation

- Supported or rejected hypothesis: `REJECTED: statistics target 1000 did not materially change estimates/access path, while all guardrails remained equal.`
- Rejected alternatives:
  - `SUPPORTED` is not available: estimates and access paths on the ANALYZEd relations did not change.
  - `INVALID` is not available: every P0 guardrail passed and the only changed variable was statistics target.
  - Do not read this as “the planner is correct” or “the GiST is unused in production.” Policy-radius Seq Scan and `plan rows = 1` vs much larger actual rows are local synthetic observations, same shape as GH-163.
- Local-only applicability limits:
  - Throwaway Testcontainers, synthetic 10:1 disk, no fixed container CPU/memory
  - `ANALYZE` only on `user_account` and `active_user_presence`
  - PERF-006 fixture is not PERF-005
  - Client percentiles include JDBC; `pg_stat_statements` is DB time
  - “No sort” means no node named `Sort`; Incremental Sort would be invisible
  - Image is `linux/amd64` on an `arm64` host
  - 10K probe count 2 is an `ST_DWithin` boundary, not a data-formula change
- Follow-up decision requiring a separate Issue: any production statistics, query, index, schema, policy, or infrastructure change. This Issue makes no such recommendation.

## Verification

### Commands and results

| Command | Result | Tests / notes | Duration |
| --- | --- | --- | --- |
| `./harness test-run --id TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE` | PASS | unit 1,038 tests, 0 fail; `integrationTest` 720 tests, 0 fail. Does **not** run `performanceTest`. Scaffolded this report. | unit 21 s; integration 5 m 48 s |
| `./gradlew performanceTest --tests '*PgStatStatementsPerformanceIntegrationTest'` | PASS | PERF-001 | 8 s |
| `./gradlew performanceTest --tests '*DirectionMatchingPerformanceProbeIntegrationTest'` | PASS | PERF-010, 3 methods | 8 s |
| `./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest'` | PASS | PERF-003..009, 5 methods | 1 m 42 s |
| `./gradlew performanceTest` | PASS | 17 methods, 0 fail / 0 error (XML time 102.207 s). Published E3 numbers come from this run. Includes frozen GH-163 and GH-127 performance classes; those timings are not used as E3 evidence. | 2 m 26 s |
| `./harness check` | PASS | secret, JUnit, convention, commit-format, workflow, label, Husky, Java-convention self-test | <1 s |
| `./harness pr-ready --project-tests` | PASS | harness checks + `./gradlew check` (14 tasks, 2 executed / 12 up-to-date after the earlier fresh unit/integration run) + `git diff --check` | 5 s Gradle check; overall ~7 s |
| `npm run hooks:validate` | PASS | Husky validation | <1 s |
| `git diff --check` | PASS | working tree | <1 s |
| `git diff --name-only origin/main...HEAD` | PASS | no `src/main/java`, Flyway, Terraform, workflow, Compose, or production config | n/a |
| `git diff --check origin/main...HEAD` | PASS | no whitespace errors on the branch range | n/a |
| required `rg` sanitization scan over `docs/reports/tests` and `src/integrationTest/java/com/dnd/qello` | PASS | Matches are prohibition/warning text and this command table, not leaked query text, IDs, coordinates, tokens, or credentials. SQL in E3 test setup and the fixed Testcontainers password remain in source only. | n/a |

PERF-002: ordinary `integrationTest` excludes `@Tag("performance")` and does not set `qello.test.postgres.pg-stat-statements-enabled`. The harness `integrationTest` run (720 tests) is that evidence.

### Failed or blocked checks

- none

### Residual risks

- Host/planner/cache noise and unfixed Testcontainers resources can move latency without moving plans.
- A different PostGIS/GEOS build could flip the 10K `ST_DWithin` boundary count.
- `spotless` `ratchetFrom origin/main` can set `.git/config core.bare=true`. Restored to `false` after Gradle; `git config --get core.bare` was `false` before git commands.
- Cross-class leftover due matching events would fail PERF-008/009 rather than silently mix batches.
- Remote GitHub Actions were not re-run here.

## 5. Failures and diagnostics

No test failures. No diagnostics requiring source changes. The E3 class was not edited in this task.

## 6. Potential issues

### Application code

- Production candidate SQL, recipient selection, and direction policy were not changed. Guardrails show statistics target 1000 does not by itself alter logical preview/matching/recipient results on this fixture.

### Infrastructure and resource limits

- Testcontainers CPU/memory were not pinned. Do not promote latency deltas to capacity-planning numbers.

### Database and migrations

- Persistent `plan rows = 1` under-estimate on geography predicates was not moved by target 1000 on the two ANALYZEd relations. No migration or index change follows from that.

### Concurrency and idempotency

- Measurements are single-threaded 20-call loops. Matching uses the existing worker lease/idempotency path for one synthetic post. Worker concurrency is owned by earlier Issues.

### Transactions and event ordering

- PERF-008/009 use the existing worker transaction boundary. Run-scoped cleanup restores receive-state baseline between statistics conditions.

### External APIs

- None. No FCM/APNs/S3/AWS credentials.

### Failure recovery and reconciliation

- Cleanup is FK-safe per dedicated region; container discard isolates leftover rows. Assertion failure does not leave production data.

## 7. Regression and residual risk

Ordinary `integrationTest` remains independent of the performance preload flag. Frozen GH-163 `DirectionMatchingIndexPlanPerformanceIntegrationTest` still passes inside the full performance suite and was not modified. Residual risk is over-reading this local `REJECTED` result as a production statistics or index decision.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-214-TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE.md`
- Performance template: `templates/performance-experiment-report.md`
- Sanitized console capture: `docs/reports/tests/gh-214-TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-sanitized-console.txt`
- CI run: not executed in this task
- Related ADR: none
- PR: not created in this task

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (`./harness test-run` does not run `performanceTest`; remote Actions not run)
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 (production change remains a **separate** Issue; none opened here)
- [x] 실행 결과와 PR 설명이 일치함 (no PR in this task; TASK.md contract matches this report)
