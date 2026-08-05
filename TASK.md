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

## Explicit exclusions

- rebase 충돌 자동 해결 — 항상 사람에게 위임한다.
- `main` 외 다른 base 브랜치 지원.
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

- [ ] `./harness sync`가 정상/뒤처짐/충돌 3가지 시나리오에서 설계대로
      동작한다(격리된 scratch 저장소로 검증).
- [ ] `h start`가 최신 `origin/main`에서 분기한다.
- [ ] `./harness pr-ready`(및 Husky `pre-push`)가 뒤처진 브랜치를 거부한다.
- [ ] 관련 스킬·규약 문서가 새 절차를 반영한다.
- [ ] `./harness check`, `npm run hooks:validate` 통과.
