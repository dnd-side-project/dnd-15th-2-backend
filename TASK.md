# GitHub Issue #318 Task Contract

> Generated at: `2026-10-07T10:48:31+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `정규화 후 빈 닉네임을 게이트가 공급자 장애로 처리하지 않도록 수정`
- GitHub Issue: `#318`
- Branch: `fix/gh-318-nickname-empty-input-400`
- Base branch: `main`

## Objective

production moderation 게이트(`qello.filtering.production.enabled=true`)가 켜지면 정규화 결과가 빈 문자열인 닉네임에
400 대신 `ACC-INFRA-001`(503)이 나간다. 해당 입력은 U+200B·U+FEFF와 변경 경로의 U+3000이다.
`UnicodeTextNormalizer`가 던진 입력 오류를 `NicknameSyncModerationGate`가 주 판정기 장애로 처리해, 항상 실패하는
보조 판정기로 넘어가기 때문이다. 입력 오류는 400으로 구분하고, 판정할 수 없을 때 거부하는 fail-closed 동작은 유지한다.

## Scope

- `UnicodeTextNormalizer`: 정규화 후 빈 입력이면 `FilteringException`의 하위 전용 예외를 던진다. 오류 코드
  (`REQUIRED_VALUE_MISSING`)는 바꾸지 않는다. null 입력과 지원하지 않는 `normalizationRef`는 지금처럼 처리한다.
- `ModerationPipelineRequest`: null이 아닌 blank 원문을 같은 전용 예외로 거절한다. null은 지금처럼 처리한다(D4).
- `NicknameSyncModerationGate`: 주 판정기 `ExecutionException`의 원인이 그 전용 예외일 때만 `Reason.INVALID_INPUT`을
  반환하고, 보조 판정기는 호출하지 않는다. 그 밖의 예외는 지금처럼 보조 판정기로 넘어간다.
- `NicknameModerationOutcome.Reason`: `INVALID_INPUT` 추가
- `NicknameRegistrationService.rejectionFor`: `INVALID_INPUT`을 `AccountErrorCode.REQUIRED_VALUE_MISSING`
  (`ACC-VAL-002`, 400, field `nickname`)으로 매핑한다. 새 오류 코드는 만들지 않는다.

## Explicit exclusions

- 답변 moderation 경로(`AnswerModerationExecutionWorker`)의 같은 예외 처리
- 보조 판정기 실제 구현(#298)
- #317(닉네임 저장·중복 검사 정규화)의 범위: `ensureAvailable` 본문, `Account`, `DeviceRegistrationService`는 수정하지 않는다
- 오류 코드 값만으로 입력 오류를 판별하는 방식. 파이프라인의 다른 지점에서 서버 버그로 같은 코드가 나와도 400이 되지 않게 한다
- ApiSpec·`docs/error-codes.md` 변경. 400 설명과 `ACC-VAL-002`가 이미 이 경우를 포함한다
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 정규화기·게이트·`Reason`·`rejectionFor` | 실행 에이전트 | 사용자 PR 리뷰 |
| 테스트 수정·추가 | 실행 에이전트 | `/harness-test-plan` 승인 후 작성 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`aae33fa`)에서 별도 worktree
  (`.worktrees/gh-318-nickname-empty-input-400`)로 분기했다. 메인 작업 디렉터리는 #317 세션이 쓴다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 게이트가 켜진 상태에서 U+200B 닉네임으로 변경하거나 등록하면 400(`ACC-VAL-002`)이 나오고, 보조 판정기는 호출되지 않는다.
- 공급자 timeout/error, 예상하지 못한 예외, 지원하지 않는 `normalizationRef`는 계속 503이다.
  기존 `NicknameSyncModerationGateTest`의 `IllegalStateException` → 보조 판정기 전환 테스트는 그대로 둔다.
- `./harness check`와 `./harness pr-ready --project-tests`가 통과한다.

## Decisions

- 2026-10-07 사용자 결정: 이슈 초안 B(전용 예외·`Reason.INVALID_INPUT`·`ACC-VAL-002` 매핑) 승인. 이 세션이
  worktree에서 진행하고, #317과 둘 다 `origin/main`에서 분기해 먼저 머지된 쪽에 맞춰 나머지가 rebase한다.
- 2026-10-07 사용자 결정: 테스트 계획 `TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400` 승인. D1 production gate를 켠
  `@SpringBootTest` 대신 실제 정규화기·파이프라인·게이트·checker·서비스 조합 단위 테스트로 검증한다. D2 등록 경로는
  `ensureAvailable`에서 검증한다. D3 정규화기의 null 입력은 전용 예외로 만들지 않는다(503 유지).
- 2026-10-07 사용자 결정(구현 중 추가, D4): U+3000처럼 `isBlank()`가 true인 원문은 정규화기보다 먼저
  `ModerationPipelineRequest` 생성자에서 일반 `FilteringException`으로 거절되어 여전히 503이었다. 이 생성자도
  null이 아닌 blank 원문을 `EmptyNormalizedTextException`으로 거절하도록 범위에 추가한다.
