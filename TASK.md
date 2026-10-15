# GitHub Issue #357 Task Contract

> Generated at: `2026-10-16T01:11:30+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Harness Delta 로컬 산출물을 git 추적에서 제외`
- GitHub Issue: `#357`
- Branch: `chore/gh-357-harness-delta-gitignore`
- Base branch: `main`

## Objective

- Harness Delta 측정 중 생기는 로컬 산출물이 `git add -A`로 PR에 섞이거나 `./harness start`·`sync`의 clean worktree 검사를 막지 않게 한다.

## Scope

- `.gitignore`에 Harness Delta 로컬 산출물 6개 경로(`.harness-delta/`, `.harness-delta-managed/`, `/harness-config/`, `/.agents/skills/harness-connect/`, `/.claude/skills/harness-connect/`, `/docs/harness/harness-delta/`)를 추가한다.
- 규칙은 `!/.agents/skills/**` 예외보다 뒤에 둔다.

## Explicit exclusions

- Git hook에서 Harness Delta `check-staged` 실행. task-id가 사용자·task마다 달라 개인 설정으로 둔다.
- 이미 추적 중인 하네스 파일(AGENTS.md 등)의 측정 중 변경 처리.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `.gitignore` 규칙 | 실행 에이전트 | 사용자 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`88795311`, #353 병합)에서 분기했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 6개 경로 아래 파일이 `git check-ignore -v`로 새 규칙에 매칭된다.
- 기존 추적 파일(`.agents/skills/**`, `.claude/**`)이 새 규칙으로 무시되지 않는다.
- `./harness check`와 `git diff --check`가 통과한다.
