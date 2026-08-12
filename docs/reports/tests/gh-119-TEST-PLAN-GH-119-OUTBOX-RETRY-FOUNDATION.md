# Test Report: TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION

> Created at: `2026-08-13T01:51:52+09:00`
> GitHub Issue: `#119`
> Branch: `feat/gh-119-outbox-retry-foundation`
> Commit: `eca0172` (working tree contains uncommitted Issue #119 changes)

## 1. Executive summary

- Result: `PASS`
- Tested scope: event type별 Outbox batch claim, `SKIP LOCKED` 경쟁, lease 만료 회수,
  owner/generation fencing, retryable/permanent 및 최대 시도 DEAD 전이, 잘못된 입력,
  기존 matching payload/dedup/round와 notification Outbox 회귀, repository validation의
  `NotificationException` 변환.
- Unverified scope: 실제 scheduler/polling worker, #120 매칭 handler, #123 인앱 알림
  fan-out, 외부 Push provider, 운영 retry 기본값, #127 성능 `EXPLAIN ANALYZE`.
- Release recommendation: 승인된 #119 구현 범위는 코드 리뷰로 넘길 수 있다. 운영
  배포나 실제 worker 실행 승인으로 해석하지 않는다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Gradle Java 21 toolchain; local runtime Temurin 25.0.3 |
| Spring Boot | 3.5.16 |
| Database | PostgreSQL 16 + PostGIS 3.5 Testcontainers (`postgis/postgis:16-3.5-alpine`) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` | PASS | 265 | 4s | 전체 단위 테스트; assertion failures 0 |
| `./gradlew integrationTest --tests com.dnd.qello.OutboxLeaseIntegrationTest` | PASS | 9 | ~7s | Targeted PostgreSQL lease/type/retry suite |
| `./gradlew integrationTest --rerun-tasks --no-parallel --max-workers=1` | PASS | 242 | 2m 7s | Full PostgreSQL/PostGIS integration suite; assertion failures 0 |
| `./harness test-run --id TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION` | NOT RERUN | — | — | 후속 예외 경계 수정 후 Gradle 전체 테스트와 `pr-ready --project-tests`로 재검증 |
| `./harness check` | PASS | policy gates | <1s | secret, JUnit, convention, workflow, label, Husky checks |
| `npm run hooks:validate` | PASS | policy gate | <1s | Husky validation |
| `git diff --check` | PASS | whitespace check | <1s | no output |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `OutboxRetryPolicyTest.retryableFailureSchedulesNextAttempt` | attempt별 backoff 후 FAILED |
| UNIT-002 | PASS | `OutboxRetryPolicyTest.retryableFailureAtMaximumBecomesDead` | max attempt 경계에서 DEAD |
| UNIT-003 | PASS | `OutboxRetryPolicyTest.permanentFailureBecomesDeadImmediately` | 남은 횟수와 무관하게 DEAD |
| UNIT-004 | PASS | `OutboxRetryPolicyTest.rejectsNonProcessingEvent` | PROCESSING 외 상태 거절 |
| UNIT-005 | PASS | `OutboxRetryPolicyTest.rejectsInvalidPolicyValues` | 잘못된 정책·backoff·overflow 거절 |
| UNIT-006 | PASS | `OutboxLeaseIntegrationTest.rejectsInvalidBatchClaimInputs` | repository 인자 오류가 `NotificationException`으로 변환됨 |
| INT-001 | PASS | `OutboxLeaseIntegrationTest.claimsOnlyRequestedEventTypes` | 요청 event type만 상태 변경 |
| INT-002 | PASS | `OutboxLeaseIntegrationTest.claimsBatchWithoutOverlapUnderConcurrency` | 두 worker 반환 ID 교집합 없음 |
| INT-003 | PASS | `OutboxLeaseIntegrationTest.claimsOnlyDueRows` | due·lease·terminal 조건 및 limit 회귀 |
| INT-004 | PASS | `OutboxLeaseIntegrationTest.appliesRetryPolicyToFailedAndDeadEvents` | backoff 전 제외·후 재claim |
| INT-005 | PASS | `OutboxLeaseIntegrationTest.appliesRetryPolicyToFailedAndDeadEvents` | permanent DEAD 및 후속 claim 제외 |
| INT-006 | PASS | `OutboxLeaseIntegrationTest.reclaimsExpiredLeaseAndFencesStaleWorker` | stale owner/generation 갱신 차단 |
| INT-007 | PASS | `DirectionMatchingContractIntegrationTest.enforcesMatchingRoundUniquenessAndCoarsePayload` | 정확 좌표 비노출·round uniqueness 회귀 |
| INT-008 | PASS | `OutboxLeaseIntegrationTest.preservesExistingOutboxApi` | 기존 단건 claim 및 persistence 회귀 |
| INT-009 | PASS | `OutboxLeaseIntegrationTest.rejectsInvalidBatchClaimInputs` | SQL 전 입력 검증 및 Spring JDBC 경계 |

## 5. Failures and diagnostics

첫 targeted integration 실행에서 immutable `Set.of(...)`에 대해
`eventTypes.contains(null)`을 호출하는 입력 검증 오류가 재현됐다. Java immutable
set의 null 조회가 자체적으로 `NullPointerException`을 발생시키는 문제였으며,
`stream().anyMatch(Objects::isNull)`로 수정했다. 수정 후 targeted 9개와 전체
integration 242개가 모두 통과했다.

잘못된 repository 입력은 저장소 경계에서 `NotificationException`으로 변환된다.
테스트는 `NotificationErrorCode`가 보존되어 전역 도메인 예외 처리 경로로 전달되는지
검증한다.

## 6. Potential issues

### Application code

- event type 없는 기존 batch claim API를 제거하고 필수 타입 집합 API로 바꿨다.
- retry policy는 실패 분류만 입력받으며 구체적인 매칭·알림 오류 분류는 후속 worker
  Issue의 책임으로 남겼다.

### Infrastructure and resource limits

- Testcontainers 기반 PostgreSQL/PostGIS 실행은 Docker 자원과 호스트 아키텍처에
  따라 시간이 달라질 수 있다. 운영 인프라 변경은 수행하지 않았다.

### Database and migrations

- V1~V12 migration과 기존 claim index는 수정하지 않았다.
- event type 필터 전용 신규 index는 추가하지 않았다. 필요성은 #127의 합성 데이터와
  `EXPLAIN ANALYZE`로 별도 판단해야 한다.

### Concurrency and idempotency

- 실제 독립 transaction과 barrier로 같은 event 중복 claim이 없음을 검증했다.
- 외부 side effect는 이번 범위에 없으므로 provider 호출 중복성은 검증하지 않았다.

### Transactions and event ordering

- claim과 lease 필드 갱신은 `UPDATE ... RETURNING` 단일 SQL 경계를 유지한다.
- worker handler의 업무 트랜잭션과 Outbox 후속 이벤트 순서는 이번 범위 밖이다.

### External APIs

- 외부 API·FCM/APNs 호출은 명시적으로 제외되어 미실행이다.

### Failure recovery and reconciliation

- lease 만료 회수와 stale worker fencing은 검증했다.
- DEAD event의 운영자 재처리, 원인 보존, retention 정책과 실제 scheduler 회수는
  아직 구현·검증하지 않았다.
- lease duration, max attempts, backoff 운영 기본값은 승인되지 않은 상태로 남겼다.

## 7. Regression and residual risk

단위 265개와 PostgreSQL/PostGIS 통합 242개가 통과했다. 남은 위험은 실제 worker
polling loop와 업무 오류 분류, 운영 retry 숫자, event type 필터의 대규모 backlog
성능이다. 이 항목들은 #120·#123·#127 또는 별도 운영 정책 승인 후 검증해야 한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-119-TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION.md`
- Test report: `docs/reports/tests/gh-119-TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION.md`
- CI run: 미실행
- Related ADR: `docs/adr/0002-jpa-jdbc-boundary.md`
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제와 후속 범위가 명시됨
- [x] 실행 결과와 변경 범위가 일치함
