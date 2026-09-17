# GitHub Issue #272 Task Contract

> Generated at: `2026-09-17T21:11:01+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `컨테이너가 IMDS에 도달하도록 홉 제한 조정`
- GitHub Issue: `#272`
- Branch: `fix/gh-272-imds-hop-limit`
- Base branch: `main`

## Objective

- apply 성공만으로는 서버가 제대로 동작하지 않는 결함을 없앤다. 컨테이너가
  IMDS에 도달할 수 없어 인스턴스 Role 자격 증명을 받을 수 없었다.

## Scope

- `infra/modules/app-server/main.tf`의 `metadata_options`에서
  `http_put_response_hop_limit`을 1에서 2로 올리고 이유를 주석에 남긴다.

## Explicit exclusions

- `http_tokens = "required"`(IMDSv2 강제)는 유지한다.
- 정적 Access Key를 컨테이너에 주입하는 대안은 쓰지 않는다(AGENTS.md 4.9).
- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Terraform 모듈 | `tkv00` | PR 승인(1인, #251 기준) |

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

- [x] `http_put_response_hop_limit`이 2이고 이유가 주석에 남는다
- [x] IMDSv2 강제(`http_tokens = "required"`)가 유지된다
- [x] `terraform fmt`/`tflint`/`checkov` 통과, 새 findings 없음
- [x] `./harness pr-ready --project-tests` 통과

## 참고

- 발견 경위: apply 이후 런타임까지 확인하기 위해 user_data와 `dev` 프로필
  자격 증명 경로를 감사하다 찾았다(2026-09-17).
- 함께 검증한 항목: 렌더링된 user_data의 bash 문법, 생성되는 compose 파일의
  `docker compose config` 통과, compose 바이너리 URL 응답, `dev` 프로필 필수
  환경변수 대조(푸시 관련 10개는 `@Profile` 제외로 오탐 — 기존 통합 테스트가
  이를 보장한다).
- 관련 이슈: #229/#233/#268/#270.
