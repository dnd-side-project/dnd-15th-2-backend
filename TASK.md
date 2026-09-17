# GitHub Issue #245 Task Contract

> Generated at: `2026-09-17T10:15:43+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `infrastructure-apply workflow에 test-server 비밀 변수 주입`
- GitHub Issue: `#245`
- Branch: `chore/gh-245-apply-secret-vars`
- Base branch: `main`
- DESIGN-ID: `D-3`(기존, `docs/reports/infrastructure/gh-229-D-3.md`,
  `APPROVED_FOR_BUILD`). 이미 승인된 apply 경로의 배선 누락을 채우는
  작업이라 새 설계 승인을 요구하지 않는다(사용자 지시, 2026-09-17).

## Objective

- `infrastructure-apply.yml`이 `infra/environments/dev/test-server`를
  apply할 때 필요한 민감 변수(`db_password`, `auth_token_secret`)를
  GitHub secret에서 주입하도록 배선한다.

## Scope

- `.github/workflows/infrastructure-apply.yml`의 `apply` job `env`
  블록에 `TF_VAR_db_password`, `TF_VAR_auth_token_secret`을 각각
  `secrets.TF_VAR_DB_PASSWORD`, `secrets.TF_VAR_AUTH_TOKEN_SECRET`에서
  주입한다.
- 버전 변수는 기본값(1)을 쓰고 이번 범위에서 다루지 않는다.

## Explicit exclusions

- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- 비밀값 회전 절차 자동화.
- 새 권한 추가(이번 변경은 GitHub secret 참조만 추가, IAM 권한 변경 없음).
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Workflow 구현 (`.github/workflows/infrastructure-apply.yml`) | `tkv00` | PR 승인, `@Byuntil`·`@tkv00` |
| GitHub secret 값 생성·등록 | 사람 | 이미 완료(TF_VAR_DB_PASSWORD, TF_VAR_AUTH_TOKEN_SECRET) |

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

- [x] `python scripts/validate-workflows.py`, `npm run hooks:validate`가
      통과한다.
- [x] `actionlint`가 통과한다.
- [x] plan/apply 원문이나 이 두 변수의 실제 값이 workflow 로그에
      노출되지 않는다(Terraform이 `sensitive = true` 변수를 자동
      마스킹하는 것으로 확인, 정적으로 검토).

## 참고

- 관련 이슈: #243(같은 발견 경위의 D-3 배선 버그 수정).
- `TF_VAR_DB_PASSWORD`, `TF_VAR_AUTH_TOKEN_SECRET` GitHub secret은 이미
  사람이 등록했다(2026-09-17).
