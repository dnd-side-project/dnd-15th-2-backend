# GitHub Issue #313 Task Contract

> Generated at: `2026-10-06T12:00:15+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Harness Policy 같은 커밋 중복 실행 제거`
- GitHub Issue: `#313`
- Branch: `ci/gh-313-dedupe-workflow-runs`
- Base branch: `main`

## Objective

`.github/workflows/harness-policy.yml`이 같은 커밋을 트리거마다 다시 실행한다. 2026-07-24~10-05 전체 job 시간의
65%(4,167 job-분)가 이 중복이다. workflow의 트리거와 job 실행 조건만 바꾼다.

## Scope

| 중복 원인 | 변경 |
| --- | --- |
| `concurrency` 없음 | 같은 PR·브랜치의 이전 실행을 취소한다 |
| push와 PR이 둘 다 전체 job 실행 | push는 `policy` job만 있는 새 workflow(Branch Policy)가 받고 Harness Policy에서 push를 뺀다 |
| PR 제목·본문 수정(`edited`)에도 전체 job 실행 | PR 브랜치·제목·본문 검사를 새 workflow(Pull Request Policy)로 옮기고 Harness Policy에서 `edited`를 뺀다 |

- 수정 파일: `.github/workflows/harness-policy.yml`, `.github/workflows/branch-policy.yml`(신규),
  `.github/workflows/pull-request-policy.yml`(신규)
- push 처리 방식(사용자 결정, 2026-10-07): job `if`로 push run의 `test`, `java-conventions`를 건너뛰지 않고
  push 전용 workflow로 분리한다. push run의 skipped job도 같은 head 커밋에 check로 남는 것을 확인했다
  (기존 push run 37587951467의 `sync-api-docs` skipped check가 PR run check와 같은 커밋 d722910에 있다).
  Branch Policy의 `policy` step은 Harness Policy `policy` job의 step을 복사했고 커밋 메시지 검사만 Branch Policy에 있다.
- `edited` 처리 방식(사용자 결정, 2026-10-07): PR 검증 workflow 분리. concurrency 키에 `edited` 여부를 넣는
  방식은 쓰지 않는다. `edited` run이 test를 건너뛰면 skipped check가 같은 커밋에 남고 GitHub는 이를
  Success로 보고한다("A job that is skipped will report its status as "Success". It will not prevent a pull
  request from merging, even if it is a required check.", GitHub Docs, Control jobs with conditions).
- 새 workflow는 `opened`, `edited`, `synchronize`, `reopened`를 받는다. check가 head 커밋에 붙으므로
  `synchronize`가 없으면 새 커밋에 제목·본문 검사 결과가 남지 않는다.
- Harness Policy `policy` job의 PR 브랜치·제목·본문 검사 step은 새 workflow로 옮긴다. 남겨 두면 제목을 고쳐도
  다음 push 전까지 `policy`가 이전 제목 기준 실패로 남는다.
- `reopened`는 남긴다(닫혀 있는 동안 push됐을 수 있다). `workflow_dispatch`는 `policy`, `test`,
  `java-conventions`를 그대로 실행한다.
- 대가: PR이 없는 브랜치 push는 pre-push hook의 `./gradlew check`와 `workflow_dispatch`로만 테스트한다.
  base 브랜치를 바꾸면 `edited`만 오므로 Harness Policy가 다시 돌지 않을 수 있다. 실제 동작은 확인해 PR에 기록한다.

## Explicit exclusions

- 한 번 실행 시간 단축(통합 테스트 수명주기, Gradle 캐시, job 내부 중복)
- `main-ruleset` 활성화와 required check 변경
- 다른 workflow(`infrastructure-*.yml`, `label-policy.yml`, `deploy-*.yml`)
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `edited` 분리 방식 결정 | 사용자 | 2026-10-07 결정(PR 검증 workflow 분리) |
| `harness-policy.yml`, `branch-policy.yml`, `pull-request-policy.yml` 변경 | 실행 에이전트 | 사용자 PR 리뷰 |
| `main-ruleset` 활성화 시 새 required check(`Pull Request Policy / pull-request-metadata`) 반영 | 사용자 | 이 PR 범위 밖 |

## Existing user-owned changes

- `origin/main`(18b1adc)에서 만든 별도 worktree(`.worktrees/gh-313-dedupe-workflow-runs`)라 시작 시
  `git status --short`가 깨끗했다. 원래 작업 공간의 #312 미커밋 변경은 건드리지 않았다.
- 2026-10-07 구현 전에 `origin/main`(1a3b125)으로 fast-forward했다. 그 사이 `harness-policy.yml`은 바뀌지 않았다.

## Validation

```bash
python scripts/validate-workflows.py
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

`gh run view <id> --json jobs`로 확인한다.

- push로는 Branch Policy 실행만 생기고 `policy` job만 있다. Harness Policy 실행은 생기지 않는다.
- 열린 PR에 연속 push하면 앞 실행이 `cancelled`로 끝난다.
- PR 제목을 고치면 Harness Policy 실행이 생기지 않고 Pull Request Policy만 돈다. 진행 중이던 Harness Policy 실행은 끝까지 돈다.
- 테스트가 실패한 PR에서 제목만 고쳤을 때 `test` check가 어떻게 표시되는지 기록한다.
- base 브랜치를 바꿨을 때 테스트가 다시 도는지 기록한다.
- `validate-workflows.py`, `./harness check`가 통과한다.
- 시나리오별 run ID를 PR 본문에 기록한다.
