# GitHub Issue #251 Task Contract

> Generated at: `2026-09-17T11:03:03+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `infra apply 승인 게이트를 1인 승인으로 완화`
- GitHub Issue: `#251`
- Branch: `chore/gh-251-single-approver-gate`
- Base branch: `main`
- 정책 변경. 새 Terraform 설계가 아니므로 DESIGN-ID를 요구하지 않는다.
  `AGENTS.md` 4.8절 자체를 사용자 지시로 수정한다(2026-09-17).

## Objective

- `infrastructure-apply.yml`의 승인 게이트를 "`@Byuntil`·`tkv00` 둘 다
  승인"에서 "둘 중 한 명 이상 승인"으로 완화한다. GitHub가 PR 작성자
  본인의 승인을 허용하지 않아, 두 명 모두를 요구하면 PR을 연 사람 쪽
  조건을 구조적으로 영원히 충족할 수 없었다.

## Scope

- `scripts/verify-infra-approvals.py`: "요구된 리뷰어 전원"에서 "요구된
  리뷰어 중 한 명 이상"으로 승인 판정 로직을 바꾼다.
- `.github/workflows/infrastructure-apply.yml`: 해당 단계 이름만
  "양쪽 모두"에서 "둘 중 하나"로 바꾼다. `--required-reviewers Byuntil
  tkv00` 인자 자체는 바꾸지 않는다(스크립트가 의미를 바꾼다).
- `AGENTS.md` 4.8절 조건 3을 "최소 한 명"으로 바꾸고 완화 사유를 적는다.

## Explicit exclusions

- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- Environment 보호 규칙, GitHub Ruleset 변경.
- 두 명 요구사항이 있는 다른 게이트(예: 일반 PR 승인 수) 변경.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 정책·workflow 구현 | `tkv00` | PR 승인(이 변경 이후로는 1인 승인으로 충분) |

## Existing user-owned changes

- 이 브랜치를 만들기 전 `chore/gh-248-media-kms-var` 브랜치에서 이
  변경을 시작했다가(#248과 무관한 내용이라) `git stash`로 분리해
  이 브랜치로 옮겼다. 다른 사용자 변경은 없다.

## Validation

```bash
python scripts/validate-workflows.py
npm run hooks:validate
actionlint .github/workflows/infrastructure-apply.yml
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] `python scripts/validate-workflows.py`, `npm run hooks:validate`가
      통과한다.
- [x] `actionlint`가 통과한다.
- [x] 코드 리뷰로 "한 명만 승인해도 통과, 아무도 승인 안 하면 차단"
      로직을 확인한다.

## 참고

- 발견 경위: #249/#250 PR이 전부 `tkv00` 계정으로 생성되어 `tkv00`
  본인이 자기 PR을 승인할 수 없다는 GitHub 제약에 막혔다.
- 관련 이슈: #229, #233, #237, #240, #243, #245, #248.
