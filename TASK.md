# GitHub Issue #182 Task Contract

> Generated at: `2026-08-27T13:30:35+09:00`
> Updated at: `2026-08-27T16:13:00+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Core worker scheduling 구현`
- GitHub Issue: `#182`
- Branch: `chore/gh-182-core-worker-scheduling`
- Base branch: `origin/main`
- Task ID: `GH-182-CORE-WORKER-SCHEDULING`
- Design ID: `APP-DESIGN-GH-182-001`
- Design status: `APPROVED_FOR_BUILD`
- Design approval evidence: `2026-08-27T14:19:55+09:00` 사용자가 계획을 승인하고 SDD 구현을 요청함
- Test plan: `TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING`
- Test plan path: `docs/test-plans/gh-182-TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING.md`
- Implementation gate: `APPROVED_FOR_BUILD`

## Objective

- 이미 구현된 matching, notification fan-out, recipient sweep와 push dispatch worker를
  설정된 주기로 자동 실행한다.
- global 및 worker별 안전한 비활성화, process별 lease owner, 기존 DB fencing과
  낮은 cardinality Micrometer 지표를 제공한다.
- 기존 worker 업무 로직과 DB schema를 변경하지 않고 scheduling orchestration만 추가한다.

## Scope

- `qello.worker.scheduling` 설정 모델과 fail-fast validation
- global/worker별 conditional scheduling, 전용 `ThreadPoolTaskScheduler`
- process singleton `WorkerInstanceIdentity`
- 다음 7개 worker의 얇은 `fixedDelay` adapter
  - `DirectionMatchingWorker`
  - `RecipientNotificationFanOutWorker`
  - `NotificationFanOutWorker`
  - `ReportResolutionFanOutWorker`
  - `RecipientExpirationSweepWorker`
  - `SkipConfirmationSweepWorker`
  - `PushDeliveryDispatchWorker`
- Outbox 및 Push retry/lease 설정을 기존 command와 policy 타입으로 전달
- Push planner, payload factory, retry policy와 worker의 conditional bean wiring
- claimed/scanned/outcome Micrometer 지표와 enum tag allowlist
- 설정, adapter, context lifecycle, profile 및 기존 PostgreSQL 동시성 계약 검증
- 테스트 결과 보고서와 local architecture 문서의 실제 구현 증거 갱신

## Explicit exclusions

- Answer moderation execution/deadline/verdict scheduling — GitHub Issue #204
- Slack manual review dispatch scheduling — GitHub Issue #205
- `ReportEvidencePurgeSweepWorker` 자동 실행
- 운영 pool size, fixed delay, batch, lease와 retry 숫자 결정 및 runtime 주입
- 실제 FCM credential·network end-to-end, 인프라 apply, 배포와 production 활성화
- DB schema/migration, 기존 worker 업무 로직, API와 권한 변경
- Micrometer exporter, dashboard, alert rule와 actuator 공개 범위 변경
- Secret, 계정 식별자, 토큰, `.env` 값 기록

## Approved decisions

- `DEC-182-001`: scheduling 기본값은 OFF이며 global OFF이면 관련 bean을 등록하지 않는다.
- `DEC-182-002`: worker마다 orchestration만 담당하는 얇은 adapter를 둔다.
- `DEC-182-003`: 같은 adapter의 process 내 중첩을 피하도록 `fixedDelay`를 쓴다.
- `DEC-182-004`: prefix는 `qello.worker.scheduling`이고 운영 숫자 fallback은 두지 않는다.
- `DEC-182-005`: Outbox owner는 application context마다 생성되는 안정적인 UUID다.
- `DEC-182-006`: 기존 lease, generation, row lock을 재사용하고 새 분산 lock/schema를 만들지 않는다.
- `DEC-182-007`: Outbox와 Push의 기존 retry 타입을 설정으로 구성한다.
- `DEC-182-008`: Push helper와 worker bean은 global 및 push gate가 모두 ON일 때만 만든다.
- `DEC-182-009`: metric은 `worker`, `outcome` enum tag만 허용한다.
- `DEC-182-010`: batch 예외는 `BATCH_FAILED`를 기록하고 다시 던져 다음 주기와 격리한다.

상세 설계는 `docs/superpowers/specs/2026-08-27-core-worker-scheduling-design.md`, 실행 순서는
`docs/superpowers/plans/2026-08-27-core-worker-scheduling.md`가 소유한다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 요구사항·설계·실행 계약 통합 | Orchestrator | Human partner |
| scheduling properties·identity와 단위 테스트 | Configuration Executor | Independent verifier |
| scheduler·metrics와 단위 테스트 | Scheduling Executor | Independent verifier |
| Outbox·Sweep adapter와 단위 테스트 | Adapter Executor | Independent verifier |
| Push bean wiring·adapter와 단위 테스트 | Push Executor | Independent verifier |
| context 및 PostgreSQL 회귀 검증·test report | Test Executor | Independent verifier |
| 전체 diff·정책·회귀 독립 검증 | Independent Verifier | Human partner |

실행자는 승인된 계획에서 배정된 파일만 수정하고 서로의 변경을 되돌리지 않는다. 독립 검증자는
검증을 통과시키기 위해 production source를 수정하지 않는다.

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 비어 있었고 기존 사용자 변경은 없었다.
- 이 브랜치에서 작성한 local 문서
  `docs/reports/private/notification/07-core-worker-scheduling-architecture.local.md`는 `.gitignore`
  대상이다. 구현 증거로 갱신했으며 PR diff에 포함되지 않는다.
- 현재 working tree의 tracked 변경은 scheduling orchestration, Push 조건부 wiring,
  설계·테스트 계획·테스트 보고서와 이 계약이다. 커밋과 PR은 별도 harness 승인 전 만들지 않는다.

## Validation

Focused checks:

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.*'
./gradlew test --tests 'com.dnd.qello.scheduling.adapter.*'
./gradlew test --tests 'com.dnd.qello.notification.config.PushConfigurationTest'
./gradlew integrationTest --tests 'com.dnd.qello.CoreWorkerSchedulingIntegrationTest'
./harness test-run --id TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING
```

Final checks:

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

증거는 `docs/test-reports/gh-182-TEST-REPORT-GH-182-CORE-WORKER-SCHEDULING.md`의 focused
Gradle 결과와 Task 8 final 검증(전부 exit 0)에 근거한다. 운영 pool/delay/batch/lease/retry
숫자와 live FCM은 미검증이며 이 상자를 통과로 쓰지 않는다.

- [x] 인간이 테스트 계획과 구현 계획을 승인했다. (`2026-08-27T14:19:55+09:00`)
- [x] global OFF와 worker OFF에서 scheduler/adapter가 등록·실행되지 않는다. (INT-001/002, UNIT-007/008)
- [x] enabled 설정은 필수 숫자를 검증하며 운영 fallback을 사용하지 않는다. (UNIT-001..004)
- [x] 7개 adapter가 정확한 command로 기존 worker를 한 번씩 호출한다. (UNIT-009..012)
- [x] 같은 process 안의 같은 adapter가 겹치지 않고 다른 worker는 pool에서 병렬 실행 가능하다. (INT-003 matching adapter 비중첩 PASS. 나머지 6 adapter는 UNIT-013 `fixedDelay` annotation 계약이며 런타임 비중첩 재현은 matching만 실행함)
- [x] context별 owner 안정성과 실제 PostgreSQL claim/reclaim/fencing 계약이 유지된다. (UNIT-005 identity 타입 안정성 PASS. INT-005/007/008 PostgreSQL claim/reclaim/fencing PASS. INT-004 두 독립 ApplicationContext bean owner 비교는 NOT_RUN — 다중 context 안정성을 overclaim하지 않는다)
- [x] Push helper·retry·worker bean이 조건부로 구성되고 local/test는 실제 FCM을 호출하지 않는다. (UNIT-018/019. live FCM은 미검증)
- [x] metric 이름과 tag가 승인된 enum allowlist에 한정된다. (UNIT-014..016)
- [x] 기존 worker production source와 DB migration을 변경하지 않는다. (Task 8 Step 4 `git diff origin/main` 지정 worker/repository/migration 경로 empty, `src/main/resources/db/migration` empty)
- [x] 신규 JUnit 5 테스트가 `@DisplayName`, 정확한 생성 시각과 source scenario 규칙을 지킨다.
- [x] 테스트 보고서에 application, DB, concurrency, transaction, 외부 API와 장애 복구 위험을 기록한다.
- [x] 모든 focused 및 final 검증이 통과하거나 실행 불가 항목이 `BLOCKED`로 기록된다. (`./harness check` exit 0; `./harness pr-ready --project-tests` exit 0, `./gradlew check` BUILD SUCCESSFUL, unit 998 / integration 717, failures=0; `npm run hooks:validate` exit 0; `git diff --check` exit 0. INT-004 두 context는 NOT_RUN, `./harness test-run`은 Task 7에서 BLOCKED로 기록됨. 운영 숫자와 live FCM은 미검증)
