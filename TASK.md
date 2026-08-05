# GitHub Issue #58 Task Contract

> Generated at: `2026-08-05T09:51:05+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Terraform IaC 선정 ADR 작성 및 인프라 에이전트 고도화`
- GitHub Issue: `#58`
- Branch: `infra/gh-58-adr-terraform-iac-selection`

## Objective

- TODO

## Scope

- TODO

## Explicit exclusions

- TODO
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| TODO | TODO | TODO |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과를 확인하고 여기에 기록한다.
- `main`이 8개 커밋 앞서 있어(`origin/main`) `git merge origin/main`으로 갱신했다.
  `TASK.md`와 `docs/adr/README.md`에서 충돌이 발생했다. `TASK.md`는 브랜치별
  계약이므로 이 브랜치(#58)의 내용을 유지했다. `docs/adr/README.md`는 두
  브랜치가 각각 `ADR-0003`을 사용해 번호가 충돌해, main의 `ADR-0003`(전역
  예외 처리)을 유지하고 이 브랜치의 Terraform ADR을 `ADR-0004`
  (`0004-adopt-terraform-for-aws-iac.md`)로 재번호했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- TODO
