# GitHub Issue #253 Task Contract

> Generated at: `2026-09-17T15:38:49+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `test-server apply 승인용 PR (재생성)`
- GitHub Issue: `#253`
- Branch: `docs/gh-253-apply-approval-pr`
- Base branch: `main`

## Objective

- #249가 apply 승인 근거로 쓰이려던 PR이었는데 머지되어 더 이상 열린
  PR이 아니게 됐다(`verify-infra-approvals.py`는 열린 PR만 인정한다).
  이 PR로 다시 연다.

## Scope

- `docs/reports/infrastructure/gh-229-D-3-build.md`에 #249가 머지되어
  무효화된 경위와 이 PR(#253)이 그 대체 근거라는 사실을 기록한다.

## Explicit exclusions

- 이 PR을 apply 완료 전까지 머지하지 않는다.
- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- Terraform 코드 변경.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 문서 갱신 | `tkv00` | `@Byuntil` 또는 `@tkv00` 중 한 명(#252 이후 완화됨) |

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

- [ ] `@Byuntil` 또는 `tkv00` 중 한 명이 이 PR의 최종 commit에 Approve한다.
- [x] `./harness pr-ready --project-tests`가 통과한다.
- [ ] 이 PR은 apply 완료 전까지 머지하지 않는다.

## 참고

- 관련 이슈: #229, #233, #237, #240, #243, #245, #248, #249(무효화됨), #251, #252.
