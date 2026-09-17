# GitHub Issue #255 Task Contract

> Generated at: `2026-09-17T15:54:18+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `media_bucket_kms_key_arn을 secret으로 이동`
- GitHub Issue: `#255`
- Branch: `fix/gh-255-kms-arn-secret`
- Base branch: `main`

## Objective

- 실제 apply 실행 중 `TF_VAR_media_bucket_kms_key_arn`(계정 ID가 포함된
  전체 ARN)이 GitHub Actions 로그에 마스킹 없이 노출된 것을 고친다.
  GitHub Actions는 secret만 자동으로 로그를 마스킹하고 variable은
  마스킹하지 않는다 — Terraform의 `sensitive` 여부와는 무관하다.

## Scope

- `.github/workflows/infrastructure-apply.yml`에서
  `TF_VAR_media_bucket_kms_key_arn`을 `vars.MEDIA_BUCKET_KMS_KEY_ARN`
  대신 `secrets.MEDIA_BUCKET_KMS_KEY_ARN`에서 주입한다.

## Explicit exclusions

- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Workflow 구현 | `tkv00` | PR 승인(1인, #251 기준) |
| GitHub secret 값 등록/variable 삭제 | 사람/세션 | 이미 완료(2026-09-17) |

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
- [x] workflow 어디에도 `vars.MEDIA_BUCKET_KMS_KEY_ARN` 참조가 남지 않는다.

## 참고

- 발견 경위: 첫 실제 apply 실행 로그에 계정 ID가 노출됨(2026-09-17).
- 관련 이슈: #248, #229/#233/#237.
