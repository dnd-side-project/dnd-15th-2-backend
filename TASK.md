# GitHub Issue #317 Task Contract

> Generated at: `2026-10-07T10:39:58+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `닉네임 저장·중복 검사에 보이지 않는 문자 정규화 적용`
- GitHub Issue: `#317`
- Branch: `fix/gh-317-nickname-invisible-chars`
- Base branch: `main`

## Objective

닉네임은 앞뒤 `trim()`만 하고 저장해 zero-width 문자(U+200B 등)와 전각 공백(U+3000)이 남는다. moderation은 정규화한
값으로 판정하지만 저장과 중복 검사는 원래 값을 써서, `바람`과 똑같아 보이는 닉네임이 중복 검사와
`uq_user_account_nickname_ci`를 통과한다. 저장·중복 검사·moderation 입력이 같은 정규화 값 하나를 쓰게 한다.

## Scope

- 닉네임 정규화 규칙: NFC → 제어·서식 문자(Cc·Cf: zero-width, BOM, 방향 제어) 제거(이모지 사이 ZWJ는 남김) → 유니코드
  공백을 공백 1칸으로 축소 → 앞뒤 제거. 정규화 후 빈 값은 `REQUIRED_VALUE_MISSING`, 50자 초과는 `TEXT_TOO_LONG`(둘 다 400)
- 정규화는 새로 입력받는 값에만 적용한다. `restore`는 저장된 값을 그대로 복원한다
- 적용 위치: `Account` 닉네임 검증(저장값), `NicknameRegistrationService.ensureAvailable`의 중복 검사·moderation 입력,
  `DeviceRegistrationService`의 빈 닉네임 판단
- 순서: 정규화·길이 검증을 중복 검사·moderation보다 먼저 한다. 빈 값·50자 초과는 외부 호출 없이 400
- 문서: 요청 스키마 설명(`ChangeNicknameRequest`, `DeviceRegistrationRequest`), `docs/api/openapi.json`
- PR·이슈 작성 지침: `harness-pr`·`harness-issue`의 문체 규칙과 SKILL(`.agents/skills`, `.claude/skills` 양쪽)에
  작성 원칙(AI 말투·키워드 제거, 한 일·할 일과 근거 명시, 군더더기 제거, 가시성, 선택적 다이어그램)을 추가한다

## Explicit exclusions

- NFKC로 저장(한글 호환 자모 ㄱ U+3131이 ᄀ U+1100으로 바뀌어 표시가 깨진다). 전각 영문(ＡＢＣ와 ABC)은 중복으로 보지 않는다
- 기존 행 정규화 마이그레이션
- 한글 채움 문자(U+3164)처럼 Lo로 분류되는 투명 문자 차단
- moderation 게이트의 예외 분류 수정(정규화 후 빈 닉네임 503). #318(브랜치 `fix/gh-318-nickname-empty-input-400`)에서
  다룬다. #318은 `NicknameRegistrationService.rejectionFor`만 건드리고 `ensureAvailable` 본문은 #317이 맡는다.
  PR 선행 관계에 #318을 적고, 먼저 머지된 쪽에 맞춰 rebase한다
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `Account` 닉네임 정규화·`NicknameRegistrationService`·`DeviceRegistrationService` | 실행 에이전트 | 사용자 PR 리뷰 |
| 요청 스키마 설명·`docs/api/openapi.json` | 실행 에이전트 | 사용자 PR 리뷰 |
| 테스트 수정·추가 | 실행 에이전트 | `/harness-test-plan` 승인 후 작성 |
| PR·이슈 작성 지침(`.agents/skills/harness-{pr,issue}`, `.claude/skills/harness-{pr,issue}`) | 실행 에이전트 | 사용자 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`aae33fa`)에서 분기했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- `바람\u200B`, `바\u200B람`, `\u3000바람`이 `바람`으로 저장되고, `바람`이 있으면 409 `ACC-APP-002`
- 정규화 후 빈 값·50자 초과는 moderation 호출 없이 400
- 응답 닉네임이 정규화한 값이다
- `./harness check`와 `./harness pr-ready --project-tests`가 통과한다.

## Decisions

- 2026-10-07 사용자 결정: 이슈 초안 A(정규화 규칙·적용 위치·제외 범위) 승인. 계획 B(게이트 예외 분류)는 별도 세션
  `B`가 worktree에서 진행하고(#318), 둘 다 `origin/main`에서 분기해 먼저 머지된 쪽에 맞춰 rebase한다.
- 2026-10-07 사용자 결정: D1 정규화는 새로 입력받는 값에만 적용하고 `restore`는 저장값을 그대로 둔다. D2 이모지 사이
  ZWJ만 남기고 ZWNJ와 그 밖의 ZWJ는 지운다. D3(정규화 거절도 시도 한도 1회로 센다)는 실행 에이전트에 위임됐다.
- 2026-10-07 사용자 결정: PR·이슈 작성 지침 6가지를 별도 PR 없이 이 브랜치(PR #319)에 함께 커밋한다. `.claude/skills`의
  문체 규칙·SKILL 수정은 이 지시를 명시적 승인으로 본다. 권한·금지 명령·승인 게이트는 바꾸지 않는다.
