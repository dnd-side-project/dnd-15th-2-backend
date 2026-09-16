# GitHub Issue #233 Task Contract

> Generated at: `2026-09-16T15:51:01+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `프론트 테스트 서버 Terraform 구현 (D-3)`
- GitHub Issue: `#233`
- Branch: `infra/gh-233-test-server-terraform`
- Base branch: `main`
- DESIGN-ID: `D-3`
- Design report: `docs/reports/infrastructure/gh-229-D-3.md`
- Design status: `APPROVED_FOR_BUILD` (`tkv00`, 2026-09-16, 단독 승인).
  Ownership 표는 `@Byuntil`·`@tkv00` 두 명의 설계 승인을 요구하나 현재
  `tkv00` 단독 승인만 확보됐다. 이 예외는 사용자 지시로 확정됐다(§16 참고).

## Objective

- #229에서 승인된 D-3 설계를 Terraform으로 구현한다.
- EC2 1대에서 Docker Compose로 백엔드 컨테이너와 PostGIS 컨테이너를 구동한다.
- H-1 승인에 따라 EventBridge Scheduler 기반 자동 정지 가드레일을 함께 구현한다.
- 애플리케이션 런타임 동작, API 경로, 상태 코드, 요청·응답 구조를 변경하지 않는다.

## Scope

- `infra/bootstrap/oidc.tf`, `infra/bootstrap/deployer.tf` IAM 정책에
  EC2·VPC·ECR·SSM·`iam:PassRole`·EventBridge Scheduler 액션 추가.
- `infra/modules/network` 신설. VPC, 퍼블릭 서브넷, Internet Gateway, 라우트 테이블.
- `infra/modules/app-server` 신설. EC2, Elastic IP, 보안 그룹, 인스턴스 Role과
  Instance Profile, 데이터용 EBS 볼륨.
- `infra/environments/dev/test-server` 신설. 위 모듈 조합, ECR 리포지토리,
  SSM SecureString 파라미터(`value_wo`), EventBridge Scheduler 정지 작업.
- `docs/reports/infrastructure/gh-229-D-3-build.md`에 plan 증거 작성.

## Explicit exclusions

- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- AWS CLI를 이용한 리소스 생성·변경·삭제.
- 애플리케이션 코드 변경. CORS, actuator 헬스체크, dev 프로파일은 #230 범위다.
- ECR 빌드·푸시 GitHub Actions workflow는 #231 범위다.
- `CLAUDE.md`, `AGENTS.md`, `.claude/**`, `agents/**`, `CODEOWNERS`,
  `.github/workflows/*apply*`, `scripts/guard-*` 수정.
- TLS, 도메인, ALB, RDS, production 환경.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Infrastructure design (D-3) | `tkv00` | `tkv00` 단독 승인 확정(2026-09-16) |
| Terraform 구현 (`infra/**`) | `tkv00` | `@Byuntil`, `@tkv00` PR 승인 |
| Apply | 사람 | `infrastructure-apply` Environment 승인과 workflow dispatch |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다. 보존할 사용자 변경이 없다.

## Validation

```bash
terraform fmt -check -recursive infra
terraform -chdir=infra/modules/network init -backend=false && terraform -chdir=infra/modules/network validate
terraform -chdir=infra/modules/app-server init -backend=false && terraform -chdir=infra/modules/app-server validate
terraform -chdir=infra/environments/dev/test-server init -backend=false && terraform -chdir=infra/environments/dev/test-server validate
terraform -chdir=infra/bootstrap init -backend=false && terraform -chdir=infra/bootstrap validate
tflint --recursive
checkov -d infra --framework terraform
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [ ] 모든 리소스 이름이 `${var.project_prefix}-`로 시작하고 태그에
      `Project = "qello"`를 유지한다.
- [ ] EC2가 IMDSv2 필수(`http_tokens = "required"`)이고 루트·데이터 EBS가 암호화된다.
- [ ] 22번 포트를 열지 않고 SSM Session Manager로만 접속한다.
- [ ] DB 비밀번호와 `QELLO_AUTH_ACCESS_TOKEN_SECRET`을 SSM SecureString
      파라미터의 `value_wo`와 `value_wo_version`으로 선언하고 값은 사람이 넣는다.
      이 스택의 `required_version`은 `>= 1.11.0, < 2.0.0`이다.
- [ ] EventBridge Scheduler가 매일 정해진 시각에 EC2를 정지한다.
- [ ] `backend "s3"` 블록을 커밋해
      `key = "environments/dev/test-server/terraform.tfstate"`,
      `use_lockfile = true`를 설정한다.
- [ ] 정적 검증(fmt, validate, tflint, checkov)이 통과한다.
- [ ] checkov `--skip-check` 목록을 늘리지 않는다.
- [ ] plan 증거를 `gh-229-D-3-build.md`에 기록하고 plan 원문을 PR에 싣지 않는다.
- [ ] Claude 세션에서 apply를 실행하지 않는다.

## 설계 변경 이력 참고

- 2026-09-16: 사용자가 D-3 설계를 `tkv00` 단독 승인으로 확정하라고 명시적으로
  지시했다(원래 Ownership 표는 `@Byuntil`·`@tkv00` 두 명을 요구). 같은 지시로
  H-1(EventBridge Scheduler 자동 정지)을 구현 범위에 포함했다.
- #229는 설계 보고서 머지 시 `Closes #229`로 자동 종료됐다. 구현은 이 이슈
  (#233)가 담당하며 #229를 선행 관계로 참조한다.
- 2026-09-16: 이 프로젝트가 앞으로 쓸 AWS 계정이 `qello-new` 프로파일이
  가리키는 계정으로 바뀌었다(사람 확인). `infra/bootstrap`(#63)이 새
  계정에 아직 적용되지 않아, **bootstrap → storage → test-server 순서로
  새 계정에 먼저 적용해야** 이 이슈의 스택을 plan/apply할 수 있다. 로컬의
  기존 `infra/bootstrap/terraform.tfstate`는 이전 계정의 실제 상태이며
  재사용하지 않는다. 상세는 `gh-229-D-3-build.md` §6 참고.
