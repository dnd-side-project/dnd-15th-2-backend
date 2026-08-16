# GitHub Issue #110 Task Contract

> Generated at: `2026-08-16T19:15:18+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `수동 검토 case와 우선순위`
- GitHub Issue: `#110`
- Branch: `feat/gh-110-manual-review-priority`
- Base branch: `main`
- Test plan: `TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY`
- Test plan approval: `APPROVED` — 사용자가 2026-08-16 구현을 승인했다.
- Confirmed policy: 검토자 권한은 신규 `REVIEWER` role을 만들지 않고 기존
  `OPERATOR` role을 재사용한다(`#109`와 동일 패턴, 사용자 승인).
- Confirmed policy: 위험 band는 `HIGH`/`STANDARD` 2단계만 둔다(정확한 band
  명칭·개수는 이슈가 미결정으로 남긴 영역이라 최소 모델만 구현). aging 승격은
  별도 스케줄러 없이 조회 시점에 `now - createdAt >= agingThreshold`를
  계산하는 순수 함수로 구현하고 DB 행을 갱신하지 않는다(사용자 승인,
  2026-08-16).

## Objective

- 자동 처리가 소진되거나 결과가 필요한 답변을 수동 검토 case로 인계하고,
  검증된 report signal 기반 band와 band 내 FIFO+aging 승격으로 검토자 큐
  처리 순서를 정한다.
- `FilterJob.applyManualDecision`(`#103`이 정의만 하고 호출자가 없던 hook)을
  이 이슈가 실제로 소비하는 첫 호출자가 되어, 자동 결과와 수동 결정의
  authority 순서를 명확히 한다 — 자동 결과가 먼저 도착하면(`INV-MAN-003`)
  그 결과를 유지하고 case만 종료하며, 수동 결정 뒤 도착하는 늦은 자동
  결과는 감사 기록만 남기고 상태를 바꾸지 않는다(`INV-MAN-004`).
- priority 계산 장애가 case 유실이나 검토 중단으로 이어지지 않게
  한다(`INV-MAN-009`) — 계산 실패 시 `STANDARD` band + FIFO로 계속
  진행한다.

## Scope

1. `ManualReviewCase`(기존)를 확장한다 — `status`(`OPEN`/`RESOLVED`),
   `filterJobId`(신규 direct FK — target+release 조합의 모호성을 없애고
   case가 어느 job에서 열렸는지 명확히 한다), `band`(`HIGH`/`STANDARD`),
   `validatedReportSignalCount`, `priorityPolicyVersion`,
   `priorityReasonCode`, `resolvedAt`, `resolvedByOperatorUserId`,
   `resolvedVerdict`.
2. `ManualReviewPriorityPolicy`(신규, 주입 config) — `highBandReportSignalThreshold`,
   `agingThreshold`(`Duration`), `policyVersion`. 실제 운영 수치는 미결정이며
   생성자 주입 값으로만 존재한다.
3. Priority 평가 — `ManualReviewCase.evaluatePriority(validatedReportSignalCount, now, policy)`가
   band와 reason code(`REPORT_SIGNAL`/`DEFAULT`)를 결정한다. 호출 서비스가
   평가 중 예외를 흡수해 `STANDARD`+`reasonCode=CALCULATION_FAILED`로
   case 생성을 계속 진행한다(`INV-MAN-009`).
4. `effectiveBand(now, policy)` — aging을 조회 시점에 계산하는 순수 함수.
   저장된 `band`가 `HIGH`이거나 `now - createdAt >= agingThreshold`이면
   `HIGH`로 취급하되 행을 갱신하지 않는다.
5. `ManualReviewPriorityEvaluation`(신규, append-only 감사 테이블) —
   caseId·band·reasonCode·policyVersion·evaluatedAt. case open 시와 명시적
   재평가(report signal 갱신) 호출마다 기록한다("재평가 이력").
6. `FilterJob.applyManualDecision`에 상태 가드를 추가한다 — 이미 `RESOLVED`인
   job(자동 결과가 먼저 도착)에는 적용을 거절해, 자동 결과가 수동 결정으로
   덮어써지지 않게 한다(`INV-MAN-003`의 전제 조건).
7. `ManualReviewDecisionService`(신규) — 검토자 결정의 유일한 진입점.
   `filterJobId`로 job을 조회해 이미 `RESOLVED`면 job은 건드리지 않고
   case만 `RESOLVED`로 종료한다(`INV-MAN-003`). 아니면
   `applyManualDecision` 호출 + `MODERATION_VERDICT_READY` outbox 발행 +
   `filter_job_status_history` 기록 + case 종료를 한 트랜잭션으로 묶는다.
8. `AnswerModerationExecutionWorker`의 `ALREADY_MANUALLY_RESOLVED` race
   흡수 경로(`finishSkipped`)에 `filter_job_status_history` 감사 기록을
   추가한다 — 현재는 아무 기록도 남기지 않아 `INV-MAN-004`("감사 기록만
   남기고")를 만족하지 못한다.
9. 검토자용 REST endpoint 2개 — 큐 조회(GET, `effectiveBand` 내림차순 +
   band 내 `case_created_at` FIFO), 결정(POST, `ALLOW`/`BLOCK`). 기존
   `OPERATOR` 세션 인증(`/admin/**`)을 재사용한다.
10. DB 마이그레이션(V16) — `manual_review_case` 컬럼 추가,
    `manual_review_priority_evaluation` 신규 테이블.
11. 단위·PostgreSQL 통합(동시성 포함) 테스트와 테스트 보고서.

## Explicit exclusions

- band 명칭, 정확한 aging 시간, band 내 stable tie-breaker, report 신뢰도
  계산과 reviewer SLA — 전부 미결정이며 이슈가 명시적으로 범위 밖으로
  뒀다.
- 기존 `safety` 패키지(`Report`/`ModerationReview`/`UserBlock`)와의 통합 —
  "검증된 report signal"은 서비스 호출 시 주입받는 순수 `int` 카운트로만
  다루고, 그 값이 실제로 어디서 오는지(`safety` 패키지 연동 여부)는 이
  이슈 범위 밖이다.
- reviewer 전용 role 신설, reviewer 배정, 알림, SLA — 기존 `OPERATOR`
  role만 재사용한다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `ManualReviewCase` 확장, `ManualReviewPriorityPolicy`, priority 평가·aging, `FilterJob.applyManualDecision` 가드, `ManualReviewDecisionService`, worker 감사 기록, REST endpoint, V16 마이그레이션, 단위·통합 테스트 | Feature executor | `INV-MAN-001`~`004`, `INV-MAN-009` 검증, `#103`(FilterJob 기존 계약)·`#107`/`#108`(worker exhaustion handoff) 기존 계약과의 호환성 리뷰 |

## Existing user-owned changes

- `origin/main`(#147 병합 직후, `d99cc78`)에서 새로 분기했다. 분기 시점
  작업 트리는 clean이었다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.filtering.*" --max-workers=1 --no-daemon
./gradlew integrationTest --tests "com.dnd.qello.ManualReview*" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./harness test-run --id TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 자동 결과가 수동 결정 전에 도착하면 유효성을 확인한 뒤 case를
      종료할 수 있다(`INV-MAN-003`) — `ManualReviewDecisionService.decide`가
      job이 이미 `RESOLVED`면 job은 건드리지 않고 그 기존 `resolvedVerdict`로
      case만 종료함을 `ManualReviewDecisionServiceTest#closesCaseWithoutTouchingAlreadyResolvedJob`과
      `ManualReviewPriorityIntegrationTest#decisionAppliesAtomically`,
      `#concurrentManualAndAutomatedResolutionConverge`로 검증했다.
      `FilterJob.applyManualDecision`에 추가한 `RESOLVED` 거절 가드가 이
      계약의 도메인 측 전제조건이다(`FilterJobTest#rejectsManualDecisionAfterAutomatedResolution`).
- [x] 수동 결정 뒤 늦은 자동 결과는 감사 기록만 남기고 상태를 바꾸지
      않는다(`INV-MAN-004`) — `AnswerModerationExecutionWorker.finishSkipped`가
      `manuallyResolved` job에 한해 `filter_job_status_history`에 기록을
      남기도록 확장했다.
      `ManualReviewPriorityIntegrationTest#lateAutomatedAttemptAfterManualResolutionIsAudited`로
      검증했다.
- [x] priority 계산 장애가 case 유실이나 검토 중단으로 이어지지
      않는다(`INV-MAN-009`) — aging은 스케줄러 없이 조회 시점 순수 함수로
      계산해 계산 장애 여지 자체를 구조적으로 없앴고(`effectiveBand`),
      report signal 평가 예외는 호출 서비스가 흡수해
      `STANDARD`+`CALCULATION_FAILED`로 case 생성을 계속한다(worker의
      `openManualReviewCaseIfAbsent`). 순수 평가 함수의 예외 발생은
      `ManualReviewCaseTest#evaluatePriorityRejectsNegativeSignalCount`로,
      fallback 흡수는 코드 리뷰로 확인했다(통합 테스트 미실행 — 보고서
      6절 참고).
- [x] 승인된 P0 테스트와 저장소 필수 검증이 통과하고 테스트 보고서가
      남는다 — unit 12개(UNIT-001~012), integration 8개(INT-001~008,
      실제 PostgreSQL 동시성·트랜잭션·REST endpoint 인가 포함) 전부
      통과. 상세는
      `docs/reports/tests/gh-110-TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY.md`
      참고. `./harness check`, `./harness pr-ready --project-tests`
      (전체 unit·integration 스위트, `./gradlew check` 포함) 통과.
- [x] 실행하지 못한 검증과 남은 위험을 보고서에 기록한다 — 위 보고서
      6·7절에 "명시적 재평가" 서비스 미구현, `CALCULATION_FAILED`
      fallback 실증 테스트 미실행, `safety` 패키지 미통합, `#113`
      scheduler 배선 등을 명시했다.
