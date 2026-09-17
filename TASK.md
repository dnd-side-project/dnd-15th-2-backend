# GitHub Issue #270 Task Contract

> Generated at: `2026-09-17T21:30:00+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `암호화 EBS 볼륨용 계정 기본 KMS 키 권한 추가`
- GitHub Issue: `#270`
- Branch: `fix/gh-270-ebs-default-key-kms`
- Base branch: `main`

## Objective

- 남은 apply를 재시도하기 전에 IAM 공백을 미리 없앤다. 데이터 볼륨과 인스턴스
  루트 볼륨이 계정 기본 EBS 키로 암호화되는데, 그 키를 쓰기 위한 KMS 권한이
  공유 정책에 없어 두 리소스가 모두 거부될 상태였다.

## Scope

- `infra/bootstrap/oidc.tf`의 `test_server_shared_permissions_compute`에
  `UseDefaultEbsKmsKey` statement를 추가한다.
- 키가 아직 존재하지 않아(첫 사용 시 AWS가 생성) ARN으로 좁힐 수 없으므로
  `kms:ViaService`(EC2 경유)와 `kms:CallerAccount`(이 계정)로 한정한다.

## Explicit exclusions

- 모듈과 적용 대상 스택 코드는 바꾸지 않는다.
- 전용 Customer-managed Key로 전환하지 않는다. CMK는 월 1 USD 고정 비용이
  발생해 D-3 §6 예산을 초과한다.
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
aws iam simulate-custom-policy   # 렌더링된 정책으로 사전 검증
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 다섯 KMS action이 시뮬레이션에서 `allowed`로 바뀐다
- [x] `kms:ViaService`가 EC2가 아닌 경우 거부되는 것을 시뮬레이션으로 확인했다
- [x] 관리형 정책이 6,144바이트 한도 안에 있다(compute 4,823)
- [x] `terraform fmt`/`validate`/`tflint`/`checkov`/`harness pr-ready` 통과

## 참고

- 발견 경위: 실패를 하나씩 겪지 않기 위해 남은 8개 리소스가 필요한 action을
  `aws iam simulate-principal-policy`로 전수 검사해 찾았다(2026-09-17).
- 오탐으로 확인한 항목: `iam:PassRole`(조건 컨텍스트 제공 시 allowed),
  `scheduler:TagResource`·`ListTagsForResource`(스케줄에 `tags` 없음).
- 관련 이슈: #229/#233/#261/#263/#266/#268.
