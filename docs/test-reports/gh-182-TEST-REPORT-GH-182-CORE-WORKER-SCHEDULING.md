# Test Report: TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING

> Created at: `2026-08-27T15:52:25+09:00`
> GitHub Issue: `#182`
> Branch: `chore/gh-182-core-worker-scheduling`
> Commit: `working-tree` (HEAD `f3255aea8b3da51afb1b5213eaf8840e2e35f1cf`)

## 1. Executive summary

- Result: `PARTIAL`
- Tested scope: `qello.worker.scheduling` properties·identity·conditional scheduler, 7 adapter
  command/metric/`fixedDelay` 계약, Push helper·retry·worker 조건부 wiring, test/local 기본
  OFF context, 기존 PostgreSQL Outbox claim/reclaim/fencing, matching·recipient fan-out
  동시성, notification fan-out expansion, Push group/device lease, recipient sweep slot
  해제, 전체 `./gradlew check` 회귀(`INT-010`). worker 업무 클래스와 Flyway migration 경로의
  `origin/main` diff는 비어 있다.
- Unverified scope: `./harness test-run`(전체 suite를 다시 돌리고 다른 경로에 빈
  템플릿을 scaffold하므로 Task 7에서 BLOCKED로 남김), 두 독립 Spring context의 identity
  bean (`INT-004`), adapter가 test-double Push provider를 실제 호출하는 전용 통합
  (`INT-009`), report-resolution fan-out 전용 PostgreSQL 동시성 클래스, 운영
  pool/delay/batch/lease/retry 숫자, live FCM, Answer moderation(#204)과 Slack(#205)
  scheduling.
- Release recommendation: production scheduling을 켜지 않는다. focused Gradle과 Task 8
  전체 repository gate(`./harness pr-ready --project-tests`)는 exit 0이다. 운영 숫자와
  live FCM은 미검증 운영 범위이며, 기본 OFF 코드의 실패가 아니다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Toolchain Temurin 21.0.12 (tests). Host launcher Temurin 24.0.2 |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.3 |
| Database | PostgreSQL Testcontainers with PostGIS support |
| Test runner | JUnit 5 / Gradle `test` and `integrationTest` |
| Harness | `./harness test-run` inspected, not executed (see §5) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew integrationTest --tests 'OutboxLeaseIntegrationTest' --tests 'DirectionMatchingWorkerConcurrencyIntegrationTest' --tests 'RecipientNotificationFanOutWorkerConcurrencyIntegrationTest' --tests 'NotificationFanOutExpansionIntegrationTest' --console=plain` | PASS, exit 0 | 29 (9+3+7+10) | 51s | JUnit XML under `build/test-results/integrationTest`; `BUILD SUCCESSFUL`; class-name filters matched FQCN `com.dnd.qello.*` |
| `./gradlew integrationTest --tests 'PushDeliveryLeaseIntegrationTest' --tests 'PushDispatchGroupingIntegrationTest' --tests 'RecipientSweepConcurrencyIntegrationTest' --console=plain` | PASS, exit 0 | 18 (4+10+4) | 36s | same XML directory; `BUILD SUCCESSFUL`; filters matched |
| `git diff origin/main -- src/main/java/com/dnd/qello/direction/matching src/main/java/com/dnd/qello/direction/sweep src/main/java/com/dnd/qello/notification/fanout src/main/java/com/dnd/qello/notification/service/PushDeliveryDispatchWorker.java src/main/java/com/dnd/qello/notification/repository src/main/resources/db/migration` | PASS, empty, exit 0 | n/a | <1s | no stdout; worker 업무·repository·migration 범위 위반 없음 |
| `./gradlew test --tests 'com.dnd.qello.scheduling.*' --tests 'com.dnd.qello.notification.config.PushConfigurationTest' --console=plain` | PASS, exit 0 | 46 | 2s | JUnit XML under `build/test-results/test`; `BUILD SUCCESSFUL` |
| `./gradlew integrationTest --tests 'com.dnd.qello.CoreWorkerSchedulingIntegrationTest' --tests 'com.dnd.qello.CoreWorkerSchedulingLocalProfileIntegrationTest' --console=plain` | PASS, exit 0 | 2 | 41s | XML `tests=1` each, failures=0; package-private local-profile class matched by FQCN |
| `git diff --check` | PASS, exit 0 | n/a | <1s | no whitespace errors |
| `./harness test-run --id TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING` | BLOCKED, not executed | n/a | n/a | `--help` exit 0; implementation runs full `./gradlew test` then `./gradlew integrationTest` and scaffolds a different path. INT-010은 Task 8 `./harness pr-ready --project-tests`로 대체 실행 |
| `./harness check` | PASS, exit 0 | n/a | <1s | Secret preflight 1267 files, JUnit policy 252 files, conventions, workflows, labels, Husky |
| `./harness pr-ready --project-tests` | PASS, exit 0 | unit 998 + integration 717 | 5m 48s (`./gradlew check`) | `BUILD SUCCESSFUL`; JUnit XML `failures=0 errors=0 skipped=0`; `Local PR readiness checks passed.` 첫 시도는 도구 timeout으로 integrationTest 중 중단. 재실행은 `:test UP-TO-DATE`(첫 시도에서 unit 완료), `:integrationTest` 재실행 |
| `npm run hooks:validate` | PASS, exit 0 | n/a | <1s | `Husky validation passed.` |
| `git diff --check` (Task 8) | PASS, exit 0 | n/a | <1s | no whitespace errors |

Class-name `--tests` filters did not fail. No grep/find rerun was required.

## 4. Scenario results

시나리오 ID는 `TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-` 접두사를 생략한다.
PASS는 이번 Task에서 실행한 명령의 JUnit XML에 근거한다.

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `WorkerSchedulingPropertiesTest.disabledSchedulingDoesNotRequireOperationalValues` | global OFF, 운영 수치 없이도 binding 성공 |
| UNIT-002 | PASS | `WorkerSchedulingPropertiesTest.enabledSchedulingRejectsMissingPoolSize` | global ON + pool 누락 fail-fast |
| UNIT-003 | PASS | `WorkerSchedulingPropertiesTest.enabledOutboxWorkerRejectsMissingLeaseAndRetry`, `enabledWorkersRejectInvalidOperationalValues` | 누락·0·음수·역전 retry 거절 |
| UNIT-004 | PASS | `WorkerSchedulingPropertiesTest.bindsDistinctSettingsWithoutExchangingWorkers` | 7 worker 설정이 이름별로 보존 |
| UNIT-005 | PASS | `WorkerInstanceIdentityTest.ownerIsStablePerIdentityAndUniqueAcrossIdentities` | 동일 instance 안정, 서로 다른 identity 불일치, blank 아님, 100자 이내. Spring bean 조회는 INT-004 |
| UNIT-006 | PASS | `WorkerSchedulingConfigurationTest.enabledGlobalGateCreatesConfiguredDedicatedScheduler` | pool 3, prefix `qello-worker-`, context 종료 후 shutdown |
| UNIT-007 | PASS | `WorkerSchedulingConfigurationTest.disabledGlobalGateRegistersNoSchedulingInfrastructure` | scheduler/identity/metrics 미등록 |
| UNIT-008 | PASS | `WorkerSchedulingConfigurationTest.enabledGlobalGateWithAllWorkersDisabledRegistersNoAdapters` | 7 adapter 전부 없음. worker별 1개 ON matrix는 INT-003 |
| UNIT-009 | PASS | `CoreWorkerScheduledAdapterTest` matching adapter | limit·owner·at·leaseExpiresAt·retry 전달 |
| UNIT-010 | PASS | `CoreWorkerScheduledAdapterTest` recipient/notification/report fan-out | worker별 command와 outcome metric 분리 |
| UNIT-011 | PASS | `CoreWorkerScheduledAdapterTest` expiration/skip sweep | scanned/released/ineligible/failed |
| UNIT-012 | PASS | `PushDeliveryDispatchScheduledAdapterTest` push adapter | batch·at·leaseUntil과 SENT/RETRY/DEAD/CANCELLED/STALE |
| UNIT-013 | PASS | Outbox/sweep/push adapter `fixedDelayString` reflection | `fixedRate` 없음, 설정 placeholder만 사용 |
| UNIT-014 | PASS | `WorkerMetricsTest.recordsAllWorkerCounterKindsWithExactTagsAndCounts` | meter 이름과 `worker`/`outcome` tag만 |
| UNIT-015 | PASS | `WorkerMetricsTest.rejectsNegativeCountsBeforeRecordingMetrics` | 구현은 음수 count 거절. 계획의 자유 텍스트 tag 거절은 enum API + UNIT-014 tag allowlist로 대응 |
| UNIT-016 | PASS | `WorkerMetricsTest.instrumentationFailuresNeverPropagateToWorkers` | throwing registry가 worker API 예외를 만들지 않음 |
| UNIT-017 | PASS | Outbox matching, expiration sweep, push adapter BATCH_FAILED | 첫 예외 기록 후 다음 호출 성공 |
| UNIT-018 | PASS | `PushConfigurationTest.enabledPushSchedulingWiresSinglePlannerPayloadRetryAndWorkerBeans` | planner/payload/retry/worker 단일 bean, retry 값 일치 |
| UNIT-019 | PASS | `PushConfigurationTest.enabledPushSchedulingKeepsNoOpProviderAndDoesNotBuildFcmClient` | profiles `test`,`local`; FCM client bean 없음 |
| INT-001 | PASS | `CoreWorkerSchedulingIntegrationTest.testProfileDoesNotRegisterSchedulingBeans` | test profile 기본 OFF, 7 adapter·pool·identity·metrics 없음 |
| INT-002 | PASS | `CoreWorkerSchedulingLocalProfileIntegrationTest.localProfileDoesNotRegisterSchedulingBeans` | local profile 기본 OFF, credential 없이 기동 |
| INT-003 | PASS | `WorkerSchedulingConfigurationTest.selectedWorkerRepeatsWithoutOverlappingTheSameAdapter` | matching만 ON, 최소 2회 호출, maxConcurrent==1 |
| INT-004 | NOT_RUN | none in this command set | UNIT-005가 identity 타입 안정성만 증명. 두 독립 `ApplicationContext`의 bean owner 비교는 실행하지 않음 |
| INT-005 | PASS | `OutboxLeaseIntegrationTest` 9 methods | 동시 claim 교집합 0, 만료 후 generation 증가, stale terminal 차단, retry/DEAD |
| INT-006 | PARTIAL | matching concurrency 3 + recipient fan-out concurrency 7 + `NotificationFanOutExpansionIntegrationTest` 10 | matching·recipient·general fan-out 회귀 PASS. report-resolution fan-out 전용 동시성 클래스는 이번 명령에 없고 실행하지 않음 |
| INT-007 | PASS | `PushDeliveryLeaseIntegrationTest` 4 + `PushDispatchGroupingIntegrationTest` 10 | group/device 중복 claim 0, 만료 전 회수 0, stale terminal 0행 |
| INT-008 | PASS | `RecipientSweepConcurrencyIntegrationTest` 4 methods | expiration/skip/block 경합에서 slot 1회 해제 |
| INT-009 | PARTIAL | UNIT-012 adapter mock + UNIT-018/019 wiring | adapter 명시 호출은 unit. test-double provider로 adapter가 worker를 돌리는 전용 통합은 없음 |
| INT-010 | PASS | `./harness pr-ready --project-tests` → `./gradlew check` | exit 0, BUILD SUCCESSFUL in 5m 48s. unit 998 tests, integration 717 tests, failures=0 errors=0 skipped=0 (JUnit XML under `build/test-results/test` and `build/test-results/integrationTest`). 첫 시도는 executor timeout으로 integrationTest 중 중단되어 재실행함. `:test`는 첫 시도에서 완료되어 재실행 시 UP-TO-DATE |

## 5. Failures and diagnostics

재현 가능한 테스트 실패는 없었다. 실행한 Gradle focused 명령과 Task 8 `./gradlew check`는
모두 `BUILD SUCCESSFUL`, JUnit XML `failures=0 errors=0 skipped=0`이다.

`OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended` 는 JVM CDS 경고이며 테스트 실패가 아니다.

`./harness test-run --id TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING` 은 실행하지 않았다.

- 이유: `scripts/harness.py` `command_test_run`은 테스트 계획 식별자로 범위를 좁히지
  않는다. 항상 `./gradlew test` 전체 unit, 이어서 `./gradlew integrationTest` 전체
  integration을 실행한 뒤 `docs/reports/tests/gh-182-TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING.md`
  에 빈 템플릿을 scaffold한다. 그 경로는 이번 Task 소유 파일
  `docs/test-reports/gh-182-TEST-REPORT-GH-182-CORE-WORKER-SCHEDULING.md` 와 다르다.
- 영향: 해당 명령은 전체 unit+integration을 다시 돌리고 다른 경로에 빈 템플릿을
  scaffold한다. INT-010은 Task 8에서 `./harness pr-ready --project-tests`로 실행했다.
- 후속: Task 8 `./harness check`와 `./harness pr-ready --project-tests`는 exit 0.
  빈 scaffold를 만들지 않기 위해 보고서는 이 경로에 직접 유지한다.

테스트 환경 문제(Docker/Testcontainers 기동 실패, lock, toolchain 불일치)는 관찰되지
않았다.

## 6. Potential issues

### Application code

- scheduling 기본값은 OFF이며 test/local full context는 adapter를 등록하지 않는다.
  운영에서 켜기 전에 필수 숫자가 없으면 fail-fast한다. 운영 숫자 자체는 이번 범위에서
  승인되지 않았다.
- adapter는 기존 worker command로 한 번 위임한다. worker 업무 클래스와 repository는
  이번 scope diff에서 변경되지 않았다.
- `INT-004` 두 Spring context owner 비교가 없다. `WorkerInstanceIdentity.random()` 단위
  테스트만 있다. 다중 instance 충돌 방지는 PostgreSQL lease 회귀(INT-005/007)에 의존한다.
- `UNIT-015` 계획 원문은 자유 텍스트 tag 주입 경로를 요구한다. 구현 API는 enum만 받고
  UNIT-014가 tag key allowlist를 검사한다. 자유 문자열을 넣는 호출 경로는 코드에 없다.

### Infrastructure and resource limits

- 통합 테스트는 Testcontainers PostgreSQL/PostGIS와 로컬 Docker에 의존한다. 이번 실행은
  기동에 성공했다.
- scheduler pool size는 테스트 fixture이며 운영 용량 증거가 아니다.
- INT-003은 2초 latch와 첫 호출 hold에 의존한다. 이번 실행은 0.376s에 통과했지만
  부하가 큰 CI에서는 timing flake 가능성이 남는다.

### Database and migrations

- `src/main/resources/db/migration` 의 `origin/main` diff는 비어 있다. 새 migration은
  추가되지 않았다.
- Outbox는 기존 V12 owner/lease/generation, Push는 기존 V28 group/device lease를
  재사용한다. 이번 회귀는 그 계약을 깨지 않았다.
- adapter는 새 transaction을 열지 않는다. claim·항목 처리·terminal 경계는 기존 worker가
  소유한다.

### Concurrency and idempotency

- process 안: INT-003이 matching adapter의 `fixedDelay` 비중첩을 증명했다. 나머지 6
  adapter의 런타임 비중첩은 UNIT-013 annotation 계약에 의존한다.
- process 사이: Outbox와 Push는 PostgreSQL `FOR UPDATE SKIP LOCKED` + generation으로
  중복 claim 0과 stale 0행을 증명했다. Sweep은 owner를 추가하지 않고 row lock·멱등
  상태 전이로 slot 1회 해제를 증명했다.
- report-resolution fan-out의 두 owner 동시 claim은 이번 명령 집합에 없어 INT-006을
  PARTIAL로 남긴다.

### Transactions and event ordering

- matching stale lease worker의 domain write rollback과 reclaim worker 단독 commit이
  실제 PostgreSQL에서 통과했다.
- recipient fan-out은 source Outbox 동시 claim 시 logical notification 1회, 만료
  reclaim 후 stale rollback을 유지했다.
- Push planner 동시 편입은 notification당 member 1개·열린 group 1개를 유지했다.
- provider accepted 이후 terminal 기록 실패의 at-least-once 중복은 기존 잔여 위험이며
  이번 scheduling 추가로 새로 생기거나 사라지지 않았다.

### External APIs

- test/local/integration은 NoOp/fake Push provider만 등록한다. UNIT-019가 FCM client
  bean 부재를 확인했다. 실제 FCM credential·network 호출은 관찰되지 않았고, live
  FCM은 미검증 운영 범위다.
- Answer moderation execution/deadline/verdict와 Slack dispatch scheduling은 #204/#205
  제외 범위다. 이번 테스트가 그 스케줄을 활성화하지 않는다.

### Failure recovery and reconciliation

- Outbox retry/DEAD와 lease failure의 owner·generation 검증은 `OutboxLeaseIntegrationTest`
  가 유지했다.
- adapter batch 예외는 `BATCH_FAILED` metric 후 예외를 다시 던져 다음 `fixedDelay`와
  격리한다(UNIT-017). repeating task의 영구 중지는 이번 범위에서 재현되지 않았다.
- lease 만료 전 reclaim 0건, 만료 후 generation+1, 이전 generation terminal 0행이
  Outbox와 Push에서 유지됐다.
- 운영 delay/batch/lease/retry가 없으면 enabled context는 기동하지 않는다. 잘못된
  운영 값으로 켜는 것은 사람 승인 항목이다.

## 7. Regression and residual risk

- 기존 worker production source와 migration을 변경하지 않았다는 것은 지정 경로의
  `git diff origin/main` 이 비어 있다는 증거다. 전체 애플리케이션 회귀(`INT-010`)는
  Task 8 `./harness pr-ready --project-tests`에서 PASS다.
- 변경된 production 파일은 scheduling orchestration과 Push 조건부 wiring
  (`PushConfiguration.java`, `application.properties`의 scheduling OFF 기본값,
  `src/main/java/com/dnd/qello/scheduling/**`)이다.
- production scheduling을 켜면 7 worker가 같은 process pool에서 돌아간다. pool
  starvation, DB connection 고갈, 외부 호출 폭주는 운영 숫자 승인 전에는 미검증이다.
- 다중 application instance의 owner 안정성은 identity 타입 테스트와 DB fencing에
  기대며, 두 full Spring context를 동시에 띄운 증거는 없다.
- Micrometer exporter/dashboard/alert는 제외 범위다. cardinality 폭증은 코드 경로상
  enum tag로 제한되지만 운영 exporter는 확인하지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-182-TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING.md` (Status: Approved, `2026-08-27T14:19:55+09:00`)
- Design: `docs/superpowers/specs/2026-08-27-core-worker-scheduling-design.md` (`APP-DESIGN-GH-182-001`)
- Implementation plan: `docs/superpowers/plans/2026-08-27-core-worker-scheduling.md`
- Task 7 command log: `.superpowers/sdd/2026-08-27-core-worker-scheduling/task-7-report.md`
- Task 8 command log: `.superpowers/sdd/2026-08-27-core-worker-scheduling/task-8-report.md`
- CI run: not executed in this task
- Related ADR: none new
- PR: not created (Task 8도 commit/PR을 만들지 않음)

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (`INT-004`, `INT-006` report-resolution 동시성, `INT-009` 전용 통합, harness test-run). `INT-010`은 Task 8에서 PASS
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — #204/#205는 기존 제외. INT-004 커버리지와 운영 숫자 승인은 사람 결정
- [ ] 실행 결과와 PR 설명이 일치함 — PR 없음 (Task 7·8은 PR을 만들지 않음)
