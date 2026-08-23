# GitHub Issue #189 Task Contract

> Generated at: `2026-08-24T02:16:38+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Filtering/Appeal OpenAPI 설명 개선`
- GitHub Issue: `#189`
- Branch: `docs/gh-189-filtering-appeal-api-description`
- Base branch: `main`

## Objective

- Filtering/Appeal 도메인의 16개 엔드포인트가 실제 동작·오류·인증 조건을 설명하도록
  OpenAPI 문서 애노테이션을 검토한다.
- `docs/api/OPENAPI_WRITING_GUIDE.md`의 `합니다`체, 5문단 순서와 6점 대조 결과를
  검토 보고서에 기록하고, 승인 전에는 `*ApiSpec`과 DTO를 수정하지 않는다.

## Scope

- 대상 `*ApiSpec`: `AppealApiSpec` 2개, `AppealCaseApiSpec` 3개,
  `FilterReleaseApiSpec` 8개, `ManualReviewCaseApiSpec` 2개,
  `SnapshotHealthApiSpec` 1개, 총 16개 엔드포인트.
- Controller ↔ ApiSpec 매핑, DTO·필드별 `@Schema`, 서비스 예외와
  `FilteringErrorCode`, 보안 체인, 생성된 OpenAPI를 대조한다.
- `FilterReleaseApiSpec`는 #190 가이드 §11의 `promote()` before/after 예시를 기준으로
  8개 전환 API의 문장·오류·인증 설명을 검토한다.
- 검토 결과와 엔드포인트별 before/after 제안을
  `docs/reports/gh-189-API-DOCS-REVIEW-FILTERING-APPEAL.md`에 기록한다.

## Explicit exclusions

- 검토 승인 전 `*ApiSpec`, Controller, DTO와 애플리케이션 소스는 수정하지 않는다.
- 서비스·도메인·repository·보안 동작·DB·마이그레이션·상태 전이는 수정하지 않는다.
- `docs/api/openapi.json`은 직접 편집하지 않는다. 승인 후에도 통합 테스트 생성물만
  반영한다.
- 새로운 엔드포인트·오류 코드·응답 필드·정책 사실을 추측해 추가하지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Filtering/Appeal OpenAPI 문서 검토 | tkv00 | 제안 승인 후 담당자 반영 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과: clean, 기존 사용자 변경 없음.

## Validation

```bash
./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"
./harness check
./harness pr-ready --project-tests
git diff --check
```

- 기준선 OpenAPI 통합 테스트: PASS (2026-08-24, BUILD SUCCESSFUL in 42s)
- 기준선 `docs/api/openapi.json` diff: 없음
- 반영 후 `./gradlew compileJava --console=plain`: PASS (BUILD SUCCESSFUL in 5s, 2026-08-24)
- 반영 후 OpenAPI 통합 테스트: PASS (BUILD SUCCESSFUL in 35s, 2026-08-24)
- 반영 후 `git diff --check`: PASS (2026-08-24)
- 반영 후 Filtering/Appeal 16개 경로 응답·보안과 10개 DTO 스키마 누락: PASS (jq 확인, 2026-08-24)
- `./harness pr-ready --project-tests`: PASS (`BUILD SUCCESSFUL in 12m 40s`, 2026-08-24)

## Completion criteria

- [x] 16개 엔드포인트의 Controller ↔ ApiSpec 매핑을 확인한다.
- [x] DTO 10개와 모든 record 필드의 `@Schema(description)` 누락을 확인한다.
- [x] 서비스에서 실제로 발생하는 오류와 오류 코드·HTTP 상태를 대조한다.
- [x] 보안 체인과 `operatorSession`·`appAccessToken` 요구를 대조한다.
- [x] 엔드포인트별 before/after 제안을 리뷰 보고서에 기록한다.
- [x] 담당자 승인 후에만 애노테이션을 반영한다.
