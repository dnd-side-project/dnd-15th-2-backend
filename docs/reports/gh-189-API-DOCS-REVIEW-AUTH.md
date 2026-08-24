# API Docs Review: Auth

> Created at: `2026-08-23T22:02:00+09:00`
> GitHub Issue: `#189`
> Target: `src/main/java/com/dnd/qello/auth/web/{CsrfTokenApiSpec,DeviceAuthApiSpec,OperatorLoginApiSpec}.java`
> 문장 기준: `docs/api/OPENAPI_WRITING_GUIDE.md`

이 문서는 Auth 도메인 3개 `*ApiSpec`의 문장·오류 응답·스키마 검토 결과다.
`harness-api-docs` 스킬의 `review` 모드에서 작성했으며, 승인 전에는
`*ApiSpec`·컨트롤러·DTO 소스를 수정하지 않았다. 제안은 담당자 승인 후 enrich 단계에서
애노테이션으로 반영했다.

> **반영 완료 (2026-08-23).** 담당자 승인 후 아래 After 제안과 DTO 스키마 설명을
> 적용하고 OpenAPI 산출물을 재생성했다. §2.1과 §5에 반영 결과를 기록한다.

## 1. Executive summary

- 대상 엔드포인트 수: **5개** (`CsrfTokenApiSpec` 1개, `DeviceAuthApiSpec` 2개,
  `OperatorLoginApiSpec` 2개)
- 발견된 문제 수: **29개**
  - 문장 종결·문단·용어 문제: **13건** (`CsrfToken` 2, `DeviceAuth` 5,
    `OperatorLogin` 6 — Issue #189의 한다체 집계 기준)
  - 실제 서비스 오류와 맞지 않거나 빠진 오류 설명: **8건**
  - `@Schema(description)` 누락: **5개 스키마 타입, 15개 필드**
  - DTO 필드 설명의 사실·표기 보정: **3건** (`OperatorLoginRequest` 2,
    `OperatorSessionResponse` 1)
- 반영한 문제 수: **29개**
- 6점 대조 중 실행하지 못한 항목: **없음**
- 현재 브랜치: `docs/gh-189-auth-api-description`

Auth 웹 DTO 파일은 7개이고, 요청의 `platform`이 참조하는 `DevicePlatform` enum을 합치면
Auth 경로에서 생성되는 입력·출력 스키마 타입은 8개다. 이 중 설명이 없는 타입은
`DeviceRegistrationRequest`, `DeviceRegistrationResponse`, `DeviceTokenRequest`,
`DeviceTokenResponse`, `DevicePlatform` 5개다. `OperatorReasonRequest`는 Filtering 도메인의
공유 요청 DTO이므로 Auth 집계에서 제외한다.

## 2. 6점 대조 결과

`docs/api/OPENAPI_WRITING_GUIDE.md` §9 기준으로 도메인 전체를 대조했다.

| # | 대조 | 결과 | 근거 |
| --- | --- | --- | --- |
| 1 | Controller ↔ ApiSpec | **이상 없음** | `CsrfTokenController` 1개, `DeviceAuthController` 2개, `OperatorLoginController` 2개 구현 메서드가 각 인터페이스의 `@GetMapping`·`@PostMapping` 5개와 1:1이다. 컨트롤러에는 문서 애노테이션이 없다. |
| 2 | ApiSpec ↔ DTO | **문제 20건** | Auth 웹 DTO 7개 중 4개가 필드 `@Schema(description)`을 전혀 갖지 않는다(15필드). `DevicePlatform` enum schema도 설명이 없다. `CsrfTokenResponse`, `OperatorLoginRequest`, `OperatorSessionResponse`는 설명이 있으나 `OperatorLoginRequest` 2개와 `OperatorSessionResponse.userId`의 문장을 보정한다. |
| 3 | ApiSpec ↔ Service | **문제 8건** | `DeviceRegistrationService`가 `AUT-VAL-002`, `AUT-VAL-004`, `AUT-APP-005`와 Account 닉네임 검증의 `ACC-DOM-005`, `ACC-APP-002`, `ACC-INFRA-001`을 낼 수 있는데 400·409·503 설명이 일부만 선언돼 있다. `DeviceTokenService`의 `AUT-APP-006`, `AUT-APP-003`은 선언돼 있다. `OperatorLoginService`의 `AUT-APP-001`, `AUT-APP-002`, `AUT-APP-003`은 선언돼 있으나 입력 객체가 만드는 `AUT-VAL-001`·`AUT-VAL-002` 400 설명이 없다. |
| 4 | ApiSpec ↔ `docs/error-codes.md` | **보강 필요** | 인용된 `AUT-APP-001/002/003/005/006`, `CMN-VAL-003`, `CMN-DOM-001`의 HTTP 상태는 표와 일치한다. 다만 Device 등록의 `ACC-*`·`AUT-VAL-*`와 로그인 입력의 `AUT-VAL-*`가 산출물 설명에 빠져 있고, 400 공통 검증은 `CMN-VAL-001`로 표기해야 한다. |
| 5 | ApiSpec ↔ SecurityConfiguration | **이상 없음** | `/admin/csrf` GET과 `/admin/login` POST는 `permitAll`, `/admin/logout`은 운영자 세션·CSRF 필요, `/api/v1/auth/devices`·`/api/v1/auth/token`은 액세스 토큰 없이 `permitAll`이다. 따라서 `operatorSession`은 logout에만 있고 Device 경로에는 없다. |
| 6 | ApiSpec ↔ `docs/api/openapi.json` | **기준선 이상 없음** | 소스·산출물 변경 전에 `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest" --console=plain`을 실행했고 PASS했다. 실행으로 새 diff가 생기지 않았다. 승인 후 enrich에서 재생성한 결과를 §2.1에 기록했다. |

### 2.1 반영 후 재대조

- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest" --console=plain`:
  **PASS** (기준선 포함 2회 실행, 모두 `BUILD SUCCESSFUL`)
- 5개 operation의 description을 재파싱한 결과 금지된 한다체 종결(`한다`, `된다`, `이다`,
  `없다`) **0건**.
- Auth 웹 DTO 7개와 `DevicePlatform` enum을 재파싱한 결과 필드 description 누락
  **0건**. `DevicePlatform`은 요청 schema의 `platform` 설명으로 생성된다.
- Device 등록 응답이 `201,400,409,500,503`, 토큰 재발급이 `200,400,401,403,500`,
  운영자 로그인이 `200,400,401,403,423,500`으로 산출됐고, 제안한 오류 코드가 각
  설명에 반영됐다.
- `docs/api/openapi.json` diff는 Auth 경로 5개와 Auth schema 설명 변경으로만 구성된다.

## 3. 엔드포인트별 제안

### `GET /admin/csrf` — `CsrfTokenApiSpec.issue`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음. GET `/admin/csrf`는 `permitAll`이고 컨트롤러·서비스가 별도 도메인 예외를 던지지 않는다. |
| 누락된 `@Schema(description)` 필드 | 없음. `CsrfTokenResponse.headerName`, `token` 모두 설명이 있다. |
| 문장 기준 위반 | 한다체 2건: `돌려준다`, `인증이 필요 없다`. `description`이 무엇을 하는지·인증·성공 결과·주의점을 한 문단 흐름으로 분리하지 않았다. `same-origin`을 설명 없이 남긴다. |

**Before**

```java
@Operation(
	summary = "CSRF 토큰 발급",
	description = """
		다음 상태 변경 요청에 실어 보낼 CSRF 토큰과 그 헤더 이름을 돌려준다.

		인증이 필요 없다. 로그인 POST도 CSRF 보호 대상이라 로그인 전에 토큰을 받을 경로가 있어야 한다.

		토큰은 인증 상태와 무관한 값이라 노출해도 안전하다. 방어의 근거는 공격자가 피해자의
		브라우저에서 이 응답을 읽을 수 없다는 same-origin 정책이다.""")
```

**After**

```java
@Operation(
	summary = "CSRF 토큰 발급",
	description = """
		운영자 로그인과 다른 상태 변경 요청에 넣을 CSRF 토큰과 헤더 이름을 조회합니다.

		인증 없이 호출할 수 있습니다. 로그인 POST도 CSRF 보호 대상이므로 로그인 전에 이 경로를
		호출해야 합니다.

		성공하면 headerName에 토큰을 보낼 요청 헤더 이름을, token에 사용할 토큰을 담아 반환합니다.

		이 토큰은 운영자 세션을 만들거나 권한을 부여하지 않습니다.

		브라우저에서 읽을 수 없는 다른 사이트의 요청에는 이 응답을 그대로 사용할 수 없습니다.""")
```

**변경 근거**

- `SecurityConfiguration.backofficeSecurityFilterChain`의 GET `/admin/csrf`만
  `permitAll`이고, `CsrfTokenController`는 Spring Security가 만든 토큰의 헤더 이름과 값을
  그대로 `CsrfTokenResponse`로 반환한다.
- 토큰 자체의 인증·권한 의미를 부풀리지 않고, 운영자 로그인 전에 필요한 준비 단계라는
  사실만 설명한다. 별도 서비스 예외가 없으므로 오류 응답은 추가하지 않는다.

### `POST /api/v1/auth/devices` — `DeviceAuthApiSpec.register`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 400의 `AUT-VAL-002`, `AUT-VAL-003`, `ACC-VAL-002`, `ACC-VAL-003`, `ACC-VAL-004`, `ACC-DOM-005`, `CMN-VAL-001`; 409의 `ACC-APP-002`; 503의 `ACC-INFRA-001` |
| 누락된 `@Schema(description)` 필드 | `DeviceRegistrationRequest` 7개, `DeviceRegistrationResponse` 4개 |
| 문장 기준 위반 | 한다체·문단 순서 문제 5건. `deviceSecret`의 평문 1회 반환과 선택 nickname 조건이 요청·응답 설명에서 분리되지 않았다. |

**Before**

```java
@Operation(
	summary = "기기 등록",
	description = """
		설치 식별자와 검증된 국가·기준 지역으로 계정과 기기 자격증명을 만들고 첫 액세스 토큰을 발급한다.
		countryCode는 ISO 3166-1 alpha-2 국가 코드이며, coarseRegionCode의 최상위 국가와 일치해야 한다.

		deviceSecret은 이 응답에서만 평문으로 나간다. 서버는 해시만 보관하므로 다시 받을 수 없다.
		클라이언트가 이 값을 잃으면 재발급 경로를 쓸 수 없다.

		인증이 필요 없다. 등록 자체가 인증 수단을 얻는 과정이다.""")
```

**After**

```java
@Operation(
	summary = "기기 등록",
	description = """
		설치 정보를 확인해 새 사용자 계정과 기기 자격증명을 만들고 첫 액세스 토큰을 발급합니다.

		인증 없이 호출할 수 있습니다. installationId, platform, countryCode, coarseRegionCode,
		locale, timezone이 필요하고 nickname은 선택입니다. countryCode는 ISO 3166-1 alpha-2
		국가 코드이며 coarseRegionCode의 최상위 국가와 일치해야 합니다.

		성공하면 새 계정 식별자와 첫 액세스 토큰, 만료까지 남은 시간, 재발급에 사용할
		deviceSecret을 반환합니다.

		이미 등록된 installationId이거나 국가·지역·계정 입력값이 정책에 맞지 않으면 등록하지 않습니다.
		nickname을 보낸 경우 중복·유해성 검사를 통과해야 합니다.

		deviceSecret은 이 응답에서만 평문으로 반환합니다. 서버에는 해시만 보관하므로 이 값을
		잃으면 새 기기를 등록해야 합니다.""")
@ApiResponses({
	@ApiResponse(responseCode = "201", description = "기기를 등록했습니다. deviceSecret과 첫 액세스 토큰이 함께 발급됩니다."),
	@ApiResponse(responseCode = "400", description =
		"필수 값·기기 식별자·국가·지역·계정 입력값이 올바르지 않거나 닉네임 검사를 통과하지 못했습니다. "
		+ "(CMN-VAL-001, AUT-VAL-002, AUT-VAL-003, AUT-VAL-004, ACC-VAL-002, ACC-VAL-003, ACC-VAL-004, ACC-DOM-005)",
		content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
	@ApiResponse(responseCode = "409", description =
		"이미 등록된 기기이거나 닉네임이 이미 사용 중입니다. (AUT-APP-005, ACC-APP-002)",
		content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
	@ApiResponse(responseCode = "503", description =
		"닉네임 검증 서비스를 일시적으로 사용할 수 없습니다. (ACC-INFRA-001)",
		content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
})
```

**DTO 제안**

| 스키마 | 필드 | 제안 description |
| --- | --- | --- |
| `DeviceRegistrationRequest` | `installationId` | 앱 설치를 식별하는 값입니다. |
|  | `platform` | 기기를 등록한 앱 플랫폼입니다. `IOS` 또는 `ANDROID`입니다. |
|  | `countryCode` | ISO 3166-1 alpha-2 국가 코드입니다. 기준 지역의 최상위 국가와 일치해야 합니다. |
|  | `coarseRegionCode` | 국가 안의 대략적인 지역 코드입니다. |
|  | `locale` | 계정의 언어·지역 설정입니다. |
|  | `timezone` | 계정의 IANA 시간대 식별자입니다. |
|  | `nickname` | 선택할 수 있는 계정 닉네임입니다. |
| `DeviceRegistrationResponse` | `userId` | 새로 만든 계정 식별자입니다. |
|  | `deviceSecret` | 기기 토큰 재발급에 사용할 비밀값입니다. 이 응답에서만 평문으로 반환됩니다. |
|  | `accessToken` | 등록 직후 API 호출에 사용할 액세스 토큰입니다. |
|  | `expiresIn` | 액세스 토큰이 만료되기까지 남은 시간(초)입니다. |
| `DevicePlatform` | enum 타입 | 기기를 등록한 앱 플랫폼입니다. |

**변경 근거**

- `DeviceRegistrationService.register`는 이미 활성인 installationId에
  `AUT-APP-005`를, 국가 누락에 `AUT-VAL-002`, 국가·기준 지역 불일치에 `AUT-VAL-004`를
  던진다. `DeviceCredential.issue`와 `Account.createUser`는 요청 형식·길이·시간대에 관한
  `AUT-VAL-003`, `ACC-VAL-002/003/004`를 검증한다.
- 선택 nickname은 `NicknameRegistrationService.ensureAvailable`을 거치므로 중복
  `ACC-APP-002`, 정책 거부 `ACC-DOM-005`, 판정 서비스 불능 `ACC-INFRA-001`이 실제 경로다.
- `CMN-VAL-001`은 `@Valid` 요청 검증 실패의 공통 코드다. 400·500의 공통 content type은
  커스터마이저가 채우므로 400만 도메인 사유를 보강한다.
- `deviceSecret`·액세스 토큰에는 example을 넣지 않는다.

### `POST /api/v1/auth/token` — `DeviceAuthApiSpec.reissue`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음. `AUT-APP-006`(401)과 `AUT-APP-003`(403)이 실제 서비스와 일치한다. 400 설명은 공통 검증 조건을 보강한다. |
| 누락된 `@Schema(description)` 필드 | `DeviceTokenRequest` 2개, `DeviceTokenResponse` 2개 |
| 문장 기준 위반 | 한다체·문단 순서 문제 4건. 재발급에 필요한 두 입력과 계정 상태 조건이 흐름에 분리되지 않았다. |

**Before**

```java
@Operation(
	summary = "액세스 토큰 재발급",
	description = """
		등록 때 받은 deviceSecret으로 새 액세스 토큰을 발급한다.

		installationId는 deviceSecret 조회 결과의 교차 검증용이다. 둘이 같은 기기를 가리키지
		않으면 자격증명 불일치와 같은 401로 나간다.

		인증이 필요 없다. 이 경로가 곧 토큰을 얻는 경로다.""")
```

**After**

```java
@Operation(
	summary = "액세스 토큰 재발급",
	description = """
		기기 등록 때 받은 deviceSecret과 installationId로 새 액세스 토큰을 발급합니다.

		인증 없이 호출할 수 있습니다. 두 값은 같은 기기를 가리켜야 하며, 등록된 계정이
		사용 가능한 상태여야 합니다.

		성공하면 새 accessToken과 만료까지 남은 시간(초)을 반환하고 기기 자격증명의
		마지막 사용 시각을 갱신합니다.

		두 값이 일치하지 않거나 자격증명이 해지됐으면 재발급하지 않습니다. 계정이 사용할 수
		없는 상태여도 토큰을 발급하지 않습니다.

		deviceSecret은 기기 등록 성공 응답에서만 확인할 수 있습니다. 서버가 비밀값을
		복원해 주는 경로는 없습니다.""")
```

**DTO 제안**

| 스키마 | 필드 | 제안 description |
| --- | --- | --- |
| `DeviceTokenRequest` | `installationId` | 토큰을 재발급할 기기의 설치 식별자입니다. |
|  | `deviceSecret` | 등록 응답에서 받은 기기 비밀값입니다. |
| `DeviceTokenResponse` | `accessToken` | 새로 발급한 API 호출용 액세스 토큰입니다. |
|  | `expiresIn` | 액세스 토큰이 만료되기까지 남은 시간(초)입니다. |

`@ApiResponses`에는 커스터마이저의 기본 400을 이 엔드포인트의 입력 조건으로 보강한다.

```java
@ApiResponse(
	responseCode = "400",
	description = "installationId 또는 deviceSecret이 비어 있습니다. (CMN-VAL-001, AUT-VAL-002)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class)))
```

**변경 근거**

- `DeviceTokenService.reissue`는 secret hash 조회, installationId 교차 검증, ACTIVE
  자격증명 검사를 모두 `AUT-APP-006`으로 수렴하고, 계정이 ACTIVE가 아니면
  `AUT-APP-003`을 던진다. 현재 401·403 선언은 유지한다.
- 입력 `@NotBlank` 실패는 `CMN-VAL-001`, `DeviceSecret`의 빈 값은
  `AUT-VAL-002`에 해당하므로 400 설명을 추가할 수 있다. 공통 400 응답은 커스터마이저가
  이미 생성한다.
- `lastUsedAt` 갱신은 `credentialRepository.updateLastUsedAt`에서 실제로 수행되므로 성공
  결과 문장에만 명시한다.

### `POST /admin/login` — `OperatorLoginApiSpec.login`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 입력 형식 오류 400의 `CMN-VAL-001`, `AUT-VAL-001`, `AUT-VAL-002` |
| 누락된 `@Schema(description)` 필드 | 없음. `OperatorLoginRequest` 필드 설명의 사실·종결어미만 보정한다. |
| 문장 기준 위반 | 한다체 4건, 5문단 순서 누락, `OperatorLoginRequest.loginId`의 “소문자와 숫자만 허용” 사실 오류 |

**Before**

```java
@Operation(
	summary = "운영자 로그인",
	description = """
		자격증명을 검증하고 세션 쿠키를 발급한다.

		실패 원인은 구분해 알리지 않는다. 존재하지 않는 loginId와 잘못된 비밀번호가 같은 401로 나간다.
		계정 열거를 막기 위한 것이다.

		CSRF 보호 대상이므로 GET /admin/csrf로 받은 토큰을 함께 보내야 한다.""")
```

**After**

```java
@Operation(
	summary = "운영자 로그인",
	description = """
		운영자 자격증명을 확인하고 세션 쿠키를 발급합니다.

		로그인 자체에는 운영자 세션이 필요하지 않지만, CSRF 보호가 켜져 있으므로
		GET /admin/csrf에서 받은 토큰을 함께 보내야 합니다. loginId와 password는 필수입니다.

		성공하면 세션 쿠키와 로그인한 운영자의 계정 식별자를 반환합니다.

		존재하지 않는 loginId와 잘못된 비밀번호는 같은 401로 응답합니다. 사용할 수 없는 계정은
		403, 반복 실패로 잠긴 자격증명은 423, CSRF 토큰이 없거나 유효하지 않으면 403입니다.

		로그인 응답 본문에는 비밀번호나 액세스 토큰을 담지 않습니다. 이후 백오피스 요청은
		발급된 세션 쿠키를 사용합니다.""")
```

**DTO 보정**

```java
@Schema(description = "운영자 로그인 식별자. 앞뒤 공백을 제거하고 소문자로 변환합니다.")
String loginId,

@Schema(description = "평문 비밀번호입니다. 전송 구간은 TLS로 보호합니다.")
String password
```

`@ApiResponses`에는 로그인 입력 객체가 만들 수 있는 400을 추가한다.

```java
@ApiResponse(
	responseCode = "400",
	description = "loginId 또는 password가 비어 있거나 loginId 길이가 허용 범위를 벗어났습니다. "
		+ "(CMN-VAL-001, AUT-VAL-001, AUT-VAL-002)",
	content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ApiErrorResponse.class)))
```

**변경 근거**

- `SecurityConfiguration`은 POST `/admin/login`만 인증 없이 열고 CSRF는 유지한다.
  `OperatorLoginController`는 성공 시 세션에 `ROLE_OPERATOR`를 넣고
  `OperatorSessionResponse.userId`를 반환한다.
- `OperatorLoginService`는 `LOGIN_FAILED`(401), `ACCOUNT_NOT_ACTIVE`(403),
  `CREDENTIAL_LOCKED`(423)을 실제로 던진다. `LoginId.of`와 `RawPassword`는 입력
  형식에 따라 `AUT-VAL-001/002`를 던지고 `@Valid`는 `CMN-VAL-001`로 수렴한다.
- `LoginId.of`가 입력을 strip·소문자화하므로 “소문자와 숫자만 허용”은 현재 동작과 맞지
  않는다. 식별자에는 숫자 외 문자도 저장 규칙을 통과할 수 있으므로 기존 문장을 그대로
  두지 않는다.

### `POST /admin/logout` — `OperatorLoginApiSpec.logout`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음. 필터 단계의 `CMN-VAL-003`(401), `CMN-DOM-001`(403)이 선언돼 있다. |
| 누락된 `@Schema(description)` 필드 | 없음. `ApiResponse<Void>`에는 설명할 DTO 필드가 없다. |
| 문장 기준 위반 | 한다체 2건, 성공 결과와 CSRF·세션 선행 조건이 한 문단에 섞여 있다. |

**Before**

```java
@Operation(
	summary = "운영자 로그아웃",
	description = """
		세션을 무효화한다. 권한은 즉시 회수되며 유예 시간이 없다.

		CSRF 보호 대상이므로 GET /admin/csrf로 받은 토큰을 함께 보내야 한다.""")
```

**After**

```java
@Operation(
	summary = "운영자 로그아웃",
	description = """
		현재 운영자 세션을 무효화합니다.

		운영자 세션과 CSRF 토큰이 필요합니다. 토큰은 GET /admin/csrf에서 받을 수 있습니다.

		성공하면 세션 쿠키가 더 이상 권한을 갖지 않으며 응답 본문은 비어 있습니다.

		세션이 없거나 만료됐으면 401, 운영자 권한이 없거나 CSRF 토큰이 유효하지 않으면
		403으로 응답합니다.

		이 요청은 운영자 세션만 무효화하며 앱 기기 자격증명과 액세스 토큰에는 영향을 주지 않습니다.""")
```

**변경 근거**

- `OperatorLoginController.logout`은 현재 세션을 invalidate하고
  `SecurityContextHolder`를 비운다. 앱 API의 기기 토큰을 건드리는 호출은 없다.
- `/admin/**` 체인은 `hasRole("OPERATOR")`와 CSRF를 적용하므로 세션·CSRF 선행 조건과
  `AuthEntryPoints`의 401·403을 그대로 설명한다.

## 4. 반영하지 않은 제안

- `DevicePlatform`은 `auth/domain`의 enum이지만 Auth 요청 스키마에 직접 노출된다. API
  소비자가 `IOS`·`ANDROID`의 의미를 확인할 수 있도록 타입 수준 `@Schema(description)`을
  추가하는 제안에 포함했다. enum을 웹 DTO 경계 밖으로 유지해야 한다는 결정이 있다면
  타입 설명은 제외하고 요청 필드 설명만 유지한다.
- 공통 400·500의 content type과 기본 문구는 `OpenApiConventionCustomizer`가 자동으로
  채우므로 각 `*ApiSpec`에 중복 선언하지 않는다. 단, 위 엔드포인트별 400 설명에는 실제
  도메인 코드 묶음만 추가한다.
- 비밀번호·deviceSecret·accessToken·계정 식별자에는 `@Schema(example = ...)`를 추가하지
  않는다. 저장소에 커밋하는 산출물에 비밀값 또는 실제 식별자를 남길 수 없다.

## 5. 실행하지 못한 검증

없음. 다음 검증을 모두 실행했고 통과했다.

- `./gradlew compileJava --console=plain`: **PASS**, `BUILD SUCCESSFUL` (2026-08-23)
- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest" --console=plain`:
  **PASS**, 기준선 포함 2회 모두 `BUILD SUCCESSFUL` (2026-08-23)
- `./harness check`: **PASS** (2026-08-23)
- `./harness pr-ready --project-tests`: **PASS**, `BUILD SUCCESSFUL in 21m 18s` (2026-08-23)
- `git diff --check`: **PASS** (2026-08-23)

## 6. Reviewer checklist

- [x] 모든 제안 문장이 실제 서비스·도메인·DTO 코드로 근거를 확인했다 (추측 없음)
- [x] 승인 전 review 단계에서 `*ApiSpec` 원본을 수정하지 않았다
- [x] `@Schema(example)`에 비밀값·계정 식별자·토큰을 쓰지 않았다
- [x] 내부 불변식 ID(`INV-*`)가 제안 문장에 남아 있지 않다
- [x] 6점 대조를 모두 실행했고 기준선·반영 후 통합 테스트가 통과했다
- [x] 도메인 담당자가 위 제안을 승인했다
