# GitHub Issue #64 Task Contract

> Generated at: `2026-08-05T17:02:57+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `PR 전 origin/main rebase 강제 및 harness sync 도구 추가`
- GitHub Issue: `#64`
- Branch: `chore/gh-64-git-sync-routine`

## Objective

- 브랜치가 오래된 로컬 `main`에서 분기되고 PR 전 `origin/main` 반영 절차가
  없어 conflict가 반복되는 문제를 저장소 규약으로 고정한다.
- 설계: `.harness-local/specs/2026-08-05-git-sync-routine-design.md`
- 계획: `.harness-local/plans/2026-08-05-git-sync-routine-implementation.md`

## Scope

- `scripts/harness.py`: `./harness sync`(fetch+rebase, 충돌 시 항상 사람에게
  위임) 신설
- `scripts/harness.py`: `h start`가 로컬 `main`이 아니라 최신 `origin/main`
  에서 분기하도록 변경
- `scripts/harness.py`: `./harness pr-ready`가 `origin/main`보다 뒤처진
  브랜치를 감지해 실패시킴(Husky `pre-push`에서도 자동 적용)
- `.claude/skills/harness-pr/SKILL.md`, `.claude/skills/harness-issue/SKILL.md`
  갱신 — 0.5 최신화 단계, `--force-with-lease` 예외
- `AGENTS.md`, `docs/harness/WORKFLOW_SKILLS.md`,
  `docs/harness/DAILY_WORKFLOW.md`, `docs/harness/CHEATSHEET.md`,
  `docs/harness/FAILURE_RECOVERY.md` 갱신
- **범위 확장(PR #65 리뷰 요청 이후 추가, 같은 브랜치에서 진행하기로 사용자
  확인):** `main` 외 다른 base 브랜치 지원. 애초 "제외"로 뺐으나 이 저장소에
  실제 stacked PR 선례(#46 → #47)가 있어 확장했다.
  - `./harness start --base <브랜치>`로 다른 브랜치에서 분기하고
    `git config branch.<name>.harness-base`에 기록
  - `resolve_base_branch()`가 그 값을 읽어 `sync`/`pr-ready`/`TASK.md`가
    전부 기록된 base를 기준으로 동작
  - 신규 `./harness base` — 현재 기준 브랜치 출력
  - `TASK.md` Work gate에 `Base branch` 기록
  - `/harness-pr`의 `origin/main` 하드코딩(컨텍스트 수집, `gh pr create
    --base`)을 `./harness base`로 대체

## Explicit exclusions

- rebase 충돌 자동 해결 — 항상 사람에게 위임한다.
- `scripts/harness.py`에 대한 신규 pytest 스위트.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `scripts/harness.py` | 본인(Claude Code 세션) | 사용자 |
| `.claude/skills/harness-pr`, `.claude/skills/harness-issue` | 본인(Claude Code 세션) | 사용자 |
| `AGENTS.md`, `docs/harness/*.md` | 본인(Claude Code 세션) | 사용자 |

## Existing user-owned changes

- 브랜치 생성 시점(`git status --short`) 결과: 없음(clean).

## Validation

```bash
python3 -m py_compile scripts/harness.py
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] `./harness sync`가 정상/뒤처짐/충돌 3가지 시나리오에서 설계대로
      동작한다(격리된 scratch 저장소로 검증, `.harness-local/plans/2026-08-05-git-sync-routine-implementation.md` Task 6).
- [x] `h start`가 최신 `origin/main`에서 분기한다(scratch 저장소 시나리오 A).
- [x] `./harness pr-ready`(및 Husky `pre-push`)가 뒤처진 브랜치를 거부한다(scratch 시나리오 E + 실제 브랜치에서 `ensure_synced_with_default_branch` 통합 확인).
- [x] 관련 스킬·규약 문서가 새 절차를 반영한다.
- [x] `./harness check`, `npm run hooks:validate` 통과. `./harness pr-ready --project-tests`(전체 `./gradlew check` 포함)는 PR #65 push 시 Husky `pre-push`로 이미 통과 확인.
- [x] `main` 외 base 브랜치 지원: scratch 저장소에서 stacked 시나리오(부모
      브랜치 위에서 `--base`로 분기 → `./harness base` 확인 → 부모 브랜치가
      진행돼도 `sync`/뒤처짐 감지가 main이 아닌 그 부모를 기준으로 동작,
      main만 앞서가는 경우는 무시)를 검증.
