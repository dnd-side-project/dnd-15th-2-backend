# GitHub Issue #189 Task Contract

> Generated at: `2026-08-24T10:54:09+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`와
> `docs/api/OPENAPI_WRITING_GUIDE.md`를 따른다.

## Work gate

- Title: `OpenAPI 설명 개선`
- GitHub Issue: `#189`
- Branch: `docs/gh-189-account-safety-report-api-description`
- Base branch: `main`
- Guide: `#190`, `docs/api/OPENAPI_WRITING_GUIDE.md`

## Objective

- #189의 남은 Account/Profile 및 Safety/Report OpenAPI 설명을 실제 코드와 대조한다.
- Account/Profile은 최신 `origin/main`의 선행 반영 상태를 재검증하고, 남은 문제가 있을 때만 문서 애노테이션과 DTO 설명을 보강한다.
- Safety/Report의 앱 사용자 신고·차단 API와 운영자 사건 처리 API를 #190 가이드의 6점 대조 기준으로 검토·보강한다.
- API 런타임 동작, 경로, 상태 코드, 요청·응답 구조와 보안 동작은 변경하지 않는다.

## Scope

- `AccountApiSpec`, `ProfileApiSpec` 및 관련 Account/Profile DTO의 선행 문서 상태 확인.
- `SafetyApiSpec`, `OperatorReportCaseApiSpec`의 summary, description, path/query parameter, 인증과 실제 오류 응답 설명.
- Safety/Report request·response DTO 공개 필드의 `@Schema(description)` 보강.
- Controller ↔ ApiSpec 메서드·매핑, Service 예외, `docs/error-codes.md`, `SecurityConfiguration`, `docs/api/openapi.json` 6점 대조.
- review 보고서 `docs/reports/gh-189-API-DOCS-REVIEW-SAFETY-REPORT.md` 작성.
- 생성 테스트를 통해 `docs/api/openapi.json`을 갱신하고 diff를 검증한다.

## Explicit exclusions

- API 동작·비즈니스 로직·DB·보안 설정 변경.
- 경로, HTTP method, 상태 코드, request/response 구조 변경.
- `docs/api/openapi.json` 직접 편집.
- GitHub Pages, workflow, 배포, 인프라 변경.
- 새 GitHub Issue 생성.
- Secret, 계정 식별자, 토큰, `.env` 값 기록.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Account/Profile·Safety/Report OpenAPI 문서 | `tkv00` | 사람의 문서 검토 후 PR 승인 |

## Existing user-owned changes

- 작업 시작 시 확인한 미추적 `docs/reports/harness/`는 기존 사용자 변경으로 보존했다.
- 작업 중 이 디렉터리를 수정하지 않는다.
- 최신 `origin/main`은 Account/Profile 선행 문서 변경(#195)을 포함한다.

## Validation

```bash
./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Validation evidence (2026-08-24)

- `./gradlew compileJava`: PASS.
- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`: PASS.
- `./harness check`: PASS.
- `./harness pr-ready --project-tests`: PASS. 전체 unit/integration test 포함, `BUILD SUCCESSFUL in 41m`.
- `git diff --check`: PASS.
- `jq -e . docs/api/openapi.json`: PASS.
- `npm run hooks:validate`: PASS. WSL 2 Ubuntu 환경에서 `Husky validation passed`.

## Completion criteria

- [x] Account/Profile 선행 문서와 기존 review 결과를 재검증했다.
- [x] Safety/Report 모든 `*ApiSpec` 엔드포인트의 6점 대조와 review 보고서를 완료했다.
- [x] 실제 서비스 예외와 오류 코드에 근거한 응답 설명을 반영했다.
- [x] path/query parameter와 공개 DTO 필드 설명이 실제 코드와 일치한다.
- [x] 승인된 문서 애노테이션과 DTO `@Schema`만 수정했다.
- [x] 생성 테스트 후 `docs/api/openapi.json`이 최신 코드와 일치한다.
- [x] 필수 저장소 검증이 통과한다.
