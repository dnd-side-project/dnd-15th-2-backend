# Test Plan: TEST-PLAN-GH-108-ANSWER-MODERATION-RETRY

> Created at: `2026-08-14T23:00:00+09:00`
> GitHub Issue: `#108`
> Status: Draft (구현과 병행 작성 — `#107`과 동일하게 사용자가
> `/harness-test-plan` 승인 단계를 건너뛰고 직접 구현 착수를 명시적으로
> 선택했다. 이 문서는 실제 구현된 테스트를 사후적으로 정리한 기록이며,
> 사전 승인 게이트를 거치지 않았다.)

## 1. Objective

`#107`이 첫 시도 실패 시 이벤트를 DEAD로 종결하고 남겨둔 자리 — job은
`AUTOMATED`·미해결 상태로 남고 재시도가 없었다 — 를 실제 durable retry로
대체한다. 다음을 보장하는지 검증한다.

- 자동 처리가 무제한 또는 곱셈 형태로 재시도되지 않는다(`INV-RTY-001`~
  `007`). 깨지면 장애가 지속될 때 같은 job에 대한 재시도가 시스템 자원을
  무한정 소모하거나, provider 쪽 rate limit을 더 악화시킬 수 있다.
- deadline 경과가 release, target reference 또는 retry budget을 초기화하지
  않는다(`INV-RTY-006`). 깨지면 deadline이 지날 때마다 재시도 예산이 갱신돼
  사실상 무제한 재시도가 된다.
- manual review case handoff와 그 경로의 실패가 공개 상태를 rollback하지
  않는다(`INV-MAN-002`). 깨지면 소진 처리 중 오류가 이미 공개된 답변을
  비공개로 되돌리거나, 반대로 미검사 콘텐츠를 승인된 것처럼 보이게 할 수
  있다.

## 2. Scope

### Included

- `FilterJob.recordAutomatedAttempt(...)`: `logicalAttemptCount`가 `AUTOMATED`
  상태에서 실제 pipeline을 호출했을 때만 증가하고, gate로 미뤄진 시도는
  증가시키지 않음(`INV-RTY-001`).
- `AnswerModerationRetryPolicy.decide(...)`: deadline 전/후 cadence 선택,
  `Retry-After` 힌트를 하한으로 사용하는 backoff, `logicalAttemptCount`와
  `createdAt` 기준 max attempts/lifetime 동시 초과 판정(`INV-RTY-002`~
  `006`).
- `ExponentialJitterBackoffStrategy`: capped exponential backoff에 jitter를
  적용해 동일 attempt에서도 매번 다른 지연을 반환하되 상한을 넘지 않음
  (`INV-RTY-003`, `INV-RTY-005`).
- `OpenAiModerationProviderClient`의 429 `Retry-After` 헤더 캡처
  (`INV-RTY-004`).
- `FilterReleaseRetryGate`: release 단위 연속 실패/성공에 따른 HEALTHY↔
  DEGRADED 전이와 단계적 한도 증가(`INV-RTY-007`).
- `AnswerModerationExecutionWorker.processBatch(...)`: 실패를 재시도 예약/
  소진(→ `exhaustRetries().openManualReview()` + `ManualReviewCase` 생성)/
  게이트 지연 3갈래로 분기, 소진 경로가 `MODERATION_VERDICT_READY`를
  발행하지 않음(`INV-MAN-002`).
- 위 전체가 재시도 claim 경쟁, 소진 처리 경쟁, 게이트 행 갱신 경쟁,
  deadline-elapsed 신호와의 동시 발생 아래에서도 중복·순서 역전을 일으키지
  않음을 보이는 실제 PostgreSQL 동시성 테스트.

### Excluded

- 위 전체 메커니즘의 실제 운영 수치(max attempts, lifetime, backoff base/
  cap, jitter 비율, gate 임계값·ramp step) — 전부 미결정이며 생성자/설정
  주입 값으로만 존재한다.
- 이 워커들의 Spring bean 등록, `@ConfigurationProperties`, scheduler
  배선 — `#105`/`#106`/`#107`과 동일하게 `#113` production gate로 이연.
- `ManualReviewCase`에 우선순위/band/FIFO를 매기는 것 — `#110`.
- Slack 보조 알림 — `#111`.
- 답변 도메인이 이 진입점을 실제로 호출하고 콜백을 받아 반영하는 연결
  작업.

## 3. Design assumptions (설계 가정)

1. "logical attempt budget"은 outbox `attemptCount`(claim마다 무조건
   증가하는 인프라 카운터)가 아니라 `FilterJob` 자신의
   `logicalAttemptCount` 필드로 추적한다. gate가 pipeline 호출 없이 미룬
   재클레임은 outbox `attemptCount`는 늘리지만 `logicalAttemptCount`는
   늘리지 않는다 — 게이트로 인한 지연이 실제 재시도 예산을 소모하지 않게
   하기 위해서다.
2. max retry lifetime의 origin은 `deadlineAt`이 아니라 `createdAt`이다.
   `deadlineAt`은 job 생성 시 한 번 고정되고 이후 바뀌지 않지만
   (`INV-ANS-002`, `#107`), lifetime 판단까지 `deadlineAt`에 의존하면
   deadline 관련 로직 변경이 retry budget에 우회적으로 영향을 줄 수 있어
   두 개념을 명시적으로 분리했다.
3. `#107`이 만든 `OutboxRetryPolicy`(`#119`)의 `decide(...)`는 그대로
   재사용하지 않는다 — 그 record는 `event.attemptCount()` 기준으로 dead를
   판정하는데, 이는 위 1번 가정과 충돌한다. 대신 `OutboxBackoffStrategy`/
   `OutboxFailureKind`/`OutboxRetryDecision` 타입만 재사용하고, 조합 판단은
   `AnswerModerationRetryPolicy`라는 새 순수 도메인 클래스가 소유한다.
4. 재시도 소진 → manual review handoff는 별도 worker를 새로 만들지 않고
   `AnswerModerationExecutionWorker`의 기존 실패 트랜잭션에 편입한다 —
   같은 트랜잭션 안에서 FilterJob 저장과 claim 완료가 이미 원자적으로
   묶여 있어, 크래시 시 트랜잭션 롤백 → outbox lease 만료로 자연스럽게
   재시도되는 기존 durable 패턴을 그대로 물려받는다.
5. snapshot 단위 retry gate는 `outbox_event`에 release 컬럼을 추가해 claim
   SQL 자체를 release로 거르지 않는다 — 이미 배치 안에서 이벤트마다
   `FilterJob`을 조회하므로 `filterReleaseId`는 추가 조회 없이 얻을 수
   있다. 대신 배치 처리 루프 안에서 release별 in-batch admitted count로
   게이트를 적용하고, 게이트 상태 자체는 `filter_release_retry_gate`
   테이블에 release당 1행으로 영속화해 `SELECT ... FOR UPDATE`로 동시
   갱신을 직렬화한다.

## 4. Test matrix

| ID | Level | Scenario | Invariant |
| --- | --- | --- | --- |
| UNIT-001 | Unit | 실제 pipeline 호출 시에만 logicalAttemptCount 증가, 게이트로 미룬 시도는 미증가 | INV-RTY-001 |
| UNIT-002 | Unit | logicalAttemptCount가 maxAttempts 도달 시 exhaust 판정 | INV-RTY-002 |
| UNIT-003 | Unit | createdAt 기준 maxRetryLifetime 초과 시 attemptCount와 무관하게 exhaust 판정 | INV-RTY-002, INV-RTY-006 |
| UNIT-004 | Unit | at이 deadlineAt 이전/이후일 때 fast/slow backoff 전략이 각각 선택됨 | INV-RTY-003 |
| UNIT-005 | Unit | Retry-After 힌트가 계산된 backoff보다 크면 하한으로 사용 | INV-RTY-004 |
| UNIT-006 | Unit | Retry-After가 없으면 순수 capped exponential+jitter만 적용 | INV-RTY-003, INV-RTY-004 |
| UNIT-007 | Unit | ExponentialJitterBackoffStrategy가 상한을 넘지 않음 | INV-RTY-003 |
| UNIT-008 | Unit | 동일 attempt에 대해 반복 호출 시 지연 값이 매번 달라짐(jitter) | INV-RTY-005 |
| UNIT-009 | Unit | 429 응답 + 유효한 Retry-After 헤더 → ModerationRateLimitedException에 파싱된 Duration 보유 | INV-RTY-004 |
| UNIT-010 | Unit | Retry-After 헤더 부재/malformed → 힌트 없이 일반 실패로 처리 | INV-RTY-004 |
| UNIT-011 | Unit | 연속 실패가 임계값에 도달하면 게이트가 HEALTHY→DEGRADED로 전이 | INV-RTY-007 |
| UNIT-012 | Unit | DEGRADED 상태에서 연속 성공이 쌓이면 currentLimit이 단계적으로 증가 | INV-RTY-007 |
| UNIT-013 | Unit | DEGRADED 복구 도중 실패가 재발하면 다시 저하 | INV-RTY-007 |
| UNIT-014 | Unit | 실패 분기: maxAttempts/lifetime 미도달 → RETRY_SCHEDULED, job은 AUTOMATED 유지, logicalAttemptCount만 증가 | INV-RTY-001, INV-RTY-002 |
| UNIT-015 | Unit | 실패 분기: 소진 도달 → RETRY_EXHAUSTED, job MANUAL_REVIEW_REQUIRED 전이 + ManualReviewCase 생성, MODERATION_VERDICT_READY 미발행 | INV-MAN-002 |
| UNIT-016 | Unit | 게이트 DEGRADED로 admitted 한도 초과 → RETRY_DEFERRED_BY_GATE, pipeline 미호출, logicalAttemptCount 미증가 | INV-RTY-001, INV-RTY-007 |
| UNIT-017 | Unit | 소진 처리 대상 job이 이미 manuallyResolved면 race로 흡수 | INV-MAN-004 |
| INT-001 | Integration | 동시 재시도 claim이 logicalAttemptCount를 이중 증가시키지 않음 | INV-RTY-001 |
| INT-002 | Integration | 동시 소진 처리가 ManualReviewCase를 중복 생성하지 않음(고유 제약 경쟁 흡수) | INV-MAN-001, INV-MAN-002 |
| INT-003 | Integration | 동일 release의 동시 실패가 게이트 행 갱신을 유실하지 않음(FOR UPDATE 직렬화) | INV-RTY-007 |
| INT-004 | Integration | deadline-elapsed 신호와 retry-exhausted handoff가 동시에 발생해도 서로 충돌하지 않음 | INV-RTY-006, INV-MAN-002 |

## 5. Residual risk

- `#110`(manual review case 우선순위/band), `#111`(Slack 알림) 통합은
  검증하지 않았다 — `ManualReviewCase` row 생성까지만 확인한다.
- 게이트/backoff/lifetime의 실제 운영 수치는 검증 대상이 아니다(설정
  자리만 존재).
- 답변 도메인이 이 진입점을 실제로 호출하는 배선은 이 이슈 범위가 아니라
  검증하지 않았다.
- release별 in-batch 게이트 적용은 단일 worker 인스턴스의 한 배치 안에서만
  카운트한다 — 여러 worker 인스턴스가 동시에 서로 다른 배치를 처리할 때
  순간적으로 currentLimit을 소폭 초과할 수 있는 여지는 완전히 막지 않았고,
  이는 설계상 허용 범위로 간주했다(정확한 상한 강제가 아니라 폭주 완화가
  목적).
