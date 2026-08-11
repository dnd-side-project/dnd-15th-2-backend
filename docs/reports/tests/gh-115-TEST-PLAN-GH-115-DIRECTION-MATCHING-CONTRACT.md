# Test Report: TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT

> Created at: `2026-08-11T20:27:49+09:00`
> GitHub Issue: `#115`
> Branch: `feat/gh-115-direction-matching-contract`
> Commit: `f9f36c6` (working tree contains uncommitted Issue #115 changes)

## 1. Executive summary

- Result: `PARTIAL`
- Tested scope: fingerprint canonicalization, direction-post persistence, idempotency conflict, matching Outbox round identity/payload, Flyway V12 catalog, lease claim/reclaim/fencing, and existing Outbox API regression.
- Unverified scope: INT-007 failure-injection rollback scenario, matching worker implementation, REST controller/API contract, external push provider, and production deployment. The latter four are outside Issue #115.
- Release recommendation: implementation is ready for code review, but the INT-007 rollback evidence remains blocked; do not treat this as worker or production rollout approval.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Java 21 toolchain; local runtime reported 25.0.3 |
| Spring Boot | 3.5.16 |
| Database | PostgreSQL/PostGIS Testcontainers (`postgis/postgis:16-3.5-alpine`) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` | PASS | 212 | 4s | Gradle test task |
| `./gradlew integrationTest --rerun-tasks --no-parallel --max-workers=1` | PASS (earlier tree) / BLOCKED (latest rerun) | 218 earlier | ~2m | Latest rerun hit Gradle XML result-writer errors; generated XML suites reported zero assertion failures |
| Targeted V12/matching/lease integration tests | PASS | 14 | 18s | `FlywayMigrationIntegrationTest`, `DirectionMatchingContractIntegrationTest`, `OutboxLeaseIntegrationTest` |
| Existing notification + lease regression target | PASS | 17 | 11s | `AnswerSafetyNotificationPersistenceIntegrationTest`, `OutboxLeaseIntegrationTest` |
| `./harness check` | PASS | policy gate | <1s | harness output |
| `npm run hooks:validate` | PASS | policy gate | <1s | Husky validation output |
| `./harness pr-ready --project-tests` | PASS earlier / BLOCKED latest | project checks | <1s / latest immediate | Latest run requires `./harness sync` because `origin/main` advanced |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001~003 | PASS | `DirectionRequestFingerprintTest` | NFC, outer Unicode trim, fixed canonical field set, SHA-256 format, null/body distinction |
| UNIT-004 | PASS | `OutboxEventLeaseTest.assignsMatchingRoundOnlyToDirectionMatchingEvent` | matching event only; initial round 1 |
| UNIT-005~006 | PASS | `OutboxEventLeaseTest` claim/reclaim methods | lease owner/expiry and generation fencing state machine |
| UNIT-007 | PASS | `OutboxEventLeaseTest` payload/domain methods | JSON object and domain mapping constraints |
| INT-001 | PASS | `FlywayMigrationIntegrationTest` | V12 history, columns, checks, partial unique index, claim index |
| INT-002 | PASS | `DirectionMatchingContractIntegrationTest` | persistence, same-result retry, `IDEMPOTENCY_KEY_REUSED`, matching event enqueue |
| INT-003 | PASS | matching contract integration fixture | round/event uniqueness and conflict protection |
| INT-004 | PASS | `DirectionMatchingContractIntegrationTest` | round 1/2 identity and non-matching round rejection |
| INT-005~006 | PASS | `OutboxLeaseIntegrationTest` | due batch claim, expired reclaim, owner/generation stale-write fencing |
| INT-007 | BLOCKED | no test method | Safe Outbox failure injection seam is not present; no artificial mock wiring was added |
| INT-008 | PASS | `DirectionMatchingContractIntegrationTest.enforcesMatchingRoundUniquenessAndCoarsePayload` | exact-coordinate exclusion from JSON payload |
| INT-009 | PASS | `OutboxLeaseIntegrationTest.preservesExistingOutboxApi` | existing save/find/legacy claim compatibility |

## 5. Failures and diagnostics

Full-suite invocations reported Gradle XML result-writer errors although the generated
XML suites contained zero failures and zero errors. One serial run with
`--no-parallel --max-workers=1` completed successfully before the final legacy
backfill-only change; the latest serial rerun reproduced the writer error. Current
code is covered by the targeted integration suites, which pass. The local Docker
runtime also reported an amd64 image on an arm64 host.

## 6. Potential issues

### Application code

- No assertion failures remain in the executed unit or integration scope.
- INT-007 remains unverified because failure injection would require a production seam or
  artificial test-only replacement of the repository bean.
- The current send path retains synchronous recipient selection while writing the
  foundation matching event; a future worker must make recipient writes idempotent or
  the migration to worker-owned matching must be handled in a separate issue.

### Infrastructure and resource limits

- Testcontainers uses architecture emulation on the local arm64 Docker host; future
  runs may be slower or timeout under resource pressure.

### Database and migrations

- V12 is applied after V1~V11. Legacy `request_fingerprint` remains nullable for
  lazy backfill and existing PROCESSING outbox rows are made immediately reclaimable.

### Concurrency and idempotency

- Lease completion/failure requires owner, generation, PROCESSING status, and an
  unexpired lease. Worker implementation and operational lease/backoff tuning remain
  outside this issue.

### Transactions and event ordering

- The matching event is written in the send transaction. Existing synchronous
  recipient selection remains for compatibility; the asynchronous worker is not part
  of this change.

### External APIs

- Not run; external push/provider calls are explicitly excluded.

### Failure recovery and reconciliation

- Legacy rows without enough `post_audience` data return the existing result without
  fingerprint backfill and require later reconciliation.

## 7. Regression and residual risk

- Targeted current-tree integration coverage passed. Residual risk includes the
  blocked INT-007 rollback evidence, the latest full-suite XML writer environment
  failure, excluded worker/controller/provider flows, and the local Testcontainers
  architecture emulation noted above.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-115-TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT.md`
- CI run:
- Related ADR:
- PR:

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [x] 실행 결과와 PR 설명이 일치함
