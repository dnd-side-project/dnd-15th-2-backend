# Test Report: TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION

> Created at: `2026-08-17T16:15:00+09:00`
> GitHub Issue: `#111`
> Branch: `feat/gh-111-slack-manual-review-notification`
> Commit: `3acbc4a` 기준 분기, 이 보고서는 구현 커밋 직전 상태에서 작성했다

## 1. Executive summary

- Result: `PASS`
- Tested scope: `NotificationEvent`/`NotificationEventStatus` 상태 전이,
  `NotificationRetryPolicy` 재시도·dead 판정, `SlackNotification` 필드
  allowlist, `SlackManualReviewNotificationDispatchWorker`의 claim→send→
  complete/fail 흐름, producer(`AnswerModerationExecutionWorker`) 원자적
  발행, V17 마이그레이션 스키마, 생성·전송 두 지점의 동시성 dedup.
- Unverified scope: 실제 Slack HTTP 클라이언트 호출(구현체 없음, port만
  존재), `notification_event` polling scheduler 배선(`#113`), 실제 Slack
  Incoming Webhook/Bot API 응답 형식에 대한 검증.
- Release recommendation: 이 이슈 범위(producer + dispatch worker 구조)는
  merge 가능. 실제 운영 알림 발송은 `#113`에서 webhook/secret과 scheduler를
  배선한 뒤에만 가능하다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 17.0.8 LTS (Gradle toolchain) |
| Spring Boot | 3.5.16 |
| Database | PostgreSQL 16 (Testcontainers, PostGIS 이미지) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests "com.dnd.qello.notification.*" --tests "com.dnd.qello.filtering.*"` | PASS | 194 | ~8s | 로컬 실행 로그 |
| `./gradlew integrationTest --tests "com.dnd.qello.NotificationEventIntegrationTest"` | PASS | 6 | ~20s | 로컬 실행 로그 |
| `./gradlew integrationTest` (영향받은 기존 스위트 5개 회귀 재실행: FlywayMigrationIntegrationTest, AnswerModerationJobIntegrationTest, AnswerModerationRetryIntegrationTest, ManualReviewPriorityIntegrationTest, SnapshotHealthMigrationIntegrationTest) | PASS | 해당 스위트 전체 | ~47s | 로컬 실행 로그 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `NotificationEventTest#createsPendingEventWithoutLease` | |
| UNIT-002 | PASS | `NotificationEventTest#rejectsNonPositiveCaseId`, `#rejectsBlankAdminLinkPath`, `#rejectsNegativeAttemptCount` | 계획의 UNIT-002를 3개 케이스로 분리 |
| UNIT-003 | PASS | `NotificationEventTest#claimsPendingEvent` | |
| UNIT-004 | PASS | `NotificationEventTest#rejectsClaimFromTerminalStatus` | |
| UNIT-005 | PASS | `NotificationEventTest#completesProcessingEvent`, `#rejectsCompletingNonProcessingEvent`, `#failsProcessingEvent` | |
| UNIT-006 | PASS | `NotificationRetryPolicyTest#decidesRetryWhenAttemptsRemain` | |
| UNIT-006b | PASS | `NotificationRetryPolicyTest#decidesDeadWhenMaxAttemptsReached` | |
| UNIT-006c | PASS | `NotificationRetryPolicyTest#decidesDeadImmediatelyOnPermanentFailure` | |
| UNIT-007 | PASS | `SlackNotificationTest#exposesOnlyAllowlistedFields`, `#rejectsNonPositiveCaseId`, `#rejectsBlankAdminLinkPath` | |
| UNIT-008 | PASS | `SlackManualReviewNotificationDispatchWorkerTest#completesEventOnSuccessfulSend` | |
| UNIT-009 | PASS | `SlackManualReviewNotificationDispatchWorkerTest#failsEventOnRetryableSendFailure` | 계획엔 없던 `marksDeadOnPermanentSendFailure` 케이스도 함께 검증 |
| UNIT-010 | PASS | `SlackManualReviewNotificationDispatchWorkerTest#classifiesMissingLeaseIdentityAsStale` | |
| UNIT-011 | PASS | `SlackManualReviewNotificationDispatchWorkerTest#isolatesUnexpectedRuntimeExceptionFromSend` | 계획엔 없던 케이스. PR 리뷰에서 발견한 결함(5절 3항) 회귀 방지 |
| INT-001 | PASS | `NotificationEventIntegrationTest#producerCreatesNotificationEventAtomicallyWithCase` | |
| INT-002 | PASS | `NotificationEventIntegrationTest#concurrentCaseCreationRaceProducesExactlyOneNotificationEvent` | |
| INT-003 | PASS | `NotificationEventIntegrationTest#concurrentClaimDueGrantsExactlyOneWorker` | |
| INT-004 | PASS | `NotificationEventIntegrationTest#dispatchSuccessLeavesCaseAndJobUntouched` | |
| INT-005 | PASS | `NotificationEventIntegrationTest#dispatchFailureNeverTouchesCaseOrJob` | `INV-SLK-002` 핵심 시나리오 |
| INT-006 | PASS | `NotificationEventIntegrationTest#claimDueReclaimsExpiredLease` | |
| INT-007 | PASS | `FlywayMigrationIntegrationTest#v17AddsNotificationEventForSlackManualReview` | |
| INT-008 | PASS | 기존 회귀 스위트 5개 재실행 | `outbox_event`/`RecipientNotificationFanOutWorker` 관련 회귀 없음 |

## 5. Failures and diagnostics

작업 중 발견해 수정한 이슈 세 가지를 기록한다(최종 실행에서는 모두 통과).

1. **JDBC 메서드 시그니처 충돌**: `NotificationEventRepository.complete`/`fail`이
   기존 `OutboxEventRepository.complete`/`fail`과 완전히 동일한 시그니처라
   같은 클래스(`JdbcNotificationRepository`)에 함께 구현할 수 없었다(컴파일
   에러: "method is already defined"). 원인은 서로 다른 두 테이블
   (`outbox_event`/`notification_event`)을 다루는 메서드가 이름과 파라미터
   타입까지 같았기 때문이다. `JdbcNotificationEventRepository`를 별도
   클래스로 분리해 해결했다.
2. **`FlywayMigrationIntegrationTest`의 constraint count 오판**: V17이
   `notification_event`에 추가한 FK 1개·UNIQUE 1개·CHECK 5개를 기존
   f/u/c 총계(52/21/116)에 더해 53/22/121로 고쳤다가 실패했다. 원인은
   `countConstraints` 헬퍼가 `EXPECTED_TABLES`에 속한 테이블만 세는데,
   `notification_event`는 `manual_review_case`/`filter_job` 등 V10~V16의
   filtering 테이블과 마찬가지로 그 목록에 없었기 때문이다(이 매니페스트는
   원래 V1~V9 catalog 전용). 디버그 출력으로 원인을 확인한 뒤 기존 값
   (52/21/116)을 그대로 유지하는 것으로 정정했다.
3. **PR 리뷰에서 발견한 예외 처리 결함**: `SlackManualReviewNotificationDispatchWorker.processClaimedEvent`가
   `SlackDeliveryException`만 잡고 있어, `SlackNotifier` 구현체가 그 외의
   `RuntimeException`(네트워크 예외 래핑, 직렬화 오류 등)을 던지면 해당
   batch의 나머지 event 처리까지 함께 중단되는 결함이었다. 이 worker가
   모델로 삼은 `RecipientNotificationFanOutWorker`는 `RuntimeException`을
   넓게 잡아 개별 event 단위로 격리하는데, 이 부분만 좁게 구현돼 있었다.
   `RuntimeException`을 넓게 잡고 `SlackDeliveryException`이 아니면
   `RETRYABLE`로 기본 처리하도록 고쳤다(`UNIT-011`).

## 6. Potential issues

### Application code

- `SlackNotifier`는 이 이슈에서 실제 구현체가 없다. `#113`에서 실제 HTTP
  클라이언트를 붙일 때, `SlackDeliveryException.retryable()` 분류(4xx는
  `false`, 5xx/timeout/network는 `true`)가 실제 Slack API 응답 코드와
  정확히 대응하는지 별도 검증이 필요하다.

### Infrastructure and resource limits

- webhook URL·secret 관리 방식(AWS Secrets Manager 등)이 미결정이다.
  `#113`에서 결정한다.

### Database and migrations

- `notification_event.case_id`는 `manual_review_case(id)`를 참조하는 FK이며
  `ON DELETE` 정책을 지정하지 않았다(기본 `NO ACTION`). `manual_review_case`
  행 삭제 정책이 이 저장소에 아직 없어 문제가 되지 않지만, 향후 case
  보존기간 정책이 생기면 재검토가 필요하다.

### Concurrency and idempotency

- `NotificationEventIntegrationTest#concurrentClaimDueGrantsExactlyOneWorker`
  는 사전에 저장된 PENDING event 1건을 대상으로 동시 claim 경합만
  검증했다. 실제 운영에서 여러 dispatch worker 인스턴스가 동시에
  polling하는 시나리오(주기적 스케줄러 다중 인스턴스)는 `#113`에서 실제
  배선과 함께 재검증이 필요하다.

### Transactions and event ordering

- producer(`AnswerModerationExecutionWorker.openManualReviewCaseIfAbsent`)는
  case·priority evaluation·notification_event 세 저장을 한 트랜잭션으로
  묶는다. 세 번째 저장(notification_event)만 실패하는 경우는 이 저장소의
  기존 `DataIntegrityViolationException` 흡수 경로로 case 생성 자체가 함께
  롤백되므로 별도 처리가 없다 — 이는 의도된 동작이며 별도 결함이 아니다.

### External APIs

- 실제 Slack Incoming Webhook/Bot API 호출은 검증하지 않았다(구현체 없음).
  `#113`에서 실제 API 계약(rate limit, 페이로드 크기 제한, 재시도 헤더 등)에
  대한 통합 테스트가 필요하다.

### Failure recovery and reconciliation

- `claimDueReclaimsExpiredLease`(INT-006)로 lease 만료 후 재claim은
  검증했지만, worker 프로세스 자체가 영구히 죽어 아무도 재claim을 시도하지
  않는 상황(scheduler 부재)은 이 이슈 범위 밖이며 `#113`에서 다룬다.

## 7. Regression and residual risk

- 기존 `outbox_event`/`RecipientNotificationFanOutWorker`/`ManualReviewCase`
  워크플로에 대한 회귀는 5개 기존 통합 테스트 스위트 재실행으로 확인했다.
  실패 없음.
- `AnswerModerationExecutionWorker` 생성자에 `NotificationEventRepository`
  파라미터가 추가되어 이를 직접 생성하는 4개 통합 테스트 파일과 1개 단위
  테스트 파일의 호출부를 갱신했다. 갱신 누락이 있으면 컴파일 자체가
  실패하므로 은닉 위험은 낮다.
- 잔여 위험: `SlackNotifier` 미구현으로 인해 이 이슈의 코드는 실제로
  아무 Slack 메시지도 보내지 않는다(구조만 존재). `#113`이 완료되기 전까지
  운영 알림 기능은 활성화되지 않는다 — 이는 설계상 의도된 상태다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-111-TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION.md`
- CI run: 로컬 실행만 수행. PR 생성 후 GitHub Actions 결과를 추가로
  연결한다.
- Related ADR: 없음
- PR: 아직 생성 전

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (실제 Slack API 호출, scheduler 배선)
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 (`#113`)
- [ ] 실행 결과와 PR 설명이 일치함 (PR 생성 후 확인)
