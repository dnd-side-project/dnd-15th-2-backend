# Test Plan: TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP

> Created at: `2026-08-17T20:40:45+09:00`
> GitHub Issue: `#126`
> Status: Approved

## 1. Objective

만료·넘김 확정 전이를 batch 실행기에 연결했을 때, 수신 슬롯
(`recipient_receive_state.active_unhandled_count`)이 **정확히 한 번만** 해제되고
어떤 재실행·경합에서도 중복 감소하지 않음을 증명한다.

실패 시 위험:

- 슬롯이 해제되지 않으면 수신자가 상한(`qello.direction.receive-capacity=5`)에
  영구히 묶여 새 질문글을 받지 못한다.
- 슬롯이 중복 해제되면 카운터가 실제 미처리 항목 수보다 낮아져 상한을 넘는
  수신이 발생한다. `RELEASE` SQL의 `active_unhandled_count > 0` 조건은 음수만
  막을 뿐 중복 감소 자체를 막지 못하므로, 중복 방지의 유일한 근거는 조건부 전이
  (`transitionToExpired`/`transitionToSkipped`의 `previousStatus` 조건)가 빈 결과를
  돌려줄 때 `release()`를 호출하지 않는 경로다. 이 계약이 실행기 수준에서
  유지되는지가 이 계획의 핵심이다.
- 만료 sweep이 검사 중 답변을 가진 수신 항목을 선점하면 정상 제출된 답변이
  영원히 공개되지 못한다(#125에서 확보한 보장의 회귀).
- 처리량 제한이 없으면 대량 만료 시 한 batch가 DB를 장시간 점유한다.

## 2. Scope

### Included

- 만료 sweep 실행기의 후보 조회·행별 위임·결과 집계.
- 넘김 확정 sweep 실행기의 유예 판정·행별 위임·결과 집계.
- `findExpirableAsOf`/`findConfirmableSkips`의 `limit`과 결정적 정렬.
- 행 단위 트랜잭션 경계 유지와 행 단위 실패 격리.
- 동일 sweep 재실행 및 답변 제출·공개, 사용자 넘김 되돌리기, 차단과의 경합.
- batch 결과 카운터(`scanned`/`released`/`ineligible`/`failed`)와 로그의
  민감정보 미노출.

### Excluded

- `@Scheduled`·`@EnableScheduling`과 운영 주기 실행 활성화(`TASK.md` 제외 항목).
- actuator/Micrometer 메트릭 등록과 대시보드.
- 다중 인스턴스 분산 lease·advisory lock.
- `PostRecipient.expire()`/`confirmSkip()` 도메인 전이 규칙 자체의 재검증.
  `DirectionDomainTest`가 이미 소유하며 이 계획은 실행기 경계만 다룬다.
- 만료·넘김·차단 전이의 슬롯 해제 기본 동작. `ReceiveSlotReleaseIntegrationTest`
  (TEST-PLAN-GH-93)가 소유한다. 이 계획은 그 위에 얹히는 batch 실행기만 다룬다.
- 답변 제출·공개 계약(TEST-PLAN-GH-125). 경합 상대로만 등장하고 계약 자체는
  검증하지 않는다.
- 보존·삭제 정책과 만료 항목의 물리 삭제.
- HTTP endpoint. 이슈 명시대로 내부 실행기이며 외부 API가 없다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #126 | 미답변 만료 항목이 `EXPIRED`로 전이되고 슬롯이 한 번 해제된다 |
| GitHub Issue #126 | 유예 중인 `SKIP_PENDING`은 확정되지 않는다 |
| GitHub Issue #126 | 유예가 지난 항목은 `SKIPPED`로 전이되고 슬롯이 한 번 해제된다 |
| GitHub Issue #126 | 같은 sweep을 재실행해도 카운터가 중복 감소하지 않는다 |
| GitHub Issue #126 | 처리량 제한·실패 로그·기본 메트릭을 제공한다 |
| GitHub Issue #126 | 행 단위 transaction 경계와 재실행 안전성을 유지한다 |
| `TASK.md` | 트리거 없는 worker 빈만 추가하고 스케줄러를 도입하지 않는다 |
| `TASK.md` | 만료 후보 조회는 잠금 없는 스냅샷을 유지하고 정합성 근거는 행 잠금 재검사다 |
| `PostRecipient.java:263` | `expire()`는 `AVAILABLE`/`DISCOVERED`/`OPENED`에서만 가능하고 재호출 시 예외 |
| `ReceiveSlotReleaseService.java:76` | `expire()`는 `findByIdForUpdate` 후 검사 중 답변을 재확인한다 |
| `ReceiveSlotReleaseService.java:98` | `confirmSkip()`은 유예를 자체 재확인하고 `findById`(비잠금)를 쓴다 |
| `RecipientReceiveStateSql.java:85` | `RELEASE`는 `active_unhandled_count > 0`에서만 1 감소하고 영향 행 수를 반환한다 |
| `PostRecipientSql.java:87` | 만료 후보는 `NOT EXISTS(SUBMITTED, SAFETY_CHECKING answer)`로 축소된다 |
| `application.properties:34` | 넘김 유예는 `qello.direction.skip-confirmation-grace-seconds` |
| `AGENTS.md` §3 | JUnit 5, `@DisplayName`, 클래스 헤더에 ISO 8601 생성 시각과 계획 ID |
| `AGENTS.md` §4.10 | 좌표·내부 식별자·비밀값을 로그와 보고서에 남기지 않는다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 재실행 시 슬롯 카운터 중복 감소 | 상한 초과 수신 | Medium | P0 | 같은 sweep 2회 실행 후 카운터 변화가 1회분뿐임을 DB에서 확인 |
| 실행기가 batch 전체를 한 트랜잭션으로 묶음 | 한 행 실패가 전체 롤백 | Medium | P0 | 실패 주입 후 다른 행의 전이가 커밋된 상태로 남음을 새 커넥션에서 확인 |
| 만료 sweep이 검사 중 답변 항목을 선점 | 정상 답변이 영구 미공개 | Medium | P0 | 후보 조회 후 제출된 답변에 대해 `expire()`가 empty 반환 |
| 유예 경계 오차(off-by-one) | 되돌릴 수 있어야 할 넘김이 조기 확정 | Medium | P0 | `at - grace` 정확히 일치·1초 전후 3케이스 |
| `confirmSkip`의 비잠금 조회와 `revertSkip` 경합 | 되돌린 항목이 확정되거나 슬롯 이중 감소 | Low | P0 | latch 동시 실행에서 둘 중 하나만 성립하고 감소는 최대 1회 |
| limit 없는 조회로 대량 만료 시 장시간 점유 | 응답 지연·잠금 확산 | Medium | P1 | 후보 > limit일 때 처리 건수가 정확히 limit |
| 정렬 없는 limit으로 특정 행이 영구 미처리 | 슬롯 영구 점유 | Medium | P1 | 반복 실행이 남은 대상을 결정적 순서로 모두 소진 |
| 만료 sweep이 `SKIP_PENDING`을 후보에 포함 | 되돌리기 유예 설계 파괴 | Low | P1 | `SKIP_PENDING` 행이 만료 후보에서 제외됨 |
| 결과·로그에 좌표·본문·사용자 식별자 노출 | 개인정보 유출 | Low | P1 | 결과 record 필드와 로그 인자에 해당 값 부재 |
| 이미 0인 카운터에서 만료 전이 | 상태와 카운터 불일치 은폐 | Low | P2 | 상태는 `EXPIRED`, 카운터는 0 유지, 예외 없음 |

## 5. Unit scenarios

Mockito 기반이며 `DirectionMatchingWorkerTest`의 스타일을 따른다. 대상은 실행기
클래스이고 `ReceiveSlotReleaseService`는 mock으로 대체한다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| ...-UNIT-001 | 만료 sweep 명령의 limit이 N | `processBatch` 실행 | 후보 조회에 limit N과 batch 시각이 그대로 전달된다 | P0 | Unit executor |
| ...-UNIT-002 | 후보 3건 | `processBatch` 실행 | 각 `postRecipientId`로 `expire()`가 정확히 1회씩 호출된다 | P0 | Unit executor |
| ...-UNIT-003 | 후보 3건 중 1건의 `expire()`가 `Optional.empty()` | `processBatch` 실행 | `released=2`, `ineligible=1`, `failed=0` | P0 | Unit executor |
| ...-UNIT-004 | 후보 3건 중 2번째 `expire()`가 `RuntimeException` | `processBatch` 실행 | 3번째도 호출되고 `failed=1`, `released=2`, 예외가 batch 밖으로 전파되지 않는다 | P0 | Unit executor |
| ...-UNIT-005 | 후보 0건 | `processBatch` 실행 | `scanned=0`이고 `expire()` 호출이 없다 | P1 | Unit executor |
| ...-UNIT-006 | `limit <= 0`인 명령 | `processBatch` 실행 | `DirectionException(INVALID_VALUE_RANGE)`로 거절되고 후보 조회가 호출되지 않는다 | P1 | Unit executor |
| ...-UNIT-007 | 명령의 `at`이 null, Clock 고정 | `processBatch` 실행 | Clock의 현재 시각으로 후보를 조회한다 | P1 | Unit executor |
| ...-UNIT-008 | 넘김 sweep 명령의 limit이 N | `processBatch` 실행 | 유예 계산은 서비스가 소유하고 실행기는 batch 시각과 limit만 전달한다 | P0 | Unit executor |
| ...-UNIT-009 | 넘김 후보 3건 중 1건이 `Optional.empty()` | `processBatch` 실행 | `released=2`, `ineligible=1` | P0 | Unit executor |
| ...-UNIT-010 | 넘김 후보 중 1건이 예외 | `processBatch` 실행 | 나머지가 계속 처리되고 `failed=1` | P0 | Unit executor |
| ...-UNIT-011 | 임의의 batch 결과 | 카운터 합산 | `released + ineligible + failed == scanned` 불변식이 성립한다 | P1 | Unit executor |
| ...-UNIT-012 | 만료·넘김 batch 결과 | 결과 객체 검사 | 좌표·답변 본문·`recipientId`·`postId` 필드가 존재하지 않는다 | P1 | Unit executor |
| ...-UNIT-013 | 음수 카운터로 결과 생성 시도 | 생성자 호출 | `DirectionException(INVALID_VALUE_RANGE)` | P2 | Unit executor |

## 6. Integration scenarios

`PostgisContainerIntegrationTestSupport`를 상속하고 실제 PostgreSQL/PostGIS를 쓴다.
픽스처는 `ReceiveSlotReleaseIntegrationTest`의 패턴(전용 `REGION` 코드, `@BeforeEach`
테이블 정리, 고정 `NOW`)을 따른다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| ...-INT-001 | 만료 sweep 실행기, `post_recipient`, `recipient_receive_state` | 만료된 질문글의 `AVAILABLE` 수신 1건, 카운터 1 | 만료 sweep 1회 | 상태 `EXPIRED`, 카운터 0, `released=1` | `@BeforeEach` 정리 |
| ...-INT-002 | 동일 | INT-001 상태에서 이어서 | 만료 sweep 재실행 | `scanned=0`, `released=0`, 카운터 0 유지 | 동일 |
| ...-INT-003 | 동일 | 만료되지 않은 질문글의 수신 1건, 카운터 1 | 만료 sweep 1회 | 상태 변화 없음, 카운터 1 유지 | 동일 |
| ...-INT-004 | 동일 | 만료된 질문글의 `SKIP_PENDING` 수신 1건 | 만료 sweep 1회 | 후보에 포함되지 않고 상태·카운터 불변 | 동일 |
| ...-INT-005 | 만료 sweep, `answer` | 만료된 질문글에 `SUBMITTED` 답변이 있는 수신 1건 | 만료 sweep 1회 | 후보에서 제외되어 상태·카운터 불변 | 동일 |
| ...-INT-006 | 만료 sweep, 답변 제출 | 후보 목록을 먼저 확보한 뒤 같은 행에 답변을 제출·커밋 | 확보한 후보로 `expire()` 호출 | 잠금 재검사로 `Optional.empty()`, 상태·카운터 불변 | 동일 |
| ...-INT-007 | 넘김 sweep | `skip_requested_at = NOW - (grace - 1s)`인 `SKIP_PENDING` | 넘김 sweep 1회 | 확정되지 않고 카운터 불변 | 동일 |
| ...-INT-008 | 넘김 sweep | `skip_requested_at = NOW - grace` 정확 일치 | 넘김 sweep 1회 | `SKIPPED` 전이, 카운터 1 감소 | 동일 |
| ...-INT-009 | 넘김 sweep | `skip_requested_at = NOW - (grace + 1s)` | 넘김 sweep 1회 | `SKIPPED` 전이, `capacity_released_at` 설정, 카운터 1 감소 | 동일 |
| ...-INT-010 | 넘김 sweep | INT-009 상태에서 이어서 | 넘김 sweep 재실행 | `scanned=0`, 카운터 추가 감소 없음 | 동일 |
| ...-INT-011 | 만료 sweep, limit | 만료 후보 5건, limit 2 | 만료 sweep 3회 반복 | 회차별 2·2·1건 처리, 총 5건 `EXPIRED`, 누락·중복 없음 | 동일 |
| ...-INT-012 | 만료 sweep, 실패 주입 | 후보 3건 중 1건이 실패하도록 주입 | 만료 sweep 1회 | 실패 행은 원래 상태 유지, 나머지 2건은 별도 커넥션에서도 `EXPIRED`로 확인됨 | 동일 |
| ...-INT-013 | 만료 sweep, `AnswerNotificationService.publish` | 만료된 질문글, 검사 통과 답변 1건, 카운터 1 | 두 스레드로 sweep과 publish 동시 실행 | 정확히 하나만 성립하고 카운터는 총 1회만 감소 | 동일 |
| ...-INT-014 | 넘김 sweep, `PostRecipientService.revertSkip` | 유예가 막 지난 `SKIP_PENDING`, 카운터 1 | 두 스레드로 확정과 되돌리기 동시 실행 | 확정 또는 되돌림 중 하나만 성립, 카운터 감소는 최대 1회, 상태·카운터가 서로 모순되지 않음 | 동일 |
| ...-INT-015 | 만료 sweep, `SafetyService.block` | 만료된 질문글의 미종결 수신 1건, 카운터 1 | 두 스레드로 sweep과 차단 동시 실행 | 상태는 `EXPIRED` 또는 `BLOCKED` 중 하나, 카운터 감소는 정확히 1회 | 동일 |
| ...-INT-016 | 만료 sweep | 한 수신자가 서로 다른 질문글에서 만료 항목 3건, 카운터 3 | 만료 sweep 1회 | 3건 모두 `EXPIRED`, 카운터 0, 음수 없음 | 동일 |
| ...-INT-017 | 만료 sweep | 만료 항목 1건, 카운터가 이미 0 | 만료 sweep 1회 | 상태 `EXPIRED`로 전이되고 카운터는 0 유지, 예외 없이 커밋 | 동일 |
| ...-INT-018 | 두 sweep 동시 | 만료 후보와 넘김 후보가 같은 수신자에 각 1건, 카운터 2 | 만료·넘김 sweep을 동시에 실행 | 각각 1회씩 총 2회 감소해 카운터 0, 교착 없음 | 동일 |

## 7. Cross-cutting scenarios

### Database and transactions

- 실행기는 자체 트랜잭션을 열지 않는다. 각 행의 커밋 경계는
  `ReceiveSlotReleaseService`의 `@Transactional` 메서드다(INT-012가 별도 커넥션
  조회로 증명).
- 조건부 전이가 0행을 갱신하면 `release()`가 호출되지 않는다. `RELEASE` SQL의
  `active_unhandled_count > 0` 조건은 음수 방지일 뿐 중복 방지 근거가 아니므로
  INT-002·INT-010은 카운터뿐 아니라 `released` 카운터가 0인지도 함께 확인한다.
- `ck_post_recipient_status_timestamps`와 `ct_post_recipient_capacity_release`가
  전이 결과에 대해 만족되는지 INT-009에서 `capacity_released_at`으로 확인한다.
- 후보 조회는 잠금 없는 스냅샷을 유지한다. INT-006이 이 스냅샷의 낡음을 재현하고
  행 잠금 재검사가 최종 판정임을 증명한다.

### Concurrency and idempotency

- INT-013·INT-014·INT-015·INT-018은 `CountDownLatch`로 두 스레드를 같은 시점에
  풀고, 각 스레드의 결과와 최종 DB 상태를 함께 단언한다.
- 멱등성 판정 기준은 "카운터의 총 변화량"이다. 어느 경로가 이겼는지는 비결정적일
  수 있으므로 승자 자체를 단언하지 않고, 성립한 전이가 정확히 하나이며 감소가
  1회임을 단언한다.
- 반복 실행(INT-002·INT-010·INT-011)은 sweep이 자연 멱등이 아니라 *상태 기반으로*
  멱등함을 확인한다. 즉 두 번째 실행에서 후보 자체가 사라진다.

### External APIs

- 이 이슈에는 외부 API 연동이 없다. moderation 공급자, S3, FCM/APNs는 호출되지
  않는다. 통합 테스트는 외부 도구(LocalStack 등)를 추가로 띄우지 않는다.
- 답변 공개 경로(INT-013)는 이미 판정이 내려진 상태를 픽스처로 만들어 외부
  moderation 호출 없이 구성한다.

### Failure recovery and reconciliation

- 실패한 행은 상태가 바뀌지 않으므로 다음 sweep의 후보로 다시 잡힌다.
  INT-012는 실패 후 재실행에서 그 행이 정상 처리되는지까지 확인한다.
- batch가 중간에 중단되어도(프로세스 종료 가정) 이미 커밋된 행은 유지되고 남은
  행은 다음 실행이 이어받는다. INT-011의 반복 실행이 이 성질을 대신 증명한다.
- 결과 카운터는 관측용이며 정합성의 근거가 아니다. 모든 정합성 단언은 결과 객체가
  아니라 DB 상태를 직접 조회해 수행한다.

## 8. Test data and isolation

- Fixtures: 전용 지역 코드 `TEST-EXPSWEEP`으로 계정을 만들고, 질문글·수신 항목·
  수신 상태를 `JdbcTemplate`으로 직접 구성한다.
  `ReceiveSlotReleaseIntegrationTest`의 `question()`/`post()`/`available()`/
  `receiveState()` 헬퍼 구조를 재사용하되 이 클래스 안에 독립적으로 둔다.
- Database isolation: `@BeforeEach`에서 `answer`, `post_recipient`, `post_audience`,
  `direction_post`, `recipient_receive_state`, `approved_question`, `user_block`,
  해당 지역의 `user_account`, `region_code` 순으로 삭제한다. 다른 통합 테스트와
  지역 코드가 겹치지 않게 한다.
- Clock/randomness: 고정 `Instant NOW = 2026-08-17T12:00:00Z`, 기준선
  `BASELINE = NOW - 3600s`를 `matched_at`·`submitted_at`에 쓴다. 유예 경계
  시나리오는 설정값 `qello.direction.skip-confirmation-grace-seconds`를 테스트에서
  읽어 상대 시각으로 계산하고 상수 5를 하드코딩하지 않는다. 무작위 값은 쓰지
  않는다.
- External API doubles: 없음. 실패 주입(INT-012)은 외부 도구가 아니라 스파이로
  감싼 `ReceiveSlotReleaseService`나 제약 위반 유도로 구성한다.
- Cleanup: 컨테이너는 기존 지원 클래스가 공유 관리한다. 테스트가 만든 스레드 풀은
  `awaitTermination` 후 종료한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Unit executor | `src/test/java/com/dnd/qello/direction/sweep/RecipientExpirationSweepWorkerTest.java`, `src/test/java/com/dnd/qello/direction/sweep/SkipConfirmationSweepWorkerTest.java` | UNIT-001 ~ UNIT-013 | `./gradlew test --tests 'com.dnd.qello.direction.sweep.*'` |
| 2 | Integration executor | `src/integrationTest/java/com/dnd/qello/RecipientSweepIntegrationTest.java` | INT-001 ~ INT-012, INT-016, INT-017 | `./gradlew integrationTest --tests 'com.dnd.qello.RecipientSweepIntegrationTest'` |
| 3 | Concurrency executor | `src/integrationTest/java/com/dnd/qello/RecipientSweepConcurrencyIntegrationTest.java` | INT-013 ~ INT-015, INT-018 | `./gradlew integrationTest --tests 'com.dnd.qello.RecipientSweepConcurrencyIntegrationTest'` |
| 4 | Independent verifier | 없음(읽기 전용) | 전체 | `./harness check`, `./harness pr-ready --project-tests` |

세 실행자의 소유 파일은 서로 겹치지 않는다. 기존 테스트 파일
(`ReceiveSlotReleaseIntegrationTest`, `DirectionDomainTest`,
`InboxCommandConcurrencyIntegrationTest`)은 어떤 실행자도 수정하지 않는다.
구현 코드 수정은 테스트 실행자의 권한이 아니며, 테스트를 통과시키기 위한
production 코드 변경은 금지한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: Byuntil
- Decision: Approved
- Approved at: `2026-08-17` (현재 Claude Code 대화)
