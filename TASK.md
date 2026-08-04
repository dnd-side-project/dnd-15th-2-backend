# GitHub Issue #49 Task Contract

> Generated at: `2026-08-04T21:17:39+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Issue·커밋·PR 워크플로 스킬 도입`
- GitHub Issue: `#49`
- Branch: `chore/gh-49-workflow-skills`

## Objective

- TODO

## Scope

- TODO

## Explicit exclusions

- TODO
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| TODO | TODO | TODO |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과를 확인하고 여기에 기록한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- TODO
