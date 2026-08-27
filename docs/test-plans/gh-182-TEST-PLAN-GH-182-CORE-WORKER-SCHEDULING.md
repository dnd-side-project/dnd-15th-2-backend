# Test Plan: TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING

> Created at: `2026-08-27T14:05:31+09:00`
> GitHub Issue: `#182`
> Status: Approved

## 1. Objective

이미 구현된 matching, notification fan-out, recipient sweep와 push dispatch worker가 설정된
주기로 자동 실행되면서도 global/worker별 비활성화, 다중 application instance, lease 만료,
재시도와 profile별 외부 연동 경계를 안전하게 지키는지 검증한다.

가장 큰 실패 위험은 scheduler가 비활성인데도 업무를 실행하는 오류, 같은 process에서 긴 batch가
겹치는 오류, 서로 다른 instance가 같은 Outbox 또는 Push generation을 처리하는 오류, 잘못된
lease owner·기간·retry 설정을 worker에 넘기는 오류, local/test에서 실제 FCM을 호출하는 오류,
그리고 ID나 owner를 metric tag에 넣어 cardinality와 정보 노출을 일으키는 오류다.

## 2. Scope

### Included

- `qello.worker.scheduling` global/worker별 enable, pool size, fixed delay, batch, lease와 retry 설정
- scheduling 비활성 상태의 안전한 기본 기동과 활성 설정의 fail-fast 검증
- process마다 다른 동시에 process 안에서는 안정적인 Outbox lease owner
- `fixedDelay` 기반 7개 scheduled adapter와 각 기존 worker command 위임
- `DirectionMatchingWorker`, 세 fan-out worker의 Outbox claim/result mapping
- `RecipientExpirationSweepWorker`, `SkipConfirmationSweepWorker`의 scan/result mapping
- `PushDeliveryDispatchWorker`, `PushDeliveryRetryPolicy`와 필요한 순수 helper bean wiring
- worker별 claimed/scanned와 outcome Micrometer counter
- Outbox, Push와 Sweep의 기존 PostgreSQL 동시성·fencing·멱등성 회귀
- local/test/integration profile의 실제 FCM credential·network 비사용
- scheduler 비활성 상태의 기존 application context와 전체 test regression

### Excluded

- Answer moderation execution/deadline/verdict scheduling — #204
- Slack notifier와 dispatch scheduling — #205
- `ReportEvidencePurgeSweepWorker` 자동 실행
- 실제 운영 fixed delay, batch, lease, retry와 pool size 숫자의 확정
- 실제 FCM credential, 모바일 기기 end-to-end, Terraform, 배포와 production 변경
- DB schema, 기존 worker 업무 로직, API와 권한 변경
- Micrometer exporter, actuator endpoint, dashboard와 alert rule

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #182 | 7개 승인 worker를 설정 주기·batch로 실행하고 global/worker별로 비활성화할 수 있음 |
| GitHub Issue #182 | instance마다 다른 안정적인 owner, 두 instance claim 비중복, expired lease 회수와 stale fencing 유지 |
| GitHub Issue #182 | Push worker/retry bean 설정화, local/test 실제 FCM 미호출, worker별 claim/scanned·outcome metric |
| GitHub Issue #182 | 기존 worker 로직과 DB schema를 변경하지 않음 |
| `07-core-worker-scheduling-architecture.local.md` | 얇은 adapter, DB 조정, 외부 호출 transaction 분리, #204·#205 후속 연결 |
| `DirectionMatchingWorker`와 fan-out worker API | `BatchCommand(limit, leaseOwner, at, leaseExpiresAt, retryPolicy)`와 finite outcome 목록 |
| Sweep worker API | `BatchCommand(limit, at)`와 `SweepBatchResult(scanned, released, ineligible, failed)` |
| `PushDeliveryDispatchWorker` API | `BatchCommand(batchSize, at, leaseUntil)`와 group/device generation-fenced outcome |
| V12/V28 schema와 repository | Outbox owner·lease·generation, Push group/delivery lease generation과 due claim index 재사용 |
| `FilteringMetrics` | 관측 실패가 기능을 실패시키지 않고 tag를 제한하는 기존 패턴 |
| AGENTS.md §3 | JUnit 5, 모든 method `@DisplayName`, 정확한 ISO 8601 class header와 source scenario, 단위·통합 분리 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| global OFF인데 scheduled adapter가 등록·실행됨 | 준비되지 않은 production 업무·FCM 호출 | 중간 | P0 | context bean/task 부재와 worker 호출 0회 |
| worker OFF인데 해당 trigger만 계속 실행됨 | 부분 롤백 불가·불필요한 DB 부하 | 중간 | P0 | worker별 조건 matrix와 adapter bean 부재 |
| enabled worker의 필수 delay/batch/lease/retry 누락을 허용 | busy loop·과대 batch·즉시 lease 만료 | 높음 | P0 | configuration binding fail-fast matrix |
| `fixedRate` 또는 중복 trigger로 같은 process의 batch가 겹침 | 중복 외부 호출·DB pool 고갈 | 중간 | P0 | `fixedDelay` annotation 계약과 blocking worker 반복 검증 |
| instance owner가 고정 상수 또는 매 batch 변경 | instance 충돌 또는 terminal fencing 실패 | 중간 | P0 | 두 context owner 불일치, 한 context 반복 동일, 100자 제한 |
| adapter가 잘못된 limit·leaseUntil·retry를 전달 | 작업 누락·stale 증가·retry 폭주 | 중간 | P0 | 고정 Clock과 argument capture로 7개 command 검증 |
| Outbox 두 instance가 같은 event를 claim | 중복 recipient·notification | 중간 | P0 | 실제 PostgreSQL 동시 claim 결과 교집합 0 |
| lease 만료 이전 reclaim 또는 이전 generation 완료 허용 | 상태 덮어쓰기·중복 처리 | 중간 | P0 | 만료 전 0건, 만료 후 generation+1, stale update 0행 |
| Sweep 동시 실행이 slot을 두 번 해제 | 수신 capacity 왜곡 | 중간 | P0 | 실제 row lock 경합과 release 1회 회귀 |
| Push bean 일부 누락 또는 retry 값 불일치 | context 기동 실패·무한 retry | 높음 | P0 | enabled context의 worker/helper/retry 단일 bean과 값 assertion |
| local/test에서 실제 provider가 선택됨 | 외부 호출·credential 요구·비결정 테스트 | 낮음~중간 | P0 | profile별 provider type, scheduler default OFF, 호출 0회 |
| worker 예외가 반복 trigger 전체를 영구 중지 | backlog 영구 적체 | 중간 | P0 | 첫 batch 실패 후 다음 fixedDelay 호출과 batch-failed metric |
| metric 계측 예외가 worker 결과를 바꿈 | 업무 장애 확대 | 낮음 | P0 | throwing registry에서 worker 결과·예외 경계 불변 |
| owner·행 ID·user 값이 metric tag에 포함 | cardinality 폭증·정보 노출 | 중간 | P0 | 전체 meter tag key/value allowlist 검사 |
| scheduler pool이 설정과 다르거나 thread를 정리하지 않음 | worker starvation·테스트 누수 | 낮음~중간 | P1 | pool size/prefix/lifecycle context 검증 |
| 기존 worker 업무 로직 또는 schema가 함께 변경됨 | 범위 확장·회귀 | 낮음 | P0 | source/schema diff gate와 기존 worker suite 전체 실행 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-001 | global disabled이고 pool/worker 값이 전부 없음 | properties binding | context는 성공하고 scheduling은 disabled 상태 | P0 | Configuration Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-002 | global enabled와 pool size 0·음수·누락 | properties binding | context 시작이 field를 노출하지 않는 제한된 설정 오류로 실패 | P0 | Configuration Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-003 | worker enabled와 null/0/음수 fixedDelay·batch·lease 또는 잘못된 retry cap | properties 생성·binding | 해당 worker 설정이 fail-fast하고 disabled worker의 미입력 값은 허용 | P0 | Configuration Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-004 | 4 Outbox, 2 Sweep, 1 Push worker에 서로 다른 fixture | Spring binding | 각 설정이 다른 worker로 섞이지 않고 정확히 보존 | P0 | Configuration Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-005 | identity bean을 한 context에서 여러 번 조회하고 두 context를 생성 | owner 조회 | 같은 context 값은 동일, 다른 context 값은 다름, blank가 아니며 `VARCHAR(100)` 이내 | P0 | Configuration Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-006 | global enabled, pool-size=3 | scheduling configuration 기동 | `taskScheduler`는 pool 3과 전용 thread prefix를 사용하고 context 종료 시 shutdown | P1 | Scheduling Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-007 | global disabled | configuration/component scan | scheduler pool, identity, metrics와 7개 adapter가 등록되지 않음 | P0 | Scheduling Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-008 | global enabled이나 7개 worker가 각각 disabled | context matrix | disabled adapter는 등록·호출되지 않고 다른 enabled adapter에 영향 없음 | P0 | Scheduling Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-009 | fixed Clock, limit=7, lease=30s, deterministic retry fixture | Direction matching adapter 1회 호출 | 정확한 owner, `at`, `leaseExpiresAt=at+30s`, limit와 retry policy로 worker 호출 | P0 | Adapter Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-010 | 서로 다른 outcome을 반환하는 recipient/general/report fan-out worker | 각 adapter 1회 호출 | worker별 command가 정확하고 claimed/outcome metric이 worker별로 분리 | P0 | Adapter Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-011 | expiration/skip 결과에 scanned·released·ineligible·failed가 혼합 | Sweep adapter 호출 | limit/Clock 위임과 네 결과 counter가 정확함 | P0 | Adapter Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-012 | Push batch fixture와 SENT/RETRY/DEAD/CANCELLED/STALE 결과 | Push adapter 호출 | `leaseUntil=at+lease`, batch와 모든 outcome metric이 정확함 | P0 | Push Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-013 | 각 adapter class의 scheduled method | reflection으로 annotation 확인 | 모두 설정 placeholder 기반 `fixedDelay`, 하드코딩 주기·`fixedRate` 없음 | P0 | Adapter Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-014 | claimed/scanned 0과 여러 outcome | WorkerMetrics 기록 | meter 이름과 `worker`, `outcome` tag만 존재하고 count가 정확함 | P0 | Scheduling Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-015 | owner·user·entity ID와 자유 텍스트를 tag로 넣으려는 경로 | metrics API/전체 meter 검사 | API가 그런 값을 받지 않고 허용 enum 외 tag가 없음 | P0 | Scheduling Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-016 | registry가 계측 중 RuntimeException 발생 | adapter가 정상 worker 결과 처리 | 계측 실패가 worker 호출 결과나 다음 scheduling을 바꾸지 않음 | P0 | Scheduling Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-017 | 첫 호출은 batch-level 예외, 두 번째는 성공하는 worker | 반복 scheduled 실행 | 첫 호출은 `BATCH_FAILED`로 기록되고 두 번째 호출이 실행됨 | P0 | Adapter Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-018 | push scheduling enabled와 모든 dependency mock, 명시 retry fixture | PushConfiguration 기동 | planner, payload factory, retry policy와 dispatch worker가 각각 단일 bean이고 retry 값 일치 | P0 | Push Executor |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-019 | test/local/integration profile와 scheduling default OFF | PushConfiguration 기동 | NoOp provider만 사용하고 dispatch worker/adapter가 실제 FCM을 호출하지 않음 | P0 | Push Executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-INT-001 | full Spring context, test profile | scheduling property 미지정 | 애플리케이션 기동 | 기존 context 통과, scheduled adapter·pool 없음, 외부 Push 호출 없음 | container/context lifecycle |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-INT-002 | full Spring context, local profile | scheduling default OFF와 기존 NoOp provider | 애플리케이션 기동 | credential 없이 기동하고 worker 업무 자동 실행 0회 | container/context lifecycle |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-INT-003 | scheduling processor, pool, selected fake worker | global ON, 한 worker ON, 짧은 test fixedDelay, 나머지 OFF | context 실행과 latch 대기 | 선택 worker만 설정 command로 반복 호출되고 같은 adapter 호출은 겹치지 않음 | context close·executor shutdown |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-INT-004 | 두 독립 Spring context | 같은 properties와 고정 Clock | identity bean 비교 | owner는 서로 다르고 각 context 안에서는 안정적 | context close |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-INT-005 | `OutboxLeaseIntegrationTest`, PostgreSQL | due event, 두 owner, active/expired lease | 동시 claim·reclaim·stale terminal | claim 교집합 0, 만료 후 generation 증가, 이전 owner update 0행 | 기존 fixture cleanup |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-INT-006 | matching·recipient/general/report fan-out 기존 integration tests | 실제 Outbox와 notification fixture | 승인 worker regression 실행 | claim, event별 transaction, retry/dead/stale와 notification dedup 계약 유지 | 기존 fixture cleanup |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-INT-007 | `PushDeliveryLeaseIntegrationTest`, Push group repository | due group/device, 두 claimant, expired generation | 동시 claim·reclaim·terminal | group/device 중복 claim 0, stale terminal 0행, 성공 generation 보존 | 기존 fixture cleanup |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-INT-008 | `RecipientSweepConcurrencyIntegrationTest` | 같은 recipient에 sweep/API 경합 | expiration·skip 동시 처리 | 상태 전이와 slot 해제는 한 번뿐이고 음수 capacity 없음 | executor 종료·fixture cleanup |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-INT-009 | Push worker, test double provider/protector | local/test 전용 obvious fixture | scheduler adapter 명시 호출 | 실제 FCM credential·network 없이 bounded outcome과 metric만 발생 | fake reset·fixture cleanup |
| TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-INT-010 | 전체 Gradle unit/integration suite | scheduler default OFF | `check` 실행 | 기존 API, worker, DB, transaction과 profile context 회귀 없음 | suite lifecycle |

## 7. Cross-cutting scenarios

### Database and transactions

- 새 migration을 만들지 않고 V12 Outbox와 V28 Push claim/index를 그대로 사용한다.
- Scheduled adapter는 transaction을 열지 않는다. 기존 worker/service가 가진 claim, 항목별 처리와
  terminal transaction 경계를 보존한다.
- Push provider 호출은 기존처럼 claim commit 뒤와 generation-fenced terminal transaction 전에 실행한다.
- production source diff에서 worker 업무 클래스와 migration 변경이 있으면 범위 위반으로 FAIL한다.

### Concurrency and idempotency

- 한 process 안에서는 `fixedDelay`로 같은 adapter의 다음 실행이 이전 반환 뒤에 시작되는지 검증한다.
- process 사이는 Java lock이 아니라 실제 PostgreSQL `FOR UPDATE SKIP LOCKED`, lease와 generation으로 검증한다.
- owner 안정성과 두 owner의 DB claim 증거를 분리해 수집하고 둘을 합쳐 multi-instance 완료 조건을 판단한다.
- Sweep은 Outbox owner를 추가하지 않고 기존 row lock과 멱등 상태 전이 회귀로 검증한다.

### External APIs

- 실제 FCM, OAuth credential, Slack과 moderation provider를 사용하지 않는다.
- test/local/integration은 기존 NoOp/fake provider와 명시적인 protector test double만 사용한다.
- production Push bean test는 dependency wiring만 검사하고 실제 `send`를 호출하지 않는다.
- network client invocation이 관찰되면 즉시 FAIL하며 credential 값을 로그나 보고서에 남기지 않는다.

### Failure recovery and reconciliation

- Outbox retry는 설정된 max attempt와 capped jitter backoff를 기존 `OutboxRetryPolicy`로 전달한다.
- claim 단계 전체 실패는 batch-level metric으로 남기되 repeating task가 다음 fixedDelay에 다시 실행돼야 한다.
- lease 만료 뒤 이전 generation 결과는 0행이어야 하며 새 generation만 terminal 상태를 쓸 수 있다.
- provider accepted 뒤 terminal 기록 실패의 at-least-once 중복 가능성은 기존 잔여 위험으로 보고한다.
- 운영 fixed delay/batch/lease/retry 값이 승인되지 않은 상태에서는 production 활성화를 PASS로 주장하지 않는다.

## 8. Test data and isolation

- Fixtures: unit은 fixedDelay `PT0.05S`, batch `7`, lease `PT30S`, maxAttempts `3`,
  baseDelay `PT1S`, maxDelay `PT30S`를 test-only 값으로 명시한다. 운영 추천값이 아니다.
- Database isolation: 기존 PostGIS Testcontainers와 scenario별 dedup key를 사용한다. 실제 commit 경합
  테스트는 executor/latch를 `finally`에서 종료하고 FK 역순으로 정리한다.
- Clock/randomness: adapter unit은 `Clock.fixed`를 사용한다. retry jitter 계산 자체는 기존
  `ExponentialJitterBackoffStrategyTest`가 소유하고 adapter는 설정 전달만 검증한다.
- External API doubles: Mockito worker, NoOp PushProvider와 명시적 PushTokenProtector double만 사용한다.
- Cleanup: ApplicationContext, TaskScheduler, executor와 latch를 매 test에서 닫고 meter registry를 새로 만든다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Configuration Executor | `src/test/java/com/dnd/qello/scheduling/config/WorkerSchedulingPropertiesTest.java`, `src/test/java/com/dnd/qello/scheduling/WorkerInstanceIdentityTest.java` | UNIT-001~005 | 설정 binding·validation과 identity unit tests |
| 2 | Scheduling Executor | `src/test/java/com/dnd/qello/scheduling/WorkerSchedulingConfigurationTest.java`, `src/test/java/com/dnd/qello/scheduling/WorkerMetricsTest.java` | UNIT-006~008, UNIT-014~016, INT-003~004 | context runner와 meter 전체 검사 |
| 3 | Adapter Executor | `src/test/java/com/dnd/qello/scheduling/adapter/CoreWorkerScheduledAdapterTest.java` | UNIT-009~013, UNIT-017 | 6개 non-Push adapter command·outcome·fixedDelay |
| 4 | Push Executor | `src/test/java/com/dnd/qello/notification/config/PushConfigurationTest.java`, `src/test/java/com/dnd/qello/scheduling/adapter/PushDeliveryDispatchScheduledAdapterTest.java` | UNIT-012, UNIT-018~019, INT-009 | Push bean/retry/profile와 adapter 검증 |
| 5 | Context Executor | `src/integrationTest/java/com/dnd/qello/CoreWorkerSchedulingIntegrationTest.java` | INT-001~004 | full/context scheduling gate와 lifecycle |
| 6 | PostgreSQL Verifier | 기존 `OutboxLeaseIntegrationTest`, matching/fan-out integration tests, `PushDeliveryLeaseIntegrationTest`, `RecipientSweepConcurrencyIntegrationTest`는 수정하지 않음 | INT-005~008 | 실제 PostgreSQL 회귀 명령 실행 |
| 7 | Regression Verifier | 기존 unit/integration source는 수정하지 않음 | INT-010 | 전체 `check`와 source/schema diff 검사 |
| 8 | Report Owner | `docs/test-reports/gh-182-TEST-REPORT-GH-182-CORE-WORKER-SCHEDULING.md` | 전체 | test-run 결과와 잠재 문제 분석 기록 |

각 executor는 표의 테스트 파일만 수정한다. Production 파일 소유권은 승인된 구현 계획에서
별도로 지정한다. 같은 파일 변경이 필요하면 오케스트레이터가 순서를 직렬화하고 계획을 먼저
갱신한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현 또는 기존 회귀 테스트로 증거 연결
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 모든 신규 테스트 클래스 헤더에 정확한 ISO 8601 timestamp와 source scenario 기록
- [ ] global/worker별 비활성 context와 선택 worker 실제 반복 실행 검증
- [ ] 7개 adapter command, fixedDelay와 결과 metric 검증
- [ ] 두 context identity와 실제 PostgreSQL claim/reclaim/fencing 검증
- [ ] local/test/integration에서 실제 FCM network 호출 0회 확인
- [ ] 기존 worker source와 DB migration 변경 없음 확인
- [ ] `./harness test-run --id TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING` 실행
- [ ] `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` 통과
- [ ] 애플리케이션, DB, 동시성, transaction, 외부 API와 장애 복구 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: human partner
- Decision: Approved for SDD implementation
- Approved at: `2026-08-27T14:19:55+09:00`
