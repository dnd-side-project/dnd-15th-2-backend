# GitHub Issue #111 Task Contract

> Generated at: `2026-08-17T14:52:00+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Slack 보조 알림`
- GitHub Issue: `#111`
- Branch: `feat/gh-111-slack-manual-review-notification`
- Base branch: `main`
- Test plan: `TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION`
- Test plan approval: `APPROVED` — 사용자가 2026-08-17 구현을 승인했다.
- Confirmed policy: 전용 `notification_event` 테이블을 새로 만든다(기존
  `outbox_event` 재사용이 아니다). 이슈 본문이 "DB: notification_event"로
  명시했고, `case_id`·`admin_link_path`를 컴럼 스키마로 직접 두어 허용목록을
  타입 수준에서 강제한다(사용자 승인, 2026-08-17). claim/lease/retry 배관은
  `outbox_event`의 검증된 SQL 패턴과 `OutboxBackoffStrategy`/
  `OutboxFailureKind`/`OutboxRetryDecision`을 그대로 재사용하고, `decide` 로직만
  `NotificationEvent` 전용으로 별도 구현한다(중복은 사용자가 인지하고 선택).
- Confirmed policy: Slack 발송 워커(consumer) 구조까지 이 이슈에서 함께
  구현한다(사용자 승인, 2026-08-17). `SlackNotifier` port interface만 두고
  실제 HTTP 구현체·webhook/secret 배선은 만들지 않는다(`#113`로 이연,
  `AnswerModerationJobIntakeService`/`SnapshotHealthProbeRecorder`와 동일한
  "hook만 남기는" 패턴).

## Objective

- manual review case 생성 성공 직후 Slack 알림 발행 대상 event를 outbox
  성격의 경계로 발행하고, 실제 Slack 전송은 별도 dispatch worker가 처리하게
  분리한다.
- Slack 전송 실패가 `manual_review_case`나 `FilterJob` 상태를 되돌리지
  않는다(`INV-SLK-002`) — dispatch worker는 두 테이블 중 어느 것도 참조·수정
  하지 않는 구조로 이를 보장한다.
- 중복 event가 중복 알림 폭주로 이어지지 않는다(`INV-SLK-005`) —
  `notification_event.case_id` UNIQUE 제약으로 생성 시점 중복을 막고,
  `FOR UPDATE SKIP LOCKED` 기반 claim으로 동시 dispatch로 인한 중복 전송을
  막는다.
- 허용목록 밖 필드가 payload에 들어가지 않는다(`INV-SLK-003`, `INV-SLK-004`)
  — `SlackNotification` record가 `caseId`·`adminLinkPath` 두 필드만 갖도록
  타입을 제한해, 답변 원문·user ID·닉네임·이메일·신고 세부가 애초에 표현할
  방법이 없게 한다.

## Scope

1. `NotificationEvent`(신규 도메인) + `NotificationEventStatus`(신규 enum:
   `PENDING`/`PROCESSING`/`PROCESSED`/`FAILED`/`DEAD`) — `notification_event`
   테이블에 매핑되는 record. `id`, `caseId`, `adminLinkPath`, `status`,
   `attemptCount`, `nextAttemptAt`, `createdAt`, `processedAt`, `leaseOwner`,
   `leaseExpiresAt`, `leaseGeneration`. 정적 팩토리 `pending`과 전이 메서드
   `claimed`/`processed`/`failed`는 `OutboxEvent`와 동일한 shape로 둔다.
2. `NotificationRetryPolicy`(신규, 주입 config) — `maxAttempts`와 기존
   `OutboxBackoffStrategy`(재사용)로 `NotificationEvent`용 retry/dead를
   결정한다. 실제 운영 수치는 미결정이며 생성자 주입 값으로만 존재한다.
3. `NotificationEventRepository`(신규 interface) + JDBC 구현 — `save`,
   `findByCaseId`, `claimDue`(limit/leaseOwner/at/leaseExpiresAt),
   `complete`, `fail`. claim SQL은 `outbox_event`의 `CLAIM_DUE_OUTBOX_EVENTS`
   (`FOR UPDATE SKIP LOCKED` + `UPDATE ... RETURNING`)와 동일한 패턴을
   `notification_event`에 적용한다.
4. Producer — `AnswerModerationExecutionWorker.openManualReviewCaseIfAbsent`
   (`#110`)의 기존 `transactionTemplate.executeWithoutResult` 블록 안에서,
   case와 priority evaluation을 저장한 직후 같은 트랜잭션으로
   `NotificationEvent.pending(caseId, adminLinkPath, now)`를 저장한다. case
   생성이 경합해 롤백되면(`DataIntegrityViolationException`, 기존
   catch가 흡수) notification도 함께 롤백되어, "case 생성 성공 뒤에만
   발행"이 원자적으로 보장된다. `adminLinkPath`는 새 API를 추가하지 않고
   `/admin/filtering/manual-review-cases/{caseId}`로 결정적으로 구성한다
   (이슈 본문의 "API: 없음"을 그대로 따른다).
5. `SlackNotifier`(신규 port interface, 구현체 없음)와
   `SlackNotification`(신규 record: `caseId`, `adminLinkPath`만 포함) — 이
   두 필드 외에는 타입에 표현할 방법이 없어 허용목록을 컴파일 타임에
   강제한다.
6. `SlackManualReviewNotificationDispatchWorker`(신규, non-Spring-bean) —
   `NotificationEventRepository.claimDue`로 이벤트를 잠그고, DB 트랜잭션
   **밖에서** `SlackNotifier.send`를 호출한다(HTTP 호출을 트랜잭션 안에
   가두지 않는다). 성공 시 `complete`, 실패 시 `NotificationRetryPolicy`로
   retry/dead를 결정해 `fail`한다. `RecipientNotificationFanOutWorker`와
   달리 claim·send·complete/fail을 하나의 DB 트랜잭션으로 묶지 않는다 —
   send는 외부 HTTP 호출이라 DB 트랜잭션을 열어둘 이유가 없고, 각 단계는
   lease generation 검증으로 이미 개별적으로 안전하다.
7. DB 마이그레이션(V17) — `notification_event` 테이블 신규 생성(전용 테이블,
   `outbox_event`와 무관). `case_id BIGINT UNIQUE NOT NULL REFERENCES
   manual_review_case(id)`.
8. 단위·PostgreSQL 통합(동시성 포함) 테스트와 테스트 보고서.

## Explicit exclusions

- Incoming Webhook/Bot API 선택, 실제 Slack HTTP 클라이언트 구현, webhook
  URL·secret 관리 — 이슈가 명시적으로 미결정.
- Slack 채널 선택, secret rotation, 실제 retry·집계(aggregation)·throttling
  수치 — 생성자 주입 지점만 만들고 값은 정하지 않는다.
- retention·residency·DPA 정책.
- `notification_event`를 실제로 주기적으로 polling하는 scheduler와 Spring
  bean 배선, `SlackNotifier`의 실제 구현체 — `#105`~`#110`과 동일하게
  `#113` production gate로 이연.
- 기존 `outbox_event` 테이블과 그 소비자(`RecipientNotificationFanOutWorker`
  등) 변경 — 이 이슈는 별도 전용 테이블만 추가하며 기존 outbox 배관을
  건드리지 않는다.
- API 변경 — 이슈 본문이 "API: 없음"으로 명시. 새 REST endpoint를 추가하지
  않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `NotificationEvent`/`NotificationEventStatus`, `NotificationRetryPolicy`, `NotificationEventRepository`(+JDBC), producer 연동, `SlackNotifier`/`SlackNotification`, `SlackManualReviewNotificationDispatchWorker`, V17 마이그레이션, 단위·통합 테스트 | Feature executor | `INV-SLK-002`~`005` 검증, `#110`(manual review case 생성 지점) 기존 계약과의 호환성 리뷰 |

## Existing user-owned changes

- `origin/main`(#150 병합 직후, `3acbc4a`)에서 새로 분기했다
  (`git worktree add -b feat/gh-111-slack-manual-review-notification`).
  분기 시점 작업 트리는 clean이었다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.notification.*" --max-workers=1 --no-daemon
./gradlew integrationTest --tests "com.dnd.qello.*SlackManualReview*" --tests "com.dnd.qello.*NotificationEvent*" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./harness test-run --id TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] Slack 장애가 case, workflow 또는 공개 상태를 rollback하지 않는다
      (`INV-SLK-002`) — `SlackManualReviewNotificationDispatchWorker`가
      `manual_review_case`/`filter_job` 어느 것도 참조·수정하지 않는 구조로
      보장한다. `NotificationEventIntegrationTest#dispatchFailureNeverTouchesCaseOrJob`이
      Slack 전송 실패로 `notification_event`가 DEAD가 된 뒤에도 두 테이블의
      전체 행이 실행 전후 완전히 동일함을 실제 PostgreSQL로 검증했다. PR
      리뷰에서 `processClaimedEvent`가 `SlackDeliveryException`만 잡아 다른
      `RuntimeException`이 batch 전체를 중단시킬 수 있던 결함을 발견해
      `RuntimeException`을 넓게 잡도록 고쳤다(`SlackManualReviewNotificationDispatchWorkerTest#isolatesUnexpectedRuntimeExceptionFromSend`).
- [x] 중복 event가 중복 알림 폭주로 이어지지 않는다(`INV-SLK-005`) —
      생성 시점은 `notification_event.case_id` UNIQUE 제약(`concurrentCaseCreationRaceProducesExactlyOneNotificationEvent`),
      전송 시점은 `FOR UPDATE SKIP LOCKED` 기반 claim(`concurrentClaimDueGrantsExactlyOneWorker`)
      두 지점에서 각각 실제 동시성 아래 검증했다.
- [x] 허용 목록 밖의 필드가 payload에 들어가지 않는다(`INV-SLK-003`,
      `INV-SLK-004`) — `SlackNotification` record가 `caseId`·`adminLinkPath`
      두 필드만 갖도록 타입을 제한했다. `SlackNotificationTest#exposesOnlyAllowlistedFields`가
      reflection으로 필드 목록을 검증했다.
- [x] 승인된 P0 테스트와 저장소 필수 검증이 통과하고 테스트 보고서가
      남는다 — unit 20개(UNIT-001~010, 일부 세부 케이스 포함), integration
      6개(INT-001~006, 실제 PostgreSQL 동시성·원자성 포함) 전부 통과.
      상세는
      `docs/reports/tests/gh-111-TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION.md`
      참고. 영향받은 기존 통합 테스트(FlywayMigrationIntegrationTest,
      AnswerModerationJobIntegrationTest, AnswerModerationRetryIntegrationTest,
      ManualReviewPriorityIntegrationTest, SnapshotHealthMigrationIntegrationTest)
      회귀 없음을 재실행으로 확인했다.
- [x] 실행하지 못한 검증과 남은 위험을 보고서에 기록한다 — 위 보고서
      6·7절에 실제 Slack HTTP 클라이언트·webhook/secret 미구현,
      scheduler 배선(`#113`) 등을 명시했다.
