# GitHub Issue #93 Task Contract

> Generated at: `2026-08-10T14:42:40+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `수신 슬롯이 만료·넘김 확정·차단으로 해제되지 않는다`
- GitHub Issue: `#93`
- Branch: `fix/gh-93-release-receive-slot-transitions`
- Base branch: `main`

## Objective

명세 F04는 슬롯이 `답변 / 넘김 확정 / 만료` 세 경우에 정확히 한 번 해제된다고
규정한다. 현재 `RecipientReceiveStateRepository.release()`를 호출하는 곳은
`AnswerNotificationService.releaseSlot()` 한 곳뿐이라 답변 경로만 동작한다.
답을 하지 않은 사용자의 활성 슬롯이 영구히 반환되지 않아, 수신 상한이 수신자를
보호하는 대신 신규 수신에서 영구 제외하는 방향으로 오작동한다. 이 작업은 만료·
넘김 확정·차단 세 경로에서도 슬롯이 해제되게 만든다.

## Scope

- 만료 전이 — `PostRecipient`에 만료 전이 메서드를 추가하고, `expires_at`이 지난
  미처리 수신 항목을 `EXPIRED`로 전이시키며 슬롯을 해제한다.
- 넘김 확정 — `PostRecipient.confirmSkip()`을 실제로 호출하는 경로를 만든다.
  현재 프로덕션 코드에 호출자가 없고 테스트에서만 불린다.
- 차단 전이 — 차단 성립 시 해당 수신 항목을 `BLOCKED`로 전이시키고 슬롯을 해제한다.
- 되돌리기 유예(5초)가 지난 `SKIP_PENDING`만 확정 대상으로 삼는다. 유예 값은
  코드 상수가 아니라 설정값으로 둔다.
- 세 경로 모두 재실행에 안전해야 한다. 상태 전이와 카운터 감소가 갈라지지 않도록
  같은 트랜잭션에서 수행하고 `ct_post_recipient_capacity_release`를 통과시킨다.

## Explicit exclusions

- 수신자 선정 규칙(차단·계정 상태 필터, 인원 상한, 분산 정렬) — #97에서 다룬다.
- 넘김 조작 방식(스와이프/버튼)과 클라이언트 스낵바 UI.
- 만료된 질문글을 마이탭 `내 답변`으로 옮기는 조회 경로.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| direction 도메인·서비스, 슬롯 해제 워커 | Feature executor | 동시 실행·재실행 안전성, capacity 트리거 정합성 검토 |

## Existing user-owned changes

- 브랜치 생성 전 `main`에서 `git status --short`로 미커밋 변경을 확인했다.
  `FeedDistanceProperties.java`가 `feed/config/`에서 `feed/error/config/`로
  잘못 이동된 상태였다 — 이번 작업과 무관한 이력이라 판단해 사용자 승인을 받고
  원래 위치로 복원한 뒤 브랜치를 만들었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [ ] 만료된 미처리 수신 항목이 `EXPIRED`로 전이되고 슬롯이 1개 해제된다.
- [ ] 유예가 지난 `SKIP_PENDING`이 `SKIPPED`로 확정되고 슬롯이 1개 해제된다.
- [ ] 유예 중인 `SKIP_PENDING`은 확정되지 않고 슬롯을 계속 점유한다.
- [ ] 차단 성립 시 관련 수신 항목이 `BLOCKED`로 전이되고 슬롯이 해제된다.
- [ ] 같은 수신 항목에 전이가 두 번 실행돼도 슬롯이 두 번 해제되지 않는다.
- [ ] 상한까지 받은 뒤 전부 만료시킨 사용자가 다시 신규 수신자로 선정된다.
- [ ] 위 시나리오의 통합 테스트가 추가된다.
