# Test Report: TEST-PLAN-GH-124-INBOX-READ-SKIP-API

> Created at: `2026-08-16T15:26:00+09:00`
> GitHub Issue: `#124`
> Branch: `feat/gh-124-direction-inbox-api`
> Commit: working tree (not committed)

## 1. Executive summary

- Result: `PASS` for the approved local implementation and verification scope.
- Scope: application/persistence, Web/MockMvc, PostgreSQL/PostGIS integration,
  four transaction-concurrency races, OpenAPI generation, existing regressions,
  and the full local project-test gate.
- Unverified: remote CI/check status, PR/ruleset state, deployment/runtime
  behavior, and load/stress characteristics.
- Release recommendation: local PR readiness is PASS; commit, push, PR and
  remote checks remain separate approval gates.

## 2. Execution results

| Command / suite | Result | Tests | Evidence |
| --- | --- | ---: | --- |
| Targeted application/persistence unit suite | PASS | 10 | `build/test-results/test/TEST-com.dnd.qello.feed.service.InboxApplicationServiceTest.xml`, `TEST-com.dnd.qello.feed.InboxPersistenceBoundaryTest.xml` |
| Targeted Web contract/MockMvc suite | PASS | 10 | `build/test-results/test/TEST-com.dnd.qello.feed.web.InboxWebContractTest.xml`, `TEST-com.dnd.qello.feed.web.InboxApiMockMvcTest.xml` |
| #124 PostgreSQL/PostGIS API + concurrency suite | PASS | 16 | `build/test-results/integrationTest/TEST-com.dnd.qello.InboxApiIntegrationTest.xml`, `TEST-com.dnd.qello.InboxCommandConcurrencyIntegrationTest.xml` |
| Focused fan-out/inbox regression | PASS | 1 | `build/test-results/integrationTest/TEST-com.dnd.qello.RecipientNotificationFanOutWorkerIntegrationTest.xml` |
| OpenAPI specification integration | PASS | 9 | `build/test-results/integrationTest/TEST-com.dnd.qello.OpenApiSpecificationIntegrationTest.xml` |
| `./harness pr-ready --project-tests` | PASS | unit 455 + integration 365 | 0 failures, 0 errors, 0 skipped; `build/reports/tests/test/index.html`, `build/reports/tests/integrationTest/index.html` |
| `./harness check` / `npm run hooks:validate` / `git diff --check` | PASS | policy checks | no findings |

## 3. Coverage summary

- `INT-001`~`INT-012`: list/category/detail/status/expiry/ownership/account,
  bidirectional block, skip idempotency, slot/outbox invariants and exact grace
  boundary passed.
- `INT-013`~`INT-016`: duplicate skip, revert-vs-confirm,
  open/skip-vs-block and skip-vs-answer races passed with real transactions.
- OpenAPI contains the four inbox operations, security requirement, documented
  401/403/404/409 responses and privacy-safe response schemas. Generated
  artifact SHA-256: `aab40a1ecf9a54d09dcaa5655f49d22036e2b8631a3773bd29cb70ff800c3be7`.
- The existing fan-out regression expectation was reconciled to the approved
  bilateral active-block contract: active blocks hide both notification and
  inbox detail; released blocks allow both again.

## 4. Diagnostics and residual risk

- The first INT-015 run failed only because its fixture inserted the same
  `recipient_receive_state` primary key twice. The fixture was made idempotent
  with `ON CONFLICT DO UPDATE`; the complete 16-test target was rerun green.
- Testcontainers/PostGIS and Flyway initialized successfully in the final run.
- Concurrency evidence covers one execution per race, not throughput, pool
  exhaustion, repeated stress, or production lock-timeout behavior.
- #126 owns SKIP_PENDING confirmation sweep and slot release; #124 does not add
  a `SKIP_CONFIRMATION_DUE` Outbox event or migration.

## 5. Artifacts and approval gates

- Approved plan: `docs/test-plans/gh-124-TEST-PLAN-GH-124-INBOX-READ-SKIP-API.md`
- Detailed integration report: `.superpowers/sdd/implementation-plan/task-5-report.md`
- API-doc report: `.superpowers/sdd/implementation-plan/task-6-api-docs-report.md`
- Remote CI/PR: not run / not created.
- Commit and push: not performed; requires separate user approval.

## 6. Final verification contract

```text
status: PASS
issue_number: 124
task_id: TEST-PLAN-GH-124-INBOX-READ-SKIP-API
design_id: N/A
changed_files: implementation, tests, TASK.md, OpenAPI artifact and reports in working tree
executed_checks: targeted unit/Web; PostgreSQL/PostGIS integration and concurrency; OpenAPI; harness check; harness pr-ready --project-tests; hooks; diff check
passed_checks: all listed checks; unit 455/455; integration 365/365; targeted 46/46; OpenAPI 9/9
failed_checks: none in final rerun
blocked_checks: remote CI/PR/ruleset and deployment/runtime verification
assumptions: local Testcontainers/Docker is representative for persistence behavior
risks: no load/stress evidence; remote checks remain pending; #126 sweep is intentionally out of scope
required_human_decisions: approve commit/push/PR separately
```
