# Core Worker Scheduling Design

> Design ID: `APP-DESIGN-GH-182-001`
> GitHub Issue: `#182`
> Status: `APPROVED_FOR_BUILD`
> Approval evidence: human partner approved the plan and requested SDD implementation at `2026-08-27T14:19:55+09:00`
> Detailed visual companion: `docs/reports/private/notification/07-core-worker-scheduling-architecture.local.md`

## 1. Goal

기존 worker 업무 로직과 DB schema를 변경하지 않고 matching, notification fan-out,
recipient sweep와 push dispatch를 설정 가능한 주기로 자동 실행한다. 여러 application
instance가 동시에 실행돼도 기존 lease·generation·행 잠금 계약으로 중복 상태 전이를 막고,
실행량과 결과를 낮은 cardinality Micrometer 지표로 관측한다.

## 2. Scope

### Included workers

1. `DirectionMatchingWorker`
2. `RecipientNotificationFanOutWorker`
3. `NotificationFanOutWorker`
4. `ReportResolutionFanOutWorker`
5. `RecipientExpirationSweepWorker`
6. `SkipConfirmationSweepWorker`
7. `PushDeliveryDispatchWorker`

### Excluded workers

- Answer moderation execution/deadline/verdict workers — #204
- Slack manual review dispatch worker — #205
- `ReportEvidencePurgeSweepWorker` — 별도 데이터 파기 안전성 검토

## 3. Design decisions

### DEC-182-001: Scheduling은 기본 비활성이다

`qello.worker.scheduling.enabled`의 기본값은 `false`다. global OFF일 때 scheduler pool,
instance identity, worker metrics와 scheduled adapter를 등록하지 않는다. production에서
활성화하려면 global gate와 각 worker gate 및 필수 수치를 모두 명시해야 한다.

### DEC-182-002: Worker마다 얇은 adapter 하나를 둔다

각 adapter는 다음 일만 한다.

1. Spring이 제공한 trigger를 받는다.
2. 한 번의 `Clock` 시각을 읽는다.
3. properties와 instance identity로 기존 `BatchCommand`를 만든다.
4. 기존 worker를 한 번 호출한다.
5. 반환된 batch 수와 outcome을 metric으로 기록한다.

adapter는 repository를 직접 호출하거나 업무 상태를 바꾸지 않는다.

### DEC-182-003: `fixedDelay`를 사용한다

같은 process의 같은 adapter는 이전 호출이 반환된 뒤 설정된 delay를 기다린다.
`fixedRate`를 사용하지 않으며 긴 batch 때문에 같은 adapter가 process 안에서 겹치지 않게 한다.
서로 다른 worker는 설정된 `ThreadPoolTaskScheduler` pool에서 병렬 실행할 수 있다.

### DEC-182-004: 설정 prefix는 `qello.worker.scheduling`이다

설정 모델은 다음 필드를 가진다.

```text
qello.worker.scheduling.enabled
qello.worker.scheduling.pool-size

qello.worker.scheduling.<worker>.enabled
qello.worker.scheduling.<worker>.fixed-delay
qello.worker.scheduling.<worker>.batch-size
qello.worker.scheduling.<worker>.lease-duration       # Outbox와 Push만
qello.worker.scheduling.<worker>.retry.max-attempts  # Outbox와 Push만
qello.worker.scheduling.<worker>.retry.base-delay    # Outbox
qello.worker.scheduling.<worker>.retry.max-delay     # Outbox
qello.worker.scheduling.<worker>.retry.base-backoff  # Push
qello.worker.scheduling.<worker>.retry.backoff-cap   # Push
```

`<worker>` 값은 다음으로 고정한다.

```text
direction-matching
recipient-notification-fan-out
notification-fan-out
report-resolution-fan-out
recipient-expiration-sweep
skip-confirmation-sweep
push-delivery-dispatch
```

global OFF와 worker OFF에서는 나머지 수치가 없어도 기동한다. global ON에서 pool size가
유효하지 않거나 enabled worker의 필수 수치가 없거나 0·음수이면 context 시작에 실패한다.
운영 숫자에는 코드 fallback을 두지 않는다.

### DEC-182-005: Outbox owner는 process singleton UUID다

`WorkerInstanceIdentity`는 process 기동 때 한 번 생성한 UUID 기반 owner를 보관한다.
빈 값이 아니고 `outbox_event.lease_owner VARCHAR(100)` 안에 들어가야 한다. owner는 batch마다
바뀌지 않고 서로 다른 application context에서는 달라야 한다. owner를 metric tag로 쓰지 않는다.

### DEC-182-006: 기존 동시성 계약을 재사용한다

- Outbox adapters는 기존 `leaseOwner`, `leaseExpiresAt`, `leaseGeneration` fencing을 사용한다.
- Push adapter는 기존 group/device lease 시각과 generation fencing을 사용한다.
- Sweep adapters는 기존 service의 행 잠금과 멱등 전이를 사용한다.

새 DB lock table, 분산 lock library, schema 또는 migration을 추가하지 않는다.

### DEC-182-007: Retry 정책은 기존 타입으로 구성한다

Outbox adapters는 worker별 설정으로
`OutboxRetryPolicy(maxAttempts, ExponentialJitterBackoffStrategy.withRandomJitter(baseDelay, maxDelay))`
를 한 번 구성해 재사용한다. Push는 설정으로 `PushDeliveryRetryPolicy` Spring bean을 만들고
`PushDeliveryDispatchWorker`에 주입한다. Sweep은 실패한 행이 다음 scan 후보가 되므로 별도
retry policy를 만들지 않는다.

### DEC-182-008: Push worker는 scheduling gate 안에서만 bean으로 만든다

`PushConfiguration`은 global과 push worker gate가 모두 ON일 때 다음 bean을 만든다.

- `PushDispatchGroupPlanner`
- `PushPayloadFactory`
- `PushDeliveryRetryPolicy`
- `PushDeliveryDispatchWorker`

production에서는 기존 실제 `PushTokenProtector`와 `PushProvider`를 주입한다. local/test/integration은
global OFF가 기본이며 기존 `NoOpPushProvider`를 유지한다. 테스트에서 push adapter를 활성화할 때는
명시적인 test protector/provider를 주입하고 실제 FCM network를 사용하지 않는다. push gate를
켰지만 필요한 protector가 없으면 조용히 건너뛰지 않고 context가 fail closed한다.

### DEC-182-009: Metric tag는 enum allowlist만 사용한다

metric 이름은 다음 세 개다.

```text
qello.worker.claimed.total
qello.worker.scanned.total
qello.worker.outcome.total
```

tag key는 `worker`, `outcome`만 허용한다. worker와 outcome은 코드 enum으로만 전달한다.
owner, entity ID, user ID, exception message, token과 credential은 metric API 입력 자체로 받지 않는다.
관측 실패는 삼켜 업무 처리 결과를 바꾸지 않는다.

### DEC-182-010: Batch-level 실패는 기록하고 다음 주기를 유지한다

기존 worker가 batch 반환 전에 예외를 던지면 adapter는 `BATCH_FAILED` outcome을 기록한 뒤
예외를 다시 던진다. Spring repeating task의 error handler가 이를 격리해 다음 fixed delay 실행을
유지한다. adapter 로그나 metric에 entity ID와 민감정보를 추가하지 않는다.

## 4. Component structure

```text
com.dnd.qello.scheduling
├── WorkerInstanceIdentity
├── WorkerSchedulingConfiguration
├── config
│   └── WorkerSchedulingProperties
├── observability
│   └── WorkerMetrics
└── adapter
    ├── DirectionMatchingScheduledAdapter
    ├── RecipientNotificationFanOutScheduledAdapter
    ├── NotificationFanOutScheduledAdapter
    ├── ReportResolutionFanOutScheduledAdapter
    ├── RecipientExpirationSweepScheduledAdapter
    ├── SkipConfirmationSweepScheduledAdapter
    └── PushDeliveryDispatchScheduledAdapter
```

`scheduling` 패키지는 trigger와 orchestration만 소유한다. domain별 worker는 scheduling 패키지를
알지 못한다.

## 5. Runtime flow

### Outbox adapter

1. `Clock.instant()`를 `at`으로 읽는다.
2. `leaseExpiresAt = at + leaseDuration`을 계산한다.
3. worker별 `BatchCommand`에 limit, singleton owner, at, lease와 cached retry policy를 넣는다.
4. worker가 due event를 claim하고 event별 transaction을 실행한다.
5. adapter는 claimed와 enum outcome을 metric으로 기록한다.

### Sweep adapter

1. `Clock.instant()`와 batch size로 command를 만든다.
2. worker가 후보를 scan하고 각 행을 독립 service transaction으로 처리한다.
3. adapter는 scanned, released, ineligible와 failed를 metric으로 기록한다.

### Push adapter

1. `Clock.instant()`와 `leaseUntil = at + leaseDuration`으로 command를 만든다.
2. worker가 group을 편입·claim하고 최신 정책을 다시 확인한다.
3. provider 호출은 기존처럼 transaction 밖에서 수행된다.
4. generation-fenced terminal 결과와 adapter metric을 기록한다.

## 6. Error and overflow handling

- `Duration` 덧셈 overflow는 adapter command 생성 실패로 분류하고 `BATCH_FAILED`를 기록한다.
- 잘못된 설정은 첫 trigger까지 미루지 않고 properties binding/context 시작 단계에서 거절한다.
- metric registry 예외는 worker 성공·실패 판단에 영향을 주지 않는다.
- 한 worker의 예외는 다른 worker adapter와 scheduler pool을 중단하지 않는다.
- lease 만료 뒤 stale terminal update는 기존 repository가 0행으로 거절한다.

## 7. Testing strategy

- Unit: properties validation/binding, identity, pool, conditional beans, 7개 command와 metric mapping
- Context: global/worker gate, 실제 repeating fixedDelay, same-adapter non-overlap, profile별 Push wiring
- PostgreSQL regression: Outbox two-owner claim/reclaim/fence, Push group/device fence, Sweep row-lock race
- Full regression: scheduler default OFF 상태에서 전체 `check`
- No real external API: NoOp/mock provider와 명시적 token protector test double만 사용

테스트 계약은
`docs/test-plans/gh-182-TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING.md`가 소유한다.

## 8. Rollback and operational gate

첫 롤백 수단은 global 또는 해당 worker의 `enabled=false`다. 설정을 끄면 새 trigger는 업무를
시작하지 않지만 이미 저장된 Outbox, notification, delivery와 domain 원장은 삭제하지 않는다.

production 활성화 전에는 pool size, worker별 fixed delay, batch, lease와 retry 값을 사람이
승인해 runtime 환경에 주입해야 한다. 값이 미확정이어도 코드와 disabled context는 구현·검증할
수 있지만 production enable은 완료로 주장하지 않는다.
