# GitHub Issue #189 Task Contract

> Generated at: `2026-08-23T23:18:00+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Question OpenAPI 설명 개선`
- GitHub Issue: `#189`
- Branch: `docs/gh-189-question-api-description`
- Base branch: `main`

## Objective

- Question 도메인의 5개 엔드포인트가 질문 제안 제출·조회와 운영자 검수 흐름을
  실제 동작·오류·응답 필드에 맞게 설명하도록 OpenAPI 애노테이션을 보강한다.
- `docs/api/OPENAPI_WRITING_GUIDE.md`의 `합니다`체와 5문단 순서를 적용하되 질문 제안
  상태 전이와 인증 동작은 변경하지 않는다.

## Scope

- 대상 `*ApiSpec`: `QuestionProposalApiSpec` 2개, `OperatorQuestionProposalApiSpec` 3개,
  총 5개 엔드포인트.
- 각 `@Operation` description을 무엇을 하는가·선행조건·성공 결과·실패 조건·주의점
  순서로 재작성한다.
- `QuestionProposalApplicationService`, `QuestionReviewService`, Question 도메인 오류 코드와
  `docs/error-codes.md`를 대조해 엔드포인트별 오류 응답을 보강한다. 공통 400·500은
  `OpenApiConventionCustomizer`에 맡긴다.
- Question 요청·응답 DTO의 record·필드 설명을 전수 확인하고, 누락된 `@Schema(description)`과
  한다체 문장을 보강한다. API에 노출되는 `AnswerFormat` enum 설명도 확인한다.
- 검토 제안은 `docs/reports/gh-189-API-DOCS-REVIEW-QUESTION.md`에 먼저 기록하고,
  담당자 승인 후 애노테이션만 반영한다.

## Explicit exclusions

- 서비스·도메인·repository·보안 설정·컨트롤러 동작·DB·마이그레이션은 수정하지 않는다.
- 새 엔드포인트, 상태 전이, 오류 코드, 응답 필드와 실제 식별자·예시 비밀값은 추가하지 않는다.
- `docs/api/openapi.json`은 직접 편집하지 않고 통합 테스트 산출물만 반영한다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Question `*ApiSpec`·DTO 문서 애노테이션 | tkv00 | 검토 제안 승인 후 사람 리뷰 |
| OpenAPI 생성물·검증 | tkv00 | `harness-api-docs` 검증 및 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과: `M TASK.md` (하네스가 생성한 미커밋 계약 파일),
  그 외 기존 사용자 변경 없음.

## Validation

```bash
./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"
./harness check
./harness pr-ready --project-tests
git diff --check
```

- 기준선 통합 테스트: PASS (2026-08-23, Question 소스·산출물 변경 전)
- 반영 후 `./gradlew compileJava --console=plain`: PASS (`BUILD SUCCESSFUL`, 2026-08-24)
- 반영 후 OpenAPI 통합 테스트: PASS (`BUILD SUCCESSFUL`, 2026-08-24)
- `./harness check`: PASS (2026-08-24)
- `./harness pr-ready --project-tests`: PASS (`BUILD SUCCESSFUL in 13m 50s`, 2026-08-24)
- `git diff --check`: PASS (2026-08-24)
- 검토 보고서 작성 후 담당자 승인 전까지 소스를 수정하지 않았고, 승인 후 문서 애노테이션과
  생성 산출물만 반영했다.

## Completion criteria

- [x] Question 5개 엔드포인트의 설명이 `합니다`체와 가이드 문단 순서를 따른다.
- [x] 서비스가 실제로 낼 수 있는 Question·공통 인증 오류가 상태 코드·설명에 정확히 반영된다.
- [x] 대상 DTO 필드와 API 노출 enum에 필요한 설명이 모두 있다.
- [x] OpenAPI 통합 테스트로 산출물을 재생성하고 Question 범위 diff만 확인한다.
- [x] `./harness check`, `./harness pr-ready --project-tests`, `git diff --check`가 통과한다.
