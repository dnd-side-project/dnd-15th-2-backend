# Test Report: TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION

> Created at: `2026-08-03T21:06:12+09:00`
> GitHub Issue: `#40`
> Branch: `feat/gh-40-answer-safety-notification`
> Commit: `7ee225a`

## 1. Executive summary

- Result: `PASS`
- Tested scope: Answer JPA CRUD, Answer state invariants, media attachment ownership,
  block/report constraints, Notification/Outbox/Delivery JDBC persistence, same-transaction
  Answer + outbox, sequential and concurrent outbox claim, Flyway/Hibernate validation
- Unverified scope: external push provider behavior, API/controller authorization wiring,
  production PostgreSQL/RDS privileges, retention and retry-duration policy values
- Release recommendation: Issue #40 persistence slice is ready for user review. Do not treat
  this as completion of the product API/UI or provider-delivery features.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 21.0.12 |
| Spring Boot | 3.5.16 |
| Database | PostgreSQL 16 / PostGIS 3.5 Testcontainers |
| Test runner | JUnit 5, Gradle `test` + `integrationTest` |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` | PASS | Existing suite + 8 #40 unit scenarios | Gradle check | `build/test-results/test` |
| `./gradlew integrationTest --tests com.dnd.qello.AnswerSafetyNotificationPersistenceIntegrationTest` | PASS | 9 #40 integration scenarios | 10s | Testcontainers PostgreSQL/PostGIS |
| `./gradlew check --no-daemon` | PASS | Full unit + integration regression | 3s cached run | `BUILD SUCCESSFUL` |
| `./harness check` | PASS | 245 text files, 19 JUnit files | — | Harness output |
| `./harness pr-ready --project-tests` | PASS | Gradle check + repository gates | <1s cached run | Local PR readiness |
| `npm run hooks:validate` | PASS | Husky policy | — | Husky validation |
| `git diff --check` | PASS | Working tree whitespace | — | No output |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-UNIT-001~002 | PASS | `AnswerPersistenceBoundaryTest` | scalar FK values, bearing, safety/publish/delete transition, idempotency contract |
| TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-UNIT-003~006 | PASS | `SafetyNotificationBoundaryTest` | block/report target, outbox JSON/status, notification/delivery state |
| TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-INT-001 | PASS | `reproducesAnswerSafetyNotificationSchema` | 9 tables and dispatch indexes exist after Flyway V1 |
| TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-INT-002 | PASS | `persistsAnswerAndRejectsWrongAuthor` | Answer JPA round-trip and composite recipient-author FK |
| TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-INT-003 | PASS | `mediaAttachmentIsOwnedByOneContent` | same `media_id` cannot be attached twice |
| TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-INT-004~005 | PASS | `persistsSafetyAndDeduplicatesOpenReport` | report partial unique index and self-block validation |
| TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-INT-006 | PASS | `publishesAnswerAndWritesOutboxAtomically`, `rollsBackAnswerWhenOutboxInsertFails` | same transaction commit/rollback |
| TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-INT-007~009 | PASS | `claimsOutboxOnlyOnce`, `claimsOutboxOnlyOnceUnderConcurrency`, `persistsNotificationAndDeliveryDedup` | conditional claim, concurrent winner, notification/device dedup |
| TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-INT-010 | PASS | full `check` regression | existing Account/Question/Direction integration remains green |

## 5. Failures and diagnostics

초기 통합 fixture에서 `Instant`를 `JdbcTemplate`에 직접 전달해 PostgreSQL timestamp
타입을 추론하지 못하는 문제가 있었고, `Timestamp` 변환으로 수정했다. 이후 전체
통합 테스트가 통과했다. 운영 자격 증명이나 실제 provider 오류는 실행하지 않았다.

## 6. Potential issues

### Application code

- API/controller가 아직 없으므로 실제 요청자 권한 검증은 service/DB port 수준에
  한정되어 있다.
- Safety review는 persistence transaction 경계까지 제공하며, 운영자 workflow와
  moderation provider 연결은 후속 기능 범위다.

### Infrastructure and resource limits

- Testcontainers PostgreSQL/PostGIS 환경만 검증했다. 운영 RDS의 extension 권한과
  connection pool 설정은 별도 운영 검토가 필요하다.

### Database and migrations

- 적용된 V1 migration은 수정하지 않았다. 보관·삭제 정책과 retry 기간은 미정으로
  남겼다.
- V1에 정의된 제약을 사용했으며, 새 product policy 상수는 추가하지 않았다.

### Concurrency and idempotency

- Outbox 조건부 claim은 두 worker 동시 실행에서 한 worker만 성공하는 것을 검증했다.
- delivery worker의 실제 외부 응답 순서와 provider 재시도는 외부 API 제외 범위라
  미검증이다.

### Transactions and event ordering

- Answer JPA 저장과 `ANSWER_PUBLISHED` outbox insert의 commit/rollback 경계를
  검증했다.
- 실제 event publisher/worker의 commit 이후 전달 순서는 후속 작업에서 검증한다.

### External APIs

- 외부 API 호출은 하지 않았다. push provider integration은 의도적으로 제외했다.

### Failure recovery and reconciliation

- 중복 outbox insert rollback, duplicate report/device delivery, stale claim 거절을
  검증했다.
- 운영 dead-letter 재처리 runbook과 데이터 대사 job은 아직 없다.

## 7. Regression and residual risk

- 영속성 계층은 검토 가능한 상태지만 API/UI가 없으므로 사용자 여정 완료를 의미하지
  않는다.
- token 암호화 값의 생성·복호화 책임은 provider/secret 관리 후속 작업에서 확정해야
  한다. 테스트에는 실제 secret을 기록하지 않았다.
- 운영 정책이 확정되면 retention, retry/backoff, notification preference 적용 규칙을
  별도 Issue로 추가하고 현재 persistence 계약과 충돌하지 않는지 재검토해야 한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-40-ANSWER-SAFETY-NOTIFICATION.md`
- Test report: `docs/reports/tests/gh-40-ANSWER-SAFETY-NOTIFICATION.md`
- Related ADR: `docs/adr/0001-database-schema-ownership.md`, `docs/adr/0002-jpa-jdbc-boundary.md`
- PR: 생성하지 않음; origin push 전 사용자 검토 대기

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 범위가 명시됨
- [x] 잠재 문제와 후속 범위가 분리됨
- [x] 실행 결과와 현재 로컬 커밋이 일치함
