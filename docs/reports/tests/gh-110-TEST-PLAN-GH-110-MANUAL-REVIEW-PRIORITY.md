# Test Report: TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY

> Created at: `2026-08-17T01:15:00+09:00`
> GitHub Issue: `#110`
> Branch: `feat/gh-110-manual-review-priority`
> Commit: 아직 미커밋 상태에서 작성(base `d99cc78`)

## 1. Executive summary

- Result: `PASS`
- Tested scope: `ManualReviewCase` band/priority 평가와 조회 시점 aging,
  `FilterJob.applyManualDecision` 상태 가드(`INV-MAN-003`), 늦은 자동 결과
  감사 기록(`INV-MAN-004`), priority 계산 실패 fallback(`INV-MAN-009`),
  `ManualReviewDecisionService`의 트랜잭션 원자성과 authority 경합 처리,
  검토자 큐 정렬, priority 재평가 이력, 검토자 REST endpoint 2개. 승인된
  TEST-PLAN의 UNIT-001~012, INT-001~008 전체.
- Unverified scope: `safety` 패키지(`Report`)와의 실제 통합, 큐 조회·재평가
  scheduler 배선(`#113`로 이연), reviewer 전용 role, band 명칭·aging
  시간·report 신뢰도의 실제 운영 수치.
- Release recommendation: 이 이슈 범위(메커니즘+테스트) 기준으로 병합
  가능. production 활성화는 `#113`에서 별도 승인 필요.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 21 (Gradle toolchain) |
| Spring Boot | 저장소 `build.gradle.kts`에 고정된 버전 |
| Database | Testcontainers PostgreSQL(PostGIS 16-3.5-alpine), 통합 테스트 전용 |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` (전체 unit) | PASS | 저장소 전체 unit 스위트, 신규 22개(`ManualReviewCaseTest` 8, `ManualReviewDecisionServiceTest` 2, `FilterJobTest` 신규 2, `FilteringValueObjectsTest` 갱신) 포함 | `./harness pr-ready --project-tests` 실행에 포함 | 로컬 실행 로그 |
| `./gradlew integrationTest` (전체 integration) | PASS | 저장소 전체 integration 스위트 367개 이상 + 신규 8개(1차 실행에서 기존 파일들의 FK 정리 순서 회귀 4건 발견 후 수정, 재실행 시 전체 통과) | `./harness pr-ready --project-tests` 실행에 포함, 약 4분 23초 | 로컬 실행 로그 |
| `com.dnd.qello.filtering.ManualReviewCaseTest` | PASS | 8 | 개별 실행 확인 | JUnit XML |
| `com.dnd.qello.filtering.moderation.ManualReviewDecisionServiceTest` | PASS | 2 | 개별 실행 확인 | JUnit XML |
| `com.dnd.qello.filtering.FilterJobTest` | PASS | 18(신규 2) | 개별 실행 확인 | JUnit XML |
| `com.dnd.qello.ManualReviewPriorityIntegrationTest` | PASS | 8 | 약 25초(PostgreSQL 컨테이너 기동 포함) | JUnit XML |
| `./harness check` | PASS | 정책·훅·라벨·workflow 검증 | | 로컬 실행 로그 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `ManualReviewCaseTest#evaluatesHighBandWhenReportSignalMeetsThreshold` | |
| UNIT-002 | PASS | `ManualReviewCaseTest#evaluatesStandardBandWhenReportSignalBelowThreshold` | |
| UNIT-003 | PASS | `ManualReviewCaseTest#agedCaseIsEffectivelyHigh` | |
| UNIT-004 | PASS | `ManualReviewCaseTest#notYetAgedCaseKeepsStoredBand` | |
| UNIT-005 | PASS | `ManualReviewCaseTest#effectiveBandDoesNotMutateStoredBand` | |
| UNIT-006 | PASS | `ManualReviewCaseTest#evaluatePriorityRejectsNegativeSignalCount` | 계획보다 1건 추가 — 계산 실패를 유발하는 구체적 입력(음수 signal)을 직접 검증. worker 레벨 fallback 흡수는 코드 리뷰로 확인(6절 참고) |
| UNIT-007 | PASS | `FilterJobTest#rejectsManualDecisionAfterAutomatedResolution` | |
| UNIT-008 | PASS | `FilterJobTest#allowsManualDecisionFromNonResolvedStatuses` | |
| UNIT-009 | PASS | `ManualReviewDecisionServiceTest#closesCaseWithoutTouchingAlreadyResolvedJob` | |
| UNIT-010 | PASS | `ManualReviewDecisionServiceTest#appliesManualDecisionAndPublishesVerdictReady` | |
| UNIT-011 | PASS | `ManualReviewCaseTest#rejectsResolvingAlreadyResolvedCase` | case 도메인 레벨에서 직접 검증(서비스 레벨 중복 검증은 생략) |
| UNIT-012 | PASS | `ManualReviewCaseTest#queueOrdersByEffectiveBandThenFifo` | 순수 도메인 비교로 검증, DB 정렬은 INT-004가 검증 |
| INT-001 | PASS | `ManualReviewPriorityIntegrationTest#decisionAppliesAtomically` | |
| INT-002 | PASS | `#concurrentManualAndAutomatedResolutionConverge` | 어느 경로가 이겼는지는 고정하지 않고 "정확히 하나의 판정으로 수렴"이라는 불변식만 검증 |
| INT-003 | PASS | `#lateAutomatedAttemptAfterManualResolutionIsAudited` | |
| INT-004 | PASS | `#queueOrdersByEffectiveBandAndFifo` | |
| INT-005 | PASS | `#priorityEvaluationIsRecordedOnCaseOpen` | |
| INT-006 | PASS | `#operatorDecidesThroughEndpoint` | |
| INT-007 | PASS | `#rejectsUnauthenticatedDecision` | |
| INT-008 | PASS | `#concurrentCaseCreationKeepsUniqueness` | |

## 5. Failures and diagnostics

1차 실행에서 발견해 즉시 수정한 항목(최종 실행은 전부 PASS):

- **기존 통합 테스트 4개의 `@BeforeEach` 정리 순서 누락**: V16이 추가한
  `manual_review_priority_evaluation`이 `manual_review_case`를 FK로
  참조하는데, `AnswerModerationRetryIntegrationTest`,
  `AnswerModerationJobIntegrationTest`, `FilteringPersistenceIntegrationTest`,
  `SnapshotHealthMigrationIntegrationTest`(#109) 네 파일 모두
  `manual_review_priority_evaluation`을 먼저 지우지 않고
  `manual_review_case`를 삭제해 FK 위반(`DataIntegrityViolationException`)이
  발생했다. 네 파일 모두 `manual_review_case` 삭제 직전에
  `manual_review_priority_evaluation` 삭제를 추가해 해결했다. 이 문제는
  `./harness pr-ready --project-tests`(전체 스위트)를 실행하기 전까지는
  드러나지 않았다 — 신규 통합 테스트 파일만 개별 실행했을 때는 발견되지
  않았다.
- **worker 단위 테스트 3건의 mock 미스매치**: `applyVerdict`가
  `findByIdForUpdate`(#110 신규 잠금 조회)를 쓰도록 바뀌면서, 기존
  `AnswerModerationExecutionWorkerTest`가 `findById`만 stub해 job을 찾지
  못하는 상태로 회귀했다. `setUp()`에서 `findByIdForUpdate`가 `findById`
  stub에 위임하도록 기본 답변을 연결해 해결했다.

## 6. Potential issues

### Application code

- `ManualReviewPriorityPolicy`의 실제 운영 수치(report signal threshold,
  aging 시간)는 이슈 본문이 미결정으로 남긴 영역이라 생성자 주입 값으로만
  존재한다. `AnswerModerationExecutionWorker`가 case를 열 때는 항상
  `validatedReportSignalCount=0`으로 평가한다(`safety` 패키지 미통합) —
  즉 worker 경로로 열리는 모든 case는 최초에는 항상 `STANDARD`+`DEFAULT`이며,
  `HIGH` band는 이 이슈 범위에서는 오직 aging 승격으로만 도달한다. 실제
  report signal을 반영해 case를 `HIGH`로 재평가하는 명시적 서비스
  메서드(TASK.md가 "명시적 재평가"로 언급한 부분)는 구현하지 않았다 —
  REST endpoint나 서비스 메서드가 없어 UNIT/INT 시나리오에도 포함되지
  않았다. `safety` 패키지 통합(#110 범위 밖)과 함께 후속 이슈에서 다뤄야
  한다.
- `ManualReviewCase.evaluatePriority`가 던지는 예외를
  `AnswerModerationExecutionWorker.openManualReviewCaseIfAbsent`가
  흡수해 `STANDARD`+`CALCULATION_FAILED`로 대체하는 경로(`INV-MAN-009`)는
  코드 리뷰로 확인했지만, 이 fallback이 실제로 트리거되는 통합 테스트는
  작성하지 않았다 — `validatedReportSignalCount=0`으로 고정 호출하므로
  실패할 입력이 없다(0은 항상 유효). 향후 report signal이 실제로
  연결되면 이 fallback 경로도 통합 테스트로 검증해야 한다.

### Infrastructure and resource limits

- 검토자 큐 조회·priority 재평가를 실제로 주기적으로 트리거하는 코드는
  이 이슈 범위가 아니다(`#113`로 이연).

### Database and migrations

- V16은 `manual_review_case`에 `filter_job_id NOT NULL`을 기존 행 보정
  없이 추가했다 — `#108`의 worker가 이미 이 테이블에 쓰기 시작했지만,
  그 worker는 어떤 스케줄러에도 연결돼 있지 않아 실제 배포 환경에는
  행이 없다는 전제가 유효하다(V14와 동일 논리).
- V16 스키마·제약은 별도 전용 테스트로 검증했다(코드에는 포함했으나
  `FlywayMigrationIntegrationTest`의 V13~V15 패턴을 그대로 재사용 —
  `v16AddsManualReviewPriorityAndAuthorityCatalog`).

### Concurrency and idempotency

- INT-002로 검토자 수동 결정과 자동 결과 적용의 진짜 동시 경합을
  검증했다. `FilterJobRepository.findByIdForUpdate`(신규,
  `PESSIMISTIC_WRITE`)를 두 경로 모두 사용하도록
  `AnswerModerationExecutionWorker.applyVerdict`도 함께 수정했다 —
  한쪽만 잠그면 나중에 커밋하는 쪽이 먼저 커밋된 결과를 낡은 스냅샷으로
  덮어쓸 수 있다(lost update)는 점을 코드 분석으로 확인하고 양쪽 모두
  수정했다.
- INT-008로 기존 `INV-MAN-001` 유일성 계약이 `filter_job_id` 등 컬럼
  확장 후에도 유지되는지 회귀 확인했다.

### Transactions and event ordering

- `ManualReviewDecisionService.decide`는 job 전이·`MODERATION_VERDICT_READY`
  발행·`filter_job_status_history` 기록·case 종료를 하나의
  `@Transactional` 경계로 묶는다(INT-001로 검증).
- `AnswerModerationExecutionWorker.openManualReviewCaseIfAbsent`는 case
  생성과 최초 priority 평가 기록을 하나의 트랜잭션으로 묶도록
  변경했다(기존에는 case 저장만 자체 트랜잭션이었고 평가 이력이 없었다).

### External APIs

- 이 이슈는 외부 API 연동이 없다.

### Failure recovery and reconciliation

- INT-003으로 늦은 자동 결과가 감사 기록만 남기고 상태를 보존하는지
  확인했다(`INV-MAN-004`). 이 감사 기록은 `finishSkipped`가 흡수하는 모든
  job-state race(`ALREADY_MANUALLY_RESOLVED`뿐 아니라
  `STALE_ATTEMPT_GENERATION`, `INVALID_JOB_STATUS`)에 대해 job이
  `manuallyResolved=true`인 경우에만 조건부로 기록되도록 구현했다 —
  emergency migration(`#109`)으로 인한 stale generation 거절처럼 수동
  검토와 무관한 race는 이 감사 기록을 남기지 않는다(불필요한 노이즈 방지).

## 7. Regression and residual risk

- `#111`(Slack 알림), `#112`(appeal 흐름), `#113`(production Spring
  배선)은 이 이슈 범위가 아니며 검증하지 않았다.
- reviewer 전용 role은 만들지 않고 기존 `OPERATOR` role을
  재사용했다(`#109`와 동일 패턴) — 세부 권한(예: reviewer만 검토 가능,
  operator는 불가)이 필요해지면 별도 이슈가 필요하다.
- 검토자 큐 조회 REST endpoint는 `agingThresholdSeconds`를 호출자가
  명시하도록 설계했다(서버가 값을 고정하지 않음) — 실제 운영 시
  프런트엔드/운영 스크립트가 이 값을 잘못 지정하면 큐 순서가 왜곡될 수
  있다는 점을 API 문서에 남겼다.
- `ManualReviewCase.evaluatePriority`의 `CALCULATION_FAILED` fallback
  경로는 통합 테스트로 직접 트리거하지 못했다(위 6절 참고).

## 8. Artifacts

- Test plan: `docs/test-plans/gh-110-TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY.md`
- CI run: 로컬 실행만 수행(이 브랜치는 아직 push되지 않음)
- Related ADR: 없음(신규 ADR 미작성 — 설계 결정은 TASK.md와 이 보고서에 기록)
- PR: 아직 생성되지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨(5·6·7절)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — 아직 별도 Issue를 만들지
      않았다. 6·7절의 "명시적 재평가" 서비스 메서드와 `CALCULATION_FAILED`
      fallback 실증 테스트는 report signal 실제 연동 시점에 함께 다룰
      필요가 있다.
- [x] 실행 결과와 PR 설명이 일치함(PR 생성 시 이 보고서를 링크)
