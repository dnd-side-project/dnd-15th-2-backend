# GitHub Issue #86 Task Contract

> Generated at: `2026-08-08T17:05:38+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Codex용 Claude 스킬·에이전트 마이그레이션`
- GitHub Issue: `#86`
- Branch: `chore/gh-86-codex-agent-skill-migration`
- Base branch: `main`

## Objective

Claude Code에서 사용하던 저장소 스킬과 에이전트를 Codex에서도 사용할 수 있는
프로젝트 전용 형식으로 이식한다. 기존 Claude 실행 경로는 보존하고, Codex가 같은
저장소 정책과 역할 문서를 사용할 수 있게 한다.

## Scope

- `.claude/skills/`의 스킬 10개를 `.agents/skills/` 형식으로 변환한다.
- `.claude/agents/`의 에이전트 5개를 `.codex/agents/` TOML 형식으로 변환한다.
- Claude 전용 인자·호출·권한 표현을 Codex의 스킬·샌드박스 지침으로 정리한다.
- Codex 에이전트 경로를 Git으로 추적할 수 있도록 ignore 규칙을 보완한다.
- 기존 `.claude/`, `CLAUDE.md`, `AGENTS.md` 동작을 변경하지 않는다.

## Explicit exclusions

- 애플리케이션 코드, Terraform 리소스, 배포와 인프라 적용
- Claude 원본 스킬·에이전트·설정 파일의 변경
- MCP 서버와 글로벌 Codex 설정의 마이그레이션
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Codex 스킬·에이전트 변환 | Codex migration executor | Codex 대상 검증과 Claude 원본 보존 확인 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과를 확인하고 여기에 기록한다.
- 기존 구현 작업은 `stash@{0}` (`wip: Codex skill and agent migration`)에 보존한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [ ] Claude 스킬 10개가 `.agents/skills/`에 생성되고 frontmatter 검증을 통과한다.
- [ ] Claude 에이전트 5개가 `.codex/agents/`에 생성되고 TOML 검증을 통과한다.
- [ ] Codex 변환 파일에 `$ARGUMENTS`와 Claude 전용 마이그레이션 블록이 남아 있지 않다.
- [ ] 기존 `.claude/`, `CLAUDE.md`, `AGENTS.md`는 변경되지 않는다.
- [ ] `migrate-to-codex --validate-target .codex`가 통과한다.
- [ ] `./harness check`, `npm run hooks:validate`, `git diff --check`가 통과한다.
