# Test Plan: TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET

> Created at: `2026-08-25T12:41:06+09:00`
> GitHub Issue: `#180`
> Status: Approved — P0/P1 scope approved

## 1. Objective

같은 종류의 짧은 시간 내 notification이 알림함 원장을 잃지 않고 논리 push 한 건으로 묶이고,
사용자 단위 일일 예산·질문글 예약 우선권·quiet hours·질문 추천 빈도가 provider 호출 직전에
원자적이고 재시도 가능한 방식으로 적용되는지 검증한다.

가장 큰 실패 위험은 두 worker가 같은 notification을 서로 다른 group에 넣거나 일일 예산을
중복 소비하는 경합, 다중 기기에서 예산을 기기 수만큼 차감하는 오류, quiet 지연 뒤 오래된
권위값으로 발송하는 오류, 부분 성공 retry가 이미 성공한 기기에 중복 push를 보내는 오류,
그리고 묶음 구현이 `notification` 원장이나 콘텐츠 열람 자격을 변경하는 회귀다.

## 2. Scope

### Included

- `ANSWER_RECEIVED`·`ANSWER_REACTED` sliding 묶음과 종류 분리
- 즉시 알림 singleton group과 `QUESTION_RECOMMENDED` cycle group
- group/member 편입 멱등성, claim lease, generation fencing과 crash 회수
- 사용자 account timezone 기준 일일 예산과 질문글 예약량
- group당 1회 예산 소비, 다중 기기와 retry의 추가 소비 방지
- global/type OFF 우선순위와 기존 quiet 값 보존
- null·일반·overnight·DST gap/overlap quiet 판정과 최대 지연 취소
- 추천 동일 cycle 중복 억제와 cycle 간 최소 간격
- provider 호출 직전 member/device/차단/대상 유효성 재검사
- `type`, 동적 `count`, `hasRemainingTime` payload allowlist
- 기존 #179 FCM 결과, invalid token, retry, token redaction과 원장 불변 회귀
- V28 schema, constraint, index, migration inventory와 PostgreSQL query plan

### Excluded

- 실제 운영 묶음 창·최대 지연·일일 상한·예약량·추천 최소 간격 값의 확정
- 실제 FCM credential과 Android/iOS 실기기 end-to-end 발송
- 사용자 알림 설정 API 변경과 별도 quiet toggle
- scheduler/polling 활성화 — #182
- 모바일 UI, 잠금화면 문구, deep link와 알림함 목록 의미 변경
- Terraform, AWS resource, 배포와 production 변경

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #180 | 같은 종류 묶음 1건, 답변/공감 분리, 일 상한 뒤 원장 보존, quiet 종료 후 발송 또는 만료, 정책값 설정화 |
| `TASK.md` DEC-180-001~010 | 별도 논리 group/member, 사용자 local-date 예산, 질문글 예약 우선권, quiet 보존, cycle dedup, 운영 기본값 금지 |
| `APP-DESIGN-GH-180-001` | V28 세 테이블, group lease/fencing, 다중 기기 분리, group당 예산 1회, 깊은 dispatch module |
| #179 push pipeline | provider I/O는 transaction 밖, delivery retry/invalid-token/fencing, payload privacy allowlist, 원장 불변 |
| #178 notification preference | global/type 설정과 nullable quiet hours, overnight 허용, IANA Zone ID, global OFF가 quiet 값을 삭제하지 않음 |
| V1/V26 schema | `notification` 1행=1알림, delivery 기기별 unique, quiet 세 필드 all-or-none |
| AGENTS.md §3 | JUnit 5, 모든 method `@DisplayName`, 정확한 ISO 8601 class header와 source scenario, 단위·통합 분리 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 같은 notification이 두 group에 편입 | push 중복·예산 중복 | 중간 | P0 | 실제 PostgreSQL 동시 편입, unique/row lock 결과 |
| 답변과 공감 또는 서로 다른 사용자가 같은 group에 혼합 | 잘못된 문구·개인정보 경계 위반 | 낮음~중간 | P0 | type/recipient별 group assertion |
| 창 끝 경계에서 notification 유실·중복 | count 오류·push 누락 | 중간 | P0 | `collectUntil` 직전/정각/직후 deterministic Clock |
| 두 worker가 같은 group을 claim | 중복 provider 호출 | 중간 | P0 | `FOR UPDATE SKIP LOCKED` 동시성 테스트 |
| stale worker terminal update | 성공 상태 역행·재발송 | 중간 | P0 | lease 만료 회수와 generation 0행 assertion |
| 사용자 다중 기기가 예산을 여러 번 소비 | 일 상한 조기 소진 | 높음 | P0 | 기기 2대 provider 2회, budget 1 증가 |
| 일반 알림이 질문글 예약량까지 소비 | 질문글 도착 push 누락 | 중간 | P0 | 일반 limit과 priority total을 같은 budget row에서 검증 |
| local date를 UTC로 계산 | 자정 부근 상한 오판정 | 중간 | P0 | 서로 다른 account timezone의 같은 Instant 검증 |
| quiet 중 예산을 먼저 소비 | 실제 발송 없이 예산 소진 | 중간 | P0 | defer 전후 budget 불변 assertion |
| global OFF가 quiet 값을 삭제 | 재활성화 시 사용자 설정 손실 | 중간 | P0 | 설정 DB snapshot 보존과 provider 미호출 |
| overnight/DST 계산 오류 | 너무 일찍 발송·무한 지연 | 중간 | P0 | gap/overlap의 실제 next Instant 검증 |
| quiet 종료 뒤 오래된 eligibility 사용 | 차단·만료 뒤 push 발송 | 높음 | P0 | defer 뒤 정책/대상 변경 후 provider 0회 |
| 일부 member 무효인데 원래 count 사용 | 사용자 메시지 불일치 | 중간 | P0 | 유효 member distinct count와 취소 row 검증 |
| 부분 기기 성공 retry가 성공 기기에 재발송 | 중복 push | 중간 | P0 | 기기별 scripted provider와 상태/호출 횟수 |
| 추천 assignment마다 push 발송 | 같은 cycle 알림 폭주 | 높음 | P0 | 같은 cycle 3 notification, provider 1회 |
| 추천 최소 간격이 quiet/no-device에도 전진 | 다음 유효 cycle push 누락 | 중간 | P1 | `first_attempted_at` 미기록 assertion |
| payload에 member/internal ID 또는 본문 포함 | 잠금화면 개인정보 노출 | 중간 | P0 | FCM wire body allowlist/sentinel 검사 |
| group query가 backlog에서 full scan | dispatch 지연·DB 부하 | 중간 | P1 | bounded fixture `EXPLAIN`과 index 사용 증거 |
| migration check/FK가 상태 불변식을 놓침 | 복구 불가능한 group 상태 | 낮음~중간 | P0 | 실제 Flyway migration과 constraint rejection |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-001 | 명시적 fixture 값과 null·0·음수·reserved>limit·maxDelay<bundleWindow 조합 | policy properties 생성 | 정상 조합만 생성되고 production 기본값 없이 잘못된 필드가 제한된 오류로 거절됨 | P0 | Policy Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-002 | 답변 3개, 공감 2개, 같은 recipient | grouping policy 계산 | 답변과 공감은 각각 하나의 다른 group key/type이며 서로 섞이지 않음 | P0 | Grouping Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-003 | 첫 event T, T+window-1ns, T+window, T+window+1ns | join 판정 | 정각까지 같은 group, 직후는 새 group | P0 | Grouping Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-004 | 방향글·신고결과·제안검토 notification | grouping policy 계산 | 각각 notification별 singleton group이고 collectUntil=createdAt | P1 | Grouping Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-005 | 같은 user/cycle assignment 3개와 다른 cycle | recommendation group key 계산 | 같은 cycle key는 동일하고 다른 cycle은 다르며 assignment ID는 key 단위가 아님 | P0 | Grouping Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-006 | global OFF, type OFF, quiet ON이 동시에 존재 | suppression 평가 | global OFF가 우선 CANCEL이고 저장 quiet 변경 명령은 생성하지 않음 | P0 | Suppression Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-007 | quiet null, 22:00~07:00과 오전/야간 Instant | suppression 평가 | null은 SEND, overnight 내부는 정확한 다음 종료 Instant로 DEFER, 외부는 SEND | P0 | Suppression Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-008 | DST gap과 overlap이 있는 IANA zone의 quiet 종료 | 다음 허용 Instant 계산 | ZoneRules가 선택한 존재하는 실제 Instant이며 무한/과거 시각을 반환하지 않음 | P0 | Suppression Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-009 | quiet 종료가 policy expiry 직전·정각·직후 | 최대 지연 판정 | expiry 이하만 DEFER, 초과는 CANCEL/MAX_DELAY_EXCEEDED | P0 | Suppression Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-010 | daily=5, reserved=2, 일반 소비 0~3 | budget policy 평가 | 일반은 3건까지만 허용하고 이후 거절 | P0 | Budget Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-011 | 같은 budget row에서 일반 3건과 방향글 2건 | priority budget 평가 | 일반은 예약량을 못 쓰고 방향글은 total 5까지 허용 | P0 | Budget Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-012 | 같은 Instant와 Asia/Seoul·America/Los_Angeles account timezone | local date 계산 | 각 사용자 local date가 정확하고 quiet zone은 budget date에 사용하지 않음 | P0 | Budget Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-013 | 같은 cycle, 최근 시도 전/정각/후와 quiet/no-device group | 추천 빈도 판정 | 같은 cycle은 항상 억제, 다른 cycle은 최소 간격 정각부터 허용, 실제 시도 없는 group은 기준에서 제외 | P0 | Suppression Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-014 | 3 member 중 block/target invalid 1개 | member eligibility와 payload 생성 | invalid member 제외·취소, count="2", 세 key만 포함 | P0 | Payload Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-015 | 동일 notification의 기기 2대 delivery | logical count 계산 | delivery 행은 2개여도 count는 distinct notification 기준 1 | P0 | Payload Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-016 | COLLECTING/PENDING/PROCESSING/FAILED와 generation/lease | group state transition | 허용된 전이만 가능하고 stale generation terminal command는 생성되지 않음 | P0 | Grouping Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-017 | 이미 budgetConsumed인 retry group과 새 group | budget reservation 결정 | retry group은 추가 소비 없이 진행하고 새 group만 reserve를 요구 | P0 | Budget Unit Executor |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-018 | DIRECTION_POST_RECEIVED singleton과 answer bundle | payload 생성 | 방향글 count=1/hasRemainingTime 실제값, answer count>1/hasRemainingTime=false, 내부 ID 없음 | P0 | Payload Unit Executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-001 | Flyway, PostgreSQL catalog | V1~V27 schema | V28 migrate/validate와 invalid insert | 세 테이블·FK·check·unique·partial/due index 존재, 잘못된 상태/count/FK 거절 | container lifecycle |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-002 | planner, group repository | 같은 user/type의 T·T+5m·T+10m notification, window 10m | planner 반복 후 T+10m 직전/정각 claim | 한 group과 member 3개, 직전 claim 0·정각 claim 1, notification/delivery 행 수 불변 | 관련 row FK 역순 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-003 | planner, repository | 같은 user의 답변·공감과 다른 user 답변 | 동시에 편입 | recipient/type별 3 group, 교차 member 없음 | 관련 row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-004 | planner, PostgreSQL transaction | member 없는 같은 notification 묶음과 시작 latch | worker 2개 동시 plan | notification당 member 1개, 열린 group 1개, unique 오류 외부 노출 없음 | executor 종료·row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-005 | group claim repository | due group 4개와 processing lease | worker 2개 claim, lease 전/후 reclaim, stale terminal | claim 중복 0, 만료 뒤 generation+1, stale update 0행 | executor 종료·row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-006 | preference DB, Clock, worker | quiet 22~07, 23시 group, budget 0 | dispatch 후 07시 재호출 | 첫 호출 provider/budget 0·nextAttemptAt=07시, 두 번째 fresh recheck 후 발송 | 설정·group·delivery 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-007 | quiet policy, worker | quiet 종료가 maxDelay 뒤인 group | dispatch | provider/budget 0, 미발송 delivery CANCELLED, notification/열람 자격 보존 | 관련 row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-008 | preference API/repository, worker | quiet 값 저장 후 global/type OFF | dispatch·preference 재조회·global 재활성화 | OFF에서는 provider 0/delivery 취소, quiet start/end/zone 보존, 재활성화 뒤 새 group은 같은 quiet 일정으로 DEFER | 설정·관련 row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-009 | budget repository, PostgreSQL | daily=5/reserved=2, 일반 group 10개, latch | worker 다중 동시 reserve | 성공 정확히 3, consumedGeneral/total=3, 나머지 delivery 취소, notification/열람 자격 보존, 음수/초과 없음 | executor 종료·budget/group 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-010 | budget/dispatch | INT-009 상태와 방향글 group 3개 | 방향글 동시 reserve | 2개만 허용, total=5, 일반 group은 예약량 사용 불가 | 관련 row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-011 | account timezone, budget repository | 같은 UTC 시각의 서로 다른 timezone 사용자와 자정 전후 | dispatch | 각 local date row가 정확히 분리되고 날짜별 상한 독립 | 관련 row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-012 | group worker, fake provider | notification 3개·ACTIVE device 2대·예산 0 | dispatch | device별 provider 1회씩, 각 payload count=3, delivery 6개 SENT, budget total=1 | fake reset·관련 row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-013 | member eligibility, block/answer state | answer member 3개 중 block 1개·hidden 1개 | dispatch | 유효 1개만 count=1/SENT, 나머지 delivery CANCELLED, notification 3개 보존 | block·관련 row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-014 | multi-device provider/retry | device A accepted, B retryable 후 accepted | 두 번 dispatch | A 1회/B 2회, 성공 delivery 재전송 없음, budget total=1, group 최종 COMPLETED | fake reset·관련 row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-015 | invalid token transaction | group 2개에 같은 device 미발송 delivery | 첫 group invalid token | device INVALID, 모든 group의 해당 device 미발송 취소, 다른 device와 notification 보존 | 관련 row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-016 | question cycle/outbox/fan-out/planner | cycle A assignment 3개, interval 전 cycle B와 정각 cycle C, minInterval 24h fixture | 각 cycle fan-out·dispatch | notification 전부 유지, A provider 1회, B 억제, C 정각 허용, B는 frequency 기준을 전진시키지 않음 | question/outbox/notification FK 역순 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-017 | real FCM adapter, fake HTTP server | 유효 member 4개와 privacy sentinel | dispatch wire capture | data key 세 개, count="4", token 외 본문·닉네임·ID sentinel 없음, accepted ID가 member delivery에 반영 | server 종료·관련 row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-018 | upgrade planner | group 도입 전 형태의 PENDING/FAILED와 expired PROCESSING delivery | planner/dispatch | 별도 data migration 없이 group/member 편입, future/active lease 제외, 원래 attempt 의미 보존 | 관련 row 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-019 | PostgreSQL query planner | 운영 비율을 모사한 group/member/budget fixture | 편입·due claim·member lookup·budget SQL `EXPLAIN` | predicate/index/lock 계획과 row estimate 기록, unbounded full scan이면 FAIL 후 schema 재검토 | fixture 삭제 |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-020 | 전체 notification regression | #176~#179 테스트 fixture | 전체 unit/integration suite | 알림함 1행=1알림, preference, token redaction, FCM mapping, lease/retry/invalid-token 계약 전부 통과 | suite lifecycle |
| TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-021 | quiet defer와 fresh eligibility | quiet 중 defer된 group, 이후 global/type OFF·block·target 만료·device revoke 각각 | quiet 종료 뒤 dispatch | 각 변경을 새 snapshot으로 읽고 provider 0·미발송 delivery CANCELLED, notification/열람 자격 보존 | 설정·block·관련 row 삭제 |

## 7. Cross-cutting scenarios

### Database and transactions

- group 편입은 notification별 unique member와 열린 recipient/type partial unique를 함께 사용한다.
- group/member 생성은 전부 성공하거나 rollback하며 기존 notification/delivery를 삭제하지 않는다.
- group claim과 terminal은 group generation과 member delivery generation을 함께 확인한다.
- budget row와 group `budget_consumed_at`은 같은 transaction에서 갱신한다.
- provider network call은 claim commit 뒤와 terminal transaction 전에 수행한다.

### Concurrency and idempotency

- 동일 window plan, group claim, local-date budget reserve를 latch와 독립 transaction으로 재현한다.
- sleep 기반 우연한 순서가 아니라 row lock 획득/해제 barrier로 경합을 결정한다.
- 같은 planner/worker command 반복은 member, budget와 provider 성공 delivery를 중복시키지 않는다.
- recommendation cycle key와 DB unique constraint가 application pre-check race를 보완한다.

### External APIs

- 실제 FCM과 OAuth credential은 사용하지 않는다.
- local fake HTTP server와 기존 FCM adapter로 token/data wire contract를 검증한다.
- provider success/retryable/permanent/invalid-token 결과를 기기별 script로 분리한다.
- payload에는 실제 token fixture 외 개인정보 sentinel과 내부 ID를 넣지 않는다.

### Failure recovery and reconciliation

- planner commit 실패, budget update 실패, terminal 일부 update 실패를 trigger 또는 repository double로 주입한다.
- group lease 만료 뒤 이전 generation 결과는 반영되지 않고 새 worker가 미종결 device만 회수한다.
- provider accepted 뒤 terminal 실패는 at-least-once 중복 가능성을 남은 위험으로 보고한다.
- 운영 값이 `UNKNOWN`인 상태에서는 production readiness를 주장하지 않고 설정 주입 확인을 `BLOCKED`로 기록한다.

## 8. Test data and isolation

- Fixtures: bundleWindow `PT10M`, maxDelay `PT8H`, dailyLimit `5`, directionReserved `2`,
  recommendationMinInterval `PT24H`를 테스트 전용 값으로 명시한다. 운영 추천값이 아니다.
- Database isolation: test별 고유 coarse region, nickname, dedup/group key를 사용하고
  budget → group member → group → delivery → notification → outbox/device/account 순서의 FK를 확인해 정리한다.
- Clock/randomness: 고정 `MutableClock`과 명시한 IANA Zone ID를 사용한다. jitter가 필요한 기존
  retry는 deterministic backoff double을 사용한다.
- External API doubles: 기기별 scripted provider와 local fake FCM HTTP server를 사용한다.
- Cleanup: executor/latch/server/log appender/trigger/function을 `finally` 또는 lifecycle에서 제거한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Policy Unit Executor | `src/test/java/com/dnd/qello/notification/push/PushPolicyPropertiesTest.java`, `PushSuppressionPolicyTest.java` | UNIT-001, UNIT-006~009, UNIT-013 | `./gradlew test --tests '*PushPolicyPropertiesTest' --tests '*PushSuppressionPolicyTest'` |
| 2 | Grouping Unit Executor | `src/test/java/com/dnd/qello/notification/push/PushGroupingPolicyTest.java`, `PushDispatchGroupStateTest.java` | UNIT-002~005, UNIT-016 | `./gradlew test --tests '*PushGroupingPolicyTest' --tests '*PushDispatchGroupStateTest'` |
| 3 | Budget Unit Executor | `src/test/java/com/dnd/qello/notification/push/PushBudgetPolicyTest.java` | UNIT-010~012, UNIT-017 | `./gradlew test --tests '*PushBudgetPolicyTest'` |
| 4 | Payload Unit Executor | `src/test/java/com/dnd/qello/notification/push/PushPayloadFactoryTest.java` | UNIT-014~015, UNIT-018 | `./gradlew test --tests '*PushPayloadFactoryTest'` |
| 5 | Schema Executor | `src/integrationTest/java/com/dnd/qello/PushDispatchGroupMigrationIntegrationTest.java` | INT-001 | `./gradlew integrationTest --tests '*PushDispatchGroupMigrationIntegrationTest'` |
| 6 | Group Persistence Executor | `src/integrationTest/java/com/dnd/qello/PushDispatchGroupingIntegrationTest.java` | INT-002~005, INT-018~019 | `./gradlew integrationTest --tests '*PushDispatchGroupingIntegrationTest'` |
| 7 | Suppression Persistence Executor | `src/integrationTest/java/com/dnd/qello/PushDispatchSuppressionIntegrationTest.java` | INT-006~011, INT-016, INT-021 | `./gradlew integrationTest --tests '*PushDispatchSuppressionIntegrationTest'` |
| 8 | Dispatch Integration Executor | `src/integrationTest/java/com/dnd/qello/PushDeliveryDispatchIntegrationTest.java` | INT-012~015, INT-017 | `./gradlew integrationTest --tests '*PushDeliveryDispatchIntegrationTest'` |
| 9 | Regression Executor | 기존 #176~#179 unit/integration files, production 수정 금지 | INT-020 | `./gradlew test integrationTest` |
| 10 | Test Report Executor | `docs/reports/tests/gh-180-TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET.md` | 전체 | `./harness test-run --id TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET` 및 저장소 필수 검증. INT-020 수정 후 재실행 결과: Gradle `:test`/`:integrationTest` UP-TO-DATE `BUILD SUCCESSFUL` (XML 969/0, 715/0). harness exit 2, 기존 보고서 overwrite 거절. Gradle/`pr-ready`로 대체하지 않음 |

각 executor는 표의 파일만 소유하고 다른 executor나 사용자의 변경을 되돌리지 않는다.
production code와 migration은 승인된 implementation plan의 실행자가 별도로 소유한다.

## 10. Completion criteria

- [x] 모든 P0 시나리오 구현
- [x] P1 미실행 항목은 이유·영향·남은 위험·후속 검증 방법 기록
- [x] 모든 테스트 메서드에 `@DisplayName`
- [x] 모든 테스트 class header에 정확한 ISO 8601 생성 시각과 source scenario 기록
- [x] 단위와 통합 테스트 source set 분리
- [x] 동시성 테스트가 sleep 대신 latch/transaction으로 결정적으로 동작
- [x] 실제 credential·token·URL·계정 식별자 비노출
- [x] 단위 테스트 통과
- [x] 통합 테스트 통과
- [x] 애플리케이션·DB·동시성·transaction·외부 API·장애 복구 잠재 문제 분석
- [x] `templates/test-report.md` 기반 테스트 보고서 생성
- [x] `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` 결과 기록
- [ ] Executor 10 `./harness test-run --id TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET` 통과 (INT-020 수정 후 재실행: Gradle UP-TO-DATE SUCCESS, harness exit 2 overwrite)

## 11. Human approval

- Reviewer: `@Byuntil`
- Decision: `APPROVED`
- Approved at: `2026-08-25T12:48:40+09:00`
- Approval note: `APP-DESIGN-GH-180-001`과 본 계획의 P0/P1 범위를 그대로 승인한다.
