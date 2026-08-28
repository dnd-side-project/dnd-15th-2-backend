# Test Report: TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT

> Created at: `2026-08-28T16:44:43+09:00`
> Revised at: `2026-08-28T17:04:00+09:00`
> GitHub Issue: `#163`
> Branch: `perf/gh-163-candidate-index-remeasurement`
> Task ID: `GH-163-CANDIDATE-INDEX-REMEASUREMENT`
> Commit measured: `c62f7620bf3010da6b7ac6cdc1f08e5f5cb957a2`

## 1. Executive summary

- Result: `PASS`
- Tested scope: Task 1's isolated PostgreSQL/PostGIS performance integration test, its deterministic 100,000-account/10,000-presence fixture, and current preview and matching candidate SQL at the actual `GLOBAL / 0..20,100km` policy baseline and diagnostic 5km selectivity probe.
- Unverified scope: production data distributions, production cache and statistics, production load, and any future query rewrite. No production behavior was changed or load-tested.
- Release recommendation: no release-affecting source change is proposed. The partial GiST is conditionally useful at the diagnostic radius, but the current policy baseline did not use it; a production query rewrite is `NOT_RECOMMENDED` from this task alone.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는 기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Eclipse Temurin OpenJDK 24.0.2 |
| Spring Boot | 3.5.16 |
| Database | isolated local Testcontainers PostgreSQL/PostGIS database |
| Test runner | Gradle performanceTest and JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew performanceTest --tests '*DirectionMatchingIndexPlanPerformanceIntegrationTest'` | PASS | 5 JUnit methods (PERF-001 through PERF-005) | 26s | Original Task 2 focused measurement; class-level `@Tag("performance")` plus this command is PERF-006 focused evidence. |
| `./gradlew performanceTest --tests '*DirectionMatchingIndexPlanPerformanceIntegrationTest'` (plan-rows revision) | PASS | 5 JUnit methods (PERF-001 through PERF-005) | 28s | Fresh focused re-run confirmed `plan rows=1` for both target relations in all four observations. Access paths, actual rows, loops, filter counts, buffers, and GiST outcomes matched the earlier full-suite evidence. |
| `./gradlew performanceTest` | PASS | Full tagged performance suite | 37s | Fresh full performance run completed; this is PERF-006 full-suite evidence and supplied the four observations below. |
| `./harness test-run --id TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT` | PASS | Configured unit and non-performance integration regression suites | 5m 44s | Harness completed test and integration tasks successfully and scaffolded this report. |
| `./harness check` | PASS | Repository policy checks | 0.5s | Secret, JUnit policy, convention, commit-format, workflow, label, and Husky validations passed. |
| `./harness pr-ready --project-tests` | PASS | Project test/readiness checks | 1.5s | Gradle `check` and harness checks passed. The optional local-main fast-forward helper could not update `main` because another checkout owns it; it left this branch unchanged. |
| `npm run hooks:validate` | PASS | Husky validation | 0.1s | Husky validation passed. |
| `git diff --check` | PASS | Working-tree whitespace validation | 0.0s | No whitespace errors after the Task 2 report and task-contract edits. |

All durations are observational only; no timing is a pass/fail threshold.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-001` | PASS | `DirectionMatchingIndexPlanPerformanceIntegrationTest#seedsApprovedCandidateDataShape` | Verified 100,000 synthetic accounts, 10,000 valid presences, 10,000 policy-baseline candidates, 25 probe candidates, and therefore 9,975 probe-external presences. |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-002` | PASS | `#explainsPreviewAtPolicyBaseline` | Plan parsed and included both required relations. |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-003` | PASS | `#explainsMatchingAtPolicyBaseline` | Plan parsed and included both required relations. |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-004` | PASS | `#explainsPreviewAtSelectivityProbe` | Plan parsed and included both required relations. |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-005` | PASS | `#explainsMatchingAtSelectivityProbe` | Plan parsed, included both required relations, and the selected sector produced at least one candidate. |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-006` | PASS | Class `@Tag("performance")` and focused/full Gradle commands | It is command/tag evidence, not a sixth Java method. |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-REPORT-001` | PASS | This report | Records the fresh sanitized evidence and result interpretation. |

### Fresh sanitized plan observations

Fixture preconditions passed before every observation: 100,000 synthetic accounts, 10,000 valid presences, 10,000 inside `POLICY_BASELINE`, and 25 inside `SELECTIVITY_PROBE`. The following is the complete allowed plan summary: relation access path, index name, plan rows, actual rows/loops, filtered rows, buffer summary, and timings. It intentionally excludes raw EXPLAIN JSON, identifiers, coordinates, credentials, URLs, and server details. Access paths, actual rows, loops, filter counts, buffers, timings, and GiST outcomes are the original full-suite observations. Plan rows were added from a later focused re-run that reproduced the same non-timing fields.

| Query | Radius | `active_user_presence` evidence | `user_account` evidence | Partial GiST | Timing observation |
| --- | --- | --- | --- | --- | --- |
| Preview | `POLICY_BASELINE` | Seq Scan; plan 1, actual 10,000, loops 1, filtered 0, hit/read 143/0 | Index Scan `uq_user_account_id_role`; plan 1, actual 1, loops 10,000, filtered 0, hit/read 30,000/0 | `NOT_USED` | planning 1.166ms; execution 299.718ms |
| Matching | `POLICY_BASELINE` | Seq Scan; plan 1, actual 1,251, loops 1, filtered 8,749, hit/read 143/0 | Index Scan `uq_user_account_id_role`; plan 1, actual 1, loops 1,251, filtered 0, hit/read 3,753/0 | `NOT_USED` | planning 1.662ms; execution 28.767ms |
| Preview | `SELECTIVITY_PROBE` | Bitmap Heap Scan via `active_user_presence_position_gix`; plan 1, actual 25, loops 1, filtered 21, hit/read 8/0 | Index Scan `uq_user_account_id_role`; plan 1, actual 1, loops 25, filtered 0, hit/read 75/0 | `USED` | planning 4.111ms; execution 3.887ms |
| Matching | `SELECTIVITY_PROBE` | Bitmap Heap Scan via `active_user_presence_position_gix`; plan 1, actual 4, loops 1, filtered 42, hit/read 8/0 | Index Scan `uq_user_account_id_role`; plan 1, actual 1, loops 4, filtered 0, hit/read 12/0 | `USED` | planning 0.967ms; execution 0.211ms |

### Result interpretation

Both SQL shapes independently have `NOT_USED` at `POLICY_BASELINE` and `USED` at `SELECTIVITY_PROBE`. The partial GiST is therefore conditionally useful when the distance predicate is selective. This does **not** satisfy #127's operating-default claim, because the actual GLOBAL/20,100km baseline did not use the index for either query. The 5km result is only a diagnostic and is not a policy recommendation.

Planner `plan rows` were 1 for every target-relation node after `ANALYZE`, while actual rows were much higher (10,000 and 1,251 at the policy baseline; 25 and 4 at the probe). That estimate gap is recorded as a geography-selectivity observation, not as an ANALYZE failure and not as a reason to rewrite production SQL.

The required query-rewrite decision is `NOT_RECOMMENDED`: probe use already demonstrates that the existing query can use the partial GiST under a selective distance predicate, while baseline non-use alone is not a reason to rewrite production SQL. No query, index, migration, production source, or follow-up Issue was created by this task.

## 5. Failures and diagnostics

No required command failed and no required command is blocked. `./harness pr-ready --project-tests` reported that its optional local-main fast-forward helper could not fetch into a `main` branch checked out by another worktree. The helper left `main` and this branch unchanged; all required readiness checks completed successfully. This is a shared-worktree environment notice, not a test or implementation failure.

## 6. Potential issues

### Application code

No application source changed. The evidence characterizes only the two current SQL shapes and does not establish behavior or performance of unimplemented rewrites.

### Infrastructure and resource limits

The 100,000-account fixture raises local container CPU, memory, and elapsed-time cost. It is scoped to the `performance` tag and isolated Testcontainers database; production infrastructure and deployment were not touched.

### Database and migrations

No schema, migration, or index definition changed. PostgreSQL plans can differ with production statistics, data distribution, PostgreSQL/PostGIS version, and cache state. Timings remain observations rather than service objectives. After `ANALYZE`, planner `plan rows` stayed at 1 for every target-relation node while actual rows were much higher; geography `ST_DWithin` selectivity estimates therefore remain a residual measurement limit.

### Concurrency and idempotency

Seeding and plan observation are single-threaded. Concurrent presence updates, claims, idempotency, and lock behavior are outside this controlled measurement.

### Transactions and event ordering

The scenarios inspect read-query plans only. Isolation behavior, rollback, and asynchronous event ordering are not revalidated here and remain covered by their dedicated suites.

### External APIs

No external APIs, cloud services, credentials, or production data are invoked. Their timeout, retry, and failure behavior is intentionally outside scope.

### Failure recovery and reconciliation

Fixture cleanup deletes presence records before accounts and the dedicated region. If a local test JVM is forcibly stopped, Testcontainers disposal removes the isolated database; no shared or production reconciliation is required.

## 7. Regression and residual risk

The focused and full performance suites, unit/integration regression harness, repository checks, readiness checks, Husky validation, and whitespace validation passed. A review of the Issue branch changes through the measured commit found only test-plan/task metadata, `.gitignore`, and the Task 1 integration-test class; no production source, SQL, migration, or index definition was changed. Residual risk is limited to the difference between the controlled synthetic fixture and real operating data and workload. Any policy, SQL, index, or follow-up work requires separate human review.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-163-TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT.md`
- Task contract: `TASK.md`
- CI run: not run locally; no CI URL is recorded.
- Related ADR: none.
- PR: not created by this task.

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 없음; 실행 범위 밖의 검증은 명시됨
- [x] 잠재 문제와 후속 변경은 별도 사람 승인 대상임; 후속 GitHub Issue는 생성하지 않음
- [x] 실행 결과와 이 보고서의 설명이 일치함
