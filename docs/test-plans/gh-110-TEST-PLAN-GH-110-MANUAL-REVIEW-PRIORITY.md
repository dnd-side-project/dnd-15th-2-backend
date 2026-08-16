# Test Plan: TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY

> Created at: `2026-08-16T19:21:51+09:00`
> GitHub Issue: `#110`
> Status: Approved

## 1. Objective

자동 처리가 소진되거나 결과가 필요한 답변을 수동 검토 case로 인계하고,
검증된 report signal 기반 band와 band 내 FIFO+aging 승격으로 검토자 큐
처리 순서를 정하는 메커니즘을 검증한다.

- 자동 결과와 수동 결정의 authority 순서가 명확한지 검증한다. 자동
  결과가 수동 결정보다 먼저 도착하면 그 결과를 유지하고 case만 종료해야
  한다(`INV-MAN-003`). 깨지면 검토자가 수동으로 내린 결정이 이미 자동으로
  확정된 판정을 덮어써 판정 이력이 모순될 수 있다.
- 수동 결정 뒤 도착하는 늦은 자동 결과가 감사 기록만 남기고 상태를
  바꾸지 않는지 검증한다(`INV-MAN-004`). 깨지면 검토자의 최종 결정이
  뒤늦게 도착한 자동 판정으로 조용히 뒤집힐 수 있다.
- priority 계산 장애가 case 유실이나 검토 중단으로 이어지지 않는지
  검증한다(`INV-MAN-009`). 깨지면 report signal 계산이 실패했을 때 case
  자체가 생성되지 않거나 큐에서 사라져, 실제로 검토가 필요한 콘텐츠가
  영구히 노출될 수 있다.
- `ManualReviewCase`의 동일 대상·release 유일성이 확장 후에도 유지되는지
  확인한다(`INV-MAN-001`, 기존 계약 회귀 방지).

## 2. Scope

### Included

- `ManualReviewCase` 확장: `status`(`OPEN`/`RESOLVED`), `filterJobId`(신규
  direct FK), `band`(`HIGH`/`STANDARD`), `validatedReportSignalCount`,
  `priorityPolicyVersion`, `priorityReasonCode`, `resolvedAt`,
  `resolvedByOperatorUserId`, `resolvedVerdict`.
- `ManualReviewPriorityPolicy`(신규): `highBandReportSignalThreshold`,
  `agingThreshold`, `policyVersion`.
- `ManualReviewCase.evaluatePriority(...)`: report signal이 임계값을
  넘으면 `HIGH`+`REPORT_SIGNAL`, 아니면 `STANDARD`+`DEFAULT`. 계산 중
  예외가 나면 호출 서비스가 흡수해 `STANDARD`+`CALCULATION_FAILED`로
  대체한다.
- `ManualReviewCase.effectiveBand(now, policy)`: 저장된 band가 `HIGH`이거나
  `now - createdAt >= agingThreshold`이면 `HIGH`. 조회 시점에만 계산하고
  DB 행을 갱신하지 않는다.
- `ManualReviewPriorityEvaluation`(신규 append-only 감사 레코드): case
  open과 명시적 재평가마다 band·reasonCode·policyVersion·evaluatedAt을
  기록한다.
- `FilterJob.applyManualDecision`에 상태 가드 추가: 이미 `RESOLVED`인
  job에는 거절한다.
- `ManualReviewDecisionService`(신규): `filterJobId`로 job을 조회해
  이미 `RESOLVED`면 case만 종료하고, 아니면 `applyManualDecision` +
  `MODERATION_VERDICT_READY` outbox 발행 + `filter_job_status_history`
  기록 + case 종료를 한 트랜잭션으로 묶는다.
- `AnswerModerationExecutionWorker.finishSkipped`가 `ALREADY_MANUALLY_RESOLVED`
  race를 흡수할 때 `filter_job_status_history`에 감사 기록을 남기도록
  확장한다.
- 검토자용 REST endpoint 2개: 큐 조회(GET, `effectiveBand` 내림차순 +
  band 내 `case_created_at` FIFO), 결정(POST, `ALLOW`/`BLOCK`). 기존
  `OPERATOR` 세션 인증 재사용.
- V16 마이그레이션: `manual_review_case` 컬럼 추가,
  `manual_review_priority_evaluation` 신규 테이블.
- 단위·PostgreSQL 통합(동시성 포함) 테스트와 테스트 보고서.

### Excluded

- band 명칭, 정확한 aging 시간, band 내 stable tie-breaker, report 신뢰도
  계산과 reviewer SLA — 전부 미결정이며 생성자/설정 주입 값 또는 최소
  모델로만 존재한다.
- 기존 `safety` 패키지(`Report`/`ModerationReview`/`UserBlock`)와의 통합
  — "검증된 report signal"은 서비스 호출 시 주입받는 순수 `int` 카운트로만
  다룬다.
- reviewer 전용 role 신설, reviewer 배정, 알림, SLA — 기존 `OPERATOR`
  role만 재사용한다.
- 큐 조회·재평가를 실제로 주기적으로 실행하는 scheduler와 Spring bean
  배선 — `#105`~`#109`와 동일하게 `#113` production gate로 이연.
- appeal(이의제기) 흐름과의 연동 — 별도 이슈(`#112`) 범위.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #110 | 자동 결과가 수동 결정 전에 도착하면 유효성을 확인한 뒤 case를 종료할 수 있다 (`INV-MAN-003`) |
| GitHub Issue #110 | 수동 결정 뒤 늦은 자동 결과는 감사 기록만 남기고 상태를 바꾸지 않는다 (`INV-MAN-004`) |
| GitHub Issue #110 | priority 계산 장애가 case 유실이나 검토 중단으로 이어지지 않는다 (`INV-MAN-009`) |
| 기존 코드: `FilterJob.applyManualDecision` | `#103`이 정의만 하고 호출자가 없던 hook — 이 이슈가 실제로 소비하는 첫 호출자 |
| 기존 코드: `V10__create_filtering_schema.sql`의 `manual_review_case` 주석 | "우선순위 계산, band, reviewer 배정, FIFO/aging은 #110(F07)가 컬럼을 추가해 구현한다" |
| 기존 코드: `AnswerModerationExecutionWorker.finishSkipped` | `ALREADY_MANUALLY_RESOLVED` race를 흡수하지만 감사 기록을 남기지 않음(확인된 갭) |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 수동 결정이 이미 자동 확정된 판정을 덮어씀 | High — 판정 이력 모순, 공개 상태 오염 가능 | Medium(가드 없으면 상시 발생) | P0 | UNIT-007, UNIT-009, INT-001, INT-002 |
| 늦은 자동 결과가 수동 결정을 조용히 뒤집음 | High — 검토자 결정 무력화 | Low(기존 `manuallyResolved` 가드가 이미 일부 차단) | P0 | INT-003 |
| priority 계산 실패로 case가 생성되지 않거나 유실됨 | Critical — 검토 필요 콘텐츠 노출 지속 | Low | P0 | UNIT-006 |
| band/FIFO 정렬 오류로 실제 HIGH 우선순위 case가 뒤로 밀림 | Medium — 검토 지연 | Medium | P1 | UNIT-012, INT-004 |
| `ManualReviewCase` 유일성 회귀(확장 컬럼 추가 중 실수) | Medium — 중복 case 생성 | Low | P1 | INT-008 |
| REST endpoint 인가 누락 | High — 미인증 사용자의 검토 결정 | Low | P0 | INT-006, INT-007 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-001 | report signal이 threshold 이상 | priority 평가 | `HIGH`+`REPORT_SIGNAL` | P0 | Feature executor |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-002 | report signal이 threshold 미만 | priority 평가 | `STANDARD`+`DEFAULT` | P0 | Feature executor |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-003 | 저장된 band `STANDARD`, aging threshold 경과 | `effectiveBand(now)` 호출 | `HIGH` 반환 | P0 | Feature executor |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-004 | 저장된 band `STANDARD`, aging threshold 미경과 | `effectiveBand(now)` 호출 | `STANDARD` 반환 | P0 | Feature executor |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-005 | 임의 상태의 case | `effectiveBand` 반복 호출 | 저장된 `band` 필드 자체는 불변(순수 함수) | P0 | Feature executor |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-006 | priority 평가 도중 예외 발생하도록 구성 | case open 서비스 호출 | 예외를 흡수해 `STANDARD`+`CALCULATION_FAILED`로 case 생성 계속 | P0 | Feature executor |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-007 | `FilterJob`이 `RESOLVED` 상태 | `applyManualDecision` 호출 | `INVALID_JOB_STATUS`로 거절 | P0 | Feature executor |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-008 | `FilterJob`이 `MANUAL_REVIEW_REQUIRED`/`RETRY_EXHAUSTED`/`AUTOMATED` | `applyManualDecision` 호출 | 허용되어 `RESOLVED`로 전이(회귀 확인) | P0 | Feature executor |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-009 | job이 이미 `RESOLVED`(자동 결과 도착) | `ManualReviewDecisionService.decide` 호출 | job 미변경, case만 `RESOLVED`로 종료 | P0 | Feature executor |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-010 | job이 `MANUAL_REVIEW_REQUIRED` | `ManualReviewDecisionService.decide` 호출 | `applyManualDecision` 적용 + `MODERATION_VERDICT_READY` 발행 + case 종료 | P0 | Feature executor |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-011 | `ManualReviewCase`가 이미 `RESOLVED` | 재결정 시도 | 거절(case 상태 가드) | P0 | Feature executor |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-012 | `HIGH`/`STANDARD` case 혼합, 서로 다른 `createdAt` | 큐 정렬 비교 | `effectiveBand` 내림차순, band 내 `createdAt` 오름차순 | P1 | Feature executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-INT-001 | `ManualReviewDecisionService`, PostgreSQL | job `MANUAL_REVIEW_REQUIRED`, case `OPEN` | 검토자 결정(ALLOW) 제출 | job `RESOLVED`+`resolvedVerdict=ALLOW`, `MODERATION_VERDICT_READY` outbox 발행, case `RESOLVED`, `filter_job_status_history` 기록 전부 한 트랜잭션 반영 | 테스트 데이터 정리 |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-INT-002 | `ManualReviewDecisionService`, `AnswerModerationExecutionWorker`, PostgreSQL | 동시에 (a) 검토자 수동 결정, (b) 자동 pipeline 결과 적용 경쟁 | 두 경로 동시 실행 | 하나만 성공해 job을 `RESOLVED`로 확정, 다른 하나는 상태 충돌로 거절, job 이중 전이 없음 | 정리 |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-INT-003 | `AnswerModerationExecutionWorker`, PostgreSQL | job이 이미 수동으로 `RESOLVED`(`manuallyResolved=true`)된 상태에서 지연된 자동 pipeline 결과 도착 | worker가 이벤트 처리 | `filter_job_status_history`에 감사 기록 1건 추가, job의 `resolvedVerdict`는 수동 결정값 유지 | 정리 |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-INT-004 | `ManualReviewCaseRepository`, PostgreSQL | `HIGH`/`STANDARD` case 여러 건, 서로 다른 `created_at` | 큐 조회 쿼리 실행 | `effectiveBand` 내림차순 + band 내 `created_at` 오름차순으로 반환 | 정리 |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-INT-005 | `ManualReviewPriorityEvaluation`, PostgreSQL | case open 시 priority 평가 | 저장 확인 | `manual_review_priority_evaluation`에 band·reasonCode·policyVersion·evaluatedAt append-only 기록 | 정리 |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-INT-006 | 검토자 결정 REST endpoint, PostgreSQL | `OPERATOR` 세션 인증된 요청 | 결정 제출 | 200과 함께 job·case 갱신 반영 | 정리 |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-INT-007 | 검토자 결정 REST endpoint | 미인증 요청 | 결정 제출 시도 | 401 거절, 상태 미변경 | 정리 |
| TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-INT-008 | `ManualReviewCaseRepository`, PostgreSQL | 동일 대상·release로 case 생성 2회 동시 시도 | 동시 실행 | 유일성 제약(`INV-MAN-001`)으로 하나만 성공, 다른 하나는 기존 case를 멱등 반환(회귀 확인) | 정리 |

## 7. Cross-cutting scenarios

### Database and transactions

- `ManualReviewDecisionService`의 job 전이 + outbox 발행 + status
  history 기록 + case 종료는 단일 트랜잭션으로 묶는다(INT-001).
- V16 마이그레이션은 기존 `manual_review_case` 행이 없다는 전제(`#103`
  이후 이 테이블에 실제 writer가 없었음)로 신규 컬럼에 NOT NULL을 바로
  건다.

### Concurrency and idempotency

- INT-002로 수동 결정과 자동 결과 동시 도착 경쟁을 검증한다.
- INT-008로 기존 `INV-MAN-001` 유일성 계약이 확장 후에도 유지되는지
  회귀 확인한다.

### External APIs

- 이 이슈는 외부 API 연동이 없다(이슈 본문 "외부 연동: 없음").

### Failure recovery and reconciliation

- INT-003으로 늦은 자동 결과가 감사 기록만 남기고 상태를 보존하는지
  확인한다(`INV-MAN-004`).
- UNIT-006으로 priority 계산 장애 시 fallback 경로를 확인한다(`INV-MAN-009`).

## 8. Test data and isolation

- Fixtures: `FilterJob`(`AUTOMATED`/`MANUAL_REVIEW_REQUIRED`/`RESOLVED`
  혼합), `ManualReviewCase`(`OPEN`/`RESOLVED`, `HIGH`/`STANDARD` band
  혼합) 빌더.
- Database isolation: 기존 `#108`/`#109` 통합 테스트와 동일하게
  Testcontainers PostgreSQL, 클래스별 격리된 Spring context.
- Clock/randomness: 고정 `Clock` 재사용 — 실제 wall-clock 의존 없음.
- External API doubles: 해당 없음(외부 연동 없음).
- Cleanup: 각 통합 테스트가 자신이 만든 `manual_review_case`/
  `manual_review_priority_evaluation`/`filter_job` 행을 트랜잭션 롤백
  또는 명시적 삭제로 정리한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Feature executor | `src/main/resources/db/migration/V16__*.sql`, `ManualReviewCase`(확장), `ManualReviewPriorityPolicy`, `ManualReviewPriorityEvaluation`, `FilterJob.applyManualDecision` 가드, `ManualReviewDecisionService`, `AnswerModerationExecutionWorker` 감사 기록 확장, repository 계층, REST endpoint, 단위·통합 테스트 | UNIT-001~012, INT-001~008 | `INV-MAN-001`, `003`, `004`, `009` 검증, `#103`/`#107`/`#108` 기존 계약과의 호환성 리뷰 |

## 10. Completion criteria

- [x] 모든 P0 시나리오 구현
- [x] 모든 테스트 메서드에 `@DisplayName`
- [x] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [x] 단위 테스트 통과
- [x] 통합 테스트 통과
- [x] 잠재 문제 분석
- [x] 테스트 보고서 생성 —
      `docs/reports/tests/gh-110-TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY.md`

## 11. Human approval

- Reviewer: tkv00
- Decision: Approved
- Approved at: `2026-08-16T19:36:00+09:00`
