# Test Plan: TEST-PLAN-GH-107-ANSWER-MODERATION-JOB

> Created at: `2026-08-14T00:00:00+09:00`
> GitHub Issue: `#107`
> Status: Draft (구현과 병행 작성 — 사용자가 `/harness-test-plan` 승인 단계를
> 건너뛰고 직접 구현 착수를 명시적으로 선택했다. 이 문서는 실제 구현된 테스트를
> 사후적으로 정리한 기록이며, 사전 승인 게이트를 거치지 않았다.)

## 1. Objective

답변 담당 코드가 호출할 moderation job 접수 진입점과, 판정 결과·deadline 경과
신호를 콜백/이벤트로 돌려주는 outbound 계약이 다음을 보장하는지 검증한다.

- job 유실 또는 중복이 콜백/이벤트를 잘못된 횟수로 발생시키지 않는다
  (`INV-GEN-003`~`005`). 깨지면 같은 답변에 대해 판정 신호가 두 번 오거나
  아예 오지 않아 답변 공개 상태가 불확정 상태로 남는다.
- job 접수 뒤 시스템 부하가 달라져도 최초 고정한 `deadline_at`을 연장하지
  않는다(`INV-ANS-002`). 깨지면 deadline 기반 공개 전환 시점이 흔들려 사용자
  대기 시간 SLA를 보장할 수 없다.
- deadline 경과 신호가 승인을 뜻하지 않는다(`INV-ANS-003`, `INV-ANS-004`).
  깨지면 미검사 콘텐츠가 승인된 것처럼 취급될 수 있다.

## 2. Scope

### Included

- `AnswerModerationJobIntakeService.submit(...)`: idempotencyKey 기반 dedup
  (`INV-GEN-003`), 승격된 release가 없을 때 fail-closed 거부(`FLT-DOM-006`),
  `deadlineAt` 원자적 고정, `MODERATION_EXECUTION_REQUESTED` outbox 발행.
- `AnswerModerationExecutionWorker.processBatch(...)`: `#105`
  `ModerationPipelineService`를 답변 전용 실행 자원(executor+timeout)으로
  호출, `ALLOW`/`BLOCK`/timeout/error 분기, `MODERATION_VERDICT_READY` 발행,
  claim 완료/실패(fencing)를 통한 중복·순서 역전 방어.
- `AnswerModerationDeadlineWorker.processBatch(...)`: deadline 경과 미해결
  job 탐지, `MODERATION_DEADLINE_ELAPSED` 멱등 발행(재스캔·경쟁 방어).
- `FilterJob.deadlineAt` 확장(V13 migration)과 outbox 계약 확장(`FILTER_JOB`
  aggregate, 3개 신규 event type).
- 답변 전용 outbox 이벤트가 `#105` pipeline·`#118/#119` Outbox lease/fencing
  인프라를 재사용함을 보이는 실제 PostgreSQL 동시성 테스트.

### Excluded

- `Account`/답변 제출 API에서 이 진입점을 실제로 호출하는 배선 — 답변 담당
  영역, 별도 이슈로 조율.
- retry/backoff 정책, `RETRY_EXHAUSTED` 전환, manual review case handoff —
  `#108`.
- 독립 보조 판정기, `deadline_at` 산정 실제 값(최소/최대/fallback) — 미결정.
- 신규 REST endpoint.

## 3. Design assumptions (설계 가정)

1. 콜백/이벤트 전달 메커니즘은 `notification.domain.OutboxEvent`/
   `OutboxEventRepository`를 그대로 재사용한다(사용자 선택, 저장소에 이미
   있는 `#118/#119/#138` 패턴과의 일관성). `OutboxAggregateType.FILTER_JOB`과
   `MODERATION_EXECUTION_REQUESTED`/`MODERATION_VERDICT_READY`/
   `MODERATION_DEADLINE_ELAPSED` 3개 event type을 추가한다.
2. job 생성(intake)과 pipeline 실행(worker)을 분리한다 — 답변 제출 요청
   스레드가 외부 provider 호출을 기다리지 않게 하기 위해서다. 둘 사이의
   연결은 `MODERATION_EXECUTION_REQUESTED` outbox 이벤트가 담당한다.
3. `#107`은 최초 1회 pipeline 시도만 다룬다(`#108`이 retry를 소유) — 시도가
   timeout/error로 끝나면 이벤트를 DEAD로 종결하고 재시도하지 않는다. job은
   `AUTOMATED`·미해결로 남아 `#108`의 미래 retry 메커니즘이 이어받을 수 있는
   상태를 유지한다.
4. `AnswerModerationJobIntakeService`/`AnswerModerationExecutionWorker`는
   `ModerationPipelineService`와 같은 이유로 Spring 빈이 아니다(하위 구현체
   미결정). `AnswerModerationDeadlineWorker`는 그런 의존성이 없어 빈으로
   등록했다(`DirectionMatchingWorker`와 동일하게 trigger는 없다).

## 4. Test matrix

| ID | Level | Scenario | Invariant |
| --- | --- | --- | --- |
| UNIT-001 | Unit | 중복 idempotencyKey는 새 job 없이 기존 job 반환 | INV-GEN-003 |
| UNIT-002 | Unit | 승격된 release 없으면 fail-closed 거부 | INV-GEN-002 |
| UNIT-003 | Unit | deadlineAt = now + window로 고정 | INV-ANS-002 |
| UNIT-004 | Unit | 상태 이력 + EXECUTION_REQUESTED 이벤트 발행 | INV-GEN-003 |
| UNIT-005~006 | Unit | ALLOW/BLOCK verdict로 job RESOLVED 전이 + VERDICT_READY 발행 | INV-GEN-001 |
| UNIT-007~008 | Unit | timeout/provider error 시 job 불변, 이벤트만 DEAD | INV-GEN-002 |
| UNIT-009 | Unit | 이미 비-AUTOMATED job은 pipeline 미호출·claim만 완료 | INV-GEN-004 |
| UNIT-010 | Unit | claim 경쟁 패자는 STALE_LEASE | INV-GEN-004 |
| UNIT-011~014 | Unit | deadline worker 발행/멱등/경쟁 흡수/limit 검증 | INV-ANS-003, INV-GEN-005 |
| INT-001 | Integration | 동시 중복 접수 → filter_job/이벤트 정확히 1개 | INV-GEN-003 |
| INT-002 | Integration | 동시 execution worker → job 정확히 1회 RESOLVED | INV-GEN-004 |
| INT-003 | Integration | 동시 deadline scan → 신호 정확히 1개 | INV-GEN-005, INV-ANS-003 |
| INT-004 | Integration | deadline 경과 후 도착한 판정도 VERDICT_READY 발행 | INV-ANS-004 |

## 5. Residual risk

- `#108`(retry) 경계와의 실제 통합은 검증하지 않았다 — `#107`은 첫 시도
  실패 시 이벤트를 DEAD로 종결하는 것까지만 확인했다.
- `deadlineWindow` 실제 운영값은 검증 대상이 아니다(설정 자리만 존재).
- 보조 판정기·공통 장애 영역은 이 이슈 범위가 아니라 검증하지 않았다.
