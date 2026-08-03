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

## Java 패키지 구조

Qello 백엔드는 하나의 Spring Boot 애플리케이션 안에서 Package-by-Feature를
사용합니다. 최상위 기능 패키지는 `account`, `question`, `direction`, `feed`,
`safety`, `notification`이며, 각 기능은 구현이 필요할 때 `controller`,
`service`, `repository` 3계층을 둡니다.

의존 방향은 `controller → service → repository`입니다. 다른 기능과 협력할 때는
상대 기능의 service 계약을 사용하며 다른 기능의 controller, repository, entity를
직접 참조하지 않습니다. `common`에는 설정, 오류 표현, 공통 응답처럼 기능 정책이
없는 코드만 둡니다.

`local` profile은 Git에서 제외되는 `.env`로 Compose PostgreSQL/PostGIS에 연결합니다.
`test` profile은 Testcontainers가 임시 PostGIS DB를 공급하므로 개발자 로컬 DB에
의존하지 않습니다. JDBC는 연결과 명시적 SQL 기반으로 사용하며, JPA와 Flyway는
실제 Entity 또는 추적 schema를 추가하는 후속 Issue에서 결정합니다.

## 시작하기

```bash
cp .env.example .env
docker compose pull db
docker compose up -d db
docker compose ps

./gradlew check
npm ci
./harness doctor
./harness check
```

`local` profile 애플리케이션은 다음처럼 실행합니다. `.env`는
`application-local.properties`가 선택적으로 읽으며 Git에는 포함되지 않습니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

PostGIS와 종료 상태는 다음 명령으로 확인합니다.

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT PostGIS_Version();"'
docker compose down
```

PostGIS 프로젝트의 `postgis/postgis:16-3.5-alpine` 이미지는 Compose에서 검증된
digest까지 고정하며 현재 `amd64`만 제공합니다. Testcontainers는 라이브러리의
image-name 제약 때문에 같은 tag를 사용합니다. 두 경로 모두 `linux/amd64`를
명시하므로 Apple Silicon 개발 환경에서는 Docker Desktop의 x86_64 에뮬레이션이
필요합니다. 이미지 업데이트는 tag와 Compose digest를 함께 바꾸고 통합 테스트로
검증합니다.

데이터까지 초기화할 때만 `docker compose down --volumes`를 사용합니다. 이 명령은
named volume의 로컬 데이터를 삭제하므로 일반 종료 명령에는 포함하지 않습니다.

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
