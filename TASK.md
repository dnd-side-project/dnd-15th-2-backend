# GitHub Issue #94 Task Contract

> Generated at: `2026-08-10T15:12:48+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `신규 수신자 상태 초기화 경쟁으로 활성 슬롯 카운터가 0으로 리셋된다`
- GitHub Issue: `#94`
- Branch: `fix/gh-94-receive-state-init-race`
- Base branch: `main`

## Objective

`DirectionPostService.reserve()`는 `recipient_receive_state` 행이 없을 때만
`save()`로 초기 행을 만든다. 이 SELECT-then-INSERT는 원자적이지 않고, `save()`의
UPSERT가 충돌 시 `active_unhandled_count`를 제안값 0으로 **덮어쓴다**. 두 발송이
같은 신규 사용자를 동시에 대상으로 잡으면 먼저 예약된 슬롯이 사라지고,
`recent_received_count`와 `last_received_at`도 함께 초기화된다. 결과적으로 수신
상한이 실제 배달 건수를 세지 못해 사용자가 상한을 초과해 받을 수 있다.

이 작업은 초기 행 생성이 기존 값을 덮어쓰지 않게 만들고, `reserve()`가 제공하던
조건부 UPDATE의 원자성이 초기화 경로에서도 깨지지 않게 한다.

## Scope

- 초기 행 생성이 기존 카운터를 덮어쓰지 않게 만든다. 이슈가 제시한 두 방향 중
  `reserve()` 자체를 단일 UPSERT로 합쳐 SELECT-then-INSERT 패턴을 제거하는 쪽을
  기본안으로 둔다.
- `reserve()`의 조건부 성립(`active_unhandled_count < :activeLimit`)이 INSERT
  경로와 CONFLICT 경로 양쪽에서 동일하게 판정되는지 보장한다. 반환값(`boolean`)의
  의미 — "이 호출이 슬롯을 실제로 점유했는가" — 를 그대로 유지한다.
- `DirectionPostService.reserve()`의 2단계 호출을 제거한다.
- 동시 예약 상황의 통합 테스트를 추가한다.

## Explicit exclusions

- **슬롯 해제 경로 복구(만료·넘김확정·차단) — `#93`.** `#93`은
  `RecipientReceiveStateRepository.release()`만 호출하고 `save()`/`reserve()`는
  건드리지 않는다. 이 작업은 `release()`의 시그니처와 동작을 변경하지 않는다.
- **`save()`의 덮어쓰기 의미 자체를 제거하는 것.** `save()`는
  `InboxSentPostWriteIntegrationTest` 7개 지점에서 카운터를 정확한 값으로
  세팅하는 테스트 시더로 쓰이고 있다. 프로덕션 초기화 경로를 `save()`에서
  분리하되 시더로서의 덮어쓰기 계약은 유지한다.
- 수신 상한 초기값(D05) 조정.
- 수신자 선정 규칙(차단·계정 상태 필터, 인원 상한, 분산 정렬) — `#97`.
- 수신 상한값(`receive-capacity`) 자체의 조정. DB 안전 상한과 애플리케이션
  검증 범위는 이미 50으로 일치한다(`V2__add_reactions_and_skip_pending.sql`이
  V1의 `BETWEEN 0 AND 5`를 `BETWEEN 0 AND 50`으로 교체했고
  `RecipientReceiveState.SAFETY_CEILING`이 같은 값이다). 이 작업은 상한 판정
  로직만 다루고 값은 건드리지 않는다.
- 스키마 변경. DDL은 건드리지 않고 UPSERT 구문만 바꾼다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `RecipientReceiveState` 리포지토리 UPSERT, `DirectionPostService.reserve()` | Feature executor | 동시성·원자성, `release()` 계약 불변 확인, 기존 통합 테스트 시더 회귀 |

## Concurrent work

`#93`(`fix/gh-93-release-receive-slot-transitions`)이 동시에 진행 중이다.
`../dnd-15th-2-backend`에서 작업하며 이 브랜치는 별도 git worktree
(`../dnd-15th-2-backend-gh94`)에서 `origin/main` 기준으로 분기했다.

- 코드 파일 충돌 없음. `#93`은 `PostRecipient*`·`SafetyService`·
  `SkipConfirmationProperties`를, 이 작업은 `RecipientReceiveState*`·
  `DirectionPostService`를 소유한다.
- `TASK.md`는 두 브랜치가 모두 재작성하므로 병합 시 충돌한다. 각 브랜치의
  버전을 유지하는 방향으로 해결한다.
- 공유 인터페이스 `RecipientReceiveStateRepository`의 `release()`는 이 작업에서
  변경하지 않는다.
- `PostgisContainerIntegrationTestSupport`의 공유 컨테이너를 두 워크트리에서
  동시에 구동하지 않는다.

## Existing user-owned changes

- worktree 생성 시점의 `git status --short`는 clean이었다. `#93`의 미커밋 변경은
  `../dnd-15th-2-backend`에 그대로 남아 있으며 이 브랜치로 넘어오지 않았다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [ ] 신규 사용자를 대상으로 한 동시 예약에서 `active_unhandled_count`가 실제
      배달 건수와 일치한다.
- [ ] 기존 행의 `active_unhandled_count`, `recent_received_count`,
      `last_received_at`이 초기화 경로에서 덮어써지지 않는다.
- [ ] 수신 상한을 초과해 예약되지 않는다(동시 예약 포함).
- [ ] `reserve()`의 반환값이 "이 호출이 슬롯을 점유했는가"를 계속 정확히
      표현한다 — 상한 도달 시 `false`.
- [ ] `release()`의 동작과 시그니처가 변경되지 않는다.
- [ ] 기존 `InboxSentPostWriteIntegrationTest`의 `save()` 시더 시나리오가
      회귀하지 않는다.
- [ ] 동시성 시나리오 통합 테스트가 추가된다.
