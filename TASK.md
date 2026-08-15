# GitHub Issue #108 Task Contract

> Generated at: `2026-08-14T22:59:04+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `답변 moderation job durable retry`
- GitHub Issue: `#108`
- Branch: `feat/gh-108-answer-retry-exhaustion`
- Base branch: `main`

## Objective

- `AnswerModerationExecutionWorker`가 `PIPELINE_UNAVAILABLE`을 만났을 때
  무조건 이벤트를 영구 종결시키는 현재 동작(#107이 남긴 자리 —
  "retry/backoff는 이 클래스의 책임이 아니다(#108)")을 durable retry로
  대체한다. `FilterJob.exhaustRetries()`/`openManualReview()`,
  `ManualReviewCase`는 #107에서 이미 정의됐지만 어디서도 호출되지 않는
  상태다 — 이 이슈가 실제로 그 훅을 소비하는 첫 호출자가 된다.
- 재시도 수치(max attempts, lifetime, backoff cap, jitter, cadence, gate
  임계값·ramp 폭)는 이슈 본문에서 전부 "미결정"으로 명시돼 있으므로,
  이번 브랜치는 메커니즘과 테스트만 완결하고 값은 생성자/설정 주입으로만
  존재시킨다. 프로덕션 Spring 배선(`@ConfigurationProperties`, scheduler)은
  `#105`/`#106`/`#107`과 동일하게 `#113` production gate로 이연한다.

## Scope

1. `FilterJob`에 `logicalAttemptCount` 필드와 자동 시도 기록 전이 메서드를
   추가한다 — "SDK 호출을 포함한 단일 logical attempt budget"을 outbox
   `attemptCount`(claim마다 무조건 증가하는 인프라 카운터)와 분리해 실제
   pipeline 호출 횟수만 정확히 센다.
2. `max attempts`와 `max retry lifetime`을 함께 강제한다. 둘 다
   `logicalAttemptCount()`/`createdAt()` 기준으로 계산해 deadline 경과가
   기준을 초기화하지 않도록 한다(`INV-RTY-006`).
3. deadline 전 fast cadence / 이후 slow safety-completion cadence를
   `at`과 `job.deadlineAt()` 비교로 두 개의 주입된 `OutboxBackoffStrategy`
   중 선택하는 방식으로 구현한다.
4. `ExponentialJitterBackoffStrategy`(신규, `notification.domain`)로
   capped exponential backoff + jitter를 구현한다 — #119가 인터페이스만
   남기고 구현체를 두지 않은 자리를 채운다.
5. `OpenAiModerationProviderClient`가 429 응답의 `Retry-After` 헤더를
   감지해 `ModerationRateLimitedException`(신규)으로 던지도록 확장하고,
   실행 worker가 이를 다음 재시도 지연의 최소 하한으로 사용한다.
6. 위 cadence/backoff/Retry-After 조합 판단을 `AnswerModerationRetryPolicy`
   (신규, 순수 도메인)로 분리한다.
7. 재시도 소진 시 `exhaustRetries().openManualReview()` 저장은 기존 실패
   트랜잭션에 편입한다(별도 worker 신설 없음). `ManualReviewCase` idempotent
   생성은 그 앞에서 별도 트랜잭션으로 수행해, 유일성 제약 위반이 `FilterJob`/
   outbox 전이를 rollback하지 않게 한다.
8. `FilterReleaseRetryGate`(신규 도메인 + 테이블)로 release(snapshot)
   단위 상태를 갖는 재시도 게이트를 구현한다 — 연속 실패로 저하, 연속
   성공으로 단계적 한도 증가. claim SQL 자체는 건드리지 않고 배치 처리
   루프 안에서 release별 in-batch admitted count로 게이트를 적용한다.

## Explicit exclusions

- 위 8개 항목의 실제 운영 수치(max attempts, lifetime, backoff base/cap,
  jitter 비율, gate 임계값·ramp step) 확정 — 전부 미결정이며 주입 값으로만
  존재한다.
- 이 워커들을 Spring bean으로 등록하고 `@ConfigurationProperties`/
  scheduler를 배선하는 것 — `#113` production gate로 이연.
- `ManualReviewCase`에 우선순위/band/FIFO를 매기는 것(`#110` 범위).
- Slack 보조 알림(`#111` 범위).
- 답변 도메인이 이 진입점을 실제로 호출하고 콜백을 받아 상태에 반영하는
  연결 작업.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `FilterJob.logicalAttemptCount`, `AnswerModerationRetryPolicy`, `FilterReleaseRetryGate`, `AnswerModerationExecutionWorker` 재시도/소진/게이트 분기, Retry-After 캡처 | Feature executor | `INV-RTY-001`~`007`, `INV-MAN-002` 검증, `#107`/`#119` 기존 계약과의 호환성 리뷰 |

## Existing user-owned changes

- `main`(#141 병합 직후, `83dbffb`)에서 새로 분기했다
  (`./harness start --issue 108 --type feat --slug
  answer-retry-exhaustion`). 분기 시점 `git status --short`는 비어 있었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 자동 처리가 무제한 또는 곱셈 형태로 재시도되지 않는다
      (`INV-RTY-001`~`007`) — `FilterJob.logicalAttemptCount`(실제 pipeline
      호출만 카운트)와 `AnswerModerationRetryPolicy`의 max attempts/lifetime
      동시 강제(UNIT-002~003), `ExponentialJitterBackoffStrategy`의 capped
      exponential+jitter(UNIT-007~008), `FilterReleaseRetryGate`의 snapshot
      단위 폭주 완화(UNIT-011~013, INT-003)로 검증했다.
- [x] job 접수 뒤 시스템 부하가 달라져도 최초 고정한 `deadline_at`을
      연장하지 않는다는 `#107`의 기존 보장(`INV-ANS-002`)은 이번 변경으로
      건드리지 않았다(`FilterJob`의 어떤 전이 메서드도 `deadlineAt`을
      바꾸지 않는 구조 유지, `FilterJobTest#logicalAttemptCountSurvivesDeadlineElapse`로
      재확인).
- [x] deadline 경과가 release, target reference 또는 retry budget을
      초기화하지 않는다 (`INV-RTY-006`) — budget 기준을 `deadlineAt`이
      아닌 `createdAt`/`logicalAttemptCount`로 고정해 만족하며,
      `FilterJobTest#logicalAttemptCountSurvivesDeadlineElapse`와
      INT-004(deadline-elapsed와 exhaustion handoff 공존)로 검증했다.
- [x] manual review case handoff와 관련 실패가 공개 상태를 rollback하지
      않는다 (`INV-MAN-002`) — 소진 처리 경로가 `MODERATION_VERDICT_READY`를
      발행하지 않음을
      `AnswerModerationExecutionWorkerTest#exhaustsRetriesAndOpensManualReviewWithoutPublishingVerdict`로
      검증했다. `ManualReviewCase` 생성을 별도 트랜잭션으로 분리해
      PostgreSQL 트랜잭션 abort가 `FilterJob`/outbox 쓰기를 오염시키지
      않도록 했고, INT-002로 동시 소진 시 중복 생성되지 않음을 확인했다.
- [x] job 생성·deadline scheduler·worker 간 중복과 순서 역전을 방어하는
      단위·통합 테스트가 추가된다 — unit 32개 신규(총 385개), integration
      4개 신규(총 291개, 실제 PostgreSQL 동시성 포함). 상세는
      `docs/reports/tests/gh-108-TEST-PLAN-GH-108-ANSWER-MODERATION-RETRY.md`
      참고. `#110`(manual review 우선순위), `#111`(Slack), 이 워커들의
      Spring 배선(`#113`), 답변 도메인 실제 연결부는 이 이슈 범위 밖이며
      검증하지 않았다.
