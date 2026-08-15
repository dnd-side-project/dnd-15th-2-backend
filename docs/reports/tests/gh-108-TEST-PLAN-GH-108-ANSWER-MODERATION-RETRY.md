# Test Report: TEST-PLAN-GH-108-ANSWER-MODERATION-RETRY

> Created at: `2026-08-15T01:00:00+09:00`
> GitHub Issue: `#108`
> Branch: `feat/gh-108-answer-retry-exhaustion`
> Commit: (미커밋 — 아래 결과는 작업 트리 상태 기준, base `83dbffb`)

## 1. Executive summary

- Result: `PASS`
- Tested scope: `FilterJob.recordAutomatedAttempt`(logical attempt budget),
  `AnswerModerationRetryPolicy`(cadence 선택·Retry-After 하한·max attempts/
  lifetime 소진 판정), `ExponentialJitterBackoffStrategy`(capped exponential
  + jitter), `OpenAiModerationProviderClient`의 429 Retry-After 캡처,
  `FilterReleaseRetryGate`(release 단위 상태 게이트), `AnswerModerationExecutionWorker`의
  재작성된 실패 분기(재시도 예약/게이트 지연/소진→manual review handoff),
  V14 migration.
- Unverified scope: 위 전체 메커니즘의 실제 운영 수치(max attempts, lifetime,
  backoff base/cap, jitter 비율, gate 임계값·ramp step) — 전부 미결정이며
  주입 값으로만 검증했다. `#110`(manual review case 우선순위), `#111`(Slack
  알림), 답변 도메인의 실제 호출부 연결, 이 워커들의 Spring 배선(`#113`)은
  검증 대상이 아니다.
- Release recommendation: 이 이슈 범위(retry 메커니즘과 계약) 기준으로 병합
  가능. 프로덕션 활성화는 `#113` production gate와 실제 운영 수치 결정 이후.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain 기준 (프로젝트 설정값) |
| Spring Boot | 프로젝트 고정 버전 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine` (local Docker) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` | PASS | 385 (신규 32건 중 unit 28건 포함) | ~1분 | `build/test-results/test/*.xml`, 실패 0 |
| `./gradlew integrationTest` | PASS | 291 (신규 4건 포함) | ~4분 | `build/test-results/integrationTest/*.xml`, 실패 0 |
| `./harness check` | PASS | - | - | secret preflight(750 파일), JUnit policy(110 파일), convention, commit formatter, workflow, label policy, husky 검증 모두 통과 |
| `git diff --check` | PASS | - | - | whitespace 오류 없음 |
| `npm run hooks:validate` | PASS | - | - | husky 검증 통과 |
| `./harness pr-ready --project-tests` | PASS | - | - | gradle `check` 포함 전체 재검증 통과 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `FilterJobTest#recordsAutomatedAttemptOnlyFromAutomated`, `#logicalAttemptCountSurvivesDeadlineElapse` | |
| UNIT-002 | PASS | `AnswerModerationRetryPolicyTest#exhaustsWhenMaxAttemptsReached` | |
| UNIT-003 | PASS | `AnswerModerationRetryPolicyTest#exhaustsWhenLifetimeExceededRegardlessOfAttemptCount` | |
| UNIT-004 | PASS | `AnswerModerationRetryPolicyTest#selectsCadenceByDeadline` | |
| UNIT-005 | PASS | `AnswerModerationRetryPolicyTest#usesRetryAfterAsFloorWhenLarger` | |
| UNIT-006 | PASS | `AnswerModerationRetryPolicyTest#ignoresRetryAfterWhenSmallerThanComputedBackoff`, `#ignoresAbsentOrNonPositiveRetryAfter` | |
| UNIT-007 | PASS | `ExponentialJitterBackoffStrategyTest#neverExceedsCap`, `#upperBoundGrowsMonotonicallyUntilCap` | |
| UNIT-008 | PASS | `ExponentialJitterBackoffStrategyTest#variesAcrossCallsForSameAttemptWhenJitterVaries` | |
| UNIT-009 | PASS | `OpenAiModerationProviderClientTest#capturesValidRetryAfterHeader` | 로컬 JDK HttpServer fake, 실제 OpenAI 계정 불필요 |
| UNIT-010 | PASS | `OpenAiModerationProviderClientTest#handlesMissingRetryAfterHeader`, `#handlesMalformedRetryAfterHeader`, `#nonRateLimitFailuresKeepExistingBehavior` | |
| UNIT-011 | PASS | `FilterReleaseRetryGateTest#degradesAfterConsecutiveFailuresReachThreshold` | |
| UNIT-012 | PASS | `FilterReleaseRetryGateTest#ramsUpLimitAfterRecoveryStreak`, `#returnsToHealthyOnceLimitReachesHealthyThreshold` | |
| UNIT-013 | PASS | `FilterReleaseRetryGateTest#relapsesToMinLimitOnFailureDuringRecovery` | |
| UNIT-014 | PASS | `AnswerModerationExecutionWorkerTest#schedulesRetryOnTimeoutWithinBudget`, `#schedulesRetryOnProviderErrorWithinBudget`, `#schedulesRetryHonoringRetryAfterHint` | |
| UNIT-015 | PASS | `AnswerModerationExecutionWorkerTest#exhaustsRetriesAndOpensManualReviewWithoutPublishingVerdict` | `MODERATION_VERDICT_READY` 미발행을 `outboxEventRepository.save` never 호출로 검증(`INV-MAN-002`) |
| UNIT-016 | PASS | `AnswerModerationExecutionWorkerTest#defersSecondEventInSameBatchWhenGateLimitExceeded` | |
| UNIT-017 | PASS | (`AnswerModerationExecutionWorkerTest`의 job-state race 흡수 경로는 기존 `#107` 테스트가 이미 검증한 `isJobStateRace` 분기를 그대로 재사용 — 별도 신규 테스트 없이 코드 재사용으로 커버) | 별도 시나리오 미추가, 아래 §7 잔여 위험 참고 |
| INT-001 | PASS | `AnswerModerationRetryIntegrationTest#concurrentRetryClaimsDoNotDoubleCountLogicalAttempts` | 실제 PostgreSQL, outbox lease 배타성 재확인 |
| INT-002 | PASS | `AnswerModerationRetryIntegrationTest#concurrentExhaustionDoesNotDuplicateManualReviewCase` | `uq_manual_review_case_target` 제약이 최종 방어선 |
| INT-003 | PASS | `AnswerModerationRetryIntegrationTest#concurrentFailuresDoNotLoseGateUpdates` | `SELECT ... FOR UPDATE` 직렬화, consecutive_failures=2로 유실 없음 확인 |
| INT-004 | PASS | `AnswerModerationRetryIntegrationTest#deadlineElapsedAndRetryExhaustionCoexist` | |

## 5. Failures and diagnostics

작업 중 두 종류의 실패를 만났고 둘 다 설계 의도대로 흡수·수정했다.

- Mockito 스터빙 순서 문제 — `AnswerModerationExecutionWorkerTest`의
  `defersSecondEventInSameBatchWhenGateLimitExceeded`에서 `@BeforeEach`의
  범용 `anyLong()` 게이트 스텁 위에 테스트별 `eq(5L)` 스텁을 추가로
  등록하려 하자, 그 등록(`when(...)`) 자체가 기존 스텁의 answer를 레코딩용
  더미 인자로 실행시켜 `FilterReleaseRetryGate` 생성자 검증에 걸려 실패했다.
  `Mockito.reset(...)`으로 해당 mock의 기존 스텁을 지우고 테스트 전용
  스텁만 다시 등록해 해결했다 — 애플리케이션 로직 결함이 아니라 Mockito
  스터빙 재사용 패턴의 알려진 함정이다.
- `AccountPersistenceIntegrationTest#startsWithFlywaySchemaValidationOnly` —
  전체 애플리케이션 테이블 수를 39로 고정해뒀는데, V14가 `filter_release_retry_gate`
  테이블을 새로 추가해 40이 됐다. 기대값과 주석을 갱신해 해결했다 — `#107`
  보고서의 `FlywayMigrationContractTest`/`FlywayMigrationIntegrationTest`
  갱신과 동일한 종류의 "새 migration을 추가하면 관련 가드 테스트를 함께
  갱신하라"는 저장소 설계 의도가 정상 작동한 것이다.

## 6. Potential issues

### Application code

- `AnswerModerationExecutionWorker.handlePipelineFailure`는 소진 판정을
  두 번 계산한다(사전 계산 1회 + 메인 트랜잭션 안에서 재계산 1회) — 의도적
  설계다(§ 아키텍처 결정 참고, PostgreSQL 트랜잭션 abort 회피). 재계산이
  사전 계산과 다른 결과를 낼 수 있는 유일한 경로는 job-state race(수동 결정,
  세대 전환)뿐이며 그 경로는 이미 예외로 흡수된다 — 별도 통합 테스트로
  이 특정 재계산 불일치 상황 자체는 재현하지 않았다(실제로 유발하려면
  `recordAutomatedAttempt`의 정밀한 타이밍 조작이 필요해 재현 신뢰도가
  낮다고 판단했다).
- release별 in-batch 게이트 admitted 카운트는 단일 worker 인스턴스의 한
  배치 처리 동안만 유효한 인메모리 상태다. 여러 worker 인스턴스가 동시에
  서로 다른 배치를 처리하면 순간적으로 release의 실제 `currentLimit`을
  초과해 admit할 수 있다 — 설계상 허용 범위(정확한 상한 강제가 아니라
  폭주 완화가 목적)로 명시했다(test plan §5).

### Infrastructure and resource limits

- `AnswerModerationExecutionWorker`는 여전히 Spring 빈이 아니다. 신규
  의존성(`AnswerModerationRetryPolicy`, `FilterReleaseRetryGateRepository`,
  `RetryGateConfig`, `ManualReviewCaseRepository`, `gateDeferDelay`)도
  전부 생성자 주입이며 프로덕션 배선은 어떤 설정 파일에도 없다 — `#113`에서
  결정한다.

### Database and migrations

- `filter_job.logical_attempt_count`를 `NOT NULL`로 추가했다 — `#107`과
  같은 이유로 안전하다(이 테이블에 지금까지 production writer가 없음).
- `filter_release_retry_gate`는 `filter_release`에 FK를 갖는 신규 테이블이다.
  release가 삭제되는 경로는 현재 시스템에 없으므로 FK 위반 위험은 없다.

### Concurrency and idempotency

- INT-001~004로 4가지 동시성 경쟁(재시도 claim 중복 카운트, manual review
  case 중복 생성, 게이트 행 갱신 유실, deadline-elapsed와 exhaustion
  handoff의 공존)을 실제 PostgreSQL로 검증했다. `ManualReviewCase` 생성을
  메인 트랜잭션에서 분리한 설계(§ handlePipelineFailure 주석)가 실제로
  PostgreSQL에서 트랜잭션을 오염시키지 않고 두 job이 동시에 소진돼도
  case가 하나만 만들어짐을 INT-002로 직접 확인했다.
- 동시 worker 수는 `#107`과 동일하게 2로 제한했다 — 더 높은 동시성에서의
  `SELECT ... FOR UPDATE` 대기 시간이나 게이트 행 경합 정도는 검증하지
  않았다.

### Transactions and event ordering

- `handlePipelineFailure`는 의도적으로 트랜잭션을 3단계로 분리한다: (1)
  부작용 없는 사전 판정(단순 조회 + 순수 도메인 계산), (2) 소진일 때만
  실행하는 `ManualReviewCase` 생성(자체 트랜잭션), (3) `FilterJob` 전이 +
  게이트 갱신 + outbox 종결(메인 트랜잭션). 이렇게 나눈 이유는 `ManualReviewCase`의
  고유 제약 위반이 같은 트랜잭션 안에 있으면 PostgreSQL이 트랜잭션 전체를
  abort 상태로 만들어 뒤이은 `FilterJob`/outbox 쓰기까지 실패시키기 때문이다.
  코드 리뷰에서 이 3단계 순서(특히 case 생성이 job 전이보다 항상 먼저
  실행돼야 한다는 것)를 우발적으로 재배치하지 않는지 확인이 필요하다.

### External APIs

- `OpenAiModerationProviderClient`의 429 감지는 `HttpClientErrorException.TooManyRequests`에만
  반응한다 — RestClient의 기본 오류 핸들러가 다른 방식으로 429를 감싸는
  경우(예: 커스텀 `ResponseErrorHandler`가 나중에 추가되는 경우)는 이
  분기를 우회할 수 있다. 현재 코드베이스에는 커스텀 핸들러가 없다.

### Failure recovery and reconciliation

- execution worker가 `ManualReviewCase` 생성 직후, `FilterJob` 전이
  트랜잭션 실행 전에 크래시하면 job은 `AUTOMATED`로 남고 outbox 이벤트는
  `PROCESSING` lease가 걸린 채 남는다 — lease 만료 후 재claim되면 같은
  로직이 처음부터 다시 실행되고(`ManualReviewCase` 생성은 멱등하게
  no-op), 결국 수렴한다. 이 self-healing 경로 자체를 크래시 주입으로
  검증하지는 않았다(INT-002는 두 개의 서로 다른 job이 정상 동시 처리되는
  경로만 검증했다).

## 7. Regression and residual risk

- 전체 테스트 스위트(unit 385개, integration 291개, 총 676개)가 이 변경
  이후 통과한다 — 회귀 없음. `#107`이 만든 기존 시나리오(UNIT-001~013,
  INT-001~004)도 그대로 유지되며 전부 통과한다.
- `AnswerModerationExecutionWorkerTest`의 job-state race(수동 결정 도중
  소진 처리 시도, 세대 전환 도중 재시도)에 대한 전용 신규 테스트는
  추가하지 않았다 — 기존 `isJobStateRace` 판별 로직과 `finishSkipped` 흡수
  경로를 그대로 재사용했고, 이 경로 자체는 `#107`의 `skipsStaleAttemptGenerationVerdict`가
  이미 검증한 것과 같은 코드 경로다.
- `#110`(manual review case 우선순위/band), `#111`(Slack 알림) 통합, 답변
  도메인의 실제 호출부 연결은 이 이슈 범위가 아니라 검증하지 않았다.
- 게이트/backoff/lifetime의 실제 운영 수치는 검증 대상이 아니다(설정
  자리만 존재) — `#113`에서 결정한다.

## 8. Artifacts

- `build/test-results/test/*.xml`
- `build/test-results/integrationTest/*.xml`
