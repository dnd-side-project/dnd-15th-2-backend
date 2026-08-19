# GitHub Issue #172 Task Contract

> Generated at: `2026-08-19T11:23:11+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `이슈·PR 작성 규칙 정비 — 가시성 향상`
- GitHub Issue: `#172`
- Branch: `docs/gh-172-issue-pr-writing-style`
- Base branch: `main`

## Objective

- 최근 이슈·PR 본문이 AI가 작성한 것처럼 읽혀, 사람이 해당 이슈에서 무엇을
  할지·했는지 한눈에 파악하기 어렵다는 문제를 해소한다.
- 조사 결과 PR 쪽은 이미 `.claude/skills/harness-pr/references/writing-style.md`
  로 문체 규칙(합쇼체 통일, 금지 표현 목록, 사실/추측 구분, 제출 전 점검
  목록)이 있고 `harness-pr/SKILL.md`에 배선돼 있다. 반면 이슈 쪽
  (`harness-issue`)에는 동등한 문체 규칙이 없다 — 이것이 실제 공백이다.
- 이번 작업은 이슈 본문에 같은 수준의 작성 규칙을 만들고 `harness-issue`
  skill에 배선하는 것을 중심으로 한다. 이슈·PR 템플릿(`.yml`, `.md`) 구조
  자체는 바꾸지 않는다.

## Scope

- `harness-issue/references/writing-style.md`(신규) — 이슈 본문 문체 규칙.
  `harness-pr/references/writing-style.md`를 모델로 삼되, 이슈 본문에 맞는
  차이(목적/범위/완료 조건 섹션의 서술 방식, 완료 조건 체크리스트 표현)를
  반영한다.
- `harness-issue/SKILL.md` — 5절(초안 제시) 직전 또는 직후에 새
  `writing-style.md`를 읽고 적용하는 지시를 추가한다(harness-pr/SKILL.md
  11번째 줄, 108번째 줄, 209번째 줄과 동일한 배선 패턴).
- `harness-pr/references/writing-style.md`의 기존 규칙이 이슈 본문에도
  그대로 통하는 항목(이모지 금지, 과장 형용사/근거 없는 정도 부사 금지,
  자기 평가·인사말·상투적 도입 금지, 번역투·피동 남용 금지, 기계적 나열
  금지)이 있는지 확인하고, 두 문서 간 규칙이 어긋나지 않게 정리한다.
- `AGENTS.md` 7절(커밋과 PR)에 이슈 작성 규칙 존재를 짧게 언급할지는 구현
  중 판단한다(필수 아님 — PR 규칙도 AGENTS.md 본문에는 없고 skill
  reference로만 존재한다).

## Explicit exclusions

- 이슈/PR 템플릿(`.github/ISSUE_TEMPLATE/*.yml`,
  `.github/PULL_REQUEST_TEMPLATE.md`) 섹션·필드 구조 변경.
- `harness-pr/references/writing-style.md` 자체의 규칙 변경(이미 존재하고
  이번 이슈 범위 밖) — 이슈 쪽 문서와의 정합성 확인 과정에서 명백한 오류를
  발견하면 별도로 보고하고 사용자 승인 없이 고치지 않는다.
- `harness-commit` skill의 커밋 메시지 문체 규칙 신설 — 커밋 메시지는
  한 줄 요약이 중심이라 이번 이슈의 "본문이 길어 파악하기 어렵다" 문제와
  다르다. 필요하면 별도 이슈로 다룬다.
- 과거에 이미 작성된 이슈·PR 본문의 소급 수정.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `harness-issue/references/writing-style.md`(신규), `harness-issue/SKILL.md` 배선 | Feature executor | 완료 조건 3개 항목 검증, `harness-pr/references/writing-style.md`와의 규칙 정합성 리뷰 |

## Existing user-owned changes

- `main`(#167 병합 직후, `origin/main` 최신 커밋 `5309cc3`)에서
  worktree(`docs/gh-172-issue-pr-writing-style`)로 새로 분기했다. 분기
  시점 `git status --short`는 비어 있었다(`TASK.md` 갱신 이후 현재
  `M TASK.md`만 있다).

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] `harness-issue/references/writing-style.md`가 존재하고 이슈 본문
      문체 규칙(종결 어미, 금지 표현, 사실/추측 구분, 제출 전 점검 목록)을
      `harness-pr/references/writing-style.md`와 같은 수준으로 담는다 —
      평서체 종결(PR은 합쇼체와 대비), 이슈 특유 항목(모호한 동사, 판별
      기준 없는 완료 조건) 추가로 반영했다.
- [x] `harness-issue/SKILL.md`가 초안 작성 단계에서 새
      `writing-style.md`를 읽고 적용하도록 지시를 명시한다 — 참조 목록,
      5절(초안 제시) 직전, 금지 절 3곳에 배선했다.
- [x] 이슈 폼(`.github/ISSUE_TEMPLATE/*.yml`)과 PR 템플릿
      (`.github/PULL_REQUEST_TEMPLATE.md`)의 섹션·필드 구조가 이번 변경
      전후로 동일하다 — `git status --short` 결과 두 경로 모두 변경 없음
      확인.

## Verification evidence

- `./harness check` — 통과(secret preflight 981개 파일, JUnit 정책 171개
  파일, convention·workflow·label·husky 검사 전부 통과).
- `./harness pr-ready --project-tests` — 통과(`BUILD SUCCESSFUL in 6m 3s`,
  unit+integration 테스트 포함). 실행 중 `git fetch`가 셰어드 체크아웃의
  `main`을 fast-forward하지 못했다는 무해한 경고
  (`refusing to fetch into branch 'refs/heads/main' checked out at
  '/Users/kimdoyeon/Desktop/dnd'`)가 있었으나, worktree 구조상 정상이며
  검증 결과에 영향 없다.
- `git diff --check` — 통과(공백·충돌 마커 없음).
- `npm run hooks:validate` — 통과.
