# GitHub Issue #118 Task Contract

> Generated at: `2026-08-12T14:08:46+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `질문글 멱등 제출과 MatchRequested Outbox 기록`
- GitHub Issue: `#118`
- Branch: `feat/gh-118-direction-post-submit`
- Base branch: `main`

## Objective

- 질문글 제출을 수신자 확정과 분리하고, 같은 의미의 재시도는 기존 결과로
  복원하며 다른 요청의 멱등 키 재사용은 충돌로 거절한다.
- `direction_post`, `post_audience`, `RECIPIENT_MATCH_REQUESTED` Outbox를
  하나의 트랜잭션으로 기록한다.

## Scope

- `DirectionPostService.send()`의 제출 트랜잭션을 post·audience·matching
  Outbox 기록으로 제한한다.
- 기존 #115의 `request_fingerprint` 정규화·비교와 legacy lazy backfill을
  유지한다.
- 최초 matching event는 승인된 #115 계약에 따라 `match_round = 1`을 사용한다.
- 동일 key·동일 fingerprint 재시도는 기존 제출 결과를 반환한다.
- 동일 key·상이 fingerprint 요청은 `IDEMPOTENCY_KEY_REUSED`로 거절한다.
- 제출 경로에서 후보 조회, 수신 슬롯 예약, `post_recipient` 생성을 수행하지
  않는 것을 단위·PostgreSQL 통합 테스트로 증명한다.
- 제출 실패 시 post·audience·Outbox 부분 커밋이 남지 않는지 검증한다.

## Explicit exclusions

- 매칭 워커와 PostGIS 후보 재계산
- `post_recipient` 확정과 수신 슬롯 예약
- 인앱/외부 푸시 알림 및 REST Controller
- 새로운 migration 또는 #115의 `match_round`/lease 계약 변경
- P02/P03 미확정 제품 숫자 결정
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| DirectionPostService 제출 orchestration | Backend | 멱등성·트랜잭션 리뷰 |
| 기존 발송 회귀 테스트 정리 | Backend | #120 경계와의 계약 리뷰 |
| 테스트 계획·보고서 | Backend | 검증 증거 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과는 clean이었다.
- `./harness start` 후 현재 브랜치는 `feat/gh-118-direction-post-submit`이다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 질문글 제출 성공 시 post·audience·matching Outbox가 각각 한 행 기록된다.
- 제출 성공 시 `post_recipient`와 `recipient_receive_state`가 변경되지 않는다.
- 같은 key·같은 fingerprint 재시도는 row 수와 결과 ID를 증가시키지 않는다.
- 같은 key·다른 fingerprint는 `IDEMPOTENCY_KEY_REUSED`이고 기존 행을 보존한다.
- 제출 트랜잭션 rollback 후 부분 결과가 남지 않는다.
- 승인된 P0 단위·통합 테스트와 저장소 필수 검증이 통과한다.
- 실행하지 못한 검증과 남은 위험을 테스트 보고서에 기록한다.
