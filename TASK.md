# GitHub Issue #261 Task Contract

> Generated at: `2026-09-17T17:44:28+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `kms:ListAliases 권한 추가`
- GitHub Issue: `#261`
- Branch: `fix/gh-261-kms-list-aliases`
- Base branch: `main`

## Objective

- `infra/environments/dev/test-server`의
  `data "aws_kms_alias" "ssm_default"`가 plan/apply 시점마다 호출하는
  `kms:ListAliases`를 `infra-plan`/`infra-apply`/`infra-deployer` Role에
  추가한다. 실제 apply가 `AccessDeniedException`으로 실패한 것을
  확인했다.

## Scope

- `infra/bootstrap/oidc.tf`: `infra_plan_permissions`,
  `infra_apply_permissions`에 `kms:ListAliases`(Resource `*`) statement
  추가.
- `infra/bootstrap/deployer.tf`: `infra_deployer_permissions`에 동일
  statement 추가.

## Explicit exclusions

- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Terraform 구현 | `tkv00` | PR 승인(1인, #251 기준) |
| Apply | 사람 | 로컬에서 `infra-deployer` Role로 직접 실행(D-1 예외) |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다(변경사항은
  이전 브랜치에서 stash로 옮겨온 것). 보존할 다른 사용자 변경은 없다.

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

- [x] `terraform fmt`/`validate`/`tflint`/`checkov`가 통과한다.
- [ ] 실제 apply로 `kms:ListAliases` 오류가 재발하지 않음을 확인한다
      (사람 실행).

## 참고

- 발견 경위: 실제 apply 실행 로그의
  `AccessDeniedException...kms:ListAliases` 오류(2026-09-17).
- 관련 이슈: #229/#233/#237/#240/#259.
