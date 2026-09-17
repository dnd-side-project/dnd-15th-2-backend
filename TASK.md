# GitHub Issue #247 Task Contract

> Generated at: `2026-09-17T10:42:49+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `test-server 최초 apply 승인용 PR`
- GitHub Issue: `#247`
- Branch: `docs/gh-247-test-server-first-apply`
- Base branch: `main`
- DESIGN-ID: `D-3`(기존, `APPROVED_FOR_BUILD`). 코드 변경이 없는 문서
  갱신이라 새 설계 승인을 요구하지 않는다.

## Objective

- `infra/environments/dev/test-server`의 최초 실제 apply를 승인받기
  위한 PR을 연다. `infrastructure-apply.yml`의 승인 게이트는 열린 PR과
  그 head SHA에 대한 `@Byuntil`·`tkv00` 승인을 요구하는데, 이 스택의
  기존 PR(#238)은 이미 병합·종료되어 재사용할 수 없다.

## Scope

- `docs/reports/infrastructure/gh-229-D-3-build.md`에 최초 apply
  시점까지의 경위(#243, #245 발견·수정)와 현재 apply 준비 상태를
  기록한다.

## Explicit exclusions

- Terraform 코드 변경.
- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 문서 갱신 | `tkv00` | `@Byuntil`·`@tkv00` PR 승인(이 PR 자체가 apply 승인 근거) |

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

- [ ] `@Byuntil`, `tkv00` 둘 다 이 PR의 최종 commit에 Approve한다.
- [ ] `./harness pr-ready --project-tests`가 통과한다.

## 참고

- 관련 이슈: #229, #233, #237, #240, #243, #245.
