# Miri Repository Agent Contract

이 문서는 이 저장소에서 동작하는 모든 사람과 AI 에이전트의 공통 계약이다.
도구별 세부 사용법보다 이 문서와 `TASK.md`가 우선한다.

## 1. 작업 시작 게이트

구현 전에 다음 항목을 모두 충족한다.

1. Jira 티켓을 읽고 범위와 완료 조건을 확인한다.
2. Jira 키가 연결된 GitHub Issue가 존재하는지 확인한다.
3. `TASK.md`에 목표, 범위, 제외 범위, 소유 파일, 검증 명령을 기록한다.
4. Jira 키와 GitHub Issue 번호가 포함된 브랜치에서 작업한다.

브랜치 형식:

```text
<type>/<JIRA-KEY>-gh-<ISSUE-NUMBER>-<short-slug>
```

예:

```text
test/MIR-123-gh-42-order-reservation
infra/PAY-314-gh-77-aws-baseline
```

Jira 키와 Issue 번호는 예시값으로 고정하지 않는다. 현재 작업 문맥은 branch에서
파생하며 commit, PR title, PR body의 값이 branch와 일치해야 한다.

Jira 티켓 또는 GitHub Issue가 없으면 분석과 초안 작성까지만 허용한다. 구현,
인프라 변경, 배포, PR 생성은 중지한다.

## 2. 역할 분리

- 오케스트레이터는 시나리오, 작업 분해, 위험, 인수 조건을 설계한다.
- 실행 에이전트는 승인된 계획의 파일만 구현하고 검증한다.
- PM/리뷰어는 Jira 범위, 증거, 승인 조건을 점검한다.
- 실행 에이전트가 임의로 범위를 넓히거나 인프라를 적용해서는 안 된다.

상세 역할은 `agents/` 문서를 따른다. 모델은 논리 프로필로 선택하며 실제 모델
식별자는 개인 로컬 설정에서 결정한다.

## 3. 테스트 규칙

- JUnit 5를 사용한다.
- 단위 테스트와 통합 테스트를 분리한다.
- 모든 테스트 메서드에 `@DisplayName`을 작성한다.
- 모든 테스트 클래스 상단에 정확한 ISO 8601 생성 시각과 원본 테스트 계획
  식별자를 기록한다.
- 테스트 실행 후 애플리케이션 코드, 인프라, 데이터베이스, 동시성, 트랜잭션,
  외부 API, 장애 복구 관점의 잠재 문제를 분석한다.
- 보고서는 `templates/test-report.md`에서 생성한다.
- `.env` 값, 토큰, URL, 계정 식별자 등 민감정보를 테스트나 보고서에 기록하지
  않는다.

테스트 클래스 헤더 예:

```java
/**
 * Created at: 2026-07-24T13:52:05+09:00
 * Source scenario: TEST-PLAN-<JIRA-KEY>-BASELINE
 */
```

## 4. 인프라 규칙

- AWS 우선, Terraform 또는 AWS CDK를 사용한다.
- 초기 설계는 가장 낮은 실용 사양의 단일 애플리케이션 워크로드와 저비용 RDS를
  기준으로 한다.
- EC2와 ECS, 관리형과 자체 운영 대안을 문서에서 비교한다.
- 설계 문서, 비용 가정, 계획 결과를 PR에서 검토한 뒤에만 적용한다.
- `terraform apply`, CDK deploy, 프로덕션 변경은 기본적으로 금지한다.
- 적용 전 `@Byuntil`, `@tkv00` 두 명의 명시적 승인을 확인하고 사람이 직접
  실행을 확인해야 한다.
- 최소 권한 IAM과 GitHub OIDC를 사용한다. 장기 액세스 키를 만들거나 저장하지
  않는다.
- 비밀 키, 서버 주소, IAM ID, AWS 계정 ID, 토큰, `.env` 값은 코드, 이슈, PR,
  보고서, 로그, 예시에 기록하지 않는다.

`CODEOWNERS`는 리뷰어를 요청할 뿐 두 명 승인을 강제하지 않는다. 실제 강제는
GitHub Ruleset과 `infrastructure-apply` Environment 보호 규칙에서 별도로
설정한다.

## 5. 변경 안전성

- 사용자 또는 다른 에이전트가 수정한 파일을 덮어쓰지 않는다.
- 작업 시작 시 `git status --short`를 확인하고 `TASK.md`에 제외 파일을 적는다.
- 넓은 파일 변경, 삭제, 인프라 적용, 프로덕션 변경은 명시적 사람 확인 없이는
  실행하지 않는다.
- 원본 원장, 마이그레이션 이력, 운영 감사 이력을 수정하거나 삭제하지 않는다.
- 명령은 재실행에 안전하게 만들고, 실패 시 부분 반영을 피한다.

## 6. 커밋과 PR

커밋 형식:

```text
<type>(<scope>): [<JIRA-KEY>] <summary> (#<ISSUE-NUMBER>)
```

예:

```text
test(order): [MIR-123] add reservation concurrency scenarios (#42)
docs(harness): [MIR-123] document macOS shortcuts (#42)
ci(infra): [PAY-314] add manual apply approval gate (#77)
```

PR 제목:

```text
[<JIRA-KEY>] <type>: <summary>
```

PR에는 Jira, GitHub Issue, 설계 또는 테스트 계획, 실행 증거, 보고서, 위험,
롤백/복구 절차를 연결한다. 하나의 커밋에는 하나의 검토 목적만 담는다.
PR body의 `Closes #<ISSUE-NUMBER>`도 branch의 Issue 번호와 일치해야 한다.

새 작업을 시작한 뒤에는 현재 branch 문맥으로 작업 계약을 교체한다.

```bash
./harness task-init --title "주문 예약 동시성 보강" --replace
```

기존 `TASK.md`가 커밋되었는지 확인한 뒤에만 `--replace`를 사용한다.

## 7. Husky 로컬 게이트

`npm ci` 또는 `npm install`의 `prepare` 단계가 Husky Hook을 설치한다.

- `pre-commit`: branch 규칙, staged 공백, staged secret, 변경된 테스트와
  workflow 정책을 빠르게 검사한다.
- `prepare-commit-msg`: 짧은 요약을 branch 기반 규칙형 메시지로 조립한다.
- `commit-msg`: 커밋 제목 형식을 검사한다.
- `pre-push`: 전체 하네스 검사와 Gradle `check`를 실행한다.

따라서 개발자는 일반적으로 다음처럼 요약만 입력할 수 있다.

```bash
git commit -m "주문 예약 기능 개발"
```

`feat/PAY-314-gh-42-order-reservation` branch라면 다음 제목으로 저장된다.

```text
feat: [PAY-314] 주문 예약 기능 개발 (#42)
```

scope가 필요하면 `test(order): 예약 테스트 추가`까지만 입력한다. 이미 완성된
메시지는 그대로 유지하며, 다른 Jira/Issue가 들어간 메시지는 자동 교체하지 않고
검증 단계에서 차단한다.

Hook은 개발자 피드백을 앞당기는 보조 장치다. `--no-verify`는 `pre-commit`과
`commit-msg` 검사를 우회할 수 있고 `HUSKY=0`은 Husky 전체를 비활성화할 수
있으므로 GitHub Actions를 최종 강제 기준으로 유지한다. 장애 대응 등으로
우회했다면 PR에 사유와 수동 검증 결과를 남긴다. Hook을 삭제·약화하는 변경은
CI 정책 변경으로 보고 백엔드 리뷰를 받는다.

## 8. 완료 전 검증

기본 명령:

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
```

인프라 변경은 추가로 정적 검증과 `terraform fmt -check`, `terraform validate`,
`terraform plan`을 실행한다. 테스트나 계획을 실행하지 못했다면 이유와 남은
위험을 PR에 명시하며 성공했다고 표현하지 않는다.
