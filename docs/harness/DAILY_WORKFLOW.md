# Daily Workflow

## 1. GitHub Issue 확인

Issue의 목표, 완료 조건, 의존성, Project의 Sprint·Status·Priority를 확인한다.
Issue가 없으면 canonical Issue Form으로 먼저 만든다.

## 2. 작업 계약과 브랜치

작업 트리가 깨끗할 때 시작한다.

```bash
git status --short
h start --issue "$GITHUB_ISSUE" --type feat --slug direction-post
h task-init --title "방향 글 API" --replace
```

`TASK.md`에는 목표, 포함·제외 범위, 소유 파일, 기존 변경, 검증 명령, 완료 조건을
기록한다.

`h start`는 최신 `origin/main`에서 분기한다.

## 3. 역할 선택

| 작업 | 먼저 실행 | 승인 후 실행 |
| --- | --- | --- |
| 테스트 | Test Orchestrator | Test Executor |
| AWS/IaC | Infrastructure Orchestrator | Infrastructure Executor |
| 범위/완료 검토 | PM/Reviewer | - |

## 4. 로컬 검증

```bash
./harness check
./gradlew test
npm run hooks:validate
```

PR 전:

```bash
./harness sync
./harness pr-ready --project-tests
git diff --check
git status --short
```

## 5. 커밋

```text
feat(feed): add direction post endpoint (#42)
test(feed): add expiration scenarios (#42)
infra(aws): add baseline plan (#42)
```

짧은 요약만 입력하면 Husky가 branch의 type과 Issue 번호를 붙인다. 각 커밋은
독립적으로 검토 가능한 한 가지 목적만 가진다.

## 6. PR

PR 제목은 `<type>: <summary>`, 본문은 `Closes #<issue>`를 사용한다. 실행한
테스트, 범위와 제외 범위, 보고서, 잠재 문제, 복구·롤백을 함께 기록한다.

병합 후 Project의 Status를 갱신한다. 이월 작업은 새 Issue로 분리하며 다른
사람의 미커밋 변경을 정리하거나 되돌리지 않는다.
