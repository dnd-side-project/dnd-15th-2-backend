# GitHub Issue #240 Task Contract

> Generated at: `2026-09-17T02:28:11+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `GitHub Actions 배포 IAM Role 신설`
- GitHub Issue: `#240`
- Branch: `infra/gh-240-test-server-deploy-role`
- Base branch: `main`
- DESIGN-ID: `D-4`
- Design report: `docs/reports/infrastructure/gh-240-D-4.md`
- Design status: `APPROVED_FOR_BUILD` (`tkv00`, 2026-09-17, 단독 승인).
  Ownership 표는 `@Byuntil`·`@tkv00` 두 명의 설계 승인을 요구하나 현재
  `tkv00` 단독 승인만 확보됐다. 이 예외는 D-3(#233)과 동일 근거로
  사용자 지시로 확정됐다.

## Objective

- GitHub Actions가 OIDC로 assume할 수 있는 IAM Role을 신설해 #231(테스트
  서버 배포 workflow)이 ECR push와 EC2 SSM SendCommand를 수행할 수 있게
  한다.
- 기존 Role(`infra-plan`, `infra-apply`, `infra-deployer`, EC2 인스턴스
  Role) 중 어느 것도 이 용도로 assume할 수 없다는 것을 이슈 조사에서
  확인했다.

## Scope

- `infra/bootstrap/oidc.tf`에 새 IAM Role(`${project_prefix}-test-server-deploy`,
  D-4에서 확정)을 추가한다. GitHub OIDC로 assume하며 `sub` claim을
  `repo:<org>/<repo>:ref:refs/heads/main`으로만 한정한다(D-4 §4/§15 —
  저장소 전체 허용은 채택하지 않았다). GitHub Environment 게이트는
  요구하지 않는다(D-4 §15).
- ECR push 권한을 이 스택 소유 ECR 리포지토리
  (`arn:aws:ecr:*:*:repository/${project_prefix}-*`)로 한정한다.
- SSM `SendCommand`/`GetCommandInvocation` 권한을 #229가 만든 EC2 인스턴스와
  `AWS-RunShellScript` 문서로 한정한다.
- 이 Role의 ARN을 #231이 참조할 GitHub Actions repository secret 이름을
  정하고 문서화한다.

## Explicit exclusions

- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- AWS CLI를 이용한 리소스 생성·변경·삭제.
- #231의 workflow 파일 자체(별도 이슈, 이 Role의 ARN이 나온 뒤 진행).
- EC2·네트워크·ECR 리포지토리 자체의 신규 생성(이미 존재, #229/#233/#237).
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Infrastructure design | `tkv00` | `@Byuntil`·`@tkv00` 설계 승인 |
| Terraform 구현 (`infra/**`) | `tkv00` | `@Byuntil`·`@tkv00` PR 승인 |
| Apply | 사람 | `infrastructure-apply` Environment 승인과 workflow dispatch |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다. 보존할 사용자
  변경이 없다.

## Validation

```bash
terraform fmt -check -recursive infra
terraform -chdir=infra/bootstrap init -backend=false && terraform -chdir=infra/bootstrap validate
tflint --recursive
checkov -d infra --framework terraform
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] Infrastructure Design Report 작성 및 `APPROVED_FOR_BUILD` 승인 확보.
- [x] 새 Role의 신뢰 정책이 이 저장소 외 다른 저장소·다른 브랜치에서
      assume되지 않는다(`sub` claim을 `ref:refs/heads/main`으로 한정,
      정적 검사로 확인).
- [x] ECR·SSM 권한이 이 스택 소유 리소스로만 좁혀져 있고 불가피한
      AWS 제약(`GetAuthorizationToken`, `GetCommandInvocation` 등)만
      `Resource = "*"`다(policy_sentry로 검증, gh-240-D-4-build.md §3.5).
- [x] `terraform fmt`/`validate`/`tflint`/`checkov`가 통과한다.
- [ ] `terraform plan` 증거를 문서화하고 plan 원문은 PR에 싣지 않는다.
      실제 Backend 접근 문제로 이 세션에서는 미실행(gh-240-D-4-build.md
      §0/§5) — 사람이 backend 재구성 후 실행해야 한다.
- [ ] `@Byuntil`, `@tkv00` 리뷰를 요청한다.
- [x] Claude 세션에서 apply를 실행하지 않는다.

## 참고

- 관련 이슈: #229/#233/#237(EC2·ECR·네트워크), #231(이 Role의 ARN을 secret으로
  참조할 배포 workflow)
