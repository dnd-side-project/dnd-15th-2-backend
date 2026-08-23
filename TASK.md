# GitHub Issue #189 Task Contract

> Generated at: `2026-08-23T20:09:18+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Account/Profile OpenAPI 설명 개선`
- GitHub Issue: `#189`
- Branch: `docs/gh-189-account-profile-api-description`
- Base branch: `main`

## Objective

- `#189`의 tkv00 담당 범위 중 Account/Profile 도메인의 OpenAPI 설명을
  `docs/api/OPENAPI_WRITING_GUIDE.md` 기준으로 검토하고 보강한다.
- API 런타임 동작과 계약은 변경하지 않고, `AccountApiSpec`·`ProfileApiSpec`의
  summary/description·오류 응답·parameter 및 관련 DTO 필드 설명을 실제 코드와
  일치하도록 정리한다.
- 검토 결과를 `docs/reports/gh-189-API-DOCS-REVIEW-ACCOUNT-PROFILE.md`에 먼저
  기록한 뒤 승인된 제안만 소스에 반영한다.

## Scope

- 대상 API:
  - `src/main/java/com/dnd/qello/account/web/AccountApiSpec.java`
  - `src/main/java/com/dnd/qello/account/web/ProfileApiSpec.java`
- 대상 DTO와 요청 필드:
  - `ChangeNicknameRequest`, `NicknameResponse`
  - `ProfileImageChangeRequest`, `ProfileResponse`
- `AccountController`·`ProfileController`의 구현 메서드 수와 ApiSpec 메서드 수를
  대조한다.
- 각 엔드포인트의 Service 예외, `docs/error-codes.md`,
  `SecurityConfiguration`, 현재 `docs/api/openapi.json`을 대조한다.
- 400·500 공통 응답은 `OpenApiConventionCustomizer`의 자동 처리 범위로 보고,
  엔드포인트별로 실제 발생하는 401·403·404·409·503 등만 근거를 확인한다.
- review 보고서 작성 후 승인된 범위에서 문서 애노테이션과 DTO `@Schema`만
  수정하고, 생성 테스트를 통해 OpenAPI 산출물을 갱신·검증한다.

## Explicit exclusions

- Auth, Question, Filtering/Appeal, Safety/Report 및 전체 일관성 검토
- Controller·Service·Domain·Repository의 런타임 동작 변경
- 경로, HTTP method, 상태 코드 동작, request/response 구조 변경
- `docs/api/openapi.json` 직접 편집
- GitHub Pages 제공, workflow 변경, 배포와 인프라 변경
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Account/Profile OpenAPI 문서 | `tkv00` | 사람 승인 후 `*ApiSpec` 반영 검토 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과는 clean이었다.
- `main`은 `origin/main`보다 57커밋 뒤처져 있었고, `harness start`가 최신
  `origin/main`을 fetch한 뒤 이 브랜치를 생성했다.
- 기존 #189 도메인 PR의 문서·리뷰 보고서 형식을 따른다. 다른 도메인 변경은
  이 브랜치에서 되돌리거나 재작성하지 않는다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Validation evidence (2026-08-23)

- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`:
  보강 전 기준선과 보강 후 모두 통과했다. 기준선 OpenAPI diff는 없었고, 보강 후
  `docs/api/openapi.json` diff는 63줄(추가 51, 삭제 12)이었다.
- `./harness pr-ready --project-tests`: 통과. 저장소 정책 검사와 전체
  unit/integration test 포함, `BUILD SUCCESSFUL in 13m 41s`.
- `./harness check`: 통과.
- `npm run hooks:validate`: 통과.
- `git diff --check`: 통과.
- `jq`로 네 operation의 응답 코드와 대상 DTO 필드 설명이 생성물에 반영된 것을
  확인했다.

## Completion criteria

- [x] 4개 엔드포인트의 6점 대조와 검토 보고서가 완료되었다.
- [x] `AccountApiSpec`·`ProfileApiSpec`의 설명이 목적, 선행 조건·인증, 성공 결과,
  실패 조건과 주의점을 사용자 관점의 문장으로 설명한다.
- [x] 대상 DTO의 모든 공개 필드에 필요한 `@Schema(description)`이 있다.
- [x] 엔드포인트별 오류 응답이 실제 Service 예외와 `docs/error-codes.md`에
  근거하고, 공통 400·500을 중복 선언하지 않는다.
- [x] 승인된 문서 애노테이션만 수정되었고 런타임 코드 변경이 없다.
- [x] `docs/api/openapi.json`이 생성 테스트 결과와 일치한다.
- [x] 필수 저장소 검증이 통과한다.
