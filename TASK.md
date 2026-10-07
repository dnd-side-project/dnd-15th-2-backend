# GitHub Issue #137 Task Contract

> Generated at: `2026-10-07T21:56:09+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `필터링 시스템 — 방향 질문글 비동기 moderation 연결 (F12)`
- GitHub Issue: `#137`
- Branch: `feat/gh-137-direction-post-moderation`
- Base branch: `main`

## Objective

질문글은 `moderation_status = PENDING`으로 만들어지지만 PASSED로 바꾸는 코드가 없다. `DirectionMatchingWorker`는
PENDING을 재시도 대상으로 보고 만료될 때까지 재시도만 하므로 질문글이 아무에게도 전달되지 않는다. 판정 대상 종류
(`FilterTargetType`)도 `ANSWER`와 `NICKNAME`뿐이다. 질문글을 공통 moderation 비동기 파이프라인에 연결해 PASSED
질문글만 매칭되게 하고, 늦거나 중복된 판정 결과가 상태를 되돌리지 않게 한다.

## Scope

- `FilterTargetType.DIRECTION_POST` 추가. Flyway V32로 `filter_job`·`manual_review_case`의 `target_type` CHECK 제약에
  `DIRECTION_POST`를 추가한다.
- `DirectionPostService.send`:
  - 본문이 있는 질문글(`글`, `미디어+글`)은 질문글 저장과 같은 트랜잭션에서 `AnswerModerationIntake`로 job을 접수한다.
  - 미디어 단독 질문글은 job 없이 `moderation_status = PASSED`로 만든다. 첨부 asset의 READY 여부는 기존 첨부 정책이
    확인한다.
- 질문글 판정 반영:
  - `MODERATION_VERDICT_READY` ALLOW → PASSED, BLOCK → REJECTED
  - `MODERATION_DEADLINE_ELAPSED` → REVIEW_HELD
  - PENDING 또는 REVIEW_HELD일 때만 바꾸는 조건부 갱신으로 처리한다. PASSED·REJECTED 질문글과 만료된 질문글
    (`status`가 MATCHING이 아니거나 `expires_at`이 지난 질문글)은 늦거나 중복된 이벤트로 바뀌지 않는다.
    DEADLINE_ELAPSED는 PENDING에서만 REVIEW_HELD로 바꾼다.
- `DirectionPostApiSpec` 제출 응답에 503(`FLT-DOM-006`)을 문서화하고 `docs/api/openapi.json`을 재생성한다(D3).
- 판정 이벤트 분배: 지금은 `AnswerModerationVerdictWorker`가 두 이벤트를 모두 claim하고 `ANSWER`가 아니면 상태를 바꾸지
  않고 완료 처리한다. filtering 모듈에 대상 종류별 판정 적용기 인터페이스를 두고, 답변 모듈과 질문글 모듈이 각각
  구현해 `targetType`에 따라 분배한다. 답변 판정 반영 동작은 바꾸지 않는다.

## Explicit exclusions

- 질문글 이의제기(`appeal_case` 제약, `AppealCaseService`)
- 이미지·영상 자체 안전 검사, EXIF, OCR
- #120 매칭 워커의 후보 재계산·슬롯 예약·수신자 확정 로직. `DirectionMatchingWorker`의 moderation gate는 그대로 둔다
- 이미 PENDING으로 저장된 질문글 backfill. 만료 처리로 정리된다
- BLOCK 시 질문글 `status` 변경. `moderation_status`만 REJECTED로 바꾼다(D1)
- provider별 threshold 정책 조정, 외부 push 발송
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `FilterTargetType`·V32 마이그레이션·판정 분배·질문글 판정 적용·`send` job 접수 | 실행 에이전트 | 사용자 PR 리뷰 |
| 테스트 수정·추가 | 실행 에이전트 | `/harness-test-plan` 승인 후 작성 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`1a3b125`)에서 메인 작업 디렉터리로 분기했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 질문글 저장 API는 moderation provider 응답을 기다리지 않고 완료된다.
- `PENDING`, `REVIEW_HELD`, `REJECTED` 질문글은 매칭 워커가 수신자·슬롯을 만들지 못한다.
- ALLOW 판정을 받은 질문글은 PASSED가 되고 다음 매칭 시도에서 수신자가 확정된다.
- 중복 판정 이벤트, 판정 후 도착한 DEADLINE_ELAPSED, 확정 후 도착한 반대 판정이 상태를 되돌리지 않는다.
- 늦은 ALLOW가 만료된 질문글을 다시 매칭 가능하게 만들지 않는다.
- `글`, `미디어+글`, `미디어` 질문글이 매칭 대상에 포함되고, `미디어` 단독 질문글은 job 없이 처리되는 정책이 테스트로
  고정된다.
- 답변 판정 반영 기존 테스트가 그대로 통과한다.
- `./harness check`와 `./harness pr-ready --project-tests`가 통과한다.

## Decisions

- 2026-10-07 사용자 결정: 새 이슈를 만들지 않고 #137에서 바로 작업한다. Project Priority를 P1에서 P0으로 바꿨다.
- 2026-10-07 사용자 결정(D1): BLOCK 판정은 `moderation_status`만 REJECTED로 바꾼다. 질문글 `status`는 MATCHING으로
  남고, 매칭 워커가 REJECTED를 보고 매칭 없이 이벤트를 완료하며, 만료 처리가 EXPIRED로 닫는다.
- 2026-10-07 사용자 결정(D2): 판정 이벤트는 filtering 모듈의 대상 종류별 적용기 인터페이스로 분배한다.
- 2026-10-07 사용자 결정: 테스트 계획 `TEST-PLAN-GH-137-DIRECTION-POST-MODERATION` 승인. 기존 Direction 통합 테스트 8개에는
  release fixture 호출만 추가한다. 매칭 이벤트 재시도 운영값(R8)은 범위 밖이며 보고서에 남긴다.
- 2026-10-07 사용자 결정(D3, 구현 중 추가): 승격된 release가 없으면 질문글 제출이 503(`FLT-DOM-006`)이 된다.
  `DirectionPostApiSpec` 제출 응답에 503을 추가하고 `docs/api/openapi.json`을 재생성한다.
