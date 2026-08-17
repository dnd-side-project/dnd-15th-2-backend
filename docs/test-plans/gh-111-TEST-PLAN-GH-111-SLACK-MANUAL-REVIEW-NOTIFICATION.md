# Test Plan: TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION

> Created at: `2026-08-17T15:37:20+09:00`
> GitHub Issue: `#111`
> Status: Approved

## 1. Objective

manual review case 생성 성공 직후 Slack 알림 대상 event를 발행하고, 별도
dispatch worker가 실제 Slack 전송을 처리하는 구조를 검증한다. 실패 시
위험: (1) Slack 장애가 `manual_review_case`/`FilterJob` 상태를 되돌리면
운영자가 이미 처리한 case가 재오픈되거나 자동/수동 authority 계약
(`#110`)이 깨진다, (2) 중복 event나 동시 dispatch가 같은 case에 대해
Slack 메시지를 반복 전송하면 관리자 채널이 알림 폭주로 무력화된다,
(3) payload에 답변 원문·직접 식별정보가 섞이면 제한된 관리자 채널이라도
개인정보 노출 사고가 된다.

## 2. Scope

### Included

- `NotificationEvent`/`NotificationEventStatus` 도메인 record와 상태 전이
  (`pending`/`claimed`/`processed`/`failed`) 검증
- `NotificationRetryPolicy.decide()` 재시도/dead 판정 로직
- `NotificationEventRepository`(JDBC) — `save`, `findByCaseId`, `claimDue`
  (`FOR UPDATE SKIP LOCKED` 기반), `complete`, `fail`
- Producer 연동 — `AnswerModerationExecutionWorker.openManualReviewCaseIfAbsent`
  가 case·priority evaluation과 같은 트랜잭션으로 `NotificationEvent`를
  저장하는지
- `SlackNotification` record의 필드 allowlist(`caseId`, `adminLinkPath`만)
- `SlackManualReviewNotificationDispatchWorker` — claim → send(트랜잭션
  밖) → complete/fail 흐름, `manual_review_case`/`filter_job` 미접근
- V17 마이그레이션(`notification_event` 테이블) 스키마 검증
- 동시성: case 생성 경합 시 중복 notification 방지, 동시 dispatch claim
  시 중복 전송 방지, lease 만료 후 재claim

### Excluded

- 실제 Slack HTTP 클라이언트 구현과 실제 Slack API 호출 — `SlackNotifier`는
  테스트 double로만 검증한다.
- `notification_event`를 실제로 주기적으로 polling하는 scheduler 배선 —
  `#113` production gate 범위.
- retry·집계·throttling의 실제 운영 수치 — 생성자 주입 구조만 검증하고
  구체적 숫자의 운영 적정성은 검증 대상이 아니다.
- 기존 `outbox_event`/`RecipientNotificationFanOutWorker` 회귀 — 이 이슈는
  별도 테이블만 추가하므로 해당 스위트는 변경하지 않는다(기존 스위트가
  깨지지 않는지만 `pr-ready` 전체 실행으로 확인한다).

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #111 | Slack 장애가 case, workflow 또는 공개 상태를 rollback하지 않는다(`INV-SLK-002`) |
| GitHub Issue #111 | 중복 event가 중복 알림 폭주로 이어지지 않는다(`INV-SLK-005`) |
| GitHub Issue #111 | 허용 목록 밖의 필드가 payload에 들어가지 않는다(`INV-SLK-003`, `INV-SLK-004`) |
| GitHub Issue #111 | case 생성 성공 뒤 notification event를 발행하는 outbox 성격의 경계 |
| GitHub Issue #111 | Slack 전송 상태와 case 상태 분리 |
| GitHub Issue #111 | case reference 기반 deduplication |
| GitHub Issue #111 | 제한된 관리자 채널, opaque reference와 관리자 링크만 사용하는 payload |
| GitHub Issue #111 | 재시도, aggregation과 throttling을 연결할 수 있는 구조 |
| TASK.md | `notification_event` 전용 테이블, V17 마이그레이션 |
| TASK.md | `SlackManualReviewNotificationDispatchWorker`는 non-Spring-bean, `#113`로 실제 배선 이연 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| Slack 전송 실패가 dispatch worker의 예외 전파를 통해 `manual_review_case`나 `filter_job`을 롤백/오염시킴 | High | Medium | P0 | INT-005 |
| case 생성 경합(동시 요청)으로 같은 case에 대해 `notification_event`가 두 번 만들어짐 | High | Low | P0 | INT-002 |
| 두 dispatch worker 인스턴스가 같은 PENDING event를 동시에 claim해 Slack에 중복 전송 | High | Medium | P0 | INT-003 |
| `SlackNotification`에 답변 원문/user ID/닉네임/이메일 등 비허용 필드가 섞임 | High | Low | P0 | UNIT-007 |
| dispatch worker가 send 도중 crash해 lease가 만료 전 영구 PROCESSING으로 남음 | Medium | Low | P1 | INT-006 |
| `NotificationRetryPolicy`가 PERMANENT 실패를 재시도 가능으로 오판해 무한 재시도 | Medium | Low | P1 | UNIT-006 |
| V17 마이그레이션이 `manual_review_case` FK 제약을 위반하는 상태로 배포됨 | Medium | Low | P1 | INT-007 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-001 | 유효한 필드 | `NotificationEvent.pending(caseId, adminLinkPath, at)` 호출 | status=PENDING, attemptCount=0, leaseOwner/leaseExpiresAt=null인 인스턴스 생성 | P0 | Feature executor |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-002 | caseId<=0, adminLinkPath blank, attemptCount<0 등 각 위반 | canonical constructor 호출 | `NotificationException` 발생(각 필드별 개별 케이스) | P0 | Feature executor |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-003 | PENDING/FAILED 상태 event | `claimed(owner, at, expiresAt)` 호출 | PROCESSING 전이, attemptCount+1, leaseOwner/leaseExpiresAt/leaseGeneration 갱신 | P0 | Feature executor |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-004 | PROCESSED/DEAD 상태 event | `claimed(...)` 호출 | `NotificationException`(INVALID_NOTIFICATION_STATUS) | P0 | Feature executor |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-005 | PROCESSING 상태 event | `processed(at)` 호출 | PROCESSED 전이, processedAt 설정, lease 필드 초기화 | P0 | Feature executor |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-006 | PROCESSING 상태, `NotificationRetryPolicy(maxAttempts=3, backoff)` | attemptCount=2에서 RETRYABLE 실패로 `decide()` 호출 | dead=false, nextAttemptAt=at+backoff(2) | P0 | Feature executor |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-006b | 위와 동일 정책 | attemptCount=3에서 RETRYABLE 실패로 `decide()` 호출 | dead=true(maxAttempts 도달) | P0 | Feature executor |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-006c | 위와 동일 정책 | attemptCount=0에서 PERMANENT 실패로 `decide()` 호출 | dead=true(재시도 횟수와 무관) | P0 | Feature executor |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-007 | `SlackNotification` 클래스 | reflection으로 선언된 필드 목록 조회 | `caseId`, `adminLinkPath` 두 필드만 존재(`INV-SLK-003`, `INV-SLK-004`) | P0 | Feature executor |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-008 | mock `NotificationEventRepository`/`SlackNotifier`, claim된 event 1건 | `SlackNotifier.send`가 정상 반환 | `complete(id, leaseOwner, leaseGeneration, at)` 호출됨, `SlackNotifier`에는 caseId·adminLinkPath만 전달됨 | P0 | Feature executor |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-009 | mock 저장소, claim된 event 1건 | `SlackNotifier.send`가 retryable 예외로 실패 | `fail(...)`이 `NotificationRetryPolicy` 판정값으로 호출됨, mock에는 다른 repository(`ManualReviewCaseRepository`, `FilterJobRepository`) 상호작용이 존재하지 않음(주입 자체가 없음을 생성자 시그니처로 확인) | P0 | Feature executor |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-010 | claim 응답에 leaseOwner/leaseGeneration이 없는 손상된 event | `processBatch` 처리 | STALE_LEASE로 분류되고 `complete`/`fail` 어느 것도 호출되지 않음 | P1 | Feature executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-INT-001 | `AnswerModerationExecutionWorker`, `NotificationEventRepository`, PostgreSQL | 소진 임박 job 1건 | 재시도 소진으로 `openManualReviewCaseIfAbsent` 트리거 | `manual_review_case` 1행과 `notification_event` 1행(같은 caseId, PENDING)이 같은 트랜잭션으로 함께 생성됨 | 두 테이블 truncate |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-INT-002 | 위와 동일 + 동시 실행 | 같은 target·release로 job 2건(경합 유발) | 두 스레드가 동시에 `openManualReviewCaseIfAbsent` 호출 | `manual_review_case`도 `notification_event`도 정확히 1행만 존재(`INV-SLK-005` 생성 시점 dedup) | 두 테이블 truncate |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-INT-003 | `NotificationEventRepository.claimDue`, PostgreSQL | PENDING event 1건 | 두 스레드가 동시에 `claimDue(limit=1, ...)` 호출 | 한 스레드만 event를 claim(PROCESSING, leaseOwner 자신), 다른 스레드는 빈 리스트(`FOR UPDATE SKIP LOCKED`로 `INV-SLK-005` 전송 시점 dedup) | truncate |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-INT-004 | `SlackManualReviewNotificationDispatchWorker`, stub `SlackNotifier`(성공), PostgreSQL | PENDING event 1건, 대응 `manual_review_case`/`filter_job` snapshot | `processBatch` 실행 | `notification_event.status=PROCESSED`, `manual_review_case`/`filter_job` 행이 실행 전후 완전히 동일(모든 컬럼) | truncate |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-INT-005 | `SlackManualReviewNotificationDispatchWorker`, stub `SlackNotifier`(매번 예외 발생), PostgreSQL | PENDING event 1건, 대응 case/job snapshot | `processBatch`를 `maxAttempts`만큼 반복 실행 | 각 실행마다 `notification_event`만 FAILED→최종 DEAD로 전이, `manual_review_case.status`와 `filter_job.status`는 최초 snapshot과 매 실행 후에도 동일(`INV-SLK-002`) | truncate |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-INT-006 | `NotificationEventRepository`, PostgreSQL | PROCESSING 상태이며 `lease_expires_at`이 과거인 event | `claimDue(at=now)` 호출 | 만료된 lease가 재claim되어 attempt_count 증가, 새 leaseOwner/leaseGeneration 부여 | truncate |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-INT-007 | Flyway, PostgreSQL | 마이그레이션 적용 | 스키마 조회 | `notification_event` 테이블에 `case_id`(UNIQUE, FK→`manual_review_case.id`), `admin_link_path`, `status`, lease 컬럼이 기대한 타입·제약대로 존재 | 없음(schema-only) |
| TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-INT-008 | REST 미변경 확인 | 기존 `pr-ready` 전체 스위트 | 실행 | `outbox_event`/`RecipientNotificationFanOutWorker` 관련 기존 테스트가 회귀 없이 통과 | 해당 없음 |

## 7. Cross-cutting scenarios

### Database and transactions

- INT-001/INT-002: producer의 case 생성과 notification 발행이 같은
  `TransactionTemplate` 블록 안에서 원자적으로 성공/실패하는지 확인한다.
- INT-004/INT-005: dispatch worker는 claim(자체 원자적 UPDATE) → send(트랜잭션
  밖) → complete/fail(자체 원자적 UPDATE)의 세 단계로 나뉘어 있어, 하나의
  DB 트랜잭션으로 묶이지 않는다는 것을 스냅샷 비교로 검증한다.

### Concurrency and idempotency

- INT-002: 동시 case-open 경합에서 정확히 1개의 notification만 남는지.
- INT-003: 동시 claim 경합에서 정확히 1개의 worker만 event를 획득하는지
  (`FOR UPDATE SKIP LOCKED`).
- INT-006: lease 만료 후 재claim이 attempt_count와 lease_generation을
  올바르게 갱신하는지.

### External APIs

- `SlackNotifier`는 이 이슈에서 실제 구현체가 없으므로, 모든 시나리오는
  test double(성공 stub, 예외 throw stub)로 대체한다. 실제 Slack API 호출은
  없다.

### Failure recovery and reconciliation

- INT-005: 재시도 소진 시 `notification_event`가 DEAD로 영구 종결되고, case
  워크플로 자체는 계속 유효한 상태로 남아 운영자가 case를 정상 처리할 수
  있는지(수동으로 알림을 대체할 여지가 있는지는 이 이슈 범위 밖이지만, case가
  손상되지 않았음은 검증한다).
- INT-006: lease 만료 후 재claim으로 worker crash를 복구할 수 있는지.

## 8. Test data and isolation

- Fixtures: `#110`의 `ManualReviewPriorityIntegrationTest` 패턴을 재사용해
  `FilterJob`을 재시도 소진 직전 상태로 구성한다.
- Database isolation: `@SpringBootTest` + Testcontainers PostgreSQL, 각
  테스트 전 `notification_event` → `manual_review_priority_evaluation` →
  `manual_review_case` → `filter_job` 순서로 삭제(FK 역순).
- Clock/randomness: 고정 `Clock.fixed(...)` 주입, lease 만료 시나리오는
  명시적으로 과거 시각을 데이터에 기록해 재현한다.
- External API doubles: `SlackNotifier`의 in-memory stub 구현(성공/예외
  각각).
- Cleanup: 각 테스트 후 truncate, 다음 테스트에 상태가 넘어가지 않게 한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Feature executor | `notification/domain/NotificationEvent.java`, `NotificationEventStatus.java`, `NotificationRetryPolicy.java`, `notification/repository/NotificationEventRepository.java`(+JDBC), `notification/slack/SlackNotifier.java`, `SlackNotification.java`, `SlackManualReviewNotificationDispatchWorker.java`, `filtering/moderation/AnswerModerationExecutionWorker.java`(producer 연동), `db/migration/V17__*.sql`, 대응 단위·통합 테스트 전체 | UNIT-001~010, INT-001~008 | `./gradlew test --tests "com.dnd.qello.notification.*"`, `./gradlew integrationTest --tests "com.dnd.qello.*SlackManualReview*" --tests "com.dnd.qello.*NotificationEvent*"`, `./harness pr-ready --project-tests` |

## 10. Completion criteria

- [x] 모든 P0 시나리오 구현
- [x] 모든 테스트 메서드에 `@DisplayName`
- [x] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [x] 단위 테스트 통과
- [x] 통합 테스트 통과
- [x] 잠재 문제 분석
- [x] 테스트 보고서 생성

## 11. Human approval

- Reviewer: tkv00
- Decision: Approved
- Approved at: 2026-08-17
