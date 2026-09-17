# GitHub Issue #248 Task Contract

> Generated at: `2026-09-17T10:48:10+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `infrastructure-apply workflow에 media_bucket_kms_key_arn 변수 주입`
- GitHub Issue: `#248`
- Branch: `chore/gh-248-media-kms-var`
- Base branch: `main`
- DESIGN-ID: `D-3`(기존, `APPROVED_FOR_BUILD`). #245와 같은 배선 누락
  수정이라 새 설계 승인을 요구하지 않는다.

## Objective

- `infrastructure-apply.yml`이 `infra/environments/dev/test-server`를
  apply할 때 필요한 `media_bucket_kms_key_arn` 변수(기본값 없음)를
  GitHub variable에서 주입하도록 배선한다.

## Scope

- `.github/workflows/infrastructure-apply.yml`의 `apply` job `env`
  블록에 `TF_VAR_media_bucket_kms_key_arn`을
  `vars.MEDIA_BUCKET_KMS_KEY_ARN`에서 주입한다.

## Explicit exclusions

- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- 새 권한 추가.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Workflow 구현 (`.github/workflows/infrastructure-apply.yml`) | `tkv00` | PR 승인, `@Byuntil`·`@tkv00` |
| GitHub variable 값 등록 | 사람/세션 | 이미 완료(`MEDIA_BUCKET_KMS_KEY_ARN`, 2026-09-17) |

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

## 참고

- 관련 이슈: #245(같은 발견 경위), #63, #229/#233/#237.
