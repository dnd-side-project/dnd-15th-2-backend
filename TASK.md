# GitHub Issue #259 Task Contract

> Generated at: `2026-09-17T17:20:37+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `GitHub OIDC sub claim 신형식 대응`
- GitHub Issue: `#259`
- Branch: `fix/gh-259-oidc-sub-new-format`
- Base branch: `main`

## Objective

- GitHub가 2026년 7월부터 신규 저장소에 적용하는 새 OIDC `sub` 클레임
  형식(`repo:<org>@<org-id>/<repo>@<repo-id>:...`)을 이 저장소의 모든
  OIDC 신뢰 정책이 인식하도록 고친다. 기존 형식도 계속 허용한다.

## Scope

- `infra/bootstrap/variables.tf`: `github_repository_with_id` 변수를
  추가한다.
- `infra/bootstrap/oidc.tf`: `infra_plan_trust`, `infra_apply_trust`,
  `test_server_deploy_trust` 세 신뢰 정책의 `sub` 조건을 기존 형식과
  신형식 둘 다 포함하는 리스트로 바꾼다.

## Explicit exclusions

- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- 권한 범위(action·resource) 변경. 신뢰 정책의 매칭 대상만 넓힌다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Terraform 구현 | `tkv00` | PR 승인(1인, #251 기준) |
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

- [x] `terraform fmt`/`validate`/`tflint`/`checkov`가 통과한다.
- [x] 세 Role 모두 기존 형식과 신형식 sub 양쪽에서 assume 가능하도록
      신뢰 정책이 리스트를 쓴다.
- [ ] 실제 apply로 `infra-apply` Role assume 성공을 확인한다(사람 실행).

## 참고

- 발견 경위: CloudTrail에서 실제 거부된 `AssumeRoleWithWebIdentity`
  이벤트의 `principalId`를 확인해 신형식 sub 클레임임을 확인했다
  (2026-09-17). 참고: GitHub 공식 변경 공지(2026-07-15부).
- 관련 이슈: #229/#233/#237/#240.
