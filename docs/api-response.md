# API 응답 계약

이 문서는 모든 HTTP 응답 본문의 계약이다. 오류 코드 목록과 계층별 예외 사용
규칙은 `docs/error-codes.md`에, 적용 방식의 결정 배경은
`docs/adr/0005-api-success-response-contract.md`에 있다.

## 1. 본문 형식

성공과 오류는 `status`와 `timestamp`를 공유하고, 그 사이 슬롯만 갈린다.

```json
{
  "status": "success",
  "data": { "postId": 1, "sentAt": "2026-08-07T09:00:00Z" },
  "timestamp": "2026-08-07T09:00:00Z"
}
```

```json
{
  "status": "error",
  "message": "요청 값이 올바르지 않습니다.",
  "errorDetail": {
    "code": "ACC-VAL-004",
    "field": "timezone",
    "reason": "timezone은 유효한 IANA ID여야 합니다"
  },
  "timestamp": "2026-08-07T09:00:00Z"
}
```

| 필드 | 성공 | 오류 | 의미 |
| --- | --- | --- | --- |
| `status` | `success` | `error` | 본문만 보고 결과를 가를 수 있게 하는 값 |
| `data` | 있음 | 없음 | 응답 본문. 없으면 `null` |
| `message` | 없음 | 있음 | 오류 코드의 기본 메시지 |
| `errorDetail` | 없음 | 있음 | `code`, `field`, `reason` |
| `timestamp` | 있음 | 있음 | 응답을 만든 시각. ISO-8601 UTC |

성공 응답에 `message`는 두지 않는다. 항상 비어 있는 필드가 되기 때문이다.

`timestamp`는 `@JsonFormat(shape = STRING)`으로 형식을 고정한다. 전역 Jackson
설정이 바뀌어도 계약이 흔들리지 않게 하기 위해서다.

## 2. HTTP 상태 매핑

| 상황 | 상태 | 본문 |
| --- | --- | --- |
| 조회 성공 | 200 | `ApiResponse<T>` |
| 생성 성공 | 201 + `Location` | `ApiResponse<T>` — 최소한 생성된 식별자 |
| 수정 성공 | 200 | `ApiResponse<T>` |
| 삭제, 부수효과만 있는 성공 | 200 | `ApiResponse<Void>` (`data`는 `null`) |
| 실패 | 오류 코드가 정한 상태 | `ApiErrorResponse` |

**204 No Content는 사용하지 않는다.** 204는 본문이 금지된 상태여서 "모든 성공
응답이 같은 형식"과 충돌한다. 돌려줄 값이 없는 성공도 200과 `data: null`로
내보내면 클라이언트가 상태 코드별로 파서를 나누지 않아도 된다.

`data`가 `null`이어도 키는 남긴다. `@JsonInclude(NON_NULL)`을 붙이지 않는다.
키가 조건부로 사라지면 클라이언트가 값이 아니라 키의 존재로 분기하게 된다.

## 3. 사용 규칙

성공 응답은 controller가 직접 감싼다. 전역 자동 래핑은 쓰지 않는다.

```java
@RestController
@RequestMapping("/api/direction-posts")
public class DirectionPostController {

	private final ApiResponseFactory responseFactory;
	private final DirectionPostService directionPostService;

	@GetMapping("/{postId}")
	public ResponseEntity<ApiResponse<DirectionPostResponse>> find(@PathVariable Long postId) {
		return ResponseEntity.ok(responseFactory.success(directionPostService.find(postId)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<DirectionPostResponse>> send(@RequestBody @Valid SendRequest request) {
		DirectionPostResponse created = directionPostService.send(request);
		return ResponseEntity.created(URI.create("/api/direction-posts/" + created.postId()))
			.body(responseFactory.success(created));
	}

	@DeleteMapping("/{postId}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long postId) {
		directionPostService.delete(postId);
		return ResponseEntity.ok(responseFactory.success());
	}
}
```

- **`ApiResponse`를 직접 `new` 하지 않는다.** `ApiResponseFactory`만 쓴다.
  시각 원천을 `Clock` 빈 하나로 유지하기 위해서다.
- **controller는 예외를 잡지 않는다.** 오류 응답은 `GlobalExceptionHandler`가
  전부 만든다.
- **`String`을 반환하지 않는다.** 모든 handler의 반환 타입은 `ApiResponse<T>`
  또는 `ResponseEntity<ApiResponse<T>>`다. `ApiResponseConventionTest`가 이를
  검사한다.
- **`void` handler를 만들지 않는다.** 204를 쓰지 않기 때문이다.

## 4. 시각 원천

`timestamp`는 `common/time/ClockConfiguration`이 제공하는 `Clock` 빈에서만 나온다.
JPA 감사 시각(`JpaAuditingConfiguration`)도 같은 빈을 쓴다. 테스트는 `Clock`
빈 하나만 고정하면 저장 시각과 응답 시각을 함께 고정할 수 있다.

`Instant.now()`를 직접 호출하지 않는다.

## 5. OpenAPI 문서

스펙은 손으로 쓰지 않는다. springdoc이 실행 중인 애플리케이션에서 추출하고,
`docs/api/openapi.json`으로 커밋한다.

```bash
./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"
```

`OpenApiSpecificationIntegrationTest`가 `/v3/api-docs`를 호출해 산출물을 덮어쓴다.
키 순서와 개행을 고정하므로 같은 코드에서는 항상 같은 파일이 나온다.

### 자동 동기화

**손으로 재생성하지 않아도 된다.** PR을 올리면 `harness-policy.yml`의
`sync-api-docs` job이 스펙을 다시 만들고, 달라졌으면 PR 브랜치에 직접 커밋한다.
커밋 메시지는 브랜치에서 타입과 이슈 번호를 뽑아 저장소 커밋 규칙에 맞춘다.

```text
feat/gh-73-device-credential-token
  → feat(docs): sync the openapi specification (#73)
```

`GITHUB_TOKEN`으로 만든 push는 새 workflow 실행을 트리거하지 않으므로 봇 커밋이
자신을 다시 깨우는 순환은 생기지 않는다. 대신 그 커밋은 policy job의 검사를 받지
못하므로 job이 커밋 직전에 `scripts/validate-conventions.py`로 메시지를 직접
확인한다.

fork에서 올린 PR은 토큰이 읽기 전용이라 되돌려 push할 수 없다. 이 경우에만
job이 실패하며, 개발자가 직접 재생성해 커밋해야 한다.

### 노출 범위

`springdoc.api-docs.enabled`는 기본값이 `false`다. 운영에서는 스펙 엔드포인트
라우트 자체가 생기지 않는다. 인증 규칙이 아니라 기능 비활성으로 막는 쪽을 택했다.
`local`과 통합 테스트 프로필에서만 켜진다. Swagger UI 번들은 아예 넣지 않았다.

### 제네릭 스키마

`ApiResponse<T>`는 springdoc이 타입 인자별로 풀어 `ApiResponseOperatorSessionResponse`
같은 스키마 이름을 만든다. 래퍼 때문에 별도 설정을 넣지 않는다.

### 공통 규칙 주입

springdoc은 반환 타입만 보고 성공 응답을 추론한다. 어느 엔드포인트에서나 참인 규칙은
`OpenApiConventionCustomizer`가 한곳에서 넣으므로 컨트롤러마다 반복하지 않는다.
새 컨트롤러도 자동으로 같은 규칙을 받는다.

| 규칙 | 근거 |
| --- | --- |
| 모든 응답 content type을 `application/json`으로 좁힌다 | 이 서비스는 JSON만 반환한다 |
| 모든 operation에 `400`, `500`을 넣는다 | `GlobalExceptionHandler`가 어떤 요청에서든 낼 수 있다 |
| `ApiErrorResponse` 스키마를 등록한다 | 어떤 컨트롤러도 반환 타입으로 쓰지 않아 springdoc이 찾지 못한다 |

### 문서 애노테이션의 위치

**문서 애노테이션은 컨트롤러가 아니라 `*ApiSpec` 인터페이스에 둔다.** 컨트롤러
`Xxx`의 문서 계약은 같은 패키지의 `XxxApiSpec`이다.

```java
@Tag(name = "백오피스 인증", description = "운영자 세션 로그인과 로그아웃")
public interface OperatorLoginApiSpec {

	@Operation(summary = "운영자 로그인", description = "...")
	@ApiResponses({ ... })
	@PostMapping("/login")
	ResponseEntity<ApiResponse<OperatorSessionResponse>> login(
		@RequestBody @Valid OperatorLoginRequest request, ...);
}

@RestController
@RequestMapping("/admin")
public class OperatorLoginController implements OperatorLoginApiSpec {

	@Override
	public ResponseEntity<ApiResponse<OperatorSessionResponse>> login(...) { ... }
}
```

문서 애노테이션은 본문보다 길다. 한 파일에 두면 로직이 애노테이션 사이에 파묻힌다.
분리하면 컨트롤러를 읽을 때 동작만 보이고, 문서를 고칠 때 로직을 건드릴 위험이 없다.

경계는 이렇게 나눈다.

| 위치 | 두는 것 |
| --- | --- |
| `XxxApiSpec` | `@Tag`, `@Operation`, `@ApiResponses`, `@SecurityRequirement`, `@Parameter`, 메서드 매핑(`@GetMapping` 등), `@RequestBody`, `@Valid`, `@PathVariable` |
| `XxxController` | `@RestController`, 클래스 수준 `@RequestMapping`, `@Override` 구현 |

`@RestController`는 빈 정의라 인터페이스로 옮길 수 없다. 클래스 수준
`@RequestMapping`도 라우팅 뿌리가 빈에서 보이는 편이 나아 구현에 남긴다.

DTO의 `@Schema`는 DTO 자신에 남는다. 옮길 대상이 아니다.

확인한 것: Spring과 springdoc 모두 인터페이스에 선언한 매핑·파라미터·문서
애노테이션을 찾아낸다. 이 저장소에서는 분리 전후의 `docs/api/openapi.json`이 바이트
단위로 같았고, `OperatorLoginIntegrationTest`가 인터페이스에 선언한 `@Valid`가
실제로 400을 내는지 검사한다.

### 엔드포인트별 보강

엔드포인트마다 다른 정보는 커스터마이저가 추측하면 안 된다. 다음은 각 `*ApiSpec`의
애노테이션으로 적는다.

| 항목 | 애노테이션 | 근거를 찾을 곳 |
| --- | --- | --- |
| operation `summary`와 `description` | `@Operation` | 컨트롤러의 설계 의도 |
| 엔드포인트별 오류 응답 | `@ApiResponse` | 해당 서비스가 던지는 `XxxErrorCode` |
| 인증 요구 | `@SecurityRequirement` | `SecurityConfiguration`의 경로 규칙 |
| 요청·응답 필드 설명 | `@Schema(description = ...)` | DTO |

오류 응답을 추측해서 적지 않는다. 근거는 서비스가 실제로 던지는 `XxxException`과 그
`XxxErrorCode`, 그리고 `docs/error-codes.md`의 코드·상태 표다. 필터 단계에서 끝나는
`401`·`403`은 `AuthEntryPoints`가 같은 `ApiErrorResponse` 형식으로 만든다.

`@Schema`에 `example`을 넣지 않는다. 스펙 산출물이 저장소에 커밋되므로 예시로 적은
자격증명이 그대로 공개된다. 통합 테스트가 금지 문자열을 검사한다.

**`@ApiResponse`를 하나라도 선언하면 springdoc이 반환 타입에서 만들던 `200`을 버린다.**
오류 응답만 적으면 성공 응답이 통째로 사라진다. `200`을 `content` 없이 함께 선언하면
springdoc이 반환 타입으로 채운다.

```java
@ApiResponses({
	@io.swagger.v3.oas.annotations.responses.ApiResponse(
		responseCode = "200", description = "..."),
	@io.swagger.v3.oas.annotations.responses.ApiResponse(
		responseCode = "423", description = "...", content = ...)
})
```

swagger의 `@ApiResponse`는 이 저장소의 응답 래퍼와 이름이 겹친다. 래퍼가 반환 타입으로
훨씬 자주 쓰이므로 그쪽을 import하고 애노테이션 쪽을 정규화한다.

`/harness-api-docs` 스킬과 `agents/api-docs-executor.md`가 이 작업을 돕는다.

## 6. 아직 정하지 않은 것

- **목록 응답과 페이지네이션.** 목록 API가 생길 때 `data` 안에 들어갈
  `PageResponse<T>` 형식을 별도로 정한다. 현재 계약은 이를 막지 않는다.
