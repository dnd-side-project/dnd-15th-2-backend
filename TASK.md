# GitHub Issue #229 Task Contract

> Generated at: `2026-09-16T00:25:08+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `프론트 테스트 서버용 EC2와 컨테이너 PostgreSQL`
- GitHub Issue: `#229`
- Branch: `infra/gh-229-frontend-test-server`
- Base branch: `main`
- DESIGN-ID: `D-3`
- Design report: `docs/reports/infrastructure/gh-229-D-3.md`
- Design status: `READY_FOR_DESIGN_REVIEW` (2026-09-16) — 설계 보고서 작성 완료.
  사람이 `APPROVED_FOR_BUILD`로 승인하기 전에는 Terraform 구현을 시작하지 않는다.

## Objective

- 프론트엔드가 실제 백엔드에 붙어 테스트할 dev 서버를 EC2 1대로 구축한다.
- EC2 안에서 Docker Compose로 백엔드 컨테이너와 PostGIS 컨테이너를 함께 구동한다.
- `/harness-infra-design --id D-3`로 설계 보고서를 먼저 만들고, 사람의 승인 이후에만
  `/harness-infra-build`로 Terraform을 구현한다.
- 애플리케이션 런타임 동작, API 경로, 상태 코드, 요청·응답 구조를 변경하지 않는다.

## Confirmed intake

| 항목 | 상태 | 값 |
| --- | --- | --- |
| AWS Region | CONFIRMED | `ap-northeast-2` |
| 환경 | CONFIRMED | dev 테스트 전용, production 아님 |
| 데이터베이스 구동 | CONFIRMED | EC2 내부 Docker Compose PostGIS 컨테이너 |
| 외부 공개 범위 | CONFIRMED | 공인 IP + HTTP 8080, TLS·도메인·ALB 없음 |
| 월 예산 상한 | CONFIRMED | 10 USD |
| 운영 접근 | CONFIRMED | SSM Session Manager 전용, 22번 포트 미개방 |
| 이미지 전달 | CONFIRMED | ECR + GitHub Actions 푸시, EC2가 IAM Role로 pull |
| 인스턴스 타입과 가동 시간 | BLOCKED | `t3.small` 24시간 구동은 예산을 초과한다. 설계에서 결정한다 |
| 가용성 목표 | ASSUMED | 공식 SLA 없음, 단일 AZ best-effort |
| RTO / RPO | ASSUMED | 없음. 테스트 데이터는 재생성 가능하다 |
| 데이터 민감도 | ASSUMED | 테스트 데이터만 저장한다 |

## Scope

- `docs/reports/infrastructure/gh-229-D-3.md` 설계 보고서 작성.
- `infra/bootstrap/oidc.tf`, `infra/bootstrap/deployer.tf` IAM 정책에 EC2·VPC·ECR·SSM·
  `iam:PassRole` 액션 추가.
- `infra/modules/network` 신설. VPC, 퍼블릭 서브넷, Internet Gateway, 라우트 테이블.
- `infra/modules/app-server` 신설. EC2, Elastic IP, 보안 그룹, 인스턴스 Role과
  Instance Profile, 데이터용 EBS 볼륨.
- `infra/environments/dev/test-server` 신설. 위 모듈 조합, ECR 리포지토리,
  SSM SecureString 파라미터.
- `docs/reports/infrastructure/gh-229-D-3-build.md` plan 증거 작성.

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
| Infrastructure design (D-3) | `tkv00` | `@Byuntil`, `@tkv00` 설계 승인 |
| Terraform 구현 (`infra/**`) | `tkv00` | `@Byuntil`, `@tkv00` PR 승인 |
| Apply | 사람 | `infrastructure-apply` Environment 승인과 workflow dispatch |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다. 보존할 사용자 변경이 없다.

## Validation

```bash
terraform fmt -check -recursive infra
terraform init -backend=false
terraform validate
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [ ] `docs/reports/infrastructure/gh-229-D-3.md`가 템플릿 11개 섹션을 채운다.
- [ ] 컴퓨트와 데이터베이스 대안 비교, 탈락 이유, AWS 공식 가격 근거와 조회일을 기록한다.
- [ ] 사람이 설계를 `APPROVED_FOR_BUILD`로 승인한 증거가 보고서에 남는다.
- [ ] 승인 이후에만 `infra/**`에 Terraform을 작성한다.
- [ ] 정적 검증(fmt, validate, tflint, checkov)이 통과한다.
- [ ] checkov `--skip-check` 목록을 늘리지 않는다.
- [ ] plan 증거를 `gh-229-D-3-build.md`에 기록하고 plan 원문을 PR에 싣지 않는다.
- [ ] Claude 세션에서 apply를 실행하지 않는다.
