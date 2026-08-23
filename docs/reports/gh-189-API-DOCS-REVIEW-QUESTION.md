# API Docs Review: Question

> Created at: `2026-08-23T23:30:00+09:00`
> GitHub Issue: `#189`
> Target: `src/main/java/com/dnd/qello/question/web/{QuestionProposalApiSpec,OperatorQuestionProposalApiSpec}.java`
> 문장 기준: `docs/api/OPENAPI_WRITING_GUIDE.md`

이 문서는 Question 도메인 2개 `*ApiSpec`의 문장·오류 응답·스키마 검토 결과다.
`harness-api-docs` 스킬의 `review` 모드로 작성했으며, 승인 전에는 `*ApiSpec`·컨트롤러·
DTO 소스를 수정하지 않는다.

> **반영 완료 (2026-08-24).** 담당자 승인 후 아래 제안과 DTO 스키마 설명을 애노테이션으로
> 반영하고 OpenAPI 산출물을 재생성했다. §2와 §5에 반영 결과를 기록한다.

## 1. Executive summary

- 대상 엔드포인트 수: **5개** (`QuestionProposalApiSpec` 2개,
  `OperatorQuestionProposalApiSpec` 3개)
- 발견된 문제 범주:
  - 5개 operation description 모두 가이드의 5문단 흐름이 없고, 일부는 내부 상태명과
    `append-only`를 설명 없이 노출한다.
  - 오류 응답의 공통 인증 코드·Question 오류 코드가 빠졌고, 사용자 조회에는 403·404,
    운영자 검수에는 401·403 응답이 누락돼 있다.
  - 응답 DTO 3개의 24개 필드와 3개 record 타입에 `@Schema(description)`이 없다.
    요청 DTO 3개도 타입 설명이 없고, 요청 필드 문장과 `AnswerFormat` enum 설명을 보강한다.
  - 운영자 경로의 `proposalId` parameter 설명이 `id`로만 적혀 있다.
- 6점 대조 중 실행하지 못한 항목: **없음**. 기준선 OpenAPI 통합 테스트까지 실행했다.
- 승인 후 반영한 문제 범주: **전부**. operation description 5개, 오류·인증 응답 보강,
  Question DTO·enum·parameter 설명을 반영했다.
- 현재 브랜치: `docs/gh-189-question-api-description`

Question 도메인 오류 enum에는 `QUE-APP-002`(제안 없음), `QUE-APP-003`(제안자 계정 없음),
`QUE-APP-004`(제안 불가 계정)가 정의되어 있지만 `docs/error-codes.md` §7에는 세 코드가
빠져 있다. 이 문서 불일치는 별도 문서 정리 대상으로 남기며, 이번 작업에서는 실제 enum과
서비스가 사용하는 코드를 API 설명에 반영한다.

## 2. 6점 대조 결과

`docs/api/OPENAPI_WRITING_GUIDE.md` §9 기준으로 Question 도메인 전체를 대조했다.

| # | 대조 | 결과 | 근거 |
| --- | --- | --- | --- |
| 1 | Controller ↔ ApiSpec | **이상 없음** | `QuestionProposalController` 2개와 `OperatorQuestionProposalController` 3개 구현 메서드가 각 인터페이스의 매핑 5개와 1:1이다. |
| 2 | ApiSpec ↔ DTO | **보강 필요** | 요청 DTO 3개에는 필드 설명이 있으나 타입 설명이 없다. `ApprovedQuestionResponse` 10개, `QuestionProposalResponse` 8개, `QuestionProposalReviewResponse` 6개 필드에 설명이 없다. `AnswerFormat` enum에도 설명이 없다. |
| 3 | ApiSpec ↔ Service | **오류 응답 보강 필요** | `QuestionProposalApplicationService`는 `QUE-APP-003`·`QUE-APP-004`를, `QuestionReviewService`는 `QUE-APP-002`와 상태 충돌 `QUE-DOM-002`를 낸다. `QuestionProposal`·`ApprovedQuestion`·`QuestionProposalReview`의 입력·시각 검증은 `QUE-VAL-002/003/004`로 수렴한다. |
| 4 | ApiSpec ↔ `docs/error-codes.md` | **문서 불일치 기록** | `QUE-VAL-*`, `QUE-DOM-*`, `QUE-INFRA-*`, `QUE-APP-001`의 HTTP 상태는 표와 일치하지만 실제 enum의 `QUE-APP-002/003/004`가 §7에 없다. |
| 5 | ApiSpec ↔ SecurityConfiguration | **인증 요구는 맞지만 응답 설명 부족** | `/api/**` Question 경로는 `appAccessToken`, `/admin/questions/**`는 `operatorSession`과 CSRF를 요구한다. 현재 사용자 경로의 401만 있고 403·404가 일부 빠졌으며 운영자 경로에는 필터 401·403이 없다. |
| 6 | ApiSpec ↔ `docs/api/openapi.json` | **기준선·반영 후 이상 없음** | 소스·산출물 변경 전과 승인 후에 `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest" --console=plain`을 실행했고 모두 PASS했다. 반영 후 diff는 Question 경로와 Question 스키마 설명으로만 구성된다. |

### 2.1 반영 후 재대조

- Question 5개 operation description에서 가이드 금지 종결(`한다`, `된다`, `이다`, `없다`,
  `않다`) **0건**을 확인했다.
- Question 요청·응답 record 6개와 `AnswerFormat`을 재파싱한 결과 class·필드 description
  누락 **0건**을 확인했다.
- Question 경로 응답 코드는 다음과 같이 반영됐다.
  - 제출: `201,400,401,403,404,500`
  - 내 제안 조회: `200,400,401,403,404,500`
  - 검수 시작·승인·반려: 각각 `200,400,401,403,404,409,500`
- `docs/api/openapi.json` diff에는 다른 도메인의 path·schema 변경이 없다.

## 3. 엔드포인트별 제안

### `POST /api/v1/questions/proposals` — `QuestionProposalApiSpec.submit`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 404 `QUE-APP-003`; 400·401·403 설명의 오류 코드 보강 |
| 누락된 `@Schema(description)` 필드 | 없음. `SubmitQuestionProposalRequest` 필드 설명은 있으나 record 타입 설명이 없다. |
| 문장 기준 위반 | 한 문장 description, 인증·성공·실패·주의점 문단 누락 |

**Before**

```java
@Operation(
	summary = "질문 제안 제출",
	description = "제안을 생성과 동시에 제출 상태로 전이합니다. 임시저장 단계는 없습니다.")
```

**After**

```java
@Operation(
	summary = "질문 제안 제출",
	description = """
		사용자가 질문 문구를 제안하고 즉시 제출합니다. 임시저장은 만들지 않습니다.

		앱 액세스 토큰이 필요합니다. 토큰의 계정은 ACTIVE 상태의 USER여야 하며, 제안 문구는
		요청 본문으로 보냅니다.

		성공하면 SUBMITTED 상태의 제안과 제출 시각을 반환합니다. 운영자 검수가 끝나야 승인 또는
		반려 상태로 바뀝니다.

		문구가 비어 있거나 2,000자를 초과하면 제출하지 않습니다. 계정을 찾을 수 없거나 질문
		제안을 사용할 수 없는 계정이면 거절합니다.

		이 호출만으로 질문이 앱에 배정되지는 않습니다. 운영자가 검수하고 승인해야 승인 질문
		풀에 추가됩니다.""")
```

`@ApiResponses` 보강안:

```java
@ApiResponse(responseCode = "400",
	description = "제안 문구가 비어 있거나 2,000자를 초과했습니다. (CMN-VAL-001, QUE-VAL-002, QUE-VAL-003)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "401",
	description = "앱 액세스 토큰이 없거나 유효하지 않습니다. (CMN-VAL-003)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "403",
	description = "현재 계정은 질문을 제안할 수 없습니다. (QUE-APP-004)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "404",
	description = "질문을 제안할 사용자 계정을 찾을 수 없습니다. (QUE-APP-003)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class)))
```

**DTO 제안**

```java
@Schema(description = "질문 제안을 제출할 때 보내는 사용자 입력입니다.")
public record SubmitQuestionProposalRequest(
	@Schema(description = "제안하는 질문 문구입니다.", example = "요즘 가장 몰두하고 있는 취미는 무엇인가요?")
	String proposedText
) {}
```

**변경 근거**

- `QuestionProposalApplicationService.submit`은 ACTIVE USER 계정을 확인한 뒤
  `QuestionReviewService.propose`를 호출한다. 계정 없음은 `QUE-APP-003`, USER가 아니거나
  ACTIVE가 아니면 `QUE-APP-004`다.
- `QuestionProposal.create`와 `submit`은 문구·제출 시각을 검증하고, 요청의 `@NotBlank`·
  `@Size(max = 2_000)` 실패는 공통 400으로 처리된다.
- 이 경로는 DRAFT를 만들고 즉시 SUBMITTED로 전이하므로 임시저장 API가 아니다.

### `GET /api/v1/questions/proposals/me` — `QuestionProposalApiSpec.findMine`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 403 `QUE-APP-004`, 404 `QUE-APP-003`; 401 설명의 공통 오류 코드 보강 |
| 누락된 `@Schema(description)` 필드 | `QuestionProposalResponse` 8개 필드와 record 타입 설명 |
| 문장 기준 위반 | 한 문장 description, 조회 범위·인증·빈 목록·주의점 문단 누락 |

**Before**

```java
@Operation(
	summary = "내가 제안한 질문 목록 조회",
	description = "인증 사용자가 제출한 모든 제안을 최신 제출 순으로 반환합니다.")
```

**After**

```java
@Operation(
	summary = "내가 제안한 질문 목록 조회",
	description = """
		인증 사용자가 제출한 질문 제안 목록을 최신 생성 순으로 조회합니다.

		앱 액세스 토큰이 필요합니다. 토큰의 계정은 ACTIVE 상태의 USER여야 합니다.

		성공하면 본인이 제출한 제안을 createdAt 기준 내림차순으로 반환합니다. 제안이 없으면
		빈 목록을 반환합니다.

		계정을 찾을 수 없거나 질문 제안을 사용할 수 없는 계정이면 목록을 조회할 수 없습니다.

		다른 사용자의 제안은 포함하지 않습니다. 각 항목의 decisionReason은 반려된 제안에서만
		값이 있을 수 있습니다.""")
```

`@ApiResponses`에는 401에 `(CMN-VAL-003)`을 보강하고 다음을 추가한다.

```java
@ApiResponse(responseCode = "403",
	description = "현재 계정은 질문 제안을 사용할 수 없습니다. (QUE-APP-004)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "404",
	description = "질문을 제안할 사용자 계정을 찾을 수 없습니다. (QUE-APP-003)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class)))
```

**DTO 제안**

```java
@Schema(description = "질문 제안의 상태와 제출 정보를 담는 응답입니다.")
public record QuestionProposalResponse(
	@Schema(description = "질문 제안 식별자입니다.") long id,
	@Schema(description = "제안한 사용자 계정 식별자입니다.") long proposerId,
	@Schema(description = "질문 제안 상태입니다.") String status,
	@Schema(description = "제안한 질문 문구입니다.") String proposedText,
	@Schema(description = "반려 사유입니다. 반려된 제안에서만 값이 있을 수 있습니다.") String decisionReason,
	@Schema(description = "질문을 제출한 시각입니다.") Instant submittedAt,
	@Schema(description = "질문 제안을 만든 시각입니다.") Instant createdAt,
	@Schema(description = "질문 제안이 마지막으로 변경된 시각입니다.") Instant updatedAt
) {}
```

**변경 근거**

- `findMine`은 `findAllByProposerIdOrderByCreatedAtDesc`를 호출하므로 다른 사용자의 제안은
  조회되지 않고 결과가 없으면 빈 목록이다.
- 조회 전에도 `ensureActiveUser`를 호출하므로 submit과 같은 `QUE-APP-003/004`가 발생한다.

### `POST /admin/questions/proposals/{proposalId}/review` — `OperatorQuestionProposalApiSpec.startReview`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001`; 404·409 설명의 Question 오류 코드 보강 |
| 누락된 `@Schema(description)` 필드 | `QuestionProposalResponse` 8개 필드 (위 제안과 공유) |
| 문장 기준 위반 | 한 문장 description, 운영자 인증·CSRF·성공·실패·주의점 문단 누락; `proposalId` parameter 설명이 `id`로만 되어 있음 |

**Before**

```java
@Operation(
	summary = "제안 검수 시작",
	description = "SUBMITTED 제안을 UNDER_REVIEW로 전이합니다. 승인·반려 전 반드시 거쳐야 합니다.")
```

**After**

```java
@Operation(
	summary = "제안 검수 시작",
	description = """
		운영자가 제출된 질문 제안을 검수 중인 상태로 전환합니다.

		운영자 세션과 CSRF 토큰이 필요합니다. 검수 대상 제안은 사용자가 먼저 제출한 상태여야
		합니다.

		성공하면 제안 상태를 UNDER_REVIEW로 바꾼 제안 정보를 반환합니다.

		제안 식별자가 없거나 이미 검수·승인·반려된 상태이면 검수를 시작하지 않습니다.

		검수를 시작한 뒤에만 승인 또는 반려 API를 호출할 수 있습니다. 이 API 자체는 승인이나
		반려 판정을 기록하지 않습니다.""")
```

`proposalId`에는 다음 설명을 적용하고 응답을 보강한다.

```java
@Parameter(description = "검수를 시작할 질문 제안 식별자입니다.") @PathVariable long proposalId

@ApiResponse(responseCode = "401",
	description = "운영자 세션이 없거나 만료되었습니다. (CMN-VAL-003)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "403",
	description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "404",
	description = "질문 제안을 찾을 수 없습니다. (QUE-APP-002)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "409",
	description = "제안이 SUBMITTED 상태가 아니어서 검수를 시작할 수 없습니다. (QUE-DOM-002)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class)))
```

**변경 근거**

- `QuestionReviewService.startReview`는 `findByIdForUpdate`로 제안을 찾고
  `QuestionProposal.startReview`를 호출한다. 조회 실패는 `QUE-APP-002`, 상태 전이 실패는
  `QUE-DOM-002`다.
- `/admin/questions/**`는 backofficeSecurityFilterChain에 속하므로 운영자 세션·CSRF와
  `CMN-VAL-003`·`CMN-DOM-001` 응답이 필요하다.

### `POST /admin/questions/proposals/{proposalId}/approve` — `OperatorQuestionProposalApiSpec.approve`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001`; 400·404·409 설명의 코드 보강 |
| 누락된 `@Schema(description)` 필드 | `ApproveQuestionProposalRequest` record 타입, `ApprovedQuestionResponse` 10개 필드와 record 타입 |
| 문장 기준 위반 | 한 문장 description, 운영자 인증·입력 조건·성공 결과·실패·주의점 문단 누락; `proposalId` 설명이 `id`로만 되어 있음 |

**Before**

```java
@Operation(
	summary = "제안 승인",
	description = "UNDER_REVIEW 제안을 승인해 승인 질문 풀에 활성 질문으로 추가합니다.")
```

**After**

```java
@Operation(
	summary = "제안 승인",
	description = """
		운영자가 검수 중인 질문 제안을 승인하고 활성 질문 풀에 추가합니다.

		운영자 세션과 CSRF 토큰이 필요합니다. 승인할 제안은 먼저 검수를 시작한 상태여야 하며,
		허용할 답변 형식과 활성 시작 시각을 함께 보내야 합니다.

		성공하면 승인 질문 식별자·문구·답변 형식·활성 기간과 승인 정보를 반환합니다.

		제안을 찾을 수 없거나 검수 중인 상태가 아니면 승인하지 않습니다. 활성 종료 시각을
		보냈다면 시작 시각보다 늦어야 합니다.

		승인하면 제안 상태와 승인 질문이 함께 기록됩니다. 활성 시작 시각이 현재보다 미래라면
		즉시 배정되는 것이 아니라 해당 시각부터 배정 대상이 됩니다.""")
```

`@ApiResponses`에는 다음 코드를 반영하고 `proposalId` 설명을 바꾼다.

```java
@ApiResponse(responseCode = "400",
	description = "답변 형식·활성 시작 시각이 없거나 활성 기간의 순서가 올바르지 않습니다. (CMN-VAL-001, QUE-VAL-002, QUE-VAL-004)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 만료되었습니다. (CMN-VAL-003)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "404", description = "질문 제안을 찾을 수 없습니다. (QUE-APP-002)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "409", description = "제안이 UNDER_REVIEW 상태가 아니어서 승인할 수 없습니다. (QUE-DOM-002)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class)))
```

**DTO 제안**

```java
@Schema(description = "질문 제안을 승인할 때 보내는 운영자 입력입니다.")
public record ApproveQuestionProposalRequest(
	@Schema(description = "승인 질문에 허용할 답변 형식입니다.") AnswerFormat answerFormat,
	@Schema(description = "질문을 배정할 수 있게 되는 시각입니다. 이 시각을 포함합니다.") Instant activeFrom,
	@Schema(description = "질문 배정을 중단할 시각입니다. 이 시각은 포함하지 않으며, 비우면 무기한 활성입니다.") Instant activeUntil
) {}

@Schema(description = "승인된 질문의 상태와 활성 기간을 담는 응답입니다.")
public record ApprovedQuestionResponse(
	@Schema(description = "승인 질문 식별자입니다.") long id,
	@Schema(description = "이 승인 질문을 만든 질문 제안 식별자입니다.") Long sourceProposalId,
	@Schema(description = "질문 출처입니다. 사용자 제안 승인 결과에서는 USER_PROPOSAL입니다.") String sourceType,
	@Schema(description = "승인 질문 상태입니다. 승인 결과에서는 ACTIVE입니다.") String status,
	@Schema(description = "승인된 질문 문구입니다.") String questionText,
	@Schema(description = "허용할 답변 형식입니다.") String answerFormat,
	@Schema(description = "질문을 배정할 수 있게 되는 시각입니다.") Instant activeFrom,
	@Schema(description = "질문 배정을 중단하는 시각입니다. null이면 종료 시각이 없습니다.") Instant activeUntil,
	@Schema(description = "질문을 승인한 시각입니다.") Instant approvedAt,
	@Schema(description = "승인한 운영자 계정 식별자입니다.") Long approvedBy
) {}
```

**변경 근거**

- `QuestionReviewService.approve`는 제안을 `UNDER_REVIEW`에서 승인하고
  `ApprovedQuestion.activeUserProposal`로 ACTIVE 질문을 만든다.
- `ApproveQuestionProposalRequest`의 `@NotNull`과 `ApprovedQuestion`의 `activeUntil > activeFrom`
  검증으로 400 사유가 결정된다. 제안 조회·상태 전이는 `QUE-APP-002`·`QUE-DOM-002`다.
- 활성 시작 시각은 서버 승인 시각과 별개이므로 미래 시각을 보내면 즉시 배정된다고 설명하지
  않는다.

### `POST /admin/questions/proposals/{proposalId}/reject` — `OperatorQuestionProposalApiSpec.reject`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001`; 400·404·409 설명의 코드 보강 |
| 누락된 `@Schema(description)` 필드 | `RejectQuestionProposalRequest` record 타입, `QuestionProposalReviewResponse` 6개 필드와 record 타입 |
| 문장 기준 위반 | 한 문장 description, `append-only` 내부 용어 설명 부족, 운영자 인증·성공·실패·주의점 문단 누락; `reason` 설명이 한다체이고 `proposalId` 설명이 `id`로만 되어 있음 |

**Before**

```java
@Operation(
	summary = "제안 반려",
	description = "UNDER_REVIEW 제안을 반려하고 사유를 append-only 이력으로 남깁니다.")
```

**After**

```java
@Operation(
	summary = "제안 반려",
	description = """
		운영자가 검수 중인 질문 제안을 반려하고 반려 사유를 판정 이력으로 기록합니다.

		운영자 세션과 CSRF 토큰이 필요합니다. 반려할 제안은 먼저 검수를 시작한 상태여야 하며,
		제안자에게 전달될 수 있는 사유를 요청 본문으로 보냅니다.

		성공하면 반려 판정, 사유, 판정 시각과 운영자 식별자를 반환합니다.

		제안을 찾을 수 없거나 검수 중인 상태가 아니면 반려하지 않습니다. 사유가 비어 있거나
		2,000자를 초과해도 요청을 처리하지 않습니다.

		판정 이력은 수정하지 않고 추가 기록으로 남깁니다. 반려된 제안을 이 API로 다시 검수
		상태로 되돌리는 경로는 없습니다.""")
```

`@ApiResponses`에는 다음 코드를 반영하고 `reason`·`proposalId` 설명을 바꾼다.

```java
@ApiResponse(responseCode = "400",
	description = "반려 사유가 비어 있거나 2,000자를 초과했습니다. (CMN-VAL-001, QUE-VAL-002, QUE-VAL-003)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 만료되었습니다. (CMN-VAL-003)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "404", description = "질문 제안을 찾을 수 없습니다. (QUE-APP-002)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class))),
@ApiResponse(responseCode = "409", description = "제안이 UNDER_REVIEW 상태가 아니어서 반려할 수 없습니다. (QUE-DOM-002)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class)))
```

**DTO 제안**

```java
@Schema(description = "질문 제안을 반려할 때 보내는 운영자 입력입니다.")
public record RejectQuestionProposalRequest(
	@Schema(description = "반려 사유입니다. 제안자에게 그대로 노출될 수 있습니다.", example = "이미 승인된 질문과 의미가 중복됩니다")
	String reason
) {}

@Schema(description = "질문 제안에 대한 운영자 판정 한 건을 담는 응답입니다.")
public record QuestionProposalReviewResponse(
	@Schema(description = "판정 이력 식별자입니다.") long id,
	@Schema(description = "판정한 질문 제안 식별자입니다.") long proposalId,
	@Schema(description = "판정한 운영자 계정 식별자입니다.") long reviewerId,
	@Schema(description = "판정 결과입니다. 반려 결과에서는 REJECTED입니다.") String decision,
	@Schema(description = "반려 사유입니다. 승인 판정에서는 null입니다.") String reason,
	@Schema(description = "판정한 시각입니다.") Instant reviewedAt
) {}
```

**변경 근거**

- `QuestionReviewService.reject`는 제안을 `UNDER_REVIEW`에서 REJECTED로 바꾸고
  `QuestionProposalReview.reject`를 저장한다. 조회 실패·상태 충돌은 각각 `QUE-APP-002`·
  `QUE-DOM-002`다.
- `RejectQuestionProposalRequest`의 `@NotBlank`·`@Size(max = 2_000)`와 domain `requireReason`
  검증으로 400 사유를 설명한다.
- 판정 이력과 outbox event는 transaction에서 저장되지만, 이 API가 실제 알림 전달까지
  완료한다고 표현하지 않는다.

## 4. 반영하지 않은 제안

- `docs/error-codes.md` §7에 실제 enum의 `QUE-APP-002/003/004`가 누락되어 있다. 이 브랜치에서는
  오류 코드 표를 수정하지 않고 API 애노테이션에 실제 코드와 HTTP 상태를 반영한다.
- 공통 400·500 응답의 content type과 기본 설명은 `OpenApiConventionCustomizer`가 자동으로
  채우므로 모든 메서드에 반복해서 선언하지 않는다. 엔드포인트별 입력·도메인 사유만 직접 적는다.
- 응답 DTO의 status·decision·sourceType·answerFormat은 현재 API가 String으로 노출하므로
  응답 record의 필드 설명만 추가한다. 응답 타입을 enum으로 변경하는 계약 변경은 하지 않는다.
- `@Schema(example)`에는 기존의 일반적인 질문·반려 사유 예시만 유지하고 토큰·계정 식별자·
  세션 값은 추가하지 않는다.

## 5. 실행하지 못한 검증

없음. 다음 검증을 모두 실행했고 통과했다.

- 기준선 `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest" --console=plain`:
  **PASS**, `BUILD SUCCESSFUL` (2026-08-23)
- 반영 후 `./gradlew compileJava --console=plain`: **PASS**, `BUILD SUCCESSFUL` (2026-08-24)
- 반영 후 `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest" --console=plain`:
  **PASS**, `BUILD SUCCESSFUL` (2026-08-24)
- `./harness check`: **PASS** (2026-08-24)
- `./harness pr-ready --project-tests`: **PASS**, `BUILD SUCCESSFUL in 13m 50s` (2026-08-24)
- `git diff --check`: **PASS** (2026-08-24)

## 6. Reviewer checklist

- [x] 모든 제안 문장이 실제 서비스·도메인·DTO 코드로 근거를 확인했다 (추측 없음)
- [x] 승인 전 review 단계에서 `*ApiSpec`·컨트롤러·DTO 원본을 수정하지 않았다
- [x] `@Schema(example)`에 비밀값·계정 식별자·토큰을 쓰지 않았다
- [x] 내부 불변식 ID(`INV-*`)가 제안 문장에 남아 있지 않다
- [x] 6점 대조를 실행했고 기준선 OpenAPI 통합 테스트가 통과했다
- [x] 도메인 담당자가 위 제안을 승인했다
