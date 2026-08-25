# Push 묶음 발행과 예산 억제 설계

> Date: 2026-08-25
> GitHub Issue: #180
> Design ID: `APP-DESIGN-GH-180-001`
> Status: `APPROVED_FOR_PLAN`
> Approved by: `@Byuntil` at `2026-08-25T12:48:40+09:00`

## 1. 목적과 불변식

이 설계는 짧은 시간에 생긴 같은 종류의 알림을 실제 push provider 호출에서만 묶고,
사용자 단위 일일 예산·기존 quiet hours·질문 추천 빈도를 적용한다.

다음 불변식은 변경하지 않는다.

- `notification`은 1행=1알림인 알림함 원장이다.
- `notification_delivery`는 notification과 기기별 provider 결과 원장이다.
- push 실패·지연·예산 초과·quiet 억제는 원장과 콘텐츠 열람 자격을 변경하지 않는다.
- provider payload에는 `type`, `count`, `hasRemainingTime`만 들어간다.
- provider I/O는 긴 DB transaction 밖에서 수행한다.
- scheduler 활성화는 #182가 소유한다.

## 2. 비교한 접근

### 선택안: 논리 group과 member, 일일 예산 원장을 별도 저장

`push_dispatch_group`이 수신자와 종류별 논리 push 한 건을 표현하고,
`push_dispatch_group_member`가 개별 notification을 연결한다. 기존 `notification_delivery`는
member notification과 기기를 통해 group에 참여하며 provider 결과 상태를 계속 소유한다.
`push_daily_budget`은 사용자 local date의 원자적 소비량만 소유한다.

이 방식은 사용자 단위 논리 push와 기기별 provider 호출을 분리한다. 한 사용자가 기기를
여러 대 등록해도 예산은 group당 한 번만 소비하고, 각 기기의 성공·retry·invalid token은
기존 delivery 상태로 독립 처리할 수 있다.

### 탈락안 A: `notification_delivery`에 group 컬럼만 추가

delivery는 기기별 행이므로 사용자 단위 예산을 한 번만 소비하는 대표 행을 별도로 정해야 한다.
대표 기기 해지, 부분 성공과 retry에서 대표성이 깨지고 claim·fencing 조건이 여러 행에 퍼진다.
기존 delivery interface가 묶음·예산·기기 결과를 모두 노출하는 shallow module이 되므로 탈락한다.

### 탈락안 B: DB 상태 없이 query 시점에만 묶음

매 claim마다 같은 종류 행을 조회해 임시 묶음을 만들 수 있지만 crash 뒤 동일 묶음 복원,
일일 예산 멱등 소비, 질문 추천 cycle 중복과 부분 기기 성공을 증명할 durable identity가 없다.
at-least-once provider 경계의 장애 복구가 불명확하므로 탈락한다.

## 3. Module과 seam

외부 seam은 기존 `PushDeliveryDispatchWorker.dispatchBatch` 하나를 유지한다. caller는 묶음
편입, quiet 계산, 예산 예약과 다중 기기 결과를 알 필요가 없다.

내부에는 다음 deep module을 둔다.

- `PushGroupingPolicy`: notification type과 cycle 정보를 받아 묶음 가능 여부, group key,
  수집 종료 시각을 계산한다.
- `PushSuppressionPolicy`: global/type 설정, quiet hours, 최대 지연, 추천 최소 간격을 받아
  `SEND`, `DEFER`, `CANCEL` 중 하나와 안전한 reason code를 반환한다.
- `PushBudgetPolicy`: 질문글 우선 여부와 설정값으로 일반 알림 한도와 전체 한도를 계산한다.
- `PushDispatchGroupRepository`: group/member 편입, claim/lease/fencing, 예산 소비와 delivery
  terminal update의 PostgreSQL adapter seam이다.
- `PushDeliveryDispatchWorker`: 위 module을 조합하고 transaction 밖에서 기기별 provider를 호출한다.

FCM adapter와 token protector interface는 #179 계약을 그대로 사용한다.

## 4. 데이터 모델

현재 migration inventory의 다음 버전인 `V28`에 세 테이블을 추가한다. rebase 충돌이 생기면
기존 migration을 수정하지 않고 다음 빈 버전으로 이동한다.

### `push_dispatch_group`

- `id BIGINT IDENTITY PRIMARY KEY`
- `recipient_id BIGINT NOT NULL` — `user_account` FK
- `notification_type VARCHAR(50) NOT NULL`
- `aggregation_key VARCHAR(255) NOT NULL UNIQUE`
- `status VARCHAR(20) NOT NULL`
- `window_started_at TIMESTAMPTZ NOT NULL`
- `collect_until TIMESTAMPTZ NOT NULL`
- `policy_expires_at TIMESTAMPTZ NOT NULL`
- `attempt_count INTEGER NOT NULL DEFAULT 0` — group lease generation
- `next_attempt_at TIMESTAMPTZ NOT NULL`
- `budget_local_date DATE`
- `budget_consumed_at TIMESTAMPTZ`
- `first_attempted_at TIMESTAMPTZ`
- `created_at TIMESTAMPTZ NOT NULL`
- `completed_at TIMESTAMPTZ`

상태는 `COLLECTING`, `PENDING`, `PROCESSING`, `FAILED`, `COMPLETED`, `CANCELLED`,
`DEAD`만 허용한다. `COLLECTING`인 `(recipient_id, notification_type)`은 하나만 허용하는
partial unique index를 둔다. `QUESTION_RECOMMENDED`의 `aggregation_key`는 recipient와
cycle ID로 결정해 같은 cycle을 한 group으로 만든다. 즉시 종류는 notification별 key를 쓴다.
budget date/consumed 시각은 둘 다 있거나 둘 다 없어야 하고, completed 시각은 terminal 상태에만
허용하는 check constraint를 둔다.

### `push_dispatch_group_member`

- `group_id BIGINT NOT NULL` — group FK, `ON DELETE CASCADE`
- `notification_id BIGINT NOT NULL UNIQUE` — notification FK, `ON DELETE RESTRICT`
- `created_at TIMESTAMPTZ NOT NULL`
- PK `(group_id, notification_id)`

notification 하나는 논리 group 하나에만 속한다. 여러 기기의 delivery는 member의
notification ID로 group에 합류하므로 중복 member를 만들지 않는다.

### `push_daily_budget`

- `user_id BIGINT NOT NULL` — `user_account` FK, `ON DELETE CASCADE`
- `budget_date DATE NOT NULL`
- `consumed_total INTEGER NOT NULL DEFAULT 0`
- `consumed_general INTEGER NOT NULL DEFAULT 0`
- `updated_at TIMESTAMPTZ NOT NULL`
- PK `(user_id, budget_date)`

두 count는 음수가 아니고 `consumed_general <= consumed_total`이어야 한다.

일반 group은 `consumed_general < dailyLimit - directionReserved`와
`consumed_total < dailyLimit`을 모두 만족할 때 두 값을 증가시킨다.
`DIRECTION_POST_RECEIVED`는 `consumed_total < dailyLimit`일 때 total만 증가시킨다.
group의 `budget_consumed_at`과 같은 transaction에서 갱신해 retry와 다중 기기가 예산을
다시 소비하지 않게 한다.

## 5. 묶음 규칙

- `ANSWER_RECEIVED`와 `ANSWER_REACTED`만 sliding collection window를 사용한다.
- 첫 notification의 `created_at`이 `window_started_at`이고
  `collect_until = window_started_at + bundleWindow`다.
- 같은 recipient와 같은 type이며 `created_at <= collect_until`인 notification만 열린
  `COLLECTING` group에 들어간다.
- 답변과 공감은 type이 다르므로 같은 group에 들어갈 수 없다.
- 다른 종류는 `collect_until = window_started_at`인 notification별 group을 만든다.
- 질문 추천은 assignment별 notification을 유지하면서 같은 cycle의 group에 합친다.
- payload `count`는 provider 호출 직전 유효성 검사를 통과한 서로 다른 notification 수다.

## 6. 발송 흐름

1. 짧은 transaction에서 아직 member가 없는 non-terminal delivery의 notification을 group에 편입한다.
   이때 `collect_until`이 지난 열린 group을 먼저 `PENDING`으로 닫은 뒤 새 group을 만든다.
2. `collect_until` 또는 `next_attempt_at`이 지난 group을 `FOR UPDATE SKIP LOCKED`로 claim하고
   `PROCESSING`, 증가한 generation, lease 만료 시각을 기록한다.
3. 같은 read snapshot에서 global/type 설정, quiet hours, recommendation history,
   notification/member 대상, block, account와 device 상태를 읽는다.
4. global/type OFF면 미발송 delivery를 `CANCELLED` 처리한다. quiet 설정값 자체는 바꾸지 않는다.
5. quiet 안이면 예산을 소비하지 않고 group을 종료 시각까지 `PENDING`으로 되돌린다.
   종료 시각이 `policy_expires_at` 뒤면 미발송 delivery를 `CANCELLED` 처리한다.
6. 유효 notification과 ACTIVE device가 없으면 provider와 예산을 건드리지 않고 취소한다.
7. device별 유효 delivery를 짧은 transaction에서 PROCESSING으로 claim하고 delivery generation을
   증가시킨다. claim된 device가 없으면 예산을 소비하지 않고 group을 취소한다.
8. 첫 provider 시도라면 recipient account timezone으로 local date를 계산하고 예산을 원자적으로
   소비한다. 상한 초과면 claim된 delivery를 포함한 미발송 delivery만 취소한다.
9. device별로 token을 복호화하고 같은 type/count payload를 provider에 한 번 호출한다.
10. 짧은 terminal transaction에서 group generation과 delivery generation을 함께 확인하고
   device별 member delivery를 `SENT`, `FAILED`, `DEAD`, `CANCELLED`로 반영한다.
11. retry 대상이 있으면 가장 이른 due 시각으로 group을 `FAILED` 처리하고, 모두 terminal이면
    `COMPLETED`로 끝낸다.

## 7. Quiet hours와 설정 우선순위

우선순위는 다음과 같다.

1. global `pushEnabled=false`
2. notification type `enabled=false`
3. notification/member·block·device·account 유효성
4. quiet hours
5. 질문 추천 cycle/최소 간격
6. 일일 예산
7. provider 호출

global OFF는 저장된 quiet 값을 삭제하지 않는다. `quietHours=null`일 때만 quiet가 OFF다.
overnight 구간과 DST gap/overlap은 `ZoneId` 규칙으로 다음 실제 `Instant`를 계산한다.
정책 시간 계산은 주입된 `Clock`만 사용한다.

## 8. 질문글 우선 예산

예산은 provider 호출 수가 아니라 사용자에게 보내려는 논리 group 수를 센다. 같은 group을
두 기기에 보내거나 retry해도 한 번이다.

일반 알림은 전체 상한에서 예약량을 뺀 구간까지만 사용할 수 있다. 질문글이 실제로 오지 않아도
예약량을 일반 알림에 빌려주지 않는다. 이는 뒤늦은 질문글이 이미 소진된 예산에 막히지 않게 하는
명시적 우선권이다.

## 9. 질문 추천 빈도

같은 recommendation cycle은 결정적 `aggregation_key`의 unique constraint로 한 group만 만든다.
다른 cycle은 가장 최근 `QUESTION_RECOMMENDED` group의 `first_attempted_at`과
`recommendationMinInterval`을 비교한다. 최소 간격 안이면 provider를 호출하지 않고 새 group의
미발송 delivery를 `CANCELLED` 처리한다. quiet로 지연됐거나 유효 기기가 없어 실제 provider
시도가 없던 group은 cycle 간 최소 간격 기준을 전진시키지 않는다.

## 10. 실패와 복구

- group 편입과 member insert는 unique constraint로 멱등하게 만든다.
- 두 worker의 열린 group 편입, claim과 예산 소비는 실제 PostgreSQL lock으로 검증한다.
- group lease 만료 후 generation을 증가시켜 회수하며 stale terminal update는 0행이어야 한다.
- 한 기기 성공 뒤 다른 기기 실패 시 성공 delivery는 다시 보내지 않고 실패 기기만 retry한다.
- provider 수락 뒤 DB 반영 전 crash의 at-least-once 중복 가능성은 #179과 동일한 남은 위험이다.
- invalid token은 기존 #179 계약대로 device를 `INVALID`로 만들고 그 기기의 미발송 delivery를
  모든 group에서 취소한다.
- 예산은 provider 호출 직전 소비하므로 호출 실패나 응답 유실 뒤 복원하지 않는다.

## 11. 설정과 운영 값

prefix는 `qello.notification.push.policy`다.

- `bundle-window: Duration`
- `max-delay: Duration`
- `daily-limit: int`
- `direction-reserved: int`
- `recommendation-min-interval: Duration`

`bundle-window`, `max-delay`, `recommendation-min-interval`은 양수이고 `max-delay`는
`bundle-window` 이상이어야 한다.
`daily-limit`은 양수이고 `direction-reserved`는 `0..daily-limit` 범위여야 한다.
운영 값은 모두 `UNKNOWN`이며 코드 기본값을 두지 않는다. production은 외부 설정으로 주입하고
test profile과 각 테스트는 민감하지 않은 명시적 fixture 값을 사용한다.

## 12. 검증과 rollout

- unit: grouping, quiet/DST, budget, recommendation frequency, payload count와 상태 결정
- integration: V28 migration, group 편입·claim·lease, 예산 경합, 다중 기기 부분 성공,
  quiet 중 설정 변경, recommendation cycle 중복, FCM wire payload
- regression: 기존 #179 lease/retry/invalid-token/redaction 테스트와 알림함 원장 불변
- query plan: 열린 group 편입, due claim, group member delivery 조회와 budget upsert를 `EXPLAIN`
- deployment/apply는 수행하지 않는다. #182 전까지 worker는 자동 실행되지 않는다.
- 기존 non-terminal delivery는 별도 backfill migration 없이 첫 planner 실행에서 group에 편입한다.

## 13. 구현 단계 경계

1. V28 schema, domain과 policy properties
2. grouping/recommendation planner
3. group claim·lease·budget PostgreSQL adapter
4. quiet·budget·frequency suppression policy
5. 다중 기기 dispatch와 terminal/retry 통합
6. payload/FCM allowlist와 기존 #179 회귀
7. migration/query-plan/full-suite 검증과 보고서

각 단계의 파일·method·RED/GREEN 명령과 커밋 목적은 이 설계 승인 뒤 별도 implementation plan에
작성한다. 구현과 테스트 코드는 그 계획 승인 전 수정하지 않는다.
