# GitHub Issue #279 Task Contract

> Generated at: `2026-09-18T13:40:14+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `IAM Role 삭제에 필요한 권한 추가`
- GitHub Issue: `#279`
- Branch: `fix/gh-279-iam-role-delete-permissions`
- Base branch: `main`

## Objective

- #277 적용이 부분 적용 상태로 중단된 원인을 없앤다. AWS Provider가 IAM Role
  삭제 전에 호출하는 `iam:ListInstanceProfilesForRole` 권한이 없어, 스케줄과
  inline policy만 삭제된 채 Role 삭제에서 실패했다.

## Scope

- `infra/bootstrap/oidc.tf`의 `test_server_iam_management`에
  `iam:ListInstanceProfilesForRole`, `iam:ListRoleTags`, `iam:UntagRole`을
  `role/${project_prefix}-*`로 한정해 추가한다.

## Explicit exclusions

- `iam:RemoveRoleFromInstanceProfile`은 추가하지 않는다. 이 action은
  instance-profile 유형만 인가 대상으로 삼아(policy_sentry 확인)
  `ManageTestServerInstanceProfiles`로 이미 충족된다.
- 모듈과 적용 대상 스택 코드는 바꾸지 않는다.
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
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 세 action이 시뮬레이션에서 `allowed`로 바뀐다
- [x] 범위 밖 Role에 대해서는 여전히 거부된다
- [x] 관리형 정책이 6,144바이트 한도 안에 있다(iam-management 613)
- [x] `terraform fmt`/`tflint`/`checkov`/`harness pr-ready` 통과

## 참고

- 발견 경위: 실행 `35246659634`(2026-09-18 01:28 KST)가 plan 해시 게이트를
  통과하고 apply 도중 실패했다. CloudTrail로 `DeleteSchedule`이 실제
  수행됐음을 확인했다.
- 오탐으로 확인한 항목: `iam:RemoveRoleFromInstanceProfile`을 role ARN으로
  시뮬레이션하면 거부로 나오지만, 이 action은 instance-profile 유형만
  지원하므로 실제 결함이 아니다.
- 관련 이슈: #229/#233/#266/#276/#277.
