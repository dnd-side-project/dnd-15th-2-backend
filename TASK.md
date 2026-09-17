# GitHub Issue #266 Task Contract

> Generated at: `2026-09-17T19:56:18+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `test-server 공유 정책 태그 조건을 AWS 인가 동작에 맞게 수정`
- GitHub Issue: `#266`
- Branch: `fix/gh-266-policy-tag-conditions`
- Base branch: `main`

## Objective

- `infra/environments/dev/test-server` 실제 apply가 VPC 생성 직후 IAM 조건
  불일치로 멈춘 원인을 없앤다. 공유 정책의 태그 조건이 AWS의 세 가지 인가
  동작(생성 호출의 부모 리소스 평가, AWS가 암묵적으로 만드는 무태그 리소스,
  리소스 수준 권한 미지원 action)과 맞지 않았다.

## Scope

- `infra/bootstrap/oidc.tf`의 공유 정책 문서
  (`test_server_shared_permissions_network`/`_tags`/`_compute`)만 수정한다.
  - 생성 action의 부모 VPC 인가를 `aws:ResourceTag/Project`로 분리한다.
  - 기본 보안 그룹의 독립 `ec2:CreateTags` 호출을 허용한다.
  - `RunInstances`/`TerminateInstances`/`AssociateAddress`가 평가하는 무태그
    리소스(AMI·ENI·루트 볼륨)를 태그 조건 없는 별도 statement로 분리한다.
  - `ssm:DescribeParameters`를 `Resource: "*"` statement로 분리한다.

## Explicit exclusions

- 적용 대상 스택(`infra/environments/dev/test-server`)과 `infra/modules/**`
  코드는 바꾸지 않는다.
- 다른 apply 게이트(승인, Environment, 확인 문구, plan 해시)는 바꾸지 않는다.
- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Terraform IAM 정책 | `tkv00` | PR 승인(1인, #251 기준) |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다. 보존할 사용자
  변경이 없다.

## Validation

```bash
terraform fmt -check -recursive infra
terraform init -backend=false && terraform validate   # 격리된 복사본
tflint --recursive
uvx checkov -d infra --framework terraform
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 생성 action의 부모 리소스 인가가 `aws:ResourceTag/Project`로 분리됨
- [x] 무태그 리소스(기본 보안 그룹·ENI·루트 볼륨·AMI)가 리소스 유형과
      계정·리전으로만 한정된 별도 statement로 분리되고 근거가 주석에 남음
- [x] `ssm:DescribeParameters`가 `Resource: "*"` statement로 분리됨
- [x] 각 관리형 정책이 6,144바이트 한도 안에 있음(network 3,677 /
      tags 1,407 / compute 4,504 / iam-management 431)
- [x] `terraform fmt`/`validate`/`tflint`/`checkov`/`harness pr-ready` 통과

## 참고

- 발견 경위: 실제 apply 중 VPC가 생성된 뒤 서브넷·라우트 테이블·보안 그룹
  생성과 SSM 파라미터 조회가 연달아 거부됐다(2026-09-17).
- 관련 이슈: #229/#233/#240/#243/#259/#261/#264.
- 사람의 결정이 필요한 항목: AMI·ENI·루트 볼륨 3개 statement는 태그 조건
  없이 리소스 유형과 계정·리전으로만 한정한다. AWS가 이 리소스를 무태그로
  암묵 생성해 태그 조건을 구조적으로 만족할 수 없다.
