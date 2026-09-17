# GitHub Issue #243 Task Contract

> Generated at: `2026-09-17T09:27:06+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `infra-apply·infra-deployer 인라인 정책 크기 초과 수정`
- GitHub Issue: `#243`
- Branch: `fix/gh-243-infra-policy-size`
- Base branch: `main`
- DESIGN-ID: `D-3`(기존, `docs/reports/infrastructure/gh-229-D-3.md`,
  `APPROVED_FOR_BUILD`). 권한 범위를 바꾸지 않는 버그 수정이라 새 설계
  승인을 요구하지 않는다(사용자 지시, 2026-09-17).

## Objective

- `infra-apply`, `infra-deployer` Role의 인라인 정책이 AWS IAM의 Role당
  10,240바이트 한도를 초과해 `terraform apply`가 실패하는 문제를
  고친다.
- 두 Role이 부여받는 실제 권한 범위는 바꾸지 않는다.

## Scope

- `infra/bootstrap/oidc.tf`: `infra_apply_permissions`에서
  `source_policy_documents`(`test_server_shared_permissions`)를 제거하고,
  `aws_iam_role.infra_apply`에 그 내용만 담는 별도의
  `aws_iam_role_policy` 리소스를 추가한다.
- `infra/bootstrap/deployer.tf`: 같은 방식으로
  `infra_deployer_permissions`에서 `source_policy_documents`를 제거하고
  `aws_iam_role.infra_deployer`에 별도 `aws_iam_role_policy` 리소스를
  추가한다.

## Explicit exclusions

- 권한 범위(어떤 action·resource를 허용하는지) 변경.
- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Terraform 구현 (`infra/bootstrap/**`) | `tkv00` | PR 승인 |
| Apply | 사람 | 로컬에서 `infra-deployer` Role로 직접 실행(D-1 예외) |

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

- [ ] `terraform fmt`/`validate`/`tflint`/`checkov`가 통과한다.
- [ ] 각 Role에 결과적으로 붙는 개별 인라인 정책이 각각 10,240바이트
      이하다(사람이 실제 apply로 확인).
- [ ] `infra-plan`/`infra-apply`/`infra-deployer`가 부여받는 실제 권한
      범위는 이번 변경 전후로 동일하다.

## 참고

- 실제 apply 오류 재현: `docs/reports/infrastructure/gh-229-D-3-build.md`
  갱신 또는 이슈 #243 본문 참고.
- 관련 이슈: #233, #237, #240.
