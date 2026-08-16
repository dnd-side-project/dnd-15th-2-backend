---
name: harness-pr
description: 로컬 검증을 실행하고 질문형으로 PR 옵션을 정한 뒤 저장소 PR 템플릿과 규칙(<type>: <summary> + Closes #N)에 맞는 초안을 보여주고, 승인 후 push와 PR 생성까지 수행한다. "PR 올려줘", "PR 만들어줘", "풀리퀘 생성" 요청에 사용한다.
---

# Pull Request

브랜치 작업을 검증 → 초안 → push → PR 생성 순으로 마무리한다.
`AGENTS.md` 6절(커밋과 PR)과 9절(완료 전 검증)의 실행 절차다.

참조: `references/writing-style.md` — PR 본문 문체 규칙과 금지 표현 목록

## 0. 컨텍스트 수집

```bash
git branch --show-current
git status --short
./harness base
git log "origin/$(./harness base)..HEAD" --oneline
git diff "origin/$(./harness base)...HEAD" --stat
gh pr status
```

- 브랜치가 `<type>/gh-<ISSUE>-<slug>`가 아니면 중단한다.
- `main`에서는 PR을 만들지 않는다.
- `./harness base`는 보통 `main`이지만, `./harness start --base <브랜치>`로 만든
  stacked 브랜치라면 그 부모 브랜치를 가리킨다(예: PR #46처럼 누적 브랜치 위에
  쌓는 경우). 이후 단계의 `origin/main`은 전부 이 값으로 바꿔서 읽는다.
- 이미 열린 PR이 있으면 새로 만들지 말고 "push만 하고 기존 PR 갱신"을 제안한다.
- 미커밋 변경이 있으면 알리고 `/harness-commit`으로 먼저 커밋할지 묻는다.
  임의로 커밋하지 않는다.
- 커밋이 하나도 없으면 중단한다.

이슈 내용을 함께 읽어 PR 본문의 근거로 쓴다.

```bash
gh issue view <ISSUE> --json title,body,labels,url
```

## 0.5 최신화

PR을 올리기 전에 base 브랜치(`./harness base`, 보통 `main`)를 rebase로 반영한다.

```bash
./harness sync
```

- 충돌이 없으면 다음 단계로 진행한다.
- 충돌이 나면 **여기서 멈춘다.** 출력된 충돌 파일 목록을 그대로 사용자에게
  보고하고 직접 해결해 달라고 요청한다. 사용자 지시 없이 임의로 충돌을
  해결하거나 `git rebase --continue`를 대신 실행하지 않는다.
- 사용자가 충돌을 해결하고 `git rebase --continue`까지 마쳤다고 확인해주면
  이 단계를 다시 확인한 뒤 진행한다.

## 1. 로컬 검증

PR 생성 전에 반드시 실행한다.

```bash
./harness pr-ready --project-tests
```

`./harness check`(규약·훅·라벨·워크플로 검사) + `./gradlew check` + `git diff --check`를
한 번에 돌린다. 시간이 오래 걸리므로 백그라운드 실행을 고려한다.

실패하면 **PR을 만들지 않고 멈춘다.** 실패 로그를 그대로 보고하고 지시를 받는다.
검증을 건너뛰기로 사용자가 결정하면 그 사실과 남은 위험을 PR 본문 `## 참고`에
명시한다 — 실행하지 않은 검증을 통과했다고 쓰지 않는다.

인프라 변경이 포함되면 추가로 실행한다.

```bash
terraform fmt -check && terraform validate && terraform plan
```

## 2. 옵션 질문

`AskUserQuestion`으로 한 번에 묻는다.

1. **PR 상태** — Ready for review(기본) / Draft
   Draft에는 `status: needs-review`가 붙지 않는다.
2. **리뷰어** — `@Byuntil` / `@tkv00` / 둘 다 / 지정 안 함
   인프라 변경이면 두 명 모두가 필수이므로 이 질문을 건너뛰고 둘 다 지정한다.
3. **Project Status 갱신** — In Progress 유지 / 지정 안 함
   PR은 아직 머지 전이므로 Done으로 바꾸지 않는다.

## 3. 초안 제시

생성 전에 제목·본문 전체를 보여주고 승인을 받는다.

**제목** — `scripts/validate-conventions.py`의 `PR_RE`가 강제한다.

```text
<type>: <summary>
```

- `type`은 **브랜치 prefix와 같아야 한다**(`feature` → `feat`).
- **PR 제목에는 scope를 쓰지 않는다.** 커밋과 달리 `feat(direction): ...`은 통과하지
  못한다.
- summary는 **한글로** 쓴다. 실제 저장소 PR 이력이 한글 summary가 기본값임을
  보여준다(예: `feat: 방향 매칭 워커와 원자적 수신자 확정 구현`, `feat: 답변
  moderation 재시도와 소진 시 수동 검토 인계 추가`) — 영어 summary는 예외로만
  쓴다.
- summary는 브랜치 전체 작업을 한 줄로 요약한다.

**본문** — `.github/PULL_REQUEST_TEMPLATE.md` 구조를 그대로 채운다.

본문을 쓰기 전에 `references/writing-style.md`를 읽고 그 규칙을 적용한다.
서술은 합쇼체(`~합니다`, `~했습니다`, `~입니다`)로 통일하고, 같은 문서의 금지
표현 표에 있는 이모지·과장 형용사·인사말·번역투·자기 평가를 쓰지 않는다.
초안을 사용자에게 보여주기 전에 그 문서의 점검 목록을 통과시킨다.

```markdown
## 관련 이슈

Closes #39

## 라벨

- [x] 브랜치 유형에 맞는 `type: *` 라벨이 정확히 하나 있습니다.
- [ ] 필요한 경우 `area: *` 또는 `status: *` 라벨을 추가했습니다.

## 작업 내용

- 커밋 단위로 무엇을 왜 했는지

## 테스트

- [x] 단위 또는 통합 테스트를 실행했습니다.
- [x] 기존 기능에 미치는 영향을 확인했습니다.

실행 결과: ./harness pr-ready --project-tests 통과

## 참고

- 설계·테스트 계획: docs/test-plans/gh-39-...md
- 실행 증거: docs/reports/tests/gh-39-...md
- 위험과 롤백: (되돌리는 방법, 남은 위험)
- API·DB·설정 또는 문서 변경: (없으면 N/A)
```

- `Closes #<N>`은 필수다. 브랜치의 이슈 번호와 반드시 같아야 한다.
- 체크박스는 **실제로 한 것만** 체크한다. 하지 않은 검증을 체크하지 않는다.
- `## 참고`에 설계·테스트 계획, 실행 증거, 위험, 롤백·복구 절차를 연결한다
  (`AGENTS.md` 6절). 해당 없으면 N/A로 남긴다.

## 4. push와 생성

```bash
git push -u origin <브랜치>
```

일반 force push는 쓰지 않는다. 단, 0.5 단계의 `./harness sync`로 이미
push된 브랜치를 rebase한 직후라면 origin이 non-fast-forward로 거부하는
것이 정상이다. 이 경우에 한해 다음을 사용한다.

```bash
git push --force-with-lease -u origin <브랜치>
```

`main`이나 다른 사람과 공유하는 브랜치에는 이 명령을 쓰지 않는다. 위 두
경우가 아닌데 push가 거부되면 원인을 확인하고 사용자에게 보고한다.

```bash
gh pr create --base "$(./harness base)" --title "<제목>" --body-file <초안파일> \
  --reviewer Byuntil --reviewer tkv00
# Draft이면 --draft 추가
```

대부분 `./harness base`는 `main`이다. stacked 브랜치의 PR은 부모 브랜치를
가리키므로, 부모 브랜치의 PR이 먼저 머지된 뒤에야 `main`으로 다시 PR을
연다(PR #46 → #47 순서).

초안 파일은 scratchpad에 쓰고 저장소에는 남기지 않는다.

`type: *` 라벨은 Label Policy 워크플로가 브랜치 접두사에서 자동으로 붙인다.
직접 붙이지 않는다. `area: *`가 필요하면 생성 후 추가한다.

```bash
gh pr edit <PR번호> --add-label "area: database"
```

## 5. 상태 확인

```bash
gh pr view --json number,url,isDraft,labels,reviewDecision
gh pr checks --watch     # 필요할 때만. 오래 걸리면 사용자에게 알린다
```

Project Status를 In Progress로 유지하기로 했다면 이슈 item이 이미 그 상태인지
확인만 하고, 다른 값이면 사용자 확인 후 변경한다. 필드 ID는
`.claude/skills/harness-issue/references/project-fields.md`에 있다.

## 6. 완료 보고

PR URL, 제목, Draft 여부, 라벨, 리뷰어, 실행한 검증과 결과, CI 상태를 보고한다.
실행하지 못한 검증은 이유와 남은 위험을 함께 명시한다.

리뷰가 필요하면 `/harness-review`를 제안한다. 머지는 사람이 한다.

## 금지

- 승인 없이 push하거나 PR을 생성하지 않는다.
- 검증 실패를 숨기거나 실행하지 않은 테스트를 통과로 보고하지 않는다.
- 일반 force push, `main` 직접 push, PR 자동 머지를 하지 않는다.
  `./harness sync`로 rebase한 직후 본인 feature 브랜치에 한해
  `--force-with-lease`만 예외로 허용한다.
- PR 본문에 반말·평서체(`~한다`)를 쓰지 않는다. 합쇼체로 통일한다.
- `references/writing-style.md`의 금지 표현을 쓰지 않는다.
- 인프라 PR에서 `terraform apply`·CDK deploy를 실행하지 않는다.
  적용은 `@Byuntil`과 `@tkv00`의 승인과 사람의 workflow dispatch가 모두 필요하다.
- PR 본문에 `.env` 값, 토큰, URL, 계정·IAM 식별자를 쓰지 않는다.
