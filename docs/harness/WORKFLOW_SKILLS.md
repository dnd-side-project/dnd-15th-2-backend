# Workflow Skills Guide

Issue → 브랜치 → 커밋 → PR을 질문형 대화로 진행하는 Claude Code 스킬 3종의
사용 가이드다. 규칙 자체는 `AGENTS.md`가 기준이며, 이 스킬들은 그 규칙의 실행
경로일 뿐 정책을 새로 만들지 않는다.

| 스킬 | 역할 | 산출물 |
| --- | --- | --- |
| `/harness-issue` | 이슈 생성 + Project 필드 연결 + 브랜치 생성 | Issue, Project item, 브랜치, `TASK.md` |
| `/harness-commit` | 변경을 검토 목적 단위로 분할해 커밋 | 커밋 N개 |
| `/harness-pr` | 로컬 검증 + PR 생성 | push, Pull Request |

기존 역할 스킬(`/harness-test-plan`, `/harness-test-run`, `/harness-infra-design`,
`/harness-infra-build`, `/harness-review`)과는 층이 다르다. 이 3종은 **작업의
포장 단계**를, 역할 스킬은 **작업의 내용**을 다룬다.

## 전체 흐름

```text
/harness-issue            새 작업 시작
   │  유형 질문 → 작업 내용 질문 → Sprint·Priority·Status 질문
   │  → 연결 작업 안내 → 초안 승인 → 생성
   ▼
Issue #N + Project item + <type>/gh-<N>-<slug> 브랜치 + TASK.md
   │
   ▼
/harness-test-plan → (승인) → /harness-test-run    구현과 검증
   │
   ▼
/harness-commit           변경 분할 커밋
   │  변경 분석 → 분할 초안 승인 → 순차 커밋
   ▼
/harness-pr               마무리
   │  ./harness pr-ready --project-tests → 옵션 질문 → 초안 승인
   │  → push → PR 생성
   ▼
/harness-review           머지 전 검토 (사람이 머지)
```

## 세 스킬의 공통 계약

1. **승인 전 초안.** 세 스킬 모두 GitHub·git 상태를 바꾸기 전에 전체 초안을
   보여주고 승인을 받는다. 승인 없이 생성·커밋·push하지 않는다.
2. **브랜치가 단일 진실 원천.** 이슈 번호와 type은 브랜치 이름에서 파생한다.
   저장소 설정이나 환경변수에 이슈 번호를 고정하지 않는다.
3. **부분 실패는 숨기지 않는다.** 중간 단계가 실패하면 즉시 멈추고 어디까지
   반영됐는지 보고한다.
4. **훅을 우회하지 않는다.** `--no-verify`, 일반 force push, `main` 직접
   push는 금지다. `/harness-pr`의 `./harness sync`로 rebase한 직후 본인
   feature 브랜치에 한해 `--force-with-lease`만 예외다.
5. **비밀정보 금지.** `.env` 값, 토큰, URL, 계정·IAM 식별자를 이슈·커밋·PR·로그에
   쓰지 않는다.

## `/harness-issue`

`.github/ISSUE_TEMPLATE/`의 6개 Issue Form을 4+3 두 단계 질문으로 압축해 묻고,
선택 결과로 `type: *` 라벨·브랜치 prefix·Project Work type을 동시에 결정한다.

물어보는 것:

1. 이슈 유형 — 기능 / 버그·장애 복구 / 백엔드 유지보수 / 설계·테스트·인프라
2. 작업 내용 — 자유 서술 (스킬 인자로 미리 넘기면 생략)
3. Sprint / Priority / Status — Project 필드 실제 옵션에서 선택

연결되는 것:

```text
Issue 생성 → Project item 추가 → 필드 설정 → 브랜치 생성 → TASK.md 갱신
```

주의:

- **스프린트·우선순위·상태는 라벨로 만들지 않는다.** GitHub Project 필드로만
  관리한다(`LABELS.md`).
- `type: *` 라벨은 정확히 하나. `area: *`는 필요할 때만.
- 브랜치 생성은 worktree가 깨끗해야 한다. 더러우면 이슈까지만 만들고 멈춘다.
- `gh project` 명령에는 `project` 스코프가 필요하다. 없으면
  `gh auth refresh -s project`를 사용자가 직접 실행해야 한다.

Project ID와 필드/옵션 ID는
`.claude/skills/harness-issue/references/project-fields.md`에 캐시돼 있다.
Sprint iteration은 주기적으로 새로 생기므로, 캐시가 오래됐으면 같은 문서의
조회 명령으로 갱신한다. 팀이 Project 필드를 추가·변경하면 이 문서도 함께 고친다.

## `/harness-commit`

`AGENTS.md`의 "하나의 커밋에는 하나의 검토 목적만 담는다"를 강제한다.
**나누는 것이 기본값이고, 나누지 않으려면 근거가 필요하다.**

분할 축(우선순위 순): 계층·경계 → 타입 → 범위 단위 → 부수 작업.
순서는 의존성의 아래에서 위로 쌓아 각 커밋에서 빌드가 깨지지 않게 한다.
자세한 기준은 `.claude/skills/harness-commit/references/splitting-rules.md`.

커밋 형식:

```text
<type>(<scope>): <summary> (#<ISSUE-NUMBER>)
```

`type`은 **브랜치 prefix와 반드시 같다.** `commit-msg` 훅이
`check_context`로 검사하므로 `feat/...` 브랜치에서 `test(...)` 커밋은 차단된다.
테스트만 따로 커밋하고 싶으면 `test/gh-<N>-<slug>` 브랜치와 이슈를 따로 만든다.

> `docs/harness/CHEATSHEET.md`의 `test(feed): 만료 테스트 추가` 예시는 `feat`
> 브랜치에서는 통과하지 않는다. 훅의 실제 동작이 기준이다.

한 파일 안에 목적이 섞여 있으면 hunk를 자동으로 쪼개지 않는다. `git add -p`는
대화형이라 에이전트가 실행할 수 없으므로, 합치거나 사용자가 직접 분리한다.

## `/harness-pr`

검증 전에 `./harness sync`로 `origin/main`을 rebase한다(0.5 단계). 충돌이
나면 멈추고 사용자에게 보고한다 — 자동으로 해결하지 않는다.

먼저 `./harness pr-ready --project-tests`(규약·훅·라벨·워크플로 검사 +
`./gradlew check` + `git diff --check`)를 돌린다. 실패하면 PR을 만들지 않는다.

PR 제목은 커밋과 형식이 다르다.

```text
PR:     feat: add direction post API              scope 없음
커밋:   feat(direction): add post endpoint (#42)  scope 선택, 이슈 번호 필수
```

본문은 `.github/PULL_REQUEST_TEMPLATE.md`를 채우고 `Closes #<N>`을 반드시 넣는다.
`## 참고`에는 설계·테스트 계획, 실행 증거, 위험, 롤백·복구 절차를 연결한다.

본문 문체는 `.claude/skills/harness-pr/references/writing-style.md`가 강제한다.
서술은 합쇼체(`~합니다`)로 통일하고, 이모지·과장 형용사·인사말·번역투·측정 없는
자기 평가를 쓰지 않는다. PR 템플릿 자체가 합쇼체이므로 채우는 글도 같은 말투를
따른다. 저장소 규범 문서(`AGENTS.md`, `docs/`, 스킬 문서)는 평서체(`~한다`)가
기준이며 이와 구분한다.

체크박스는 실제로 한 것만 체크한다. 실행하지 못한 검증은 이유와 남은 위험을
본문에 적고, 통과했다고 표현하지 않는다.

`type: *` 라벨은 Label Policy 워크플로가 브랜치 접두사에서 자동으로 붙이므로
직접 붙이지 않는다.

인프라 PR은 `@Byuntil`과 `@tkv00` 두 명이 리뷰어로 필수이며, `apply`·deploy는
이 스킬에서 절대 실행하지 않는다.

## 문제 해결

| 증상 | 원인 | 대응 |
| --- | --- | --- |
| `worktree must be clean` | 미커밋 변경 | 커밋하거나 이슈만 생성 |
| `branch is behind origin/main` | `./harness sync` 미실행 | `./harness sync` 실행 후 재시도 |
| rebase 충돌 | origin/main과 로컬 변경 충돌 | 충돌 파일 해결 → `git add` → `git rebase --continue`(또는 `--abort`) |
| `GitHub issue must be open` | 이슈가 닫힘 | 이슈 상태 확인 |
| `commit type does not match branch` | 브랜치와 커밋 type 불일치 | 커밋 type을 브랜치에 맞추거나 별도 브랜치 |
| `TASK.md already exists` | 이전 작업 계약 미커밋 | 커밋 후 `--replace` |
| `gh project` 권한 오류 | `project` 스코프 없음 | `gh auth refresh -s project` |
| `item-edit` ID 오류 | Sprint iteration 변경 | `project-fields.md` 갱신 |
| PR body 검증 실패 | `Closes #N` 누락·불일치 | 브랜치 이슈 번호와 맞춤 |

복구 절차 전반은 `docs/harness/FAILURE_RECOVERY.md`를 따른다.

## 유지보수

다음을 바꿀 때 스킬 문서도 함께 고친다.

| 변경 대상 | 함께 고칠 파일 |
| --- | --- |
| `.github/ISSUE_TEMPLATE/*.yml` | `harness-issue/references/issue-forms.md` |
| `.github/label-catalog.json`, `LABELS.md` | 같은 파일 |
| GitHub Project 필드·옵션 | `harness-issue/references/project-fields.md` |
| `scripts/validate-conventions.py` | `harness-commit/SKILL.md`, `harness-pr/SKILL.md` |
| `.github/PULL_REQUEST_TEMPLATE.md` | `harness-pr/SKILL.md`, `harness-pr/references/writing-style.md` |
| PR 본문 문체·금지 표현 | `harness-pr/references/writing-style.md` |

변경 후 검증:

```bash
./harness check
npm run hooks:validate
```
