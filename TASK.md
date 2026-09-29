# GitHub Issue #284 Task Contract

> Generated at: `2026-09-23T15:52:46+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `공개 OpenAPI 설명의 정보 노출 최소화와 사용법 정리`
- GitHub Issue: `#284`
- Branch: `docs/gh-284-openapi-public-descriptions`
- Base branch: `main`

## Objective

- TASK-ID: GH-284-OPENAPI-PUBLIC-DESCRIPTIONS
- 공개 Swagger 설명에서 호출자가 필요로 하는 사용법과 응답 해석은 유지하고,
  내부 정책 임계값·판정 순서·저장 및 운영 방식처럼 불필요한 세부 설명은 덜어낸다.
- 관련 Issue: #189, #190. 두 이슈의 기존 문서 개선과 구분해 공개 정보량을 검토한다.

## Scope

- 아래 10개 `*ApiSpec`의 28개 operation에서 `@Tag`, `@Operation`,
  `@Parameter`, `@ApiResponse` 문구를 실제 Controller·DTO·Service 동작과 대조해 정리한다.
  - `direction/web/{ActiveUserPresenceApiSpec,DirectionPostApiSpec,PostReactionApiSpec}.java`
  - `feed/web/{InboxApiSpec,SentPostApiSpec,AnswerReadApiSpec}.java`
  - `answer/web/{AnswerSubmissionApiSpec,MediaAssetApiSpec,AnswerReactionApiSpec}.java`
  - `notification/web/NotificationApiSpec.java`
- 위 API의 request/response DTO `@Schema` 문구에서 같은 문제를 발견하면
  해당 문구만 수정한다. DTO 구조나 검증 규칙은 변경하지 않는다.
- 저장소의 모든 `*ApiSpec`에서 `@ApiResponse` 설명 안의 오류 코드 식별자를 제거한다.
  이 전역 표기 정리는 아래 28개 operation 외의 API에도 적용한다.
- 공통 OpenAPI 설명에서 `docs/error-codes.md`를 직접 안내하는 문구를 제거한다.
  공통 400 설명은 오류 코드 문서 경로 없이 사용자에게 보이는 입력 오류만 설명한다.
- 이후 검토하는 API에도 동일 기준을 적용하며, 이 기준은 개별 도메인에 한정되지 않는다.
- 공통 문서 설명은 다음 파일만 수정한다.
  - `src/main/java/com/dnd/qello/common/openapi/OpenApiConfiguration.java`
  - `src/main/java/com/dnd/qello/common/openapi/OpenApiConventionCustomizer.java`
- `docs/api/openapi.json`은 생성 테스트로 재생성하고 의도한 문구 변경만 반영한다.
- 도메인별로 한 API씩 검토하며, 호출 순서·필수 입력·응답 해석·클라이언트가
  처리할 오류를 남긴다.

## Explicit exclusions

- API 경로, HTTP 메서드·상태 코드, 오류 코드 값, 인증·권한 정책,
  request/response 구조와 런타임 동작 변경.
- Controller 구현, Service·Domain·Repository, DB·인프라, 문서 배포 방식 변경.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| OpenAPI 애노테이션 및 산출물 | API 문서 실행 역할 | 실제 변경과 생성 스펙의 독립 검토 |

## Existing user-owned changes

- 브랜치 생성 전 `git status --short` 결과가 비어 있었다. 보존할 사용자 변경은 없다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"
git diff --check
```

## Completion criteria

- [x] 28개 operation의 설명을 실제 동작과 대조했다.
- [x] 프론트가 호출 방법과 응답·오류 처리에 필요한 정보를 찾을 수 있다.
- [x] 호출자에게 필요 없는 내부 임계값·판정 순서·저장·운영 세부 설명을 덜어냈다.
- [x] 모든 `*ApiSpec` 응답 설명과 공통 OpenAPI 설명에 오류 코드 식별자 및
      `docs/error-codes.md` 안내 문구가 남지 않았다.
- [x] API 계약과 구현 동작은 유지했다.
- [x] `docs/api/openapi.json`을 재생성하고 의도한 문구 변경만 확인했다.
- [x] 필수 검증을 실행하고 미실행 검증이 있으면 이유와 남은 위험을 기록했다.

## 추가 범위: GitHub Issue #286

사용자 결정(2026-09-29)에 따라 #286 작업을 새 PR로 나누지 않고 이 브랜치와 PR에
누적한다. 커밋은 브랜치 규칙에 맞춰 `(#284)`로 남기고, PR 본문에 `Closes #286`을
함께 적는다. 위 기준(내부 세부정보 제외, 호출에 필요한 정보만 유지)을 그대로 적용한다.

- 대상: 위 28개 operation에서 문구를 검토하지 않은 14개 `*ApiSpec`의 43개 operation
  - `account/web/{AccountApiSpec,ProfileApiSpec}.java`
  - `auth/web/{CsrfTokenApiSpec,DeviceAuthApiSpec,OperatorLoginApiSpec}.java`
  - `filtering/web/{AppealApiSpec,AppealCaseApiSpec,FilterReleaseApiSpec,ManualReviewCaseApiSpec,SnapshotHealthApiSpec}.java`
  - `question/web/{QuestionProposalApiSpec,OperatorQuestionProposalApiSpec}.java`
  - `safety/web/{SafetyApiSpec,OperatorReportCaseApiSpec}.java`
- 같은 용어를 쓰는 DTO `@Schema` 문구만 함께 맞춘다(구조·검증 규칙 변경 없음).
- 운영자 전용 API는 상태값과 처리 흐름을 유지한다.
- 제외 항목은 위 `Explicit exclusions`와 같다.

- [x] 43개 operation의 문구를 Controller·요청/응답 모델·Service 예외와 대조했다.
- [x] 이슈 번호, 저장·해시 방식, 정책 기간, 내부 모델·서비스 용어를 설명에서 제외했다.
- [x] API 계약과 런타임 동작은 유지했다.
- [x] `docs/api/openapi.json`을 재생성하고 문구와 태그 순서 외의 변경이 없음을 확인했다.
- [x] 필수 검증(`./harness pr-ready --project-tests` 포함)을 실행했다.
