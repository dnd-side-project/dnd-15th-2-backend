# Qello Backend

Qello는 사용자별 승인 질문에 사진이나 짧은 글로 답해 8개 방향 중 한 곳으로
보내고, 그 방향에 있는 익명 사용자들이 만료 전까지 함께 답하는 방향 기반 소통
서비스입니다.

## 현재 단계

현재 완료된 제품 산출물은 ERD 설계와 기능 명세입니다. 애플리케이션 API,
데이터베이스 마이그레이션, AWS 인프라, 배포는 아직 구현하지 않았습니다.
개발 백로그와 일정의 기준은 `Qello Backend Roadmap` GitHub Project의 draft
item입니다. 실제 구현을 시작할 항목만 repository Issue로 변환합니다.

제품의 백엔드 범위와 5~8주차 계획은
[`docs/product/BACKEND_ROADMAP.md`](docs/product/BACKEND_ROADMAP.md)를 봅니다.

## 기술 기준

- Java 21
- Spring Boot 3.5
- Gradle
- JUnit 5
- AWS 우선, Terraform 또는 AWS CDK
- GitHub Actions와 Husky

## 시작하기

```bash
./gradlew check
npm ci
./harness doctor
./harness check
```

## GitHub 작업 흐름

Jira를 사용하지 않습니다. 백로그는 GitHub Project draft item으로 관리하고,
Sprint, Status, Priority, Work type 필드를 사용합니다.

1. Project draft item의 범위와 완료 조건을 작성한다.
2. 구현을 시작할 item만 repository Issue로 변환한다.
3. Issue 번호로 branch를 만든다.
4. `TASK.md`를 현재 작업 계약으로 교체한다.
5. 작은 목적 단위로 구현·검증하고 Issue를 닫는 PR을 만든다.

```bash
./harness start --issue 42 --type feat --slug direction-post
./harness task-init --title "방향 글 API" --replace
```

### 브랜치 규칙

```text
<type>/gh-<ISSUE-NUMBER>-<short-slug>
```

허용 type은 `feat`, `feature`, `fix`, `refactor`, `test`, `docs`, `infra`,
`perf`, `chore`, `ci`, `build`입니다.

```text
feat/gh-42-direction-post
test/gh-51-feed-expiration
infra/gh-60-aws-baseline
```

### 커밋 규칙

```text
<type>(<scope>): <summary> (#<ISSUE-NUMBER>)
```

scope는 선택이고 Issue 번호는 필수입니다. Jira 키나 Jira 티켓 번호는 넣지
않습니다.

```text
feat(feed): add direction post endpoint (#42)
fix(location): correct bearing boundary (#47)
chore(harness): remove Jira integration (#6)
```

Husky의 `prepare-commit-msg`가 branch에서 type과 Issue 번호를 읽기 때문에 짧은
요약만 입력해도 됩니다.

```bash
git commit -m "Qello 이름 변경"
# chore/gh-6-qello-project-migration 브랜치라면
# chore: Qello 이름 변경 (#6)
```

### PR 규칙

PR 제목:

```text
<type>: <summary>
```

PR 본문에는 branch와 같은 Issue를 닫는 문장이 필요합니다.

```text
Closes #42
```

## 라벨 정책

Issue와 PR은 다음 canonical label만 사용합니다.

- `type: feature`, `type: bug`, `type: refactor`, `type: test`, `type: docs`,
  `type: infrastructure`, `type: performance`, `type: chore` 중 정확히 하나
- 필요한 경우 `area: api`, `area: database`, `area: security`,
  `area: operations`
- 자동화·예외 상태에만 `status: blocked`, `status: needs-review`,
  `status: needs-triage`

스프린트, 일정, 우선순위는 label이 아니라 GitHub Project 필드로 관리합니다.
Draft item은 `Work type` 필드를 사용하고 repository Issue로 변환할 때 canonical
type label을 붙입니다. PR type label은 branch 접두사에서 자동 산출됩니다. 상세 정책과 강제 범위는
[`docs/harness/LABELS.md`](docs/harness/LABELS.md)를 봅니다.

## 로컬·CI 검증

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

Husky는 다음 시점에 빠른 검사를 실행합니다.

- `pre-commit`: branch, staged 공백·비밀정보, 테스트·workflow 정책
- `prepare-commit-msg`: 규칙형 커밋 메시지 조립
- `commit-msg`: 커밋 형식과 Issue 문맥
- `pre-push`: 전체 하네스와 Gradle `check`

GitHub Actions는 Hook 우회와 관계없이 같은 핵심 규칙을 다시 검사합니다.

## 인프라 안전 원칙

인프라는 plan과 apply를 분리합니다. `terraform apply`, CDK deploy, 프로덕션
변경은 일반 개발 작업에서 실행하지 않습니다. 적용에는 두 명의 지정 리뷰어,
보호된 `infrastructure-apply` Environment, GitHub OIDC 단기 권한, 사람의 명시적
workflow dispatch가 모두 필요합니다.

자세한 저장소 계약은 [`AGENTS.md`](AGENTS.md)를 따릅니다.
