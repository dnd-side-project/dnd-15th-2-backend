# GitHub Issue #264 Task Contract

> Generated at: `2026-09-17T18:04:45+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `plan_sha256 검증을 정규화된 resource_changes 기준으로 변경`
- GitHub Issue: `#264`
- Branch: `fix/gh-264-plan-hash-normalize`
- Base branch: `main`

## Objective

- `infrastructure-apply.yml`의 plan 해시 검증이 raw `.tfplan` 바이너리를
  비교해 항상 불일치하는 설계 결함을 고친다. 동일한 State·설정으로
  연달아 생성한 두 plan의 raw 해시가 서로 다름을 재현 테스트로 이미
  확인했다.

## Scope

- `.github/workflows/infrastructure-apply.yml`: "Create the plan"
  단계에서 `terraform show -json tfplan | jq -S '.resource_changes'`로
  정규화한 JSON을 만들고, "Require the plan to match the reviewed
  hash" 단계가 그 정규화 파일의 SHA-256을 비교하도록 바꾼다.
- 사람이 사전에 plan 해시를 계산할 때도 같은 정규화 절차를 쓰도록
  안내 문구를 남긴다.

## Explicit exclusions

- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- 다른 apply 게이트(승인, Environment, 확인 문구) 변경.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Workflow 구현 | `tkv00` | PR 승인(1인, #251 기준) |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다. 보존할 사용자
  변경이 없다.

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

- [ ] `python scripts/validate-workflows.py`, `npm run hooks:validate`가
      통과한다.
- [ ] `actionlint`가 통과한다.
- [x] 동일한 diff에 대해 두 번 독립적으로 생성한 정규화 해시가
      일치함을 재현 테스트로 확인했다(로컬에서 검증 완료,
      2026-09-17).

## 참고

- 발견 경위: 실제 apply를 여러 차례 재시도해도 계속
  "Generated plan does not match the reviewed plan hash"가 발생해
  원인을 재현 테스트로 추적했다(2026-09-17).
- 관련 이슈: #229/#233/#237/#240.
