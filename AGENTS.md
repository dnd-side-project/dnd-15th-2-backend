# Qello Repository Agent Contract

이 문서는 Qello 저장소에서 동작하는 사람과 AI 에이전트의 공통 계약이다.
도구별 지침보다 이 문서와 현재 브랜치의 `TASK.md`가 우선한다.

## 1. 작업 시작 게이트

구현 전에 다음 항목을 모두 충족한다.

1. 백로그와 스프린트는 GitHub Project draft item으로 관리한다.
2. 실제 구현을 시작할 draft item만 repository Issue로 변환한다.
3. Issue에서 범위와 완료 조건을 확인하고 `TASK.md`에 계약을 기록한다.
4. GitHub Issue 번호가 포함된 브랜치에서 작업한다.
5. 작업 시작 시 `git status --short`를 확인하고 기존 변경을 보존한다.

브랜치 형식:

```text
<type>/gh-<ISSUE-NUMBER>-<short-slug>
```

예:

```text
feat/gh-42-direction-post
infra/gh-77-aws-baseline
```

Issue가 없으면 Project draft item의 계획·분해까지만 허용한다. 구현, 인프라 변경,
배포, PR 생성은 중지한다. 일정, 스프린트, 우선순위, 상태는 Jira가 아니라
GitHub Projects의 필드로 관리한다.

## 2. 역할 분리

- 오케스트레이터는 시나리오, 작업 분해, 위험, 인수 조건을 설계한다.
- 실행 에이전트는 승인된 계획의 파일만 구현하고 검증한다.
- PM/리뷰어는 Issue 범위, 증거, 승인 조건을 점검한다.
- 실행 에이전트가 임의로 범위를 넓히거나 인프라를 적용해서는 안 된다.

상세 역할은 `agents/` 문서를 따른다.

## 3. 테스트 규칙

- JUnit 5를 사용한다.
- 단위 테스트와 통합 테스트를 분리한다.
- 모든 테스트 메서드에 `@DisplayName`을 작성한다.
- 모든 테스트 클래스 상단에 정확한 ISO 8601 생성 시각과 원본 테스트 계획
  식별자를 기록한다.
- 테스트 후 애플리케이션, 인프라, DB, 동시성, 트랜잭션, 외부 API, 장애 복구
  관점의 잠재 문제를 분석한다.
- 보고서는 `templates/test-report.md`에서 생성한다.
- `.env` 값, 토큰, URL, 계정 식별자 등 민감정보를 기록하지 않는다.

테스트 클래스 헤더 예:

```java
/**
 * Created at: 2026-08-03T12:00:00+09:00
 * Source scenario: TEST-PLAN-GH-42-DIRECTION-UNIT-001
 */
```

## 4. 인프라 규칙

- AWS 우선, Terraform 또는 AWS CDK를 사용한다.
- 초기 설계는 가장 낮은 실용 사양의 단일 애플리케이션 워크로드와 저비용
  RDS를 기준으로 한다.
- EC2와 ECS, 관리형과 자체 운영 대안을 문서에서 비교한다.
- 설계, 비용 가정, plan 결과를 PR에서 검토한 뒤에만 적용한다.
- `terraform apply`, CDK deploy, 프로덕션 변경은 기본적으로 금지한다.
- 적용 전 `@Byuntil`, `@tkv00`의 명시적 승인과 사람의 workflow dispatch 확인이
  모두 필요하다.
- 최소 권한 IAM과 GitHub OIDC를 사용하며 장기 액세스 키를 만들거나 저장하지
  않는다.
- 비밀 키, 서버 주소, IAM ID, AWS 계정 ID, 토큰, `.env` 값은 코드, Issue, PR,
  보고서, 로그, 예시에 기록하지 않는다.

`CODEOWNERS`는 리뷰어 요청만 수행한다. 실제 강제는 GitHub Ruleset과
`infrastructure-apply` Environment 보호 규칙으로 설정한다.

## 5. 변경 안전성

- 사용자 또는 다른 에이전트가 수정한 파일을 덮어쓰지 않는다.
- 넓은 파일 변경, 삭제, 인프라 적용, 프로덕션 변경은 명시적 사람 확인 없이는
  실행하지 않는다.
- 원본 원장, 마이그레이션 이력, 운영 감사 이력을 수정하거나 삭제하지 않는다.
- 명령은 재실행에 안전하게 만들고 실패 시 부분 반영을 피한다.

## 6. 커밋과 PR

커밋 형식:

```text
<type>(<scope>): <summary> (#<ISSUE-NUMBER>)
```

scope는 선택이며 Issue 번호는 필수다.

```text
feat(feed): add direction post endpoint (#42)
test(feed): add expiration scenarios (#42)
chore(harness): remove Jira integration (#6)
```

PR 제목과 본문:

```text
<type>: <summary>
Closes #<ISSUE-NUMBER>
```

branch, commit, PR body의 Issue 번호가 일치해야 한다. branch와 commit/PR의
type도 일치해야 한다. PR에는 설계 또는 테스트 계획, 실행 증거, 위험,
롤백·복구 절차를 연결한다. 하나의 커밋에는 하나의 검토 목적만 담는다.

새 작업은 다음처럼 시작한다.

```bash
./harness start --issue 42 --type feat --slug direction-post
./harness task-init --title "방향 글 API" --replace
```

## 7. Husky 로컬 게이트

`npm ci` 또는 `npm install`의 `prepare` 단계가 Husky Hook을 설치한다.

- `pre-commit`: branch, staged 공백·비밀정보, 테스트·workflow 정책 검사
- `prepare-commit-msg`: branch의 type과 Issue 번호로 메시지 조립
- `commit-msg`: 커밋 형식과 branch 문맥 검사
- `pre-push`: 전체 하네스와 Gradle `check` 실행

개발자는 요약만 입력할 수 있다.

```bash
git commit -m "Qello 이름 변경"
# chore/gh-6-qello-project-migration 브랜치에서:
# chore: Qello 이름 변경 (#6)
```

scope가 필요하면 `chore(harness): Jira 연동 제거`까지 입력한다. 완성된 메시지는
그대로 유지하고 잘못된 Issue 번호는 `commit-msg`에서 차단한다. Hook 우회는
PR에 사유와 수동 검증 결과를 남기며 GitHub Actions를 최종 강제 기준으로 둔다.

## 8. 라벨 정책

- Issue와 PR에는 `type: *` 라벨이 정확히 하나 필요하다.
- `area: *`는 필요한 경우에만 붙인다.
- `status: *`는 자동화 또는 예외 상태만 표현한다.
- 스프린트, 일정, 우선순위는 라벨을 만들지 않고 GitHub Project 필드로 관리한다.
- PR의 type 라벨은 branch 접두사에서 자동 산출한다.

정책 상세는 `docs/harness/LABELS.md`를 따른다.

## 9. 완료 전 검증

기본 명령:

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

인프라 변경은 추가로 `terraform fmt -check`, `terraform validate`,
`terraform plan`을 실행한다. 실행하지 못한 검증은 이유와 남은 위험을 PR에
명시하며 성공했다고 표현하지 않는다.
