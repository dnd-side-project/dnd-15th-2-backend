# Test Report: TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES

> Created at: `2026-08-21T18:21:33+09:00`
> Updated at: `2026-08-21T20:47:14+09:00`
> GitHub Issue: `#178`
> Branch: `feat/gh-178-notification-preferences`
> Commit: review-fix follow-up working tree before commit; final PR-ready verification is recorded in this report

## 1. Executive summary

- Result: `PASS`
- Tested scope: Final V26 rerun of the complete approved unit, NotificationPreference integration, fan-out integration, OpenAPI, harness, hooks, diff, and `pr-ready` commands. Post-review targeted unit/MockMvc and Testcontainers integration suites were rerun after account-lock, IANA Zone ID, quiet validation, and test-fixture fixes. Earlier V25-era stale-contract failures remain recorded as historical diagnostics.
- Unverified scope: Push provider behavior, dispatch-time preference recheck, quiet suppression, and OS permissions remain excluded by the approved plan.
- Release recommendation: The approved local verification sequence and PR readiness checks passed. CI, push/provider behavior, `#179` dispatch-time recheck, `#180` quiet suppression, and client OS permission behavior remain separate scope.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는 기록하지 않았다.

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 25.0.3 LTS |
| Gradle | 8.14.3 |
| Node.js | v26.7.0 |
| npm | 11.19.0 |
| Database | Local PostgreSQL/Testcontainers; V26 migration and all four approved integration surfaces completed successfully |
| Test runner | JUnit 5 via Gradle |
| Host | macOS aarch64 |

## 3. Execution results

Two earlier runs failed on stale migration contracts before the migration was renumbered to V26. After the rebase and contract updates, the complete approved sequence was rerun. All targeted suites, local checks, and `pr-ready` passed.

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| Initial `./harness test-run --id TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES` | `FAIL` | 859 completed, 1 failed | 9s | `FlywayMigrationContractTest.migrationsMatchAcceptedContent` |
| Rerun `./harness test-run --id TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES` | `FAIL` | Unit phase successful; integration phase 617 completed, 1 failed | 5m 38s | `AccountPersistenceIntegrationTest` |
| Final V26 `./harness test-run --id TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES` | `PASS` | Full unit/integration harness tasks successful | 5m 25s | Harness output; generated harness report |
| Final V26 `./gradlew test --tests "com.dnd.qello.notification.*" --console=plain` | `PASS` | 147 notification-package tests, 0 failures | 2s | `build/test-results/test/TEST-com.dnd.qello.notification.*.xml` |
| Final V26 `./gradlew integrationTest --tests "com.dnd.qello.NotificationPreference*" --console=plain` | `PASS` | 9 tests across API, migration, persistence suites | 19s | Gradle exit 0; suite source scenario headers |
| Final V26 `./gradlew integrationTest --tests "com.dnd.qello.*NotificationFanOut*" --console=plain` | `PASS` | 44 tests across matching fan-out suites | 22s | Gradle exit 0; fan-out integration suite sources |
| Final V26 `./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --console=plain` | `PASS` | 10 tests, 0 failures | 9s | `build/test-results/integrationTest/TEST-com.dnd.qello.OpenApiSpecificationIntegrationTest.xml` |
| Final V26 `./harness check` | `PASS` | Secret/JUnit/convention/workflow/label/Husky checks passed; 1153/221 files inspected | <1s | Harness output |
| Final V26 `./harness pr-ready --project-tests` | `PASS` | Local PR readiness checks passed; full check task successful | 5m 33s | Harness output |
| Final `npm run hooks:validate` | `PASS` | Husky validation passed | <1s | npm output |
| Final `git diff --check` | `PASS` | No whitespace errors | <1s | Empty output, exit 0 |
| Post-review `./gradlew test --tests "com.dnd.qello.notification.domain.NotificationQuietHoursTest" --tests "com.dnd.qello.notification.service.NotificationPreferenceServiceTest" --tests "com.dnd.qello.notification.web.NotificationPreferenceApiMockMvcTest" --console=plain` | `PASS` | Quiet hours, preference service, and MockMvc review-fix scenarios passed | 4s | Gradle exit 0 |
| Post-review `./gradlew integrationTest --tests "com.dnd.qello.NotificationPreferencePersistenceIntegrationTest" --tests "com.dnd.qello.NotificationFanOutPersistenceIntegrationTest" --tests "com.dnd.qello.RecipientNotificationFanOutWorkerIntegrationTest" --tests "com.dnd.qello.ReportResolutionIntegrationTest" --console=plain` | `PASS` after environment retry | Preference persistence, fan-out persistence, recipient fan-out, and report fan-out suites passed | 23s | Initial Docker daemon absence was corrected by starting Docker Desktop; rerun exited 0 |
| Post-review `./harness pr-ready --project-tests` | `PASS` | Repository policy, JUnit policy, workflow, label, Husky, unit, integration, and Gradle `check` gates passed | 5m 45s | Harness output |

### Initial-run evidence

The command reached `:test` and reported:

```text
FlywayMigrationContractTest > V1은 승인된 독립 DDL과 V2는 승인된 delta와 동일하다 FAILED
...
Task :test 859 tests completed, 1 failed
Execution failed for task ':test'.
.../build/reports/tests/test/index.html
harness: command failed with exit code 1
```

The XML evidence identifies the failure as `com.dnd.qello.FlywayMigrationContractTest.migrationsMatchAcceptedContent` at `FlywayMigrationContractTest.java:53`. The actual sorted migration list contained the then-numbered V25 notification migration, while the test's exact expected list stopped at V24. This historical failure was corrected during the rebase that preserved the migration as V26.

### Rerun evidence

After the migration-name contract was fixed, the same harness command was rerun. The JVM unit phase completed successfully (`BUILD SUCCESSFUL in 8s`). The integration phase then reported:

```text
AccountPersistenceIntegrationTest > Flyway V1~V7 적용 후 Hibernate validate가 schema를 변경하지 않고 시작된다 FAILED
617 tests completed, 1 failed
Execution failed for task ':integrationTest'.
harness: command failed with exit code 1
```

The XML evidence identifies `com.dnd.qello.AccountPersistenceIntegrationTest` with `expected: 50 but was: 51` at `AccountPersistenceIntegrationTest.java:120`. The notification migration creates the additional `notification_user_setting` table, so this was another stale implementation/test-contract assertion, not an environment failure. The Testcontainers warning that the `amd64` PostGIS image runs on an `arm64` Docker server may slow execution, but it did not cause this assertion failure.

### Final-run evidence

After the rebase preserved the notification migration as V26 and the table-count contract was fixed to 51, the complete approved sequence was executed. The harness unit/integration tasks, targeted notification unit suite, NotificationPreference integration suite, fan-out integration suite, OpenAPI integration suite, `harness check`, `pr-ready`, `npm run hooks:validate`, and `git diff --check` all exited successfully:

```text
BUILD SUCCESSFUL in 5m 33s
Harness checks passed.
Local PR readiness checks passed.
```

## 4. Scenario results

The final targeted suites provide evidence for all planned P0 behavior. The two earlier stale-contract failures remain recorded as historical failures, not as final suite failures.

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-001 | `PASS` final / `FAIL` historical | `NotificationPreferenceMigrationIntegrationTest` | V26 enabled preservation, quiet removal, catalog constraints, and rerun scenarios passed in the final targeted suite. |
| QA-178-RERUN-001 | `PASS` final / `FAIL` historical | `AccountPersistenceIntegrationTest` | Table-count contract was corrected to 51; focused suite 15/15 and final verification passed. |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-001~018 | `PASS` | Notification unit, service, MockMvc, and fan-out unit suites | 147 notification-package tests passed; all P0 source scenarios are represented by the approved test classes. |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-001~013 | `PASS` | `NotificationPreference*` and `*NotificationFanOut*` integration surfaces | 9 preference and 44 fan-out integration tests passed. |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-012 | `PASS` | `OpenApiSpecificationIntegrationTest` | 10 tests passed; operation, enum, response, and error contract evidence present. |

## 5. Failures and diagnostics

- Historical failure type: implementation/test-contract mismatch; both stale contracts were corrected before the final run.
- Initial reproduction: Run `./harness test-run --id TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES`; failing assertion was `FlywayMigrationContractTest.java:53`, exact migration list assertion.
- Rerun reproduction: Run the same command after adding the notification migration to the expected list; failing assertion was `AccountPersistenceIntegrationTest.java:120`, `expected: 50 but was: 51`.
- Final status: no implementation or environment failures occurred in the V26 final sequence; `./harness pr-ready --project-tests` passed.
- Post-review status: targeted unit/MockMvc and integration suites passed after fixing review findings. The first post-review integration attempt failed before tests ran because Docker was not running; Docker Desktop was started and the same command then passed.
- Production and test code changed during post-review verification to address the reviewed account-lock, IANA Zone ID, quiet validation, batch update, and coverage issues.
- `omo ulw-loop status --json` could not provide an attempt directory because the installed OMO runtime was missing. This did not cause the Gradle failure; the report is the available evidence artifact.

## 6. Potential issues

### Application code

- The migration-name and application table-count contracts were reconciled; all final test suites pass. No application failure remains in this verification.

### Infrastructure and resource limits

- No infrastructure/resource failure caused the failure. PostgreSQL/Testcontainers did run; an amd64 PostGIS image on arm64 generated a performance warning and remains an execution-time risk.

### Database and migrations

- V26 catalog shape, `enabled` preservation, quiet-column removal, constraints, rerun safety, and preference SQL regression passed in the final targeted integration suites.

### Concurrency and idempotency

- PUT row-lock serialization, complete-snapshot behavior, retry idempotency, and fan-out dedup passed in the final targeted suites.

### Transactions and event ordering

- Rollback behavior and `notification`-before-delivery ordering passed in the final targeted suites. The approved plan requires `notification` to remain even when delivery creation is gated.

### External APIs

- Push provider calls, token registration, scheduler behavior, and OS notification permissions are outside #178. They remain owned by `#179`/`#182` and the client implementation.

### Failure recovery and reconciliation

- Existing `PENDING`/`FAILED` deliveries were not deleted or cancelled by this run, but their later dispatch-time preference recheck remains unverified and belongs to `#179`.

## 7. Regression and residual risk

- `#179`: the sender must re-check the latest preference immediately before dispatch; this report contains no evidence for that behavior.
- `#180`: actual quiet suppression, overnight/time-zone calculation at dispatch, batching, and daily caps remain unverified and excluded from #178.
- Existing `PENDING`/`FAILED` deliveries: #178 must not retroactively cancel them; reconciliation and dispatch policy remain pending.
- OS permissions: server-side preferences cannot prove or override client OS notification permission; end-to-end user-visible push remains unverified.
- All approved local commands, including `./harness pr-ready --project-tests`, have final evidence-backed results.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-178-TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES.md`
- Test report: `docs/test-reports/gh-178-TEST-REPORT-GH-178-NOTIFICATION-PREFERENCES.md`
- Gradle XML evidence: `build/test-results/test/TEST-com.dnd.qello.notification.*.xml`, `build/test-results/integrationTest/TEST-com.dnd.qello.OpenApiSpecificationIntegrationTest.xml`
- HTML test report: `docs/reports/tests/gh-178-TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES.md`
- CI run: Not run
- Related ADR: None supplied by the approved plan
- PR: `#187`

## 9. Manual QA matrix

### surfaceEvidence

| Scenario ID | Criterion reference | Surface | Exact invocation | Verdict | ArtifactRefs |
| --- | --- | --- | --- | --- | --- |
| QA-178-001 | Task 7 / approved plan execution gate | Local Gradle/JUnit test runner | Final `./harness test-run --id TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES` | `PASS` | `ART-178-001` |
| QA-178-002 | UNIT-001~018 | Gradle unit surface | `./gradlew test --tests "com.dnd.qello.notification.*" --console=plain` | `PASS` | `ART-178-004` |
| QA-178-003 | INT-001~013 | PostgreSQL/Testcontainers integration surfaces | Approved NotificationPreference and fan-out integration invocations | `PASS` | `ART-178-005` |
| QA-178-004 | INT-012 | OpenAPI integration surface | `./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --console=plain` | `PASS` | `ART-178-006` |
| QA-178-005 | Repository completion gates | Harness/hooks/diff surfaces | `./harness check`; `./harness pr-ready --project-tests`; `npm run hooks:validate`; `git diff --check` | `PASS` | `ART-178-007` |

### adversarialCases

| Scenario ID | Criterion reference | Adversarial class | Expected behavior | Verdict | ArtifactRefs |
| --- | --- | --- | --- | --- | --- |
| ADV-178-001 | AGENTS.md verification contract | Implementation failure must stop verification; no code patch by verifier | Verification stops on implementation failure | `PASS` | `ART-178-001` |
| ADV-178-002 | INT-001 | Migration/version contract must include V26 | V26 is present and historical stale contract is fixed | `PASS` final | `ART-178-001` |
| ADV-178-007 | Rerun integration regression | Application table-count contract must include V26's table | Table-count contract includes `notification_user_setting` | `PASS` final | `ART-178-001` |
| ADV-178-003 | INT-007/INT-011 | Transaction rollback and concurrent PUT | Rollback preserves the prior snapshot and concurrent PUT leaves a complete snapshot | `PASS` | `ART-178-005` |
| ADV-178-004 | INT-008/INT-009 | Global/type OFF must preserve notification and suppress only new delivery | `notification` remains and new `notification_delivery` is suppressed | `PASS` | `ART-178-005` |
| ADV-178-005 | Approved exclusions / residual risk | Existing PENDING/FAILED delivery dispatch-time recheck | #178 excludes dispatch-time recheck; #179 owns it | `not_applicable` | `ART-178-001` |
| ADV-178-006 | Approved exclusions / residual risk | OS permission versus server preference mismatch | Client/OS permission behavior is outside #178 | `not_applicable` | `ART-178-001` |

## 10. Artifact references

| ID | Kind | Description | Path |
| --- | --- | --- | --- |
| ART-178-001 | test-report | This report, including exact command, failure, scenario verdicts, and residual risks | `docs/test-reports/gh-178-TEST-REPORT-GH-178-NOTIFICATION-PREFERENCES.md` |
| ART-178-002 | junit-xml | Current final Flyway contract suite result; historical failure details are retained in this report | `build/test-results/test/TEST-com.dnd.qello.FlywayMigrationContractTest.xml` |
| ART-178-003 | html-report | Final V26 harness test report | `build/reports/tests/integrationTest/index.html` |
| ART-178-004 | junit-xml | Final notification unit suites; 147 tests, 0 failures | `build/test-results/test/TEST-com.dnd.qello.notification.*.xml` |
| ART-178-005 | integration-test-output | Final NotificationPreference (9) and fan-out (44) integration commands, exit 0 | `build/reports/tests/integrationTest/index.html` |
| ART-178-006 | junit-xml | Final OpenAPI suite; 10 tests, 0 failures | `build/test-results/integrationTest/TEST-com.dnd.qello.OpenApiSpecificationIntegrationTest.xml` |
| ART-178-007 | command-output | Final harness/check, pr-ready, hooks, and diff results | `docs/test-reports/gh-178-TEST-REPORT-GH-178-NOTIFICATION-PREFERENCES.md` |

## 11. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함 — PR은 아직 생성되지 않음
