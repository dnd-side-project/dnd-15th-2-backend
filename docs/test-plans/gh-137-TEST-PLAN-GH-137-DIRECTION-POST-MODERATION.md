# Test Plan: TEST-PLAN-GH-137-DIRECTION-POST-MODERATION

> Created at: `2026-10-07T21:58:46+09:00`
> GitHub Issue: `#137`
> Status: Approved

## 1. Objective

질문글이 공통 moderation 비동기 파이프라인을 거쳐 PASSED가 되고, PASSED 질문글만 매칭 워커가 수신자를 확정하는지
검증한다. 아울러 중복되거나 늦게 도착한 판정 신호가 이미 정해진 상태를 되돌리지 않는지 확인한다.

실패하면 두 방향으로 문제가 생긴다. 지금처럼 PASSED 전이가 없으면 질문글이 만료될 때까지 아무에게도 전달되지 않는다.
반대로 전이 조건이 너무 넓으면 BLOCK된 질문글, 판정 전 질문글, 만료된 질문글이 수신자에게 전달된다. 판정 이벤트
분배를 바꾸면서 답변 공개 경로가 깨질 위험도 있다.

## 2. Scope

### Included

- `FilterTargetType.DIRECTION_POST`와 V32 마이그레이션: `filter_job`, `manual_review_case`는 `DIRECTION_POST`를 받고
  `appeal_case`는 계속 거절한다.
- `DirectionPost` moderation 전이: ALLOW → PASSED, BLOCK → REJECTED, deadline → REVIEW_HELD. 허용 출발 상태는
  PENDING·REVIEW_HELD이고 deadline은 PENDING만이다. 질문글이 `status = MATCHING`이고 `expiresAt > at`일 때만 바뀐다.
- 미디어 단독 질문글은 PASSED로 생성되고, 본문이 있는 질문글은 PENDING으로 생성된다.
- `DirectionPostService.send`: 본문이 있으면 질문글 저장과 같은 트랜잭션에서 job을 접수하고, 미디어 단독이면 접수하지
  않는다. 멱등 재요청은 job을 다시 접수하지 않는다.
- 판정 이벤트 분배: `AnswerModerationVerdictWorker`가 `targetType`별 적용기(`ModerationVerdictApplier`)로 나눠 보낸다.
  적용기가 없는 대상(`NICKNAME`)은 기존처럼 상태 변경 없이 완료한다.
- 질문글 적용기: 질문글을 `findByIdForUpdate`로 잠그고 전이가 있을 때만 저장한다. 질문글이 없으면 상태 변경 없이 끝낸다.
- 전체 흐름(통합): 제출 → 실행 워커(파이프라인 double) → 판정 워커 → 매칭 워커 → 수신자 확정
- 중복·늦은 판정, 만료 후 ALLOW, 판정 적용과 매칭 워커의 동시 실행
- 회귀: 답변 판정 반영 단위·통합 테스트, 매칭 워커 moderation gate 테스트

### Excluded

- 질문글 이의제기(`AppealCaseService`), 미디어 자체 검사
- `DirectionMatchingWorker` 변경. 기존 PENDING·REVIEW_HELD 재시도와 REJECTED 비매칭 테스트로 회귀만 확인한다.
- 매칭 이벤트 재시도 상한·backoff 운영값(저장소에 값이 없고 배포 때 주입된다, 위험 R8)
- 보이지 않는 문자만 있는 본문이 파이프라인에서 입력 오류가 되는 경로(#318이 답변 실행 경로를 제외했고 같은 경로를 탄다, 위험 R9)
- 실제 OpenAI 호출. 파이프라인은 test double로 대체한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #137 | 질문글 저장 API는 moderation provider 응답을 기다리지 않고 완료된다 |
| GitHub Issue #137 | `PENDING`, `REVIEW_HELD`, `REJECTED` 질문글은 매칭 워커가 수신자·슬롯을 만들지 못한다 |
| GitHub Issue #137 | `ALLOW/PASSED` 질문글만 매칭 워커의 실제 처리 대상이다 |
| GitHub Issue #137 | callback/job 처리는 멱등하고 중복 실행으로 상태가 역전되지 않는다 |
| GitHub Issue #137 | 늦은 callback이 만료된 질문글을 다시 매칭 가능하게 만들지 않는다 |
| GitHub Issue #137 | `글`, `미디어+글`, `미디어` 질문글이 매칭 대상이고, `미디어` 단독은 job 없이 처리되는 정책이 테스트로 고정된다 |
| GitHub Issue #137 | transaction, concurrency, retry/deadline 경계 테스트가 포함된다 |
| TASK.md D1 | BLOCK은 `moderation_status`만 REJECTED로 바꾸고 `status`는 MATCHING으로 남는다 |
| TASK.md D2 | 판정 이벤트는 filtering 모듈의 대상 종류별 적용기 인터페이스로 분배한다 |
| 기존 동작 | `AnswerModerationVerdictWorker`는 `MODERATION_VERDICT_READY`·`MODERATION_DEADLINE_ELAPSED`를 모두 claim하고 `ANSWER`가 아니면 상태 변경 없이 완료한다 |
| 기존 동작 | `AnswerModerationJobIntakeService.submit`은 승격된 release가 없으면 `NO_ACTIVE_RELEASE`로 거절한다(fail-closed) |
| 기존 동작 | `DirectionPostService.send`는 `TransactionTemplate` 한 트랜잭션에서 질문글·audience·미디어·매칭 Outbox를 저장한다 |
| 기존 동작 | `DirectionMatchingWorker`는 PENDING·REVIEW_HELD에 Retryable을 던지고 REJECTED면 매칭 없이 이벤트를 완료한다 |
| 기존 동작 | `DirectionPostPolicy.normalizeBody`는 NFC·strip 후 빈 본문을 null로 바꾼다. 미디어 단독 여부는 정규화된 본문이 null인지로 판단한다 |
| V10 스키마 | `filter_job`·`manual_review_case`·`appeal_case`의 `target_type` CHECK는 `ANSWER`·`NICKNAME`만 허용한다 |

## 4. Risk inventory

| ID | Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- | --- |
| R1 | ALLOW를 받아도 PASSED가 되지 않아 질문글이 전달되지 않음 | 높음 | 높음(현재 동작) | P0 | 전체 흐름에서 수신자 확정 (INT-005) |
| R2 | `AnswerModerationVerdictWorker`가 질문글 판정을 소비하고 버림 | 높음 | 높음(현재 동작) | P0 | DIRECTION_POST 이벤트가 질문글 적용기로 가고 답변 쪽은 호출되지 않음 (UNIT-011, INT-005) |
| R3 | BLOCK·판정 전·만료 질문글이 매칭됨 | 높음 | 중간 | P0 | UNIT-005~007, INT-006, INT-010 |
| R4 | 늦은 DEADLINE_ELAPSED가 PASSED를 REVIEW_HELD로 되돌림 | 중간 | 중간(이벤트 처리 순서가 보장되지 않음) | P0 | UNIT-005, INT-008 |
| R5 | 질문글은 저장됐는데 job이 없어 영원히 PENDING | 높음 | 낮음 | P0 | job 접수 실패 시 질문글·audience·Outbox 전부 rollback (INT-003) |
| R6 | 판정 이벤트 분배 변경으로 답변 공개가 깨짐 | 높음 | 중간 | P0 | 기존 답변 단위·통합 테스트 무수정 통과 (UNIT-013, INT-012) |
| R7 | 승격된 release가 없으면 질문글 제출이 실패함 | 중간 | 운영 설정에 달림 | P1 | INT-003이 fail-closed 동작을 고정한다. 기존 통합 테스트 8개에는 release fixture를 추가해야 한다(9절) |
| R8 | 매칭 이벤트 재시도가 판정보다 먼저 소진되어 DEAD가 되면 PASSED 후에도 매칭되지 않음 | 높음 | UNKNOWN(재시도 값이 배포 때 주입됨) | 사람 결정 | 이 계획에서는 검증하지 않는다. 보고서에 운영값 확인 필요로 남긴다 |
| R9 | 보이지 않는 문자만 있는 본문은 strip을 통과해 job이 되고, 실행 경로에서 재시도 소진 → REVIEW_HELD | 낮음 | 낮음 | P2 | 이 계획에서는 검증하지 않는다. 보고서에 후속 후보로 남긴다 |
| R10 | 판정 적용과 매칭 워커가 같은 질문글을 동시에 처리해 비PASSED 질문글에 수신자가 생김 | 높음 | 낮음 | P1 | INT-011 |
| R11 | 존재하지 않는 질문글 판정이 예외로 lease 만료마다 무한 재처리됨 | 중간 | 낮음 | P1 | UNIT-010 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-001 | `status = MATCHING`, `moderation_status = PENDING`, 만료 전 질문글 | ALLOW 반영 | PASSED가 되고 `canMatchAt(at)`가 true이며 `status`는 그대로다 | P0 | Unit executor |
| ...-UNIT-002 | 같은 PENDING 질문글 | BLOCK 반영 | REJECTED가 되고 `status`는 MATCHING 그대로다(D1) | P0 | Unit executor |
| ...-UNIT-003 | 같은 PENDING 질문글 | deadline 반영 | REVIEW_HELD가 된다 | P0 | Unit executor |
| ...-UNIT-004 | REVIEW_HELD 질문글(만료 전) | ALLOW / BLOCK 반영(parameterized) | 각각 PASSED / REJECTED가 된다 | P0 | Unit executor |
| ...-UNIT-005 | PASSED 또는 REJECTED 질문글 | ALLOW / BLOCK / deadline 반영(2×3 parameterized) | 상태가 바뀌지 않고 전이 없음으로 보고한다 | P0 | Unit executor |
| ...-UNIT-006 | REVIEW_HELD 질문글 | deadline 반영 | 상태가 바뀌지 않는다 | P0 | Unit executor |
| ...-UNIT-007 | PENDING 질문글이지만 `expiresAt <= at`이거나 `status`가 EXPIRED·DELETED | ALLOW 반영(parameterized) | 상태가 바뀌지 않는다 | P0 | Unit executor |
| ...-UNIT-008 | 정규화된 본문 null + 미디어 1개 / 본문 있음 | 질문글 생성 | 각각 PASSED / PENDING으로 생성된다 | P0 | Unit executor |
| ...-UNIT-009 | 질문글 적용기, `findByIdForUpdate`가 PENDING 질문글 반환 | ALLOW 적용 / 이미 PASSED인 질문글에 ALLOW 적용 | 전이가 있으면 `save` 1회, 전이가 없으면 `save` 0회 | P0 | Unit executor |
| ...-UNIT-010 | 질문글 적용기, `findByIdForUpdate`가 empty | ALLOW 적용 | 예외 없이 끝나고 `save`를 호출하지 않는다 | P1 | Unit executor |
| ...-UNIT-011 | 판정 워커에 답변·질문글 적용기 등록, DIRECTION_POST VERDICT_READY claim | `processBatch` | 질문글 적용기만 호출되고 `AnswerNotificationService`는 호출되지 않으며 claim이 완료된다 | P0 | Unit executor |
| ...-UNIT-012 | DIRECTION_POST DEADLINE_ELAPSED claim | `processBatch` | 질문글 적용기의 deadline 처리가 호출되고 claim이 완료된다 | P0 | Unit executor |
| ...-UNIT-013 | 기존 답변 시나리오(기존 UNIT-011~014, NICKNAME 스킵) | `processBatch` | 기존 assertion이 그대로 통과한다. 워커는 실제 답변 적용기 + mock `AnswerNotificationService`로 구성한다 | P0 | Unit executor |
| ...-UNIT-014 | 같은 batch에 실패하는 질문글 이벤트와 정상 답변 이벤트 | `processBatch` | 실패 이벤트는 FAILED이고 claim이 완료되지 않으며, 답변 이벤트는 RESOLVED다 | P1 | Unit executor |
| ...-UNIT-015 | 같은 `targetType`을 가진 적용기 두 개 | 워커 생성 | 생성 시점에 예외가 난다 | P1 | Unit executor |
| ...-UNIT-016 | 본문 있는 질문글 send(미디어 없음 / 미디어 1개, parameterized) | `send` | intake `submit`이 1회 호출된다(`FilterTarget(DIRECTION_POST, postId)`, 정규화 본문, KO, 질문글 id 기반 멱등키). 멱등키 접두사는 답변의 `answer-moderation:`과 다르다(`filter_job.idempotency_key`는 대상 종류와 무관하게 unique). 질문글은 PENDING이다 | P0 | Unit executor |
| ...-UNIT-017 | 미디어 단독 질문글 send | `send` | intake를 호출하지 않고, 저장되는 질문글은 PASSED다 | P0 | Unit executor |
| ...-UNIT-018 | 같은 sender·멱등키의 기존 질문글 | `send` 재요청 | intake를 호출하지 않는다 | P0 | Unit executor |
| ...-UNIT-019 | intake가 `NO_ACTIVE_RELEASE`를 던짐 | `send` | 예외가 호출자에게 그대로 전파된다. rollback은 INT-003에서 확인한다 | P1 | Unit executor |
| ...-UNIT-020 | `FlywayMigrationContractTest` 목록 | V32 추가 | 마이그레이션 목록 계약이 V32를 포함해 통과한다 | P0 | Unit executor |

`...`은 `TEST-PLAN-GH-137-DIRECTION-POST-MODERATION`을 줄인 표기다. 테스트 코드의 `@DisplayName`과 헤더에는 전체 ID를 쓴다.

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| ...-INT-001 | `DirectionPostService`, intake, PostGIS | 승격된 release, 활성 질문·presence | 본문 있는 질문글 `send` | 같은 커밋에 `direction_post`(PENDING) 1, `filter_job`(DIRECTION_POST, post id) 1, `MODERATION_EXECUTION_REQUESTED` 1, `RECIPIENT_MATCH_REQUESTED` 1이 있다. 파이프라인 double은 호출되지 않는다 | 클래스 공통 정리 |
| ...-INT-002 | 같은 구성 | 승격된 release, READY 미디어 1개 | 미디어 단독 `send` | `direction_post`는 PASSED, `filter_job` 0, `MODERATION_EXECUTION_REQUESTED` 0, `RECIPIENT_MATCH_REQUESTED` 1 | 클래스 공통 정리 |
| ...-INT-003 | 같은 구성 | 승격된 release 없음 | 본문 있는 질문글 `send` | `NO_ACTIVE_RELEASE` 예외. `direction_post`·`post_audience`·`outbox_event`·미디어 첨부 모두 0건(rollback) | 클래스 공통 정리 |
| ...-INT-004 | V32 스키마 | 마이그레이션 완료 DB | `filter_job`·`manual_review_case`·`appeal_case`에 `DIRECTION_POST` 행 insert | 앞의 둘은 성공하고 `appeal_case`는 CHECK 위반이다 | 행 삭제 |
| ...-INT-005 | send, 실행 워커(ALLOW 파이프라인 double), 판정 워커, 매칭 워커 | 승격된 release, 수신 후보 사용자 | send → 매칭 워커 1회(재시도) → 실행 워커 → 판정 워커 → backoff 이후 시각으로 매칭 워커 | 첫 매칭은 수신자 0·이벤트 재시도. 판정 후 질문글이 PASSED가 되고, 두 번째 매칭에서 수신자가 확정되며 `status`가 ACTIVE다. 답변 쪽 상태는 변하지 않는다 | 클래스 공통 정리 |
| ...-INT-006 | 같은 구성(BLOCK 파이프라인 double) | 같은 준비 | send → 실행 → 판정 → 매칭 | REJECTED, `status = MATCHING`, 수신자·슬롯 0, 매칭 이벤트 완료(재시도 아님) | 클래스 공통 정리 |
| ...-INT-007 | 판정 워커 | PASSED로 반영된 질문글 | 같은 VERDICT_READY를 lease 만료 후 재수집해 다시 처리 | 상태·수신자·Outbox 수가 변하지 않는다 | 클래스 공통 정리 |
| ...-INT-008 | 판정 워커 | ALLOW 반영 완료 | 같은 job의 DEADLINE_ELAPSED 처리 | PASSED 유지 | 클래스 공통 정리 |
| ...-INT-009 | 판정 워커, 매칭 워커 | PENDING 질문글 | DEADLINE_ELAPSED → 매칭 → 수동 검토 ALLOW에 해당하는 VERDICT_READY → 매칭 | REVIEW_HELD 동안 수신자 0. ALLOW 후 PASSED가 되고 수신자가 확정된다 | 클래스 공통 정리 |
| ...-INT-010 | 판정 워커, 매칭 워커 | PENDING 질문글을 만료 시각 이후로 둠(매칭 워커가 EXPIRED 처리) | 늦은 ALLOW 처리 → 매칭 워커 | `moderation_status`는 PENDING 그대로, `status`는 EXPIRED, 수신자 0 | 클래스 공통 정리 |
| ...-INT-011 | 판정 워커, 매칭 워커, 동시 실행 | PENDING 질문글, 매칭 이벤트·VERDICT_READY(ALLOW) 둘 다 due | 두 워커를 `CountDownLatch`로 동시에 실행 | 최종 상태가 둘 중 하나다: (a) 매칭이 먼저면 수신자 0·재시도, 이후 매칭에서 확정 (b) 판정이 먼저면 같은 실행에서 확정. 어느 쪽이든 PASSED 전 수신자는 0이고 수신자 중복이 없다 | 클래스 공통 정리 |
| ...-INT-012 | 기존 답변 통합 테스트 | 기존 setup | `AnswerModerationPublicationIntegrationTest`·`AnswerModerationRetryIntegrationTest` 실행 | 무수정 통과 | 기존 |
| ...-INT-013 | 실행 워커(항상 실패 파이프라인, maxAttempts 1) | 본문 있는 질문글 | 실행 워커로 재시도 소진 | `manual_review_case`에 `DIRECTION_POST` 행이 생기고(V32), 질문글은 PENDING 그대로다 | 클래스 공통 정리 |

## 7. Cross-cutting scenarios

### Database and transactions

- 질문글 저장과 job 접수가 한 트랜잭션이다. intake는 자체 `TransactionTemplate`을 쓰지만 REQUIRED로 send 트랜잭션에
  참여한다. 실패 시 전부 rollback된다(INT-001, INT-003).
- V32는 CHECK 제약을 drop/add로 바꾼다. 기존 `ANSWER`·`NICKNAME` 행이 유지되고 `appeal_case`는 바뀌지 않는다(INT-004).
- 판정 적용은 적용기 트랜잭션에서 하고 claim 완료는 별도 트랜잭션이다(기존 답변 경로와 같다). 적용 후 완료 전에 실패하면
  재처리되므로 적용은 멱등해야 한다(INT-007).

### Concurrency and idempotency

- 같은 VERDICT_READY를 두 판정 워커가 claim하는 경우는 기존 lease가 막는다. 답변 쪽 INT-013 회귀로 확인한다.
- 판정 적용과 매칭 워커는 둘 다 질문글 행을 `FOR UPDATE`로 잠근다(INT-011).
- send 멱등 재요청은 job을 다시 만들지 않는다(UNIT-018). intake 멱등키는 질문글 id에서 만든다.

### External APIs

- 공급자는 `ModerationPipelineService` double(ALLOW·BLOCK·항상 실패)로 대체한다. send 경로는 파이프라인을 호출하지 않는다(INT-001).

### Failure recovery and reconciliation

- 승격된 release가 없을 때 send는 fail-closed다(INT-003).
- 재시도 소진은 manual review로 넘어가고, deadline 신호로 REVIEW_HELD가 되며, 이후 수동 ALLOW로 회복된다(INT-013, INT-009).
- 존재하지 않는 질문글 판정은 상태 변경 없이 완료되어 무한 재처리되지 않는다(UNIT-010).

## 8. Test data and isolation

- Fixtures: release는 기존 `AnswerModerationReleaseTestFixture.promotedRelease`를 재사용한다. 질문·scheme·presence·미디어는
  `DirectionMatchingVerticalFlowIntegrationTest`의 준비 방식을 따른다. VERDICT_READY·DEADLINE_ELAPSED payload는
  `AnswerModerationEventPayloadsTestSupport`에 `targetType`을 받는 overload를 추가해 만든다.
- Database isolation: PostGIS Testcontainers. 새 통합 클래스는 기존 Direction 통합 테스트와 같은 정리 방식을 쓴다.
- Clock/randomness: `Clock.fixed`. 매칭 재시도는 테스트가 `at`을 backoff 이후로 지정한다. jitter 없는 retry policy를 쓴다.
- External API doubles: 파이프라인 double은 호출 수를 기록한다.
- Cleanup: 각 테스트가 만든 행을 클래스 공통 정리로 지운다. 동시성 테스트의 executor는 `@AfterEach`에서 `shutdownNow()`

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Implementation executor | `filtering/domain/FilterTargetType.java`, `db/migration/V32__allow_direction_post_moderation_target.sql`(신규), `filtering/moderation/ModerationVerdictApplier.java`(신규), `filtering/moderation/AnswerModerationVerdictWorker.java`, `answer/service/AnswerModerationVerdictApplier.java`(신규), `direction/service/DirectionPostModerationVerdictApplier.java`(신규), `direction/domain/DirectionPost.java`, `direction/service/DirectionPostService.java` | — | `./gradlew compileJava javaConventionCheck` |
| 2 | Unit executor | `src/test/java/com/dnd/qello/direction/domain/DirectionPostModerationTest.java`(신규, UNIT-001~008), `src/test/java/com/dnd/qello/direction/service/DirectionPostModerationVerdictApplierTest.java`(신규, UNIT-009~010), `src/test/java/com/dnd/qello/filtering/moderation/AnswerModerationVerdictWorkerTest.java`(UNIT-011~015 추가, 기존 메서드는 생성 방식만 변경), `src/test/java/com/dnd/qello/direction/DirectionPostSubmissionServiceTest.java`(UNIT-016~019 추가, 생성자 변경 반영), `src/test/java/com/dnd/qello/FlywayMigrationContractTest.java`(UNIT-020) | UNIT-001~020 | `./gradlew test` |
| 3 | Integration executor | `src/integrationTest/java/com/dnd/qello/DirectionPostModerationIntegrationTest.java`(신규, INT-001~011, 013), `src/integrationTest/java/com/dnd/qello/filtering/moderation/AnswerModerationEventPayloadsTestSupport.java`(overload 추가), 기존 통합 테스트 8개의 release fixture 추가(아래), `docs/reports/tests/gh-137-TEST-PLAN-GH-137-DIRECTION-POST-MODERATION.md` | INT-001~013, 전체 | `./harness check`, `./harness pr-ready --project-tests`, `git diff --check` |

- release fixture 추가 대상: `DirectionMatchingContractIntegrationTest`, `DirectionMatchingWorkerConcurrencyIntegrationTest`,
  `DirectionMatchingWorkerIntegrationTest`, `DirectionPostDistanceBandIntegrationTest`, `DirectionPostgisPersistenceIntegrationTest`,
  `DirectionRecipientSelectionIntegrationTest`, `ReceiveSlotReleaseIntegrationTest`, `ReceiveStateReservationIntegrationTest`.
  본문 있는 질문글을 `send`하는데 승격된 release가 없어 `NO_ACTIVE_RELEASE`로 실패하기 때문이다.
  실행 중 같은 이유로 `DirectionPostApplicationService.submit`을 거치는 5개를 더 찾았다(2026-10-07T22:14:59+09:00):
  `DirectionMatchingVerticalFlowIntegrationTest`, `DirectionMatchingVerticalFlowRecoveryIntegrationTest`,
  `QuestionRecommendationIntegrationTest`, `@Tag("performance")`인 `DirectionMatchingPerformanceIntegrationTest`·
  `DirectionMatchingE3PerformanceIntegrationTest`. 같은 setup 한 줄만 추가했다.
- 첫 전체 실행(2026-10-07T22:38:41+09:00)에서 질문글 job이 생기면서 깨진 기존 통합 테스트를 다음처럼 고쳤다. assertion의 의미는 바꾸지 않았다.
  - `DirectionMatchingVerticalFlowIntegrationTest`, `QuestionRecommendationIntegrationTest`: `filter_job` 삭제 전에
    `filter_job_status_history`를 지운다(FK 순서).
  - `DirectionMatchingWorkerIntegrationTest`: `outbox_event`를 `aggregate_id`만으로 조회하던 5곳에 `aggregate_type = 'DIRECTION_POST'`를
    더했다. 새 컨테이너에서 질문글 id와 job id가 같은 값이 되어 두 행이 나왔다.
  - `FlywayMigrationIntegrationTest`(최신 버전 32), `NotificationPreferenceMigrationIntegrationTest`(V24 이후 적용 수 8):
    V32 추가에 따른 기대값 갱신 바꾸는 범위는 setup에서
  `promotedRelease`를 호출하는 것뿐이고, 기존 assertion은 바꾸지 않는다. 미디어 단독으로 바꿔 우회하지 않는다.
- `DirectionPostApplicationServiceTest`와 `DirectionPostApiMockMvcTest`는 `DirectionPostService`를 mock하므로 바뀌지 않을 것으로
  본다. 컴파일이 깨지면 생성자 반영만 하고 보고서에 남긴다.
- 기존 테스트 클래스에 시나리오를 추가하면 그 클래스 헤더의 `Source scenario`에 이 계획의 ID와 추가 시각을 덧붙인다.
- Gradle 명령은 JDK 21(`JAVA_HOME`)로 실행한다. 통합 테스트는 Docker가 필요하다. `pr-ready`는 오래 걸리므로 백그라운드로 실행한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과(기존 답변·매칭 통합 테스트 포함)
- [ ] 잠재 문제 분석(R8, R9 포함)
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: 사용자(tkv00)
- Decision: 승인. R8(매칭 재시도 운영값)은 범위 밖으로 두고 보고서에 남긴다.
- Approved at: `2026-10-07T22:05:11+09:00`
