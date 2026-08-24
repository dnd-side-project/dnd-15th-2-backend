# GitHub Issue #189 Task Contract

> Generated at: `2026-08-23T21:50:40+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Auth OpenAPI 설명 개선`
- GitHub Issue: `#189`
- Branch: `docs/gh-189-auth-api-description`
- Base branch: `main`

## Objective

- Auth 도메인의 5개 엔드포인트가 실제 인증 흐름·오류·응답 필드를 정확히 설명하도록
  `*ApiSpec` 애노테이션과 관련 DTO 스키마 설명을 보강한다.
- `docs/api/OPENAPI_WRITING_GUIDE.md`의 `합니다`체와 문단 순서를 적용하되, 인증 동작이나
  응답 구조는 변경하지 않는다.

## Scope

- 대상 `*ApiSpec`: `CsrfTokenApiSpec`(1개), `DeviceAuthApiSpec`(2개),
  `OperatorLoginApiSpec`(2개), 총 5개 엔드포인트.
- `@Operation`의 summary·description을 실제 서비스 동작에 맞게 재작성한다.
- 서비스·도메인·`docs/error-codes.md`를 대조해 누락되었거나 모호한 엔드포인트별 오류
  응답 설명을 보강한다. 공통 400·500은 `OpenApiConventionCustomizer`에 맡긴다.
- Auth 요청·응답 DTO 7개와 `DevicePlatform` enum schema를 필드·타입 설명 대상으로 확인하고,
  누락된 `@Schema(description)`을 추가한다.
- 검토 단계의 제안은 `docs/reports/gh-189-API-DOCS-REVIEW-AUTH.md`에 먼저 기록한다.
- 승인 후 애노테이션만 반영하고 `docs/api/openapi.json`은 통합 테스트로 재생성한다.

## Explicit exclusions

- 서비스·도메인·보안 설정·컨트롤러 동작·DB·마이그레이션은 수정하지 않는다.
- 새 엔드포인트, 새 오류 코드, 인증 방식, 응답 필드와 예시 값을 추가하지 않는다.
- `docs/api/openapi.json`을 직접 편집하지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Auth `*ApiSpec`·DTO 문서 애노테이션 | tkv00 | Auth 검토 제안 승인 후 사람 리뷰 |
| OpenAPI 생성물·검증 | tkv00 | `harness-api-docs` 검증 및 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과: `M TASK.md` (하네스가 생성한 미커밋 계약 파일),
  그 외 기존 사용자 변경 없음.

## Validation

```bash
./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"
./gradlew compileJava --console=plain
./harness check
./harness pr-ready --project-tests
git diff --check
```

- 기준선 통합 테스트: PASS (2026-08-23, Auth 소스·산출물 변경 전)
- 반영 후 `./gradlew compileJava --console=plain`: PASS (`BUILD SUCCESSFUL`, 2026-08-23)
- 반영 후 OpenAPI 통합 테스트: PASS (`BUILD SUCCESSFUL`, 2회 실행, 2026-08-23)
- `./harness check`: PASS (2026-08-23)
- `./harness pr-ready --project-tests`: PASS (`BUILD SUCCESSFUL in 21m 18s`, 2026-08-23)
- `git diff --check`: PASS (2026-08-23)
- 검토 보고서 작성 후 담당자 승인 전까지 `*ApiSpec`·DTO 소스를 수정하지 않았고,
  승인 후 문서 애노테이션과 생성 산출물만 반영했다.

## Completion criteria

- [x] Auth 5개 엔드포인트의 설명이 `합니다`체와 가이드 문단 순서를 따른다.
- [x] 서비스가 실제로 낼 수 있는 Auth·Account 오류와 보안 필터 오류가 상태 코드·설명에
      정확히 반영된다.
- [x] 대상 DTO 필드와 `DevicePlatform` schema에 필요한 설명이 모두 있다.
- [x] OpenAPI 통합 테스트로 산출물을 재생성하고 의도한 diff만 확인한다.
- [x] `./harness check`, `./harness pr-ready --project-tests`, `git diff --check`가 통과한다.
