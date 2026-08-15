# Test Report: TEST-PLAN-GH-107-ANSWER-MODERATION-JOB

> Created at: `2026-08-14T00:00:00+09:00`
> GitHub Issue: `#107`
> Branch: `feat/gh-107-answer-filter-deadline`
> Commit: (미커밋 — 아래 결과는 작업 트리 상태 기준)

## 1. Executive summary

- Result: `PASS`
- Tested scope: `AnswerModerationJobIntakeService`(job 접수·dedup·fail-closed),
  `AnswerModerationExecutionWorker`(최초 1회 pipeline 실행·ALLOW/BLOCK/
  timeout/error 분기), `AnswerModerationDeadlineWorker`(deadline 경과 신호
  멱등 발행), `FilterJob.deadlineAt` 확장, outbox 계약 확장(V13 migration).
- Unverified scope: `#108`(retry) 경계와의 실제 통합, 보조 판정기·공통 장애
  영역, `deadlineWindow` 실제 운영값, 답변 도메인의 실제 호출부 연결.
- Release recommendation: 이 이슈 범위(훅·계약 자체) 기준으로 병합 가능.
  실제 서비스 진입점 연결(답변 담당)과 프로덕션 활성화는 별도 게이트.

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
| `./gradlew test` | PASS | 291 (신규 14 포함) | ~1분 | `build/test-results/test/*.xml`, 실패 0 |
| `./gradlew integrationTest` | PASS | 265 (신규 4 포함) | ~11분 | `build/test-results/integrationTest/*.xml`, 실패 0 |
| `./harness check` | PASS | - | - | secret preflight, JUnit policy, convention, commit formatter, workflow, label policy, husky 검증 모두 통과 |
| `git diff --check` | PASS | - | - | whitespace 오류 없음 |
| `npm run hooks:validate` | **미실행** | - | - | 이 로컬 환경에서 `npm`이 Windows `node.exe`로 실행되며, `scripts/python.mjs`가 PATH의 `python3`/`python`을 찾을 때 WSL의 실제 `python3`가 아니라 Windows Microsoft Store stub을 참조해 실패한다. 이 브랜치의 코드 변경과 무관한 로컬 WSL/Windows PATH 환경 문제다 — `./harness check`가 사실상 같은 정책 검증(convention/label/husky)을 이미 통과했으므로 정책 위반 위험은 낮다고 판단하지만, 이 명령 자체는 통과를 주장하지 않는다. |
| `./harness pr-ready --project-tests` | **미실행** | - | - | 실행 전 `origin/main`이 앞서 있어 `./harness sync`가 필요했고, sync는 작업 트리가 clean해야 한다. 이 보고서 시점까지 변경을 커밋하지 않아 실행하지 못했다 — 커밋/PR은 사용자 승인 이후 별도 단계. |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `AnswerModerationJobIntakeServiceTest#returnsExistingJobForDuplicateIdempotencyKey` | |
| UNIT-002 | PASS | `AnswerModerationJobIntakeServiceTest#rejectsWhenNoActiveRelease` | |
| UNIT-003 | PASS | `AnswerModerationJobIntakeServiceTest#fixesDeadlineAtCreationTime` | |
| UNIT-004 | PASS | `AnswerModerationJobIntakeServiceTest#emitsHistoryAndExecutionRequestedEvent` | |
| UNIT-005 | PASS | `AnswerModerationExecutionWorkerTest#resolvesJobAndEmitsVerdictReadyOnAllow` | |
| UNIT-006 | PASS | `AnswerModerationExecutionWorkerTest#resolvesJobWithBlockVerdict` | |
| UNIT-007 | PASS | `AnswerModerationExecutionWorkerTest#marksEventDeadOnTimeoutWithoutResolvingJob` | |
| UNIT-008 | PASS | `AnswerModerationExecutionWorkerTest#marksEventDeadOnProviderErrorWithoutResolvingJob` | |
| UNIT-009 | PASS | `AnswerModerationExecutionWorkerTest#skipsIneligibleJobWithoutCallingPipeline` | failingPipeline을 canary로 사용해 pipeline 미호출을 이중 검증 |
| UNIT-010 | PASS | `AnswerModerationExecutionWorkerTest#returnsStaleLeaseWhenCompleteFails` | |
| UNIT-011 | PASS | `AnswerModerationDeadlineWorkerTest#emitsDeadlineElapsedForDueUnresolvedJob` | |
| UNIT-012 | PASS | `AnswerModerationDeadlineWorkerTest#skipsAlreadySignaledJob` | |
| UNIT-013 | PASS | `AnswerModerationDeadlineWorkerTest#absorbsUniqueConstraintRaceAsAlreadySignaled` | |
| UNIT-014 | PASS | `AnswerModerationDeadlineWorkerTest#rejectsNonPositiveLimit` | |
| INT-001 | PASS | `AnswerModerationJobIntegrationTest#concurrentDuplicateSubmissionCreatesExactlyOneJob` | 실제 PostgreSQL, `uq_filter_job_idempotency_key` 제약이 최종 방어선 |
| INT-002 | PASS | `AnswerModerationJobIntegrationTest#concurrentExecutionWorkersResolveJobExactlyOnce` | Outbox lease/fencing 재사용 확인 |
| INT-003 | PASS | `AnswerModerationJobIntegrationTest#concurrentDeadlineScansSignalExactlyOnce` | dedup_key unique 제약 + 사전 조회 |
| INT-004 | PASS | `AnswerModerationJobIntegrationTest#lateVerdictAfterDeadlineElapsedStillEmitsVerdictReady` | 순차 시나리오 |

## 5. Failures and diagnostics

최초 실행에서 발견한 2건은 기존 회귀 가드 테스트가 "의식적 검토"를 강제하도록
설계된 대로 동작한 것이며, 신규 migration/enum 값을 반영해 수정했다.

- `FlywayMigrationContractTest#migrationsMatchAcceptedContent` — V13 파일명이
  승인 목록에 없어 실패. 목록에 추가해 해결.
- `SafetyNotificationBoundaryTest#enumsMatchTheRevisedSchemaValueSets` —
  `OutboxEventType`에 신규 3개 값이 없어 실패. 목록에 추가해 해결.
- `FlywayMigrationIntegrationTest#appliesAllMigrationsOnApplicationStartup` —
  `applied()` 크기가 12로 고정돼 있어 실패. V13 검증 추가·기대값을 13으로
  수정해 해결.

세 건 모두 애플리케이션 로직 결함이 아니라 "새 migration/enum을 추가하면
반드시 관련 가드 테스트를 함께 갱신하라"는 저장소 설계 의도가 정상 작동한
것이다.

## 6. Potential issues

### Application code

- `AnswerModerationExecutionWorker`는 timeout/error를 겪은 job을 `AUTOMATED`
  상태로 남겨두고 재시도하지 않는다 — `#108`이 이 상태를 이어받아 retry
  루프를 구현하는 것을 전제로 한 설계다. `#108`이 이 job을 실제로 다시
  claim할 방법(별도 retry 큐 등)을 아직 만들지 않았으므로, `#108` 착수 전에는
  timeout/error를 겪은 job이 사실상 방치된다 — 이는 이 이슈의 명시적 exclusion
  이지만 운영 관점에서는 `#108`을 빠르게 이어 붙여야 한다.

### Infrastructure and resource limits

- `AnswerModerationJobIntakeService`/`AnswerModerationExecutionWorker`는
  Spring 빈이 아니므로 실제 운영 executor pool 크기·timeout 값은 아직 어떤
  설정 파일에도 없다. 프로덕션 연결 시점에 반드시 명시적으로 구성해야 한다.

### Database and migrations

- `filter_job.deadline_at`을 `NOT NULL`로 추가했다 — 이 테이블에 지금까지
  production writer가 없었기 때문에 안전했다(닉네임 경로는 ephemeral 요청만
  사용). 향후 이 가정이 깨지기 전에(예: 다른 이슈가 먼저 job을 쓰기 시작하면)
  이 migration은 재작성이 필요할 수 있다 — 지금은 문제 없음.

### Concurrency and idempotency

- INT-001~003으로 3가지 동시성 경쟁(중복 접수·중복 실행·중복 deadline 신호)을
  실제 DB로 검증했다. 다만 동시 워커 수는 2로 제한했다 — 더 높은 동시성(예:
  10+)에서의 lease 재획득 지연은 검증하지 않았다(`DirectionMatchingWorker`의
  기존 동시성 테스트도 같은 범위로 제한돼 있어 일관된 수준이다).

### Transactions and event ordering

- `ModerationPipelineService`의 공급자 호출은 의도적으로 DB 트랜잭션 밖에서
  실행된다(`AnswerModerationExecutionWorker.callPipelineBounded`) — 이 설계를
  깨고 트랜잭션 안에서 호출하면 커넥션을 오래 점유해 다른 job claim을 막을
  수 있다. 코드 리뷰에서 이 경계를 우발적으로 무너뜨리지 않는지 확인이 필요.

### External APIs

- 실제 OpenAI moderation provider 호출 경로는 이 이슈에서 새로 만들지
  않았다(`#105`의 `OpenAiModerationProviderClient`를 그대로 재사용) — 이
  보고서의 테스트는 모두 fake provider를 사용했다.

### Failure recovery and reconciliation

- deadline worker가 죽거나 배포 중 중단되면 그 사이 deadline을 넘긴 job은
  다음 실행에서 정상적으로 잡힌다(멱등 스캔이므로 안전) — 별도 복구 절차가
  필요 없다.
- execution worker가 pipeline 호출 도중 죽으면(JVM crash 등) 해당
  `MODERATION_EXECUTION_REQUESTED` 이벤트는 `PROCESSING` 상태로 lease가
  걸린 채 남는다 — lease 만료 후 다른 worker가 재claim한다(Outbox 인프라
  자체가 보장, 이 이슈에서 새로 만들지 않음).

## 7. Regression and residual risk

- 기존 테스트 스위트(unit 291개, integration 265개) 전체가 이 변경 이후에도
  통과한다 — 회귀 없음.
- `npm run hooks:validate`는 로컬 환경 문제로 미실행 — `./harness check`가
  같은 정책의 상당 부분을 이미 검증했으나, 완전한 대체는 아니다.
- 커밋/PR 준비 단계(`./harness pr-ready --project-tests`)는 사용자 승인 전
  실행하지 않았다.

## 8. Artifacts

- `build/test-results/test/*.xml`
- `build/test-results/integrationTest/*.xml`
