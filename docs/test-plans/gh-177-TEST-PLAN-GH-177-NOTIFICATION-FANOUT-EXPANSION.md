# Test Plan: TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION

> Created at: `2026-08-20T18:50:13+09:00`
> GitHub Issue: `#177`
> Status: Approved

## 1. Objective

`ANSWER_PUBLISHED`, `ANSWER_REACTED`, `QUESTION_PROPOSAL_REVIEWED`,
`QUESTION_RECOMMENDED` 이벤트가 저장된 aggregate를 기준으로 정확한 한 사용자에게
인앱 알림을 만들고, #176 알림함에서 올바른 이동 대상으로 보이는지 검증한다.

중복·경합·lease 재선점에도 알림이 한 건으로 수렴해야 하며, 차단·계정 게이트는 알림
행 자체를 막고 preference는 delivery만 막아야 한다. producer의 도메인 저장과 outbox
발행은 같은 트랜잭션에서 함께 성공하거나 함께 롤백해야 한다.

## 2. Scope

### Included

- 새 네 이벤트만 claim하는 공통 fan-out worker의 lease, fencing, event별 transaction,
  retry/DEAD, failure-recording isolation
- 이벤트별 resolver의 aggregate·payload 검증과 수신자·알림 종류·target·dedup 결정
- `ANSWER_REACTED`, `QUESTION_RECOMMENDED` producer의 트랜잭션·멱등·경합
- `ANSWER_PUBLISHED`에서 질문글 작성자 한 명에게만 `ANSWER_RECEIVED` 생성
- `ANSWER_REACTED`에서 답변 작성자에게만 생성하고 소비 전 취소된 공감 suppress
- `QUESTION_PROPOSAL_REVIEWED`에서 저장된 proposer에게 target 없는 알림 생성
- `QUESTION_RECOMMENDED`에서 assignment별 이벤트를 만들고 cycle 사용자에게 target 없는 알림 생성
- 사용자 간 이벤트의 양방향 차단·양쪽 ACTIVE 계정 게이트와 시스템 이벤트의 수신 계정 게이트
- preference off에서도 `notification`은 남고 `notification_delivery`만 생기지 않는 순서
- #176 목록·진입 판정의 `ANSWER`/`NONE` targetKind와 기존 targetState 우선순위
- PostgreSQL unique constraint, transaction rollback, concurrent claim/reclaim 증거

### Excluded

- `REPORT_RESOLVED`와 `notification.report_id`(`#155`)
- 알림 설정 API(`#178`), Push provider·토큰·발송(`#179`), 묶음(`#180`), scheduler(`#182`)
- 공감 취소 후 이미 생성된 알림의 `REVOKED` 전이와 delivery 취소
- API endpoint·OpenAPI·Flyway migration 변경
- 실제 외부 Push API, 배포, 인프라, 프로덕션 데이터 검증

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #177 | 같은 outbox 이벤트를 두 번 처리해도 `uq_notification_recipient_dedup`으로 알림 1건 |
| GitHub Issue #177 | 질문글 하나에 답변 N개면 질문글 작성자 N건, 다른 수신자 0건 |
| GitHub Issue #177 | 활성 차단 관계에서는 알림 행을 생성하지 않음 |
| GitHub Issue #177 | 종류별 단위 테스트와 PostgreSQL 통합 테스트 |
| GitHub Issue #177 | N1 목록에서 각 종류가 자기 `targetKind`로 노출됨 |
| `TASK.md` | 답변 알림은 `ANSWER`, 질문 제안 검토·추천은 `NONE`; preference는 delivery 직전에만 검사 |
| `TASK.md` | `AnswerReactionService`의 새 공감과 `QuestionAssignmentService`의 assignment 저장이 outbox와 원자적 |
| `NOTIFICATION_INBOX_DESIGN.md` | 알림 기록·인앱 상태·Push 전달은 서로 대체하지 않으며 preference off에서도 알림함은 채워짐 |
| `DIRECTION_COMMUNICATION_ERD.md` | `ANSWER_RECEIVED` 수신자는 질문글 작성자만, `ANSWER_REACTED` 수신자는 답변 작성자, 질문 종류는 이동 대상 없음 |
| DB V1/V2/V12/V18/V23 | 기존 event/type CHECK, `(recipient_id, dedup_key)` unique, lease generation과 claim/reclaim, N1 query schema를 그대로 사용 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| `ANSWER_PUBLISHED`를 모든 `PostRecipient`에게 fan-out해 M×N 알림 생성 | 사용자 스팸·F07 위반 | 높음 | P0 | N 답변·M 수신자 PostgreSQL 결과가 질문자 N, 타 수신자 0 |
| `AnswerReactionService`의 `REQUIRES_NEW` 삽입 경합에서 outbox 중복·누락 | 유일한 답변 반응 신호 손실 또는 중복 | 높음 | P0 | 동시 PUT 후 reaction 1, outbox 1, notification 1 |
| aggregate ID와 payload ID 불일치 또는 payload 수신자 신뢰 | 오발송·권한 누출 | 중간 | P0 | 저장된 aggregate 기준 수신자 판정과 불일치 event DEAD |
| 차단·계정 검사보다 알림 저장이 먼저 실행 | 차단 사용자에게 알림 원장 노출 | 중간 | P0 | 양방향 차단·비활성 계정에서 notification 0 |
| preference를 알림 저장 전에 검사 | Push off 사용자의 알림함 누락 | 높음 | P0 | preference off에서 notification 1, delivery 0 |
| lease 만료 worker와 reclaim worker가 함께 완료 | 중복 알림·상태 덮어쓰기 | 중간 | P0 | fencing 실패는 STALE, unique로 알림 1 |
| failure 상태 기록 예외가 batch 전체를 중단 | 후속 이벤트 장기 지연 | 중간 | P0 | `FAILURE_RECORDING_FAILED` 뒤 후속 event 정상 완료 |
| 공감 취소 후 늦은 worker가 오래된 이벤트를 전달 | 존재하지 않는 반응 알림 생성 | 중간 | P0 | 소비 전 cancel이면 notification 0, event는 terminal 처리 |
| assignment 일부 저장 실패 뒤 outbox만 남음 | 존재하지 않는 추천 알림 | 중간 | P0 | transaction rollback 후 cycle/assignment/outbox 모두 0 |
| 답변 알림에 `answer_id`가 빠짐 | N1 목록 target가 `NONE`이 되어 이동 불가 | 중간 | P0 | 실제 fan-out row 목록에서 `ANSWER`와 targetState 확인 |
| 질문 종류에 임의 target FK를 기록 | 스키마 계약 위반·잘못된 이동 | 낮음 | P1 | proposal/recommended row의 target FK null, 목록 `NONE` |
| 외부 Push 호출을 worker에 섞음 | #179 경계 침범·긴 트랜잭션 | 낮음 | P1 | 외부 client 의존성·호출 0, pending delivery까지만 검증 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-001 | 네 신규 event type이 등록된 worker | batch 처리 | claim set은 네 종류만 포함하고 `RECIPIENTS_CONFIRMED`·`REPORT_RESOLVED`는 포함하지 않음 | P0 | Notification executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-002 | null command, limit 0, blank owner, 닫힌 lease window, null retry policy | batch 처리 | `NotificationException`의 기능 경계 오류로 fail-fast하고 repository 미호출 | P0 | Notification executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-003 | lease owner·generation이 없는 claimed event | 처리 | `STALE_LEASE`, resolver·notification·terminal update 미호출 | P0 | Notification executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-004 | resolver가 유효한 fan-out instruction 반환 | 처리 | event별 transaction에서 gate→`saveIfAbsent`→delivery→fenced complete 순서로 `PROCESSED` | P0 | Notification executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-005 | resolver 또는 eligibility가 suppress 반환 | 처리 | notification 없이 fenced complete하고 `PROCESSED` | P0 | Notification executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-006 | notification 저장 중 transient data access 실패 | 처리 | retry policy로 fail 기록하고 `RETRYABLE` | P0 | Notification executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-007 | aggregate/payload 오류 또는 non-dedup 무결성 실패 | 처리 | permanent fail 기록 후 `DEAD` | P0 | Notification executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-008 | 첫 event의 fail 기록 자체가 예외, 다음 event 정상 | batch 처리 | 첫 결과 `FAILURE_RECORDING_FAILED`, 다음 결과 `PROCESSED`이며 순서 유지 | P0 | Notification executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-009 | preference enabled와 active device 2개 | notification 저장 | device별 pending delivery를 `saveDeliveryIfAbsent`로 2건 생성 | P1 | Notification executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-010 | preference disabled | notification 저장 | notification은 저장하고 device 조회·delivery 저장은 하지 않음 | P0 | Notification executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-011 | `saveIfAbsent`가 기존 notification 반환 | 같은 event 재처리 | 같은 notification ID로 delivery도 dedup되고 complete 가능 | P0 | Notification executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-012 | published answer, author recipient, post sender와 다른 수신자들 | `ANSWER_PUBLISHED` resolve | 수신자는 post sender 한 명, type `ANSWER_RECEIVED`, target answer ID, dedup `answer-received:{answerId}` | P0 | Answer executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-013 | aggregate type/ID·payload answerId 불일치 또는 answer/recipient/post 없음 | resolve | 수신자를 추측하지 않고 permanent failure | P0 | Answer executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-014 | 질문자 또는 답변 작성자 계정이 비활성 | eligibility 검사 | notification suppress | P0 | Answer executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-015 | 질문자↔답변 작성자 한 방향 이상 활성 차단 | eligibility 검사 | 양방향 모두 notification suppress | P0 | Answer executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-016 | 공감이 없고 자격 있는 reactor | `react` 성공 | reaction 저장과 같은 `REQUIRES_NEW` transaction에서 `ANSWER_REACTED` outbox 1건 발행 | P0 | Answer executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-017 | 이미 같은 reaction 존재 | 반복 `react` | reaction·outbox 추가 저장 없이 기존 count 반환 | P0 | Answer executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-018 | 동시 삽입 loser가 integrity failure 후 winner 행을 재조회 | `react` 복구 | loser는 count를 반환하고 두 번째 outbox를 만들지 않음 | P0 | Answer executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-019 | toggle 첫 호출은 생성, 다음 호출은 취소 | 두 번 toggle | 생성 때만 outbox 1건, 취소 때 outbox 없음 | P1 | Answer executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-020 | 현재 reaction과 answer가 존재 | `ANSWER_REACTED` resolve | 수신자는 answer author, actor는 reactor, target answer ID, notification dedup은 event ID에 안정적 | P0 | Answer executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-021 | event 발행 후 reaction 취소 | 소비 | 현재 reaction 부재를 확인해 notification suppress하고 event terminal 처리 | P0 | Answer executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-022 | answer author/reactor 비활성 또는 양방향 차단 | 소비 | notification suppress; 만료된 post라는 이유만으로는 suppress하지 않음 | P0 | Answer executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-023 | reviewed proposal의 payload proposer와 저장된 proposer가 다름 | resolve | 저장된 proposer가 수신자이고 type `QUESTION_PROPOSAL_REVIEWED`, target 없음 | P0 | Question executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-024 | proposal 없음·aggregate 불일치 또는 proposer 비활성 | resolve/eligibility | 구조 오류는 permanent, 비활성 수신자는 suppress | P0 | Question executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-025 | cycle에 저장된 assignment 3개 | `assign` | 저장된 assignment ID별 `QUESTION_RECOMMENDED` outbox 3건을 같은 transaction에서 발행 | P0 | Question executor |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-026 | assignment와 cycle owner가 존재하거나 owner가 비활성 | resolve/eligibility | 활성 owner에게 target 없는 `QUESTION_RECOMMENDED`; 비활성 owner는 suppress | P0 | Question executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-001 | answer publish, outbox, fan-out, PostgreSQL | 질문자·답변자·다른 수신자 2명과 published answer 1개 | worker 처리 | 질문자 `ANSWER_RECEIVED` 1건, 다른 수신자 0건, `answer_id` 설정 | transaction rollback/fixture cleanup |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-002 | answer publish/fan-out | 같은 질문글에 서로 다른 답변 N개, 수신자 M명 | 모든 event 처리 | 질문자 N건, 다른 수신자 각각 0건; 답변별 dedup key 구분 | 동일 |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-003 | outbox reclaim, notification unique | 동일 `ANSWER_PUBLISHED` event를 lease 만료 후 재처리 가능하게 구성 | 두 번 처리 | `(recipient_id, dedup_key)` notification 1건, event 최종 `PROCESSED` | 동일 |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-004 | N1 inbox query/target | 실제 fan-out으로 생성한 answer notification | 목록·target 조회 후 answer 숨김/삭제·차단 변경 | `targetKind=ANSWER`, 우선순위 `GONE > BLOCKED > HIDDEN > AVAILABLE` 유지 | 동일 |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-005 | `AnswerReactionService`, JPA, outbox | 같은 answer/reactor에 동시 `react` 2개 | barrier로 commit 경합 | reaction 1건, `ANSWER_REACTED` outbox 1건; 두 호출은 성공 상태로 수렴 | 명시 삭제/rollback |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-006 | reaction outbox/fan-out/inbox | 활성 author와 reactor, 현재 reaction | worker 처리 | author에게 `ANSWER_REACTED` 1건, reactor에게 0건, `targetKind=ANSWER` | 동일 |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-007 | reaction cancel/fan-out | outbox 발행 후 worker 전에 reaction 삭제 | worker 처리 | notification 0, outbox terminal; cancel 후 재공감은 새 outbox occurrence 생성 가능 | 동일 |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-008 | account/block/preference/delivery | answer event별 비활성 계정, 양방향 block, preference off matrix | worker 처리 | 비활성·차단은 notification 0; preference off는 notification 1/delivery 0 | 동일 |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-009 | proposal review, existing producer, fan-out | UNDER_REVIEW proposal을 승인/반려 | 기존 producer event를 worker가 처리 | 저장된 proposer에게 `QUESTION_PROPOSAL_REVIEWED` 1건, target FK null, 목록 `NONE` | 동일 |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-010 | proposal event dedup/reclaim | 같은 proposal event의 중복 처리와 비활성 proposer | 처리 | 활성 proposer는 notification 1, 비활성은 0; payload proposer 변조로 오발송 없음 | 동일 |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-011 | assignment JPA/outbox transaction | assignable question 3개와 실패하는 중간 assignment case | batch assign | 성공은 assignment/outbox 각각 3건; 실패는 cycle/assignment/outbox 모두 0 | 동일 |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-012 | assignment fan-out/inbox | active cycle owner와 assignment event | worker 처리 | owner에게 assignment별 `QUESTION_RECOMMENDED`, target FK null, 목록 `NONE`; 비활성 owner는 0 | 동일 |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-013 | PostgreSQL claim/reclaim/fencing | 서로 다른 owner worker와 expired lease, 한 event의 transient failure | 병렬 처리·재선점 | stale owner terminal update 0, reclaim owner 성공, notification·delivery 중복 없음 | 명시 cleanup |
| TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-014 | N1 list, all notification types | 기존 direction 알림과 실제 producer/consumer로 만든 새 4종 | cursor 목록 조회 | 5종 모두 노출, answer 2종은 `ANSWER`, question 2종은 `NONE`, 기존 direction은 `DIRECTION_POST` | 동일 |

## 7. Cross-cutting scenarios

### Database and transactions

- producer는 도메인 row와 outbox row를 한 transaction에서 저장한다. answer reaction의
  `REQUIRES_NEW` commit 실패는 해당 outbox도 롤백하고, assignment 중간 실패는 cycle·전체
  assignment·outbox를 함께 롤백한다.
- consumer는 claim transaction과 event별 fan-out transaction을 분리하고, notification 저장과
  fenced complete는 같은 event transaction에서 수행한다.
- `INSERT ... ON CONFLICT (recipient_id, dedup_key)`와 delivery dedup을 실제 PostgreSQL에서
  검증한다. H2나 repository mock만으로 unique/locking 증거를 대체하지 않는다.
- V1/V2/V12/V18/V23 외 새 migration을 만들지 않으며 기존 CHECK가 네 이벤트와 네 알림
  종류를 수용하는지 회귀 검증한다.

### Concurrency and idempotency

- 같은 answer/reactor의 동시 PUT은 `AnswerReactionService`의 `REQUIRES_NEW` 경합 복구 후
  reaction·outbox 각각 1건으로 수렴해야 한다.
- 동일 outbox event 재처리, lease expiry reclaim, stale owner complete/fail을 분리한다.
- N개의 서로 다른 답변은 서로 dedup되지 않아 질문자에게 N개를 만들고, 같은 답변의 같은
  event만 1개로 수렴해야 한다.
- batch 한 event의 failure 기록 예외가 후속 claimed event 처리를 중단하지 않아야 한다.

### External APIs

- 외부 API 없음. Push provider client를 호출하거나 새 mock server를 두지 않는다.
- 범위는 `notification_delivery` pending row 생성까지이며 실제 전송은 #179 소유다.

### Failure recovery and reconciliation

- transient data access는 retry policy의 `nextAttemptAt`으로 전이하고 permanent payload·aggregate
  오류는 DEAD로 전이한다.
- fail 기록 자체가 실패하면 lease가 만료된 뒤 원 이벤트가 reclaim될 수 있어야 하며, 별도
  `FAILURE_RECORDING_FAILED` 결과를 유지한다.
- 공감이 소비 전에 취소되면 현재 reaction 부재를 정상 suppress로 처리해 stale event를 알림으로
  만들지 않는다.
- 검증 후 남은 `PROCESSING` event, 중복 notification/delivery, source 없는 notification을 SQL로
  점검하고 보고서 잠재 문제 분석에 기록한다.

## 8. Test data and isolation

- Fixtures: `PostgisContainerIntegrationTestSupport`, #176 notification fixtures, answer/post-recipient,
  proposal/review, approved-question/cycle/assignment fixture를 사용하되 모든 자연키와 dedup key는
  scenario ID 기반으로 고유하게 만든다.
- Database isolation: 기존 통합 테스트의 transaction rollback을 우선 사용한다. 실제 commit
  경합 테스트는 전용 account/post/answer/outbox ID를 만들고 `finally`에서 worker thread와
  executor를 종료한 뒤 관련 row를 FK 역순으로 정리한다.
- Clock/randomness: `Clock.fixed`와 `Instant.parse`를 사용한다. claim 시각, lease 만료,
  reaction `createdAt`, assignment `assignedAt`을 scenario 상수로 고정한다.
- External API doubles: 없음. 단위 테스트에서 repository와 transaction manager만 Mockito로
  격리한다.
- Cleanup: notification_delivery → notification → outbox_event → reaction/assignment → aggregate
  순서를 지키며, 통합 테스트 종료 후 해당 scenario prefix row가 0인지 확인한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Notification executor | `src/test/java/com/dnd/qello/notification/fanout/NotificationFanOutWorkerTest.java` | UNIT-001~011, INT-003, INT-013 | worker claim/gate/retry unit verification |
| 2 | Domain executor | `src/test/java/com/dnd/qello/answer/service/AnswerReactionServiceTest.java`, `src/test/java/com/dnd/qello/notification/fanout/NotificationFanOutResolverTest.java`, `src/test/java/com/dnd/qello/question/service/QuestionAssignmentServiceTest.java` | UNIT-012~026 | producer and resolver unit verification |
| 3 | PostgreSQL executor | `src/integrationTest/java/com/dnd/qello/NotificationFanOutExpansionIntegrationTest.java` | INT-001~003, INT-005~013 | four event types, replay, reaction concurrency, transaction and target evidence |
| 4 | Query verifier | `src/integrationTest/java/com/dnd/qello/NotificationInboxQueryIntegrationTest.java` | INT-004, INT-014 | N1 query regression and target mapping |
| 5 | Regression verifier | existing notification/answer/question integration classes selected by validation command | existing contracts | #176 worker, answer persistence, question persistence regression |
| 5 | Report owner | `docs/test-reports/gh-177-TEST-REPORT-GH-177-NOTIFICATION-FANOUT-EXPANSION.md` | 전체 | harness test-run과 완료 명령, 잠재 문제 분석 |

각 executor는 표에 지정된 테스트 파일만 수정한다. 운영 코드 소유권은 `TASK.md`의 Ownership을
따르며, 테스트 파일 충돌이 필요해지면 오케스트레이터가 순서를 재조정하고 계획을 먼저 갱신한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 단위 시나리오 26개와 통합 시나리오 14개의 구현 또는 승인된 제외 사유 기록
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 종류별 실제 PostgreSQL notification/outbox/targetKind 증거 확인
- [ ] `./harness test-run --id TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION` 통과
- [ ] `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: human partner
- Decision: Approved for implementation and test execution
- Approved at: 2026-08-20T19:20:22+09:00
