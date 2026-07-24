# Daily Workflow

## 1. Jira와 Issue

Jira 티켓의 목표, 완료 조건, 의존성을 읽는다. GitHub Issue가 없다면 해당
템플릿으로 만들고 Jira 키를 제목에 연결한다. Jira와 Issue 링크를 서로 남긴다.

## 2. 작업 계약

`TASK.md`에 다음을 기록한다.

- 목표
- 포함/제외 범위
- 수정할 파일
- 다른 작업자가 이미 수정 중인 파일
- 검증 명령
- 완료 조건

## 3. 브랜치

작업 트리가 깨끗할 때만 생성한다.

```bash
h start --jira "$JIRA_KEY" --issue "$GITHUB_ISSUE" --type feat \
  --slug feature-name --confirm-jira-linked
h task-init --title "새 작업 제목" --replace
```

## 4. 에이전트 선택

| 작업 | 먼저 실행 | 승인 후 실행 |
| --- | --- | --- |
| 테스트 | Test Orchestrator | Test Executor |
| AWS/IaC | Infrastructure Orchestrator | Infrastructure Executor |
| 범위/완료 검토 | PM/Reviewer | - |

오케스트레이터의 계획을 승인하지 않은 상태에서 실행 에이전트가 대량 구현하지
않는다.

## 5. 로컬 검증

작업 중:

```bash
hc
./gradlew test
npm run hooks:validate
```

PR 전:

```bash
hpr
git diff --check
git status --short
```

로컬에서는 Husky가 네 단계로 자동 실행된다.

| 시점 | 검사 | 목적 |
| --- | --- | --- |
| commit 전 | branch, staged diff, staged secret, 관련 정책 | 빠른 실패 |
| commit 제목 준비 | branch 기반 type/Jira/Issue 자동 추가 | 입력 부담 감소 |
| commit message | Jira/Issue가 포함된 커밋 형식 | 추적성 |
| push 전 | 전체 하네스와 Gradle `check` | 원격 전 회귀 방지 |

Hook이 실행되지 않는 GUI 환경은 `~/.config/husky/init.sh`에서 Node/Python
PATH를 초기화한다. 상세 내용은 `docs/harness/HUSKY.md`를 따른다.

## 6. 커밋 분리

권장 순서:

1. `chore(tooling)` — Mac 실행기와 설정
2. `docs(harness)` — 역할, 치트시트, 아키텍처
3. `ci(policy)` — GitHub 템플릿과 검증
4. `test(...)` 또는 `infra(...)` — 실제 기능별 증거

각 커밋은 독립적으로 검토 가능한 목적을 가져야 한다.

```text
chore(tooling): [$JIRA_KEY] add macOS harness commands (#$GITHUB_ISSUE)
docs(harness): [$JIRA_KEY] document agent architecture (#$GITHUB_ISSUE)
ci(policy): [$JIRA_KEY] enforce Jira and infrastructure gates (#$GITHUB_ISSUE)
```

## 7. PR

PR에는 다음을 포함한다.

- `Closes #<issue>`
- Jira 링크
- 범위와 제외 범위
- 실행한 테스트/정적 검사
- 테스트 또는 인프라 보고서
- 잠재 문제와 후속 Jira
- 복구/롤백

## 8. 작업 종료

PR이 병합되면 Jira를 완료로 변경하고 보고서와 결정을 연결한다. 이월 작업은
새 Jira/Issue로 분리한다. 로컬에서 다른 사람의 미커밋 변경을 정리하지 않는다.
