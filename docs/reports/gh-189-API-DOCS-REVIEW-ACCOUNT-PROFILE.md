# API Docs Review: Account/Profile

> Created at: `2026-08-23T20:18:00+09:00`
> GitHub Issue: `#189`
> Target: `AccountApiSpec`, `ProfileApiSpec`
> 문장 기준: `docs/api/OPENAPI_WRITING_GUIDE.md`

이 문서는 `*ApiSpec`의 문장 검토 결과다. `harness-api-docs` review 모드에서 작성한
뒤, 같은 브랜치의 enrich 단계에서 아래 제안만 승인·반영했다.

## 1. Executive summary

- 대상 엔드포인트 수: 4개
- 발견된 문제 수: 18개
  - 설명 구조·용어·괄호: 7개
  - 누락·오류 응답 설명: 5개
  - DTO 필드 설명: 8개
- 반영한 문제 수: 18개
- OpenAPI 생성 전후 diff: 63줄(추가 51, 삭제 12)

## 2. 6점 대조 결과

| # | 대조 | 결과 | 근거 |
| --- | --- | --- | --- |
| 1 | Controller ↔ ApiSpec | 이상 없음 | `AccountController` 1개 구현 메서드와 `AccountApiSpec` 1개 메서드가 일치한다. `ProfileController` 3개 구현 메서드와 `ProfileApiSpec` 3개 메서드가 일치하며 클래스 경로 `/api/v1/me/profile`과 인터페이스의 메서드 경로가 결합된다. |
| 2 | ApiSpec ↔ DTO | 보강 필요 | `ChangeNicknameRequest.nickname`에는 `@Schema`가 있지만 description이 없다. `NicknameResponse.nickname`, `ProfileImageChangeRequest.mediaId`, `ProfileResponse`의 5개 필드에는 description이 없다. |
| 3 | ApiSpec ↔ Service | 보강 필요 | `NicknameRegistrationService.changeNickname`은 계정이 없으면 `ACC-APP-001`을 던진다. `ProfileService.findAccount`도 세 메서드 모두 계정이 없으면 `ACC-APP-001`을 던진다. `ProfileService.requireUsableAsset`은 `ANS-DOM-007`(404)과 `ANS-DOM-006`(409)을 던진다. |
| 4 | ApiSpec ↔ `docs/error-codes.md` | 보강 필요 | 닉네임 본문 검증 실패는 `GlobalExceptionHandler`가 `CMN-VAL-001`을 반환하지만 현재 설명은 `CMN-VAL-002`로 적혀 있다. 현재 문서에는 `ACC-VAL-003`, `ACC-APP-001`, `ANS-DOM-006`, `ANS-DOM-007` 근거가 노출되지 않는다. |
| 5 | ApiSpec ↔ SecurityConfiguration | 이상 없음 | 네 경로 모두 `/api/**`의 인증된 앱 요청이며 `appAccessToken` 요구사항이 정확하다. `permitAll` 예외 경로에 해당하지 않는다. |
| 6 | ApiSpec ↔ `docs/api/openapi.json` | 이상 없음 | 보강 후 `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`가 통과했다. 네 operation의 response·description과 대상 DTO 필드 description이 산출물에 반영되었고, diff는 Account/Profile 범위의 63줄이다. |

## 3. 엔드포인트별 제안

### `PATCH /api/v1/users/me/nickname` — `changeNickname`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 인증된 계정이 없을 때 404 `ACC-APP-001` |
| 누락된 `@Schema(description)` 필드 | `NicknameResponse.nickname`; `ChangeNicknameRequest.nickname` description |
| 문장 기준 위반 | 5문단 구조 누락, `production moderation gate`·`OpenAI moderation` 내부 구현 용어, `CMN-VAL-002` 사실 오류, `@Tag`의 `(#168)` 허용되지 않는 괄호 |

**Before**

```java
@Tag(name = "계정", description = "인증된 사용자 자신의 계정 프로필 (#168)")
@Operation(
	summary = "닉네임 변경",
	description = """
		인증된 본인의 닉네임을 변경합니다. 대소문자를 무시한 중복 닉네임(자기 자신이
		이미 쓰고 있는 값 포함)은 거절됩니다. production moderation gate가 켜져 있으면
		OpenAI moderation 판정을 통과한 뒤에만 반영됩니다.""")
```

**After**

```java
@Tag(name = "계정", description = "인증된 사용자의 닉네임 변경")
@Operation(
	summary = "닉네임 변경",
	description = """
		인증된 사용자의 닉네임을 변경합니다. 앞뒤 공백은 제거한 뒤 저장합니다.

		앱 액세스 토큰이 필요합니다. 현재 계정이 존재해야 하며, 이미 사용 중인 닉네임은
		자기 자신의 현재 닉네임을 포함해 사용할 수 없습니다.

		변경에 성공하면 새 닉네임을 반환합니다.

		닉네임이 비어 있거나 길이 제한을 넘거나 유해성 검사를 통과하지 못하면 변경하지
		않습니다. 닉네임 검증 서비스를 사용할 수 없을 때는 일시적인 오류로 응답합니다.

		중복 확인과 저장 사이에 다른 요청이 먼저 같은 닉네임을 저장하면 변경되지 않을 수
		있습니다.""")
```

`@ApiResponses`는 다음을 반영한다.

```java
@ApiResponse(responseCode = "400", description =
	"요청 값이 올바르지 않거나 닉네임이 길이 제한 또는 유해성 검사 기준을 통과하지 못했습니다. "
	+ "(CMN-VAL-001, ACC-VAL-003, ACC-DOM-005)"),
@ApiResponse(responseCode = "404", description = "변경할 계정을 찾을 수 없습니다. (ACC-APP-001)"),
@ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임입니다. 자기 자신의 현재 닉네임도 포함됩니다. (ACC-APP-002)"),
@ApiResponse(responseCode = "503", description = "닉네임 검증 서비스를 일시적으로 사용할 수 없습니다. (ACC-INFRA-001)")
```

**변경 근거**

- `NicknameRegistrationService.changeNickname`의 `findById`가 `ACC-APP-001`을 던진다.
- `ChangeNicknameRequest.@NotBlank`는 `GlobalExceptionHandler.handleValidation`을 통해
  `CMN-VAL-001`이 된다. `CMN-VAL-002`는 필수 query parameter 누락 코드다.
- `Account.validateNickname`은 닉네임을 trim하고 30자 초과 시 `ACC-VAL-003`을 던진다.
- `NicknameModerationChecker`의 거부·일시 불능 경로는 각각 `ACC-DOM-005`와
  `ACC-INFRA-001`이다.

### `GET /api/v1/me/profile` — `getProfile`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 인증된 계정이 없을 때 404 `ACC-APP-001` |
| 누락된 `@Schema(description)` 필드 | `ProfileResponse` 5개 필드 |
| 문장 기준 위반 | 5문단 구조 누락, 성공 결과·실패 조건·주의점이 한 문단에 섞임 |

**Before**

```java
@Operation(
	summary = "본인 프로필 조회",
	description = "만료가 있는 프로필 이미지 조회 URL을 함께 반환합니다. 프로필 이미지를 설정하지 않았거나 참조한 이미지를 더 이상 쓸 수 없으면 기본 이미지 URL을 반환합니다. 버킷 이름과 storage key는 반환하지 않습니다.")
```

**After**

```java
@Operation(
	summary = "본인 프로필 조회",
	description = """
		인증된 사용자의 닉네임과 프로필 이미지 정보를 조회합니다.

		앱 액세스 토큰이 필요하며, 현재 계정이 존재해야 합니다.

		성공하면 프로필 이미지 조회 URL과 만료 시각, 기본 이미지 사용 여부를 함께 반환합니다.

		프로필 이미지가 없거나 참조한 이미지가 더 이상 READY 상태가 아니면 기본 이미지 URL을
		반환합니다. 외부 저장소에서 조회 URL을 만들 수 없으면 조회에 실패할 수 있습니다.

		조회 URL은 일정 시간이 지나면 만료됩니다. 버킷 이름과 내부 storage key는 반환하지
		않습니다.""")
```

`@ApiResponses`에 다음 응답을 추가한다.

```java
@ApiResponse(responseCode = "404", description = "프로필을 조회할 계정을 찾을 수 없습니다. (ACC-APP-001)")
```

**변경 근거**

- `ProfileService.getProfile`은 `findAccount`를 먼저 호출하므로 `ACC-APP-001`이 가능하다.
- `ProfileImageResolver`는 참조가 없거나 READY가 아니면 기본 이미지로 폴백하지만,
  `ObjectStoragePort.issueGetUrl`가 외부 저장소 오류를 낼 수 있어 기존 503은 유지한다.

### `PUT /api/v1/me/profile/image` — `changeProfileImage`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 오류 코드가 없는 404·409 설명을 `ANS-DOM-007`, `ANS-DOM-006`으로 보강 |
| 누락된 `@Schema(description)` 필드 | `ProfileImageChangeRequest.mediaId`, `ProfileResponse` 5개 필드 |
| 문장 기준 위반 | 5문단 구조 누락, 상태 이름을 사용자 조건과 함께 풀어 쓰지 않음, 내부 저장 동작 주의점 누락 |

**Before**

```java
@Operation(
	summary = "프로필 이미지 변경",
	description = "본인 소유의 업로드 확인(READY)된 이미지만 프로필로 지정할 수 있습니다. 남의 이미지와 존재하지 않는 이미지는 모두 404로 응답합니다.")
```

**After**

```java
@Operation(
	summary = "프로필 이미지 변경",
	description = """
		인증된 사용자의 프로필 이미지로 업로드한 미디어를 지정합니다.

		앱 액세스 토큰이 필요합니다. 요청한 미디어는 현재 사용자 본인의 것이어야 하고,
		업로드 확인이 끝난 상태여야 합니다.

		성공하면 변경된 프로필과 새 이미지 조회 URL을 반환합니다.

		미디어 식별자가 없거나 양수가 아니면 요청을 처리하지 않습니다. 미디어가 없거나 다른
		사용자의 것이면 같은 404로 응답하고, 업로드 확인이 끝나지 않았거나 사용할 수 없는
		상태이면 409로 응답합니다.

		이 요청은 계정의 프로필 이미지 참조만 바꾸며, 업로드한 미디어 자체를 삭제하지 않습니다.""")
```

`@ApiResponses`의 오류 설명을 다음처럼 구체화한다.

```java
@ApiResponse(responseCode = "400", description = "mediaId가 없거나 양수가 아닙니다. (CMN-VAL-001)"),
@ApiResponse(responseCode = "404", description = "사용할 수 있는 미디어를 찾을 수 없습니다. (ANS-DOM-007)"),
@ApiResponse(responseCode = "409", description = "업로드 확인이 끝난 미디어만 프로필로 지정할 수 있습니다. (ANS-DOM-006)")
```

**변경 근거**

- `ProfileImageChangeRequest`의 `@NotNull`·`@Positive` 검증 실패는 `CMN-VAL-001`이다.
- `ProfileService.requireUsableAsset`은 `findByIdAndOwnerId`로 소유권과 존재 여부를
  구분하지 않고 `ANS-DOM-007`을 던진다.
- 같은 메서드가 READY가 아닌 자산에 `ANS-DOM-006`을 던진다.

### `DELETE /api/v1/me/profile/image` — `removeProfileImage`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 인증된 계정이 없을 때 404 `ACC-APP-001` |
| 누락된 `@Schema(description)` 필드 | `ProfileResponse` 5개 필드 |
| 문장 기준 위반 | 5문단 구조 누락, 성공 결과와 대상 미디어 보존 주의점이 한 문단에 압축됨 |

**Before**

```java
@Operation(
	summary = "프로필 이미지 삭제",
	description = "프로필 이미지 참조만 해제해 기본 이미지 상태로 되돌립니다. 업로드한 이미지 자체는 삭제하지 않습니다.")
```

**After**

```java
@Operation(
	summary = "프로필 이미지 삭제",
	description = """
		인증된 사용자의 프로필 이미지 설정을 해제하고 기본 이미지 상태로 되돌립니다.

		앱 액세스 토큰이 필요하며, 현재 계정이 존재해야 합니다.

		성공하면 기본 이미지가 적용된 프로필을 반환합니다.

		프로필 이미지 설정을 해제할 계정을 찾을 수 없으면 요청을 처리하지 않습니다.

		계정에서 이미지 참조만 제거하며, 업로드한 미디어와 저장소 객체는 삭제하지 않습니다.""")
```

`@ApiResponses`에 다음 응답을 추가한다.

```java
@ApiResponse(responseCode = "404", description = "프로필을 변경할 계정을 찾을 수 없습니다. (ACC-APP-001)")
```

**변경 근거**

- `ProfileService.removeProfileImage`은 `findAccount`에서 `ACC-APP-001`을 던진다.
- `Account.withoutProfileImage`는 계정의 참조만 null로 바꾸며 `media_asset`을 삭제하지 않는다.

### DTO 공통 보강

다음 필드에 사용자 관점의 `@Schema(description)`을 추가한다.

```java
// ChangeNicknameRequest
@Schema(description = "변경할 닉네임. 앞뒤 공백은 제거한 뒤 저장합니다.",
	 requiredMode = Schema.RequiredMode.REQUIRED, example = "여름바람")
String nickname

// NicknameResponse
@Schema(description = "변경된 닉네임.")
String nickname

// ProfileImageChangeRequest
@Schema(description = "프로필 이미지로 지정할 미디어 식별자.", example = "123")
Long mediaId

// ProfileResponse
@Schema(description = "프로필 소유자의 식별자.")
long userId
@Schema(description = "현재 닉네임.")
String nickname
@Schema(description = "일정 시간이 지나면 만료되는 프로필 이미지 조회 URL.")
String profileImageUrl
@Schema(description = "프로필 이미지 조회 URL이 만료되는 시각.")
Instant profileImageExpiresAt
@Schema(description = "프로필 이미지가 없거나 사용할 수 없어 기본 이미지를 쓰는지 여부.")
boolean usesDefaultProfileImage
```

## 4. 반영하지 않은 제안

- `ProfileService`가 외부 저장소 오류를 어떤 구체적인 오류 코드로 변환하는지는
  현재 `ProfileImageResolver`와 전역 예외 처리 경로만으로 도메인 전용 코드를 확인할 수
  없어 기존 503 설명을 유지한다.
- 닉네임 중복 확인과 저장 사이의 경합은 현재 DB 제약 매핑이 `ACC-APP-002`로 고정되어
  있어 별도 API 계약 변경 없이 문서에 반영한다.
- `docs/error-codes.md` 자체의 코드 정의나 API 동작은 이 도메인 범위에서 수정하지 않는다.

## 5. 검증 결과

- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`:
  보강 전 기준선과 보강 후 모두 통과했다. 기준선에서는 `docs/api/openapi.json`
  diff가 없었고, 보강 후 diff는 63줄(추가 51, 삭제 12)이었다.
- `./harness pr-ready --project-tests`: 통과했다. 저장소 정책 검사와 전체
  unit/integration test가 포함되었으며 `BUILD SUCCESSFUL in 13m 41s`와
  `Local PR readiness checks passed`를 확인했다.
- `./harness check`: 통과했다.
- `npm run hooks:validate`: 통과했다.
- `git diff --check`: 통과했다.
- 산출물을 다시 파싱해 네 operation의 엔드포인트별 응답과 4개 대상 schema의
  필드 설명이 모두 반영된 것을 확인했다.
- 실행하지 못한 필수 검증은 없다.

## 6. Reviewer checklist

- [x] 모든 제안 문장이 실제 서비스/DTO 코드로 근거를 확인했다 (추측 없음)
- [x] review 제안만 enrich 단계에서 `*ApiSpec`과 대상 DTO에 반영했다
- [x] `@Schema(example)`에 비밀값·계정 식별자를 쓰지 않았다
- [x] 내부 불변식 ID(`INV-*`)가 제안 문장에 남아 있지 않다
- [x] 6점 대조를 모두 실행했거나, 실행하지 못한 항목을 §5에 기록했다
