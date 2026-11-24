# GitHub Issue #386 Task Contract

> Generated at: `2026-11-24T18:08:34+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[harness] worktree에서 push하면 RepoMapToolTest가 저장소를 bare로 바꾼다`
- GitHub Issue: `#386`
- Branch: `fix/gh-386-test-git-env-isolation`
- Base branch: `main`

## Objective

- linked worktree에서 `git push`하면 pre-push hook의 `./harness pr-ready --project-tests` 안에서 `RepoMapToolTest`가 hook이 넘긴 절대경로 `GIT_DIR`을 물려받는다. 그 결과 임시 디렉터리의 `git init`이 실제 저장소를 재초기화하고 공통 `.git/config`에 `core.bare=true`를 쓴다. 테스트와 hook 하위 검사가 개발 환경의 저장소를 바꾸지 않게 한다.
- 확인된 사실(2026-11-24):
  - `GIT_DIR="$(git rev-parse --git-dir)"`를 설정하고 `RepoMapToolTest`를 실행하면 14개 모두 `expected: 0 but was: 128`로 실패하고 `core.bare=true`가 기록된다. 환경 변수가 없으면 14개 모두 통과한다.
  - `RepoMapToolTest.command`의 `ProcessBuilder`는 `JAVA_HOME`만 덮어쓰고 나머지 부모 환경을 그대로 물려준다.
  - 설정이 바뀐 뒤에는 `ProductionConventionRatchetTest`의 `git diff --name-only origin/main`도 `must be run in a work tree`로 실패한다.
  - 복구는 `git config --file <저장소>/.git/config core.bare false`다.
- 결정(2026-11-24, @Byuntil): 유형 버그(`fix/` 브랜치, `type: bug`), Priority P1.
- Test plan: `docs/test-plans/gh-386-TEST-PLAN-GH-386-TEST-GIT-ENV.md` — `APPROVED` — D1·D3 테스트와 pre-push 이중 방어, D2·D5 추천안, D4 가벼운 상시 테스트 + 1회 증거, `scripts/test-run-hook.py`를 `harness check`와 CI policy job에 등록. 승인자 @Byuntil, 2026-11-24. 승인 증거: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/386#issuecomment-7128148671

## Scope

- `RepoMapToolTest`가 띄우는 git·Python 프로세스 환경에서 저장소를 가리키는 `GIT_*` 변수를 제거한다.
- 임시 저장소에 git 명령을 실행하는 다른 테스트(`src/test/**`, `src/integrationTest/**`)와 `scripts/`의 테스트를 찾아 같은 위험이 있으면 같은 방식으로 막는다.
- hook 실행기(`scripts/run-hook.py`)의 pre-push 경로에서 저장소 위치 변수 네 개를 제거한다(승인된 D3).
- 회귀 `scripts/test-run-hook.py`를 `scripts/harness.py`의 `validation_commands()`와 `.github/workflows/harness-policy.yml`의 policy job 단계 하나에 등록한다.
- 재발 방지 회귀 테스트.

## Explicit exclusions

- 애플리케이션 production 코드 변경.
- CI workflow 변경. 단, `harness-policy.yml` policy job에 `scripts/test-run-hook.py` 실행 단계 하나를 추가하는 것은 승인된 예외다. `.github/workflows/*apply*`는 바꾸지 않는다.
- `.husky/*` hook 진입 스크립트, `CLAUDE.md`, `AGENTS.md`, `.claude/**`, `agents/**`, `scripts/guard-*` 변경은 별도 승인 없이 하지 않는다.
- 실제 저장소의 `GIT_DIR`을 설정한 재현 실행. 재현은 일회용 clone이나 임시 저장소의 git dir로만 한다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `TASK.md`, 범위 | @Byuntil | 사람의 범위 승인 |
| 수정 설계·테스트 계획 | 계획 에이전트(omo) | 사람의 계획 승인 |
| 테스트·hook 보조 코드 수정 | 실행 에이전트(omo) | PR에서 작성자가 아닌 백엔드 한 명 승인 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 출력은 `TASK.md`(task-init) 하나였다.
- `gh`와 `harness`는 `env -u SSL_CERT_FILE -u SSL_CERT_DIR`로 시스템 CA를 사용한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- 일회용 저장소의 git dir을 절대경로 `GIT_DIR`로 설정한 채 `RepoMapToolTest`를 실행해도 14개가 통과하고, 그 저장소의 `core.bare`와 HEAD가 바뀌지 않는다.
- linked worktree에서 `--no-verify` 없이 `git push`할 때 pre-push hook이 통과하고 `.git/config`가 바뀌지 않는다(사람 승인 후 PR push에서 확인).
- `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`가 통과한다.
