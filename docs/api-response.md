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

## 5. 아직 정하지 않은 것

- **목록 응답과 페이지네이션.** 목록 API가 생길 때 `data` 안에 들어갈
  `PageResponse<T>` 형식을 별도로 정한다. 현재 계약은 이를 막지 않는다.
- **OpenAPI 문서화.** springdoc을 도입하면 `ApiResponse<T>`의 제네릭 스키마
  노출 방식을 함께 정한다.
