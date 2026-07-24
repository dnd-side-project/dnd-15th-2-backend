# MIR-82 Harness Engineering Contract

> 이 파일은 저장소 전체에 고정되는 설정이 아니라 현재 작업 브랜치의 계약이다.
> 새 Jira 작업을 시작할 때 `h task-init`으로 현재 브랜치의 Jira 키와 GitHub
> Issue 번호를 반영해 교체한다. 전역 컨벤션은 `AGENTS.md`에만 둔다.

## Objective

MacBook을 사용하는 두 명의 백엔드 개발자가 Jira, GitHub, Claude Code,
Codex를 같은 규칙으로 운용하도록 저장소 기반 하네스를 구성한다.

## Work gate

- Jira: `MIR-82`
- Parent Jira: `MIR-66`
- GitHub Issue: `#1`
- Expected branch:
  `chore/MIR-82-gh-1-macos-harness`
- Jira가 일정·우선순위·상태·범위의 기준이다.
- GitHub Issue와 PR은 구현 과정과 검증 증거를 남긴다.

## Scope

- macOS 설치 진단, Homebrew 기반 도구 설치, zsh 단축 명령과 자동완성
- 테스트 및 AWS 인프라의 오케스트레이터/실행자 역할 분리
- Claude Code와 Codex가 공유하는 저장소 계약과 역할별 명령
- 구성 가능한 논리 모델 프로필
- JUnit 5 테스트 메타데이터, `@DisplayName`, 단위·통합 테스트 정책
- 테스트 보고서, AWS 설계 보고서, 의사결정 기록 템플릿
- Jira 키, GitHub Issue, 브랜치, 커밋, PR 규칙과 비밀값 사전 검사
- Terraform 설계/plan과 apply의 분리 및 수동 승인 게이트
- 전체 설명, 치트시트, 아키텍처, 일상 사용, 실패 복구 문서
- Husky 기반 `pre-commit`, `commit-msg`, `pre-push` 로컬 Git Hook
- staged 파일 전용 비밀정보·메타데이터 검사와 CI 이중 검증

## Explicit exclusions

- AWS 리소스를 생성·변경·삭제하지 않는다.
- Terraform apply, 배포, 운영 변경은 실행하지 않는다.
- 제품 API, DB 스키마, 도메인 동작은 변경하지 않는다.
- 모델 공급자의 실제 모델 ID를 저장소에 고정하지 않는다.
- Secret, 계정 ID, IAM ID/ARN, 서버 주소, 토큰, `.env` 값은 기록하지
  않는다.
- GitHub Ruleset, 보호 환경, 필수 리뷰가 저장소 파일만으로 활성화됐다고
  주장하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Shared harness and documents | PM/reviewer | Backend owners |
| Test plan | Test orchestrator | PM/reviewer |
| Test implementation/report | Test executor | Test orchestrator |
| AWS design and IaC | Infrastructure executor | `@Byuntil`, `@tkv00` |
| Infrastructure apply | Human operator only | Both reviewers and protected environment |

## Existing user-owned changes

The following concurrent local changes were present before harness files were
created and must not be staged or overwritten by this task:

- `.github/ISSUE_TEMPLATE/backend_work.yml`
- `.github/PULL_REQUEST_TEMPLATE.md`
- `.github/scripts/jira-common.js`
- `.github/scripts/sync-backend-issue.js`
- `.github/scripts/sync-backend-pr.js`
- `.github/workflows/jira-backend-issue.yml`
- `.github/workflows/jira-backend-pr.yml`
- `.github/workflows/jira-sync.yml`
- `.dockerignore`

The later Jira branch-automation request explicitly brings the listed Jira
Issue/PR templates, scripts, and workflows into the Jira-sync commit so the old
split workflows can be replaced atomically. `.dockerignore` remains excluded
and must not be staged.

## Validation

```bash
./harness doctor
./harness check
./gradlew test
npm run hooks:validate
git diff --check
```

Infrastructure validation is plan-only:

```bash
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
terraform plan
```

`terraform apply` is not an ordinary validation step.

## Completion criteria

- A new developer can complete macOS setup from
  `docs/harness/MACOS_SETUP.md`.
- `h` provides short commands for gate checks, plans, reports, and validation.
- Claude Code and Codex follow the same role contracts.
- Test classes fail policy validation when timestamp, scenario ID, or
  `@DisplayName` is missing.
- Infrastructure apply remains disabled unless all external and human gates are
  satisfied.
- The requested documentation and templates exist and contain no sensitive
  values.
- Only files owned by `MIR-82` are staged in its commits.
- `npm ci` 또는 `npm install` 후 Husky Hook이 자동 설치된다.
- commit 전에 branch/staged secret/테스트·workflow 정책을 빠르게 검사한다.
- commit message와 push 전에 저장소 컨벤션 및 전체 테스트를 검사한다.
- `--no-verify` 우회와 무관하게 GitHub Actions가 동일한 핵심 규칙을 재검증한다.

## Dynamic Jira context follow-up

### Objective

- 하네스는 특정 Jira 키나 스프린트에 종속되지 않는다.
- 현재 Jira 키와 GitHub Issue 번호는 작업 브랜치에서 파생한다.
- branch, commit, PR title, PR body의 Jira/Issue 문맥이 서로 일치해야 한다.
- 새 작업은 기존 `TASK.md`를 명시적으로 새 계약으로 교체할 수 있다.

### Owned files

- `scripts/validate-conventions.py`
- `scripts/harness.py`
- `.github/workflows/harness-policy.yml`
- `templates/task-contract.md`
- `AGENTS.md`, `CODEX.md`
- `docs/harness/`
- `shell/harness.zsh`, `completions/_harness`

### Validation

```bash
python3 scripts/validate-conventions.py --self-test
python3 scripts/validate-conventions.py \
  --branch "feat/PAY-314-gh-42-dynamic-jira" \
  --commit "feat(harness): [PAY-314] use branch context (#42)" \
  --pr-title "[PAY-314] feat: use branch context" \
  --pr-body "Closes #42"
./harness context
npm run hooks:validate
```

### Completion criteria

- `MIR-82` 외 Jira 키가 모든 컨벤션 검사에서 통과한다.
- branch와 commit/PR의 Jira 키 또는 Issue 번호가 다르면 실패한다.
- 오류 메시지와 공통 문서가 특정 Jira 키를 정답처럼 제시하지 않는다.
- `h task-init`이 현재 branch 문맥으로 `TASK.md` 초안을 생성한다.

## Automatic commit message follow-up

### Objective

- 개발자는 `git commit -m "요약"`만 입력해도 규칙형 메시지를 생성할 수 있다.
- type, Jira 키, GitHub Issue는 현재 branch에서 파생한다.
- `type(scope): 요약`을 입력하면 type과 scope는 유지하고 Jira/Issue를 채운다.
- 이미 완성된 메시지는 변경하지 않으며 잘못된 Jira/Issue는 기존 검증으로
  차단한다.

### Owned files

- `.husky/prepare-commit-msg`
- `scripts/format-commit-msg.py`
- `scripts/run-hook.py`
- `scripts/validate-husky.py`
- `package.json`
- `AGENTS.md`
- `docs/harness/HUSKY.md`
- `docs/harness/CHEATSHEET.md`

### Exclusions

- 커밋 내용을 AI가 분류하거나 요약하지 않는다.
- scope를 branch slug에서 임의 추측하지 않는다.
- 완성된 커밋 메시지의 Jira 키나 Issue 번호를 조용히 교체하지 않는다.

### Validation

```bash
python3 scripts/format-commit-msg.py --self-test
npm run hooks:validate
git commit -m "커밋 메시지 자동 조립 구현"
```

### Completion criteria

- 짧은 한글 메시지가 branch 문맥을 포함한 커밋 제목으로 변환된다.
- `type(scope): 요약` 형식은 type과 scope를 보존한다.
- 완성된 규칙형 메시지는 그대로 유지한다.
- 다른 Jira 키나 Issue가 포함된 완성 메시지는 `commit-msg`에서 실패한다.
- 메시지 본문과 Git comment는 그대로 유지한다.

## Jira-generated branch follow-up

### Objective

- GitHub Issue에서 Jira 하위 티켓을 만들 때 생성되는 branch도 저장소 하네스
  규칙을 따른다.
- 자동 생성 branch는 Jira 하위 티켓 키와 GitHub Issue 번호를 함께 포함한다.
- 영문 제목은 안전한 kebab-case slug로 변환하고, 영문 slug를 만들 수 없는
  제목은 `issue-<number>`를 사용한다.

### Contract

```text
<type>/<JIRA-KEY>-gh-<ISSUE-NUMBER>-<short-slug>
```

예:

```text
feat/MIR-123-gh-42-order-create
feat/MIR-123-gh-42-issue-42
```

`config` 작업은 현재 허용된 branch/commit type과 충돌하지 않도록 `chore`로
매핑한다. 알 수 없는 작업 유형도 임의의 `task` type을 만들지 않고 `chore`를
사용한다.

### Owned files

- `.github/workflows/jira-sync.yml`
- `scripts/validate-workflows.py`

### Validation

```bash
python3 scripts/validate-workflows.py .github/workflows/jira-sync.yml
python3 scripts/validate-conventions.py \
  --branch "feat/MIR-123-gh-42-order-create"
```

### Completion criteria

- 자동 생성 branch가 Husky와 CI의 branch 검증을 통과한다.
- Jira 키나 GitHub Issue 번호는 고정하지 않고 이벤트에서 가져온다.
- 한글 제목만 있는 Issue도 유효한 fallback branch를 생성한다.
