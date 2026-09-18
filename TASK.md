# GitHub Issue #281 Task Contract

> Generated at: `2026-09-18T13:50:12+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `apply 승인 게이트의 재승인 반복 제거`
- GitHub Issue: `#281`
- Branch: `fix/gh-281-approval-on-merged-pr`
- Base branch: `main`

## Objective

- apply 승인 게이트의 재승인 반복을 제거한다. 통제는 유지하고, 승인 증거를
  담는 대상만 임시 PR에서 실제 머지된 인프라 PR로 바꾼다.

## Scope

- `scripts/verify-infra-approvals.py`: 머지 여부, base 브랜치, 작성자가 아닌
  소유자의 head 승인, merge commit 일치를 검증하도록 교체한다.
- `.github/workflows/infrastructure-apply.yml`: 중복된 commit SHA 비교
  step을 제거하고 스크립트로 일원화한다. 입력 설명을 실제 의미에 맞춘다.
- `AGENTS.md` 4.8: 바뀐 메커니즘을 반영한다.

## Explicit exclusions

- 승인 게이트를 제거하지 않는다. Environment 보호 규칙, 확인 문구,
  kill switch, plan 해시 검증, OIDC 자격 증명은 전부 그대로 둔다.
- `CODEOWNERS`와 GitHub Ruleset은 바꾸지 않는다.
- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 승인 게이트 | `@Byuntil` | CODEOWNERS가 두 소유자를 요구한다 |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다. 보존할 사용자
  변경이 없다.

## Validation

```bash
python3 scripts/validate-workflows.py
actionlint .github/workflows/infrastructure-apply.yml
npm run hooks:validate
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 머지 안 됨 / base 불일치 / 승인 없음 / 승인 아님 / 옛 커밋 승인 /
      작성자 자기 승인 / 소유자 아닌 승인 / 승인 철회 / merge commit 불일치가
      각각 거부된다 (단위 테스트 11건 통과)
- [x] 실제 GitHub 데이터로 확인: #277 통과, #269 거부(승인 없이 머지됨),
      #258 거부(머지 안 됨)
- [x] `scripts/validate-workflows.py`, `actionlint`, `npm run hooks:validate` 통과
- [x] `AGENTS.md` 4.8이 실제 동작과 일치한다

## 참고

- 배경: 승인용 임시 PR을 세 번 재생성하고(#249/#253/#257), #258 하나에
  재승인을 세 번 받았다. rebase가 head를 바꿔 승인이 무효화되는 구조적
  반복이었다.
- 강화되는 항목: 승인자가 작성자가 아님을 검사한다(현재는 하지 않는다).
  적용 대상이 임의 커밋이 아니라 머지된 PR의 merge commit으로 좁혀진다.
- 관련 이슈: #229/#233/#251/#264/#277/#279.
