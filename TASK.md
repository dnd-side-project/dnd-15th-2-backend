# GitHub Issue #107 Task Contract

> Generated at: `2026-08-14T09:37:04+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `답변 필터링 연동과 deadline 공개 전환`
- GitHub Issue: `#107`
- Branch: `feat/gh-107-answer-filter-deadline`
- Base branch: `main`

## Objective

- 답변 생성 자체는 이 이슈의 범위가 아니다 — 답변 담당 코드가 호출할
  moderation job 접수 진입점과, 활성 release·동적 `deadline_at`을 산정해
  판정 결과 또는 deadline 경과 신호를 콜백/이벤트로 돌려주는 outbound 계약을
  만든다.
- 실제 `AnswerStatus` 필드 갱신, `PUBLISHED_UNREVIEWED` 같은 신규 상태
  추가는 답변 담당 영역이며 이 이슈에 포함하지 않는다.

## Scope

1. 답변 담당 코드가 호출하는 moderation job 접수 진입점(target reference,
   콘텐츠, 언어를 입력받음)을 만든다.
2. 활성 `moderation_release_id`와 동적 `deadline_at`을 job 생성 시 원자적으로
   고정한다.
3. durable `moderation_job` 생성과 `#105`의 `ModerationPipelineService` 실행을
   연결한다.
4. deadline 전 유효 판정(`ALLOW`/`BLOCK`)을 콜백/이벤트로 답변 담당 코드에
   전달한다.
5. 판정이 없을 때 deadline 경과 신호를 콜백/이벤트로 전달한다
   (`PUBLISHED_UNREVIEWED` 적용 여부는 답변 담당이 결정).
6. deadline 후 도착한 유효 결과도 같은 콜백/이벤트 계약으로 전달한다.
7. job 생성·deadline scheduler·worker 간 중복과 순서 역전을 방어한다.

## Explicit exclusions

- `AnswerStatus` 필드·상태 머신 수정, 답변 생성 API 자체 — 답변 담당 영역이며
  별도로 조율한다.
- 답변 담당 코드에서 이 진입점을 실제로 호출하고 콜백을 받아 `AnswerStatus`에
  반영하는 연결 작업, `PUBLISHED_UNREVIEWED` 대응 신규 상태 추가 — 이 이슈는
  계약과 내부 로직만 만든다.
- deadline 최소·최대, 산정 입력, fallback과 정책 버전 형식 — 미결정.
- 신규 REST endpoint 추가 — 내부 진입점 + 콜백/이벤트 계약이며 HTTP endpoint가
  아니다.
- `moderation_job` 외 신규 스키마 변경.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| moderation job 접수 진입점, deadline/`moderation_release_id` 원자적 고정, 콜백/이벤트 계약, 중복·순서 역전 방어 | Feature executor | `INV-GEN-003`~`005`, `INV-ANS-002`~`004` 검증, `#105` pipeline 재사용 여부 리뷰 |

## Existing user-owned changes

- `main`(#138 병합 직후, commit `886c423`)에서 새로 분기했다
  (`./harness start --issue 107 --type feat --slug answer-filter-deadline`).
  분기 시점 `git status --short`는 `task-init`이 갱신한 `TASK.md` 외에는
  비어 있었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] job 유실 또는 중복이 콜백/이벤트를 잘못된 횟수로 발생시키지 않는다
      (`INV-GEN-003`~`005`) — idempotency_key dedup(UNIT-001, INT-001,
      `uq_filter_job_idempotency_key` 최종 방어), 실행 이벤트 lease
      fencing(INT-002), deadline 신호 멱등 발행(UNIT-011~013, INT-003)으로
      검증했다.
- [x] job 접수 뒤 시스템 부하가 달라져도 최초 고정한 `deadline_at`을 연장하지
      않는다 (`INV-ANS-002`) — `FilterJob`의 어떤 전이 메서드도 `deadlineAt`을
      바꾸지 않는 구조로 만족(UNIT-003).
- [x] deadline 경과 신호가 승인을 뜻하지 않는다는 것이 계약 문서에 명시돼
      있다 (`INV-ANS-003`, `INV-ANS-004`) —
      `AnswerModerationEventPayloads.DeadlineElapsed`와
      `AnswerModerationDeadlineWorker` class doc에 명시. 늦게 도착한 판정도
      같은 `MODERATION_VERDICT_READY` 계약으로 전달됨을 INT-004로 검증.
- [x] job 생성·deadline scheduler·worker 간 중복과 순서 역전을 방어하는
      단위·통합 테스트가 추가된다 — unit 14개, integration 4개(실제
      PostgreSQL 동시성 포함).
      `docs/reports/tests/gh-107-TEST-PLAN-GH-107-ANSWER-MODERATION-JOB.md`
      참고. `#108`(retry) 경계와의 실제 통합, 보조 판정기, 실제 답변 도메인
      호출부 연결은 이 이슈 범위 밖이며 검증하지 않았다.
