---
name: "harness-commit"
description: "\ud604\uc7ac \ubcc0\uacbd\uc744 \uac80\ud1a0 \ubaa9\uc801 \ub2e8\uc704\ub85c \ub098\ub208 \ucee4\ubc0b \uacc4\ud68d \ucd08\uc548\uc744 \ubcf4\uc5ec\uc8fc\uace0, \uc2b9\uc778 \ud6c4 \uc800\uc7a5\uc18c \ucee4\ubc0b \uaddc\uce59(<type>(<scope>): <summary> (#<ISSUE>))\uc5d0 \ub9de\ucdb0 \uc21c\uc11c\ub300\ub85c \ucee4\ubc0b\ud55c\ub2e4. \"\ucee4\ubc0b\ud574\uc918\", \"\ucee4\ubc0b \ub098\ub220\uc918\", \"\ubcc0\uacbd\uc0ac\ud56d \uc815\ub9ac\ud574\uc11c \ucee4\ubc0b\" \uc694\uccad\uc5d0 \uc0ac\uc6a9\ud55c\ub2e4."
---

# Commit Split

작업 변경을 **하나의 검토 목적 = 하나의 커밋**으로 나눈다(`AGENTS.md` 6절).
분할 초안을 먼저 보여주고 승인을 받은 뒤에만 커밋한다.

참조: `references/splitting-rules.md` — 분할 기준, 순서, 타입·scope 선정

## 0. 컨텍스트 수집

```bash
git branch --show-current
git status --short
git diff --stat
git diff --cached --stat
git log --oneline -10
```

브랜치가 `<type>/gh-<ISSUE>-<slug>`가 아니면 중단한다. 커밋 타입과 이슈 번호는
브랜치에서 파생하며 임의로 정하지 않는다. 브랜치가 규칙에 맞지 않으면
`/harness-issue`로 이슈·브랜치를 먼저 만들도록 안내한다.

이미 staged된 변경이 있으면 그대로 커밋하지 말고 분할 계획에 포함시킨다.
계획 확정 전에는 `git reset`으로 staging을 풀지 않는다 — 사용자가 의도적으로
staging한 것일 수 있으므로 계획에 반영한 뒤 승인 단계에서 알린다.

## 1. 변경 내용 파악

`git diff`와 `git diff --cached`의 **전체 본문**을 읽는다. `--stat`만 보고
목적을 추측하지 않는다. 파일이 많으면 디렉터리 단위로 나눠 읽는다.

새 파일은 `git status`의 `??` 항목을 `Read`로 확인한다. 빌드 산출물,
`.env`, 로컬 설정처럼 커밋하면 안 되는 파일이 섞였으면 계획에서 제외하고
사용자에게 알린다(`.gitignore` 수정은 별도 제안).

## 2. 커밋 분할

기본 원칙:

- **반드시 나눈다.** 변경이 두 가지 이상 목적을 담으면 목적 수만큼 커밋을 만든다.
- 목적이 진짜 하나뿐이면 한 커밋으로 두되, **왜 나누지 않았는지 근거를 함께 보고한다**
  ("단일 파일의 단일 버그 수정" 등). 나누지 않는 것이 기본값이 되어서는 안 된다.
- 각 커밋은 그 자체로 컴파일·테스트가 깨지지 않는 순서로 배치한다.
- 파일 단위로 나눈다. 한 파일 안에 서로 다른 목적이 섞여 있으면 hunk를 임의로
  쪼개지 말고, 그 사실을 보고한 뒤 (a) 한 커밋으로 합치거나 (b) 사용자가
  `git add -p`로 직접 분리하도록 선택하게 한다.

분할 기준과 순서는 `references/splitting-rules.md`를 따른다.

## 3. 커밋 메시지 작성

형식(`scripts/validate-conventions.py`가 강제):

```text
<type>(<scope>): <summary> (#<ISSUE-NUMBER>)
```

- `type` — **브랜치 prefix와 반드시 같아야 한다.** `feature`는 `feat`으로 정규화한다.
- `scope` — 선택. 소문자·숫자·하이픈만. 도메인이나 계층 이름을 쓴다
  (`direction`, `persistence`, `harness`, `database`).
- `summary` — 명령형 현재시제 한 줄. 마침표 없음. 기존 로그는 영어가 다수이지만
  한국어도 통과한다. **해당 브랜치의 기존 커밋 언어를 따른다.**
- `(#<ISSUE>)` — 필수. 브랜치의 이슈 번호와 다르면 `commit-msg` 훅이 차단한다.
- 본문(body)은 왜 필요한지가 제목만으로 부족할 때만 추가한다.

## 4. 초안 제시

커밋 실행 전에 전체 계획을 표로 보여주고 승인을 받는다.

```text
브랜치: feat/gh-39-direction-postgis   (type=feat, issue=#39)

1. feat(direction): model direction sector and post aggregates (#39)
   - src/main/java/.../direction/domain/DirectionSector.java
   - src/main/java/.../direction/domain/DirectionPost.java
   이유: 도메인 모델 정의 — 어댑터 없이 단독 검토 가능

2. feat(direction): add repository ports and JDBC PostGIS adapters (#39)
   - src/main/java/.../direction/port/DirectionPostRepository.java
   - src/main/java/.../direction/adapter/JdbcDirectionPostRepository.java
   이유: 1의 도메인에 의존하는 영속화 경계

3. test(direction): verify PostGIS persistence with Testcontainers (#39)
   ...
```

각 커밋에 **포함 파일과 분할 이유**를 함께 적는다. 사용자가 순서·묶음·문구를
수정하면 반영한 계획을 다시 보여주고 재승인을 받는다.

> 커밋 타입이 브랜치와 달라야 하는 변경(예: `feat` 브랜치의 순수 테스트 커밋)이
> 있으면 초안에서 알린다. 훅이 차단하므로 별도 이슈·브랜치로 분리해야 한다.

## 5. 순차 커밋

승인 후 커밋마다 다음을 반복한다.

```bash
git add -- <파일1> <파일2>
git status --short          # 의도한 파일만 staged 되었는지 확인
git commit -m "<완성된 메시지>"
```

- `git add .`나 `git add -A`를 쓰지 않는다. 경로를 명시한다.
- 완성형 메시지를 넘기면 `prepare-commit-msg` 훅이 그대로 유지한다. 요약만 넘기면
  훅이 브랜치 컨텍스트로 조립하지만, 이 스킬은 항상 완성형으로 넘긴다.
- 훅(`pre-commit`, `commit-msg`)이 실패하면 **즉시 멈춘다.** 훅을 우회하는
  `--no-verify`는 쓰지 않는다. 실패 원인과 남은 커밋 목록을 보고하고 지시를 받는다.
- 커밋을 되돌리거나 `git commit --amend`로 남의 커밋을 고치지 않는다.

## 6. 완료 보고

```bash
git log --oneline -<커밋수>
git status --short
```

생성한 커밋 목록, 남은 미커밋 변경, 실행하지 못한 검증을 보고한다.
`push`와 PR 생성은 이 스킬의 범위가 아니다 — `/harness-pr`을 제안만 한다.

## 금지

- 승인 없이 커밋하지 않는다.
- `--no-verify`, `--amend`, `git reset --hard`, force push를 쓰지 않는다.
- 여러 목적을 한 커밋에 몰아넣지 않는다.
- 커밋 메시지에 `.env` 값, 토큰, URL, 계정·IAM 식별자를 쓰지 않는다.
- 사용자나 다른 에이전트의 미커밋 변경을 임의로 stash·폐기하지 않는다.
