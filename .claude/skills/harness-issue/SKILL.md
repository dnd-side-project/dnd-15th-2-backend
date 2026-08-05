---
name: harness-issue
description: 질문형 대화로 GitHub Issue를 만들고 Project 필드(Sprint·Priority·Status·Work type)와 라벨을 연결한 뒤 규칙에 맞는 작업 브랜치까지 생성한다. "이슈 만들어줘", "이슈 생성", "새 작업 시작", "티켓 만들기" 요청에 사용한다.
---

# Issue Intake

새 작업을 GitHub Issue → Project 필드 → 브랜치 → `TASK.md`까지 한 번에 연결한다.
`AGENTS.md` 1절(작업 시작 게이트)과 8절(라벨 정책)의 실행 절차다.

참조 파일:

- `references/issue-forms.md` — 이슈 유형별 라벨·본문 구조·브랜치 type 매핑
- `references/project-fields.md` — Project 번호와 필드/옵션 ID, 조회 명령

## 0. 사전 점검

```bash
gh auth status
gh repo view --json nameWithOwner
git status --short
git branch --show-current
```

- `gh` 인증이 없으면 사용자에게 `! gh auth login` 실행을 요청하고 중단한다.
- worktree가 더러우면 **브랜치 생성 단계에서만** 막힌다. 이슈 생성까지는 진행할
  수 있으므로, 미리 알리고 "이슈만 생성 / 커밋·stash 후 브랜치까지" 중 선택하게 한다.
- 다른 사람의 변경을 임의로 stash·commit·revert 하지 않는다.

## 1. 이슈 유형 질문

`AskUserQuestion`으로 묻는다. 옵션은 최대 4개이므로 2단계로 나눈다.

**Q1 — 어떤 유형의 이슈인가요?**

| 옵션 | 설명 | 대응 템플릿 |
| --- | --- | --- |
| 기능 작업 | 새 사용자 가치·기능 구현 | `feature.yml` |
| 버그·장애 복구 | 기대 동작과 다른 결함 | `bug.yml` |
| 백엔드 유지보수 | 리팩터링·설정·의존성·성능 | `backend_work.yml` |
| 설계·테스트·인프라 | ADR, 테스트 계획, AWS/IaC | 2단계 질문으로 분기 |

**Q1-b (네 번째를 고른 경우) — 어떤 산출물인가요?**

기술 설계·ADR (`design.yml`) / 테스트 시나리오 (`test-scenario.yml`) /
AWS 인프라 (`infrastructure.yml`)

선택 결과로 `type: *` 라벨과 브랜치 prefix가 결정된다. 매핑은
`references/issue-forms.md`를 그대로 따른다. `type: *` 라벨은 **정확히 하나**만 붙인다.

## 2. 작업 내용 질문

자유 서술이 필요하므로 `AskUserQuestion`이 아니라 일반 질문으로 묻는다.
사용자가 스킬 인자로 이미 설명을 넘겼으면 이 단계를 건너뛰고 그 내용을 사용한다.

> 무슨 작업을 수행하나요? 해결할 문제와 범위를 한두 문장으로 알려주세요.
> (완료 조건이나 제외 범위가 이미 정해졌다면 함께 적어주세요.)

답변이 한 줄이더라도 되묻지 말고, 저장소 코드·`docs/`·관련 이슈를 읽어
초안에서 범위와 완료 조건을 구체화한 뒤 사용자가 초안에서 고치게 한다.

필요하면 `area: *` 라벨을 추론한다(api / database / security / operations).
확신이 서지 않으면 붙이지 않는다 — `area`는 선택이다.

## 3. Project 필드 질문

`AskUserQuestion`으로 한 번에 묻는다(질문 3개, 각 옵션은 실제 필드 옵션에서 가져온다).

1. **Sprint** — `references/project-fields.md`의 iteration 목록. 오늘 날짜가 포함된
   iteration을 첫 옵션으로 두고 "(현재)"를 붙인다. 마지막 옵션은 "지정 안 함".
2. **Priority** — P0 / P1 / P2. 각 옵션에 판단 기준을 설명으로 붙인다.
3. **Status** — Todo(기본) / In Progress. 브랜치까지 만들면 In Progress를 권장한다.

Work type 필드는 1단계 선택에서 자동 결정하므로 묻지 않는다.

## 4. 연결 작업 안내

초안 직전에 앞으로 실행할 작업을 순서대로 보여준다. 예:

```text
아래 작업으로 연결됩니다.
1. GitHub Issue 생성 (라벨: type: feature, area: api)
2. Project "Qello Backend Roadmap"에 item 추가
3. 필드 설정 — Sprint: Week 6 · Core creation / Priority: P1 / Status: In Progress / Work type: Feature
4. 브랜치 생성 — feat/gh-<N>-direction-post   (./harness start)
5. TASK.md 작업 계약 갱신          (./harness task-init)
```

이슈 번호는 생성 후에야 정해지므로 `<N>`으로 표기한다.

## 5. 초안 제시

생성 전에 반드시 전체 초안을 보여주고 승인을 받는다. 승인 없이 `gh issue create`를
실행하지 않는다.

```markdown
제목: [영역] 한 줄 요약
라벨: type: feature, area: api
브랜치: feat/gh-<N>-<slug>

---
## 목적
(문제와 이유 1~3문장)

## 범위
- 포함할 작업 항목

## 완료 조건
- [ ] 검증 가능한 조건

## 백엔드 영향
API:
DB:
권한:
외부 연동:

## 선행 관계
- 없음 또는 #<N>

## 제외
- 이번 이슈에서 다루지 않는 것
```

- 제목은 기존 이슈 관행을 따른다. 접두 태그는 상위 이슈의 하위 작업일 때만 쓴다.
- slug는 `[a-z0-9]`와 `-`만 사용하고 3~5단어로 줄인다. 한글은 로마자로 옮기지 않고
  영어 키워드로 바꾼다.
- 사용자가 수정을 요청하면 반영한 초안을 다시 보여주고 재승인을 받는다.

## 6. 생성과 연결

승인 후 순서대로 실행한다. 각 단계가 실패하면 즉시 멈추고, 어디까지 반영됐는지
보고한다.

```bash
# 1) 이슈 생성 (본문은 파일로 넘겨 따옴표 이스케이프 문제를 피한다)
gh issue create --title "<제목>" --body-file <초안파일> \
  --label "type: feature" --label "area: api"
```

초안 파일은 scratchpad에 쓰고 저장소에는 남기지 않는다.

```bash
# 2) Project item 추가 — 출력 JSON의 id가 item ID다
gh project item-add 141 --owner dnd-side-project --url <이슈 URL> --format json
```

```bash
# 3) 필드 설정 (Sprint/Priority/Status/Work type)
gh project item-edit --id <ITEM_ID> --project-id <PROJECT_ID> \
  --field-id <FIELD_ID> --single-select-option-id <OPTION_ID>

# Sprint는 iteration 필드이므로 옵션이 아니라 iteration ID를 넘긴다
gh project item-edit --id <ITEM_ID> --project-id <PROJECT_ID> \
  --field-id <SPRINT_FIELD_ID> --iteration-id <ITERATION_ID>
```

ID는 `references/project-fields.md`에 캐시돼 있다. `item-edit`이 ID 오류로 실패하면
같은 문서의 조회 명령으로 최신 ID를 다시 읽고 캐시를 갱신한 뒤 재시도한다.

```bash
# 4) 브랜치 생성 + 작업 계약
./harness start --issue <N> --type feat --slug <slug>
./harness task-init --title "<이슈 제목>" --replace
```

`./harness start`는 실행 전에 `git fetch origin main`을 수행해 항상 최신
`origin/main`에서 브랜치를 만들고, 가능하면(순수 fast-forward일 때만) 로컬
`main`도 같이 갱신한다.

`--replace`는 기존 `TASK.md`가 커밋된 상태에서만 통과한다. 실패하면 덮어쓰지 말고
사용자에게 알린다.

## 7. 완료 보고

이슈 URL, 라벨, 설정된 Project 필드, 현재 브랜치, `TASK.md` 갱신 여부를 보고한다.
건너뛴 단계가 있으면 이유와 함께 명시한다.

이어서 할 일은 제안만 하고 자동 실행하지 않는다: 테스트 계획이 필요하면
`/harness-test-plan`, 구현 후 커밋은 `/harness-commit`.

## 금지

- 승인 없이 이슈를 생성하거나 브랜치를 만들지 않는다.
- Sprint·Priority·상태를 GitHub **라벨**로 만들지 않는다. Project 필드로만 관리한다.
- `.env` 값, 토큰, URL, 계정·IAM 식별자를 이슈 본문에 쓰지 않는다.
- 이슈 없이 구현을 시작하지 않는다.
