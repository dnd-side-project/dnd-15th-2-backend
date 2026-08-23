# API Docs Review: Feed

> Created at: `2026-08-23T01:16:07+09:00`
> GitHub Issue: `#189`
> Target: `AnswerReadApiSpec`, `InboxApiSpec`, `SentPostApiSpec`
> (`src/main/java/com/dnd/qello/feed/web/`)
> 문장 기준: `docs/api/OPENAPI_WRITING_GUIDE.md`

이 문서는 `*ApiSpec`의 문장 검토 결과다. `harness-api-docs` 스킬의 `review` 모드
산출물이며 작성 시점에는 `*ApiSpec` 원본을 수정하지 않았다. 아래 제안은 이후
도메인 담당자(Byuntil)가 검토 후 전부 승인해 실제 코드에 반영했다 — §3의
before/after는 그대로 두되, 현재는 "after" 상태가 저장소의 실제 코드다.

> **반영 완료 (2026-08-23)**: §3의 제안 전체(`@Tag` 종결어미 1건, 404 응답 6건,
> `@Parameter` 8건, `@Schema(description)` 필드 약 50개, `@Schema(name)` 4건)를
> `*ApiSpec` 3개·응답 DTO 8개에 적용했다. `editedAt` null 조건은 반영 단계에서
> 근거(`ANS-DOM-010`)를 확인해 그대로 반영했다(§4). `docs/error-codes.md`
> 갱신 1건만 `*ApiSpec` 밖이라 미반영으로 남아 있다(§4).
> 반영 후 `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`와
> `./harness pr-ready --project-tests`를 재실행해 통과를 확인했다.

## 1. Executive summary

- 대상 엔드포인트 수: 9 (`AnswerReadApiSpec` 2, `InboxApiSpec` 4, `SentPostApiSpec` 3)
- 발견된 문제 수:
  - `@Tag` 종결어미 위반 1건(`AnswerReadApiSpec`, `한다`체)
  - 누락된 오류 응답 6건 (계정 게이트발 `FED-APP-001` 404가 도메인별 404와 함께
    문서화되지 않음)
  - 누락된 query parameter 설명 8개 (`category`, `directionSegmentKey`, `filter`,
    `cursorSubmittedAt`, `cursorPostId`, `limit`×2, `cursorPublishedAt`,
    `cursorAnswerId` — `limit`은 두 곳에서 반복)
  - 누락된 `@Schema(description)` 필드 8개 DTO 전 필드(약 50개 필드). 이 저장소의
    응답 DTO 전체(9개 도메인)가 같은 상태라 Feed만의 문제는 아니다.
  - **스키마 이름 충돌 위험 4건** — `InboxListingResponse.Card`,
    `InboxListingResponse.Chip`, `AnswerListingResponse.Answer`,
    `SentPostListingResponse.Cursor`가 이름을 지정하지 않아 전역
    `components.schemas`에 `Card`·`Chip`·`Answer`·`Cursor`라는 흔한 이름 그대로
    노출된다. 같은 패턴을 이미 세 곳(`SentPostCard`, `AnswerCursor`,
    `NotificationCursor`)에서 이름을 붙여 피했는데 이 네 곳만 빠졌다. 지금은
    저장소 전체에 이름 중복이 없어 조용하지만, 다른 도메인이 `Card`나 `Cursor`라는
    이름의 nested record를 하나만 더 추가해도 springdoc이 두 타입을 구분하지
    못하고 한쪽 스키마가 다른 쪽으로 조용히 덮인다.
  - 별도 문서 불일치 1건 — `docs/error-codes.md` §13(feed)에 `FeedErrorCode` 9개 중
    7개(`FED-DOM-001`~`003`, `FED-APP-001`~`002`, `FED-VAL-001`~`002`)가 아예 없다.
    `*ApiSpec` 파일이 아니라서 이 리뷰의 수정 대상 밖이다(§4 참고).
- 6점 대조 중 실행하지 못한 항목: 없음. Docker가 있어 6번(재생성 diff)까지 실제로
  실행했다.

## 2. 6점 대조 결과

`docs/api/OPENAPI_WRITING_GUIDE.md` §9 기준. 항목별로 도메인 전체를 훑어 문제가
있는 엔드포인트만 나열한다.

| # | 대조 | 결과 | 근거 |
| --- | --- | --- | --- |
| 1 | Controller ↔ ApiSpec | 이상 없음 | `AnswerReadController`(2)·`InboxController`(4)·`SentPostController`(3) 구현 메서드 수가 각 `*ApiSpec`과 정확히 일치 |
| 2 | ApiSpec ↔ DTO | 문제 있음 | 응답 DTO 8개 전 필드에 `@Schema(description)` 없음(§3 각 절 표 참고). `SentPostListingResponse.Card`·`AnswerListingResponse.Cursor`의 `@Schema(name=...)`는 스키마 이름 지정용이지 필드 설명이 아니다. 요청 query parameter 8개도 `@Parameter(description)` 없음(경로 변수는 전부 있음) |
| 3 | ApiSpec ↔ Service | 문제 있음 | `AccountEligibilityGate.require()`(`AccountEligibilityGate.java:26-31`)가 모든 `FeedInteractionApplicationService`·`InboxApplicationService` 메서드 진입부에서 `FED-APP-001`(404)·`FED-APP-002`(403)를 던질 수 있다. 403은 6개 엔드포인트 모두 문서화됐지만, 도메인별 404가 이미 있는 6개 엔드포인트는 그 옆에 `FED-APP-001`을 함께 적지 않았다(§3 참고) |
| 4 | ApiSpec ↔ `docs/error-codes.md` | 문제 있음(별도 문서) | `docs/error-codes.md:290-296`(§13 feed)에 `FED-INFRA-001~003` 3개만 있고 `FeedErrorCode.java`가 실제로 정의한 `FED-DOM-001~003`·`FED-APP-001~002`·`FED-VAL-001~002` 7개가 없다 |
| 5 | ApiSpec ↔ SecurityConfiguration | 이상 없음 | `appApiSecurityFilterChain`(`SecurityConfiguration.java:121-132`)이 `/api/**`에 `DEVICE_REGISTRATION_PATH`·`DEVICE_TOKEN_PATH`만 `permitAll`이고 나머지는 `anyRequest().authenticated()` — Feed 9개 엔드포인트(`/api/v1/direction/**`) 전부 이 체인 하위이고 세 `*ApiSpec` 모두 `@SecurityRequirement(APP_ACCESS_TOKEN_SCHEME)`을 선언함 |
| 6 | ApiSpec ↔ `docs/api/openapi.json` | 이상 없음 | `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"` 재생성 후 `git diff --stat -- docs/api/openapi.json`이 비어 있음 — 현재 코드가 곧 발행 스펙 그대로다 |

## 3. 엔드포인트별 제안

### 인터페이스 수준 — `AnswerReadApiSpec`의 `@Tag`

| 항목 | 내용 |
| --- | --- |
| 문장 기준 위반 | 종결어미. `해제한다`(한다체) — 가이드 §1은 `합니다`체로 통일하라고 정함 |

**Before**

```java
@Tag(name = "답변 읽음 처리", description = "질문자·수신자의 답변 열람 시각을 기록해 미읽음 배지를 해제한다")
```

**After**

```java
@Tag(name = "답변 읽음 처리", description = "질문자·수신자의 답변 열람 시각을 기록해 미읽음 배지를 해제합니다")
```

**변경 근거**

- `@Tag`는 인터페이스에 한 번만 있어 이 파일의 두 엔드포인트(`markSenderAnswersRead`,
  `markRecipientAnswersRead`) 모두에 적용된다.
- 이 파일의 `@Operation` summary·description은 전부 `합니다`체였는데 `@Tag`만
  예외였다. 첫 리뷰에서는 `@Operation` 단위로만 종결어미를 확인하고 인터페이스
  상단의 `@Tag`는 별도로 확인하지 않아 놓쳤다 — 재검토하며 `InboxApiSpec`·
  `SentPostApiSpec`의 `@Tag`도 함께 다시 훑었고, 그 둘은 동사 종결형이 아니라
  명사구(`"...조회"`, `"...변경"`)라 이 규칙이 적용되지 않는다(문제 없음).

---

### `PUT /posts/{postId}/answers/read` — `markSenderAnswersRead`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 404에 `FED-APP-001`(계정을 찾을 수 없음) 누락. 현재는 `DIR-DOM-009`만 있음 |
| 누락된 `@Schema(description)` 필드 | `AnswersReadResponse.answersReadAt` |
| 문장 기준 위반 | 없음 (이 endpoint의 `@Operation` 자체는 준수. 같은 파일의 `@Tag` 위반은 위 절 참고) |

**Before**

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문글을 찾을 수 없습니다. (DIR-DOM-009)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
```

**After**

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없거나 질문글을 찾을 수 없습니다. (FED-APP-001, DIR-DOM-009)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
```

**변경 근거**

- `FeedInteractionApplicationService.markSenderAnswersRead`(85-87행)가
  `accountEligibilityGate.require(senderId)`를 먼저 호출한다. 이 게이트는 계정이
  없으면 `FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND`(`FED-APP-001`, 404)를 던진다
  (`AccountEligibilityGate.java:26-31`).
- 그다음 `directionPostService.markAnswersRead`가
  `DirectionErrorCode.POST_NOT_FOUND`(`DIR-DOM-009`, 404)를 던질 수 있다
  (`DirectionPostService.java:260-262`).
- 두 조건 모두 같은 404 응답 코드로 나오므로 한 줄에 합쳐 적는다.
  `NotificationApiSpec.markRead`가 이미 같은 방식을 쓴다
  (`"알림이 없거나 남의 알림입니다. (NOT-APP-001, NOT-DOM-004)"`).

DTO 제안은 아래 `markRecipientAnswersRead`와 공유한다(같은 `AnswersReadResponse`).

---

### `PUT /inbox/{postRecipientId}/answers/read` — `markRecipientAnswersRead`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 404에 `FED-APP-001` 누락. 현재는 `DIR-DOM-008`만 있음 |
| 누락된 `@Schema(description)` 필드 | `AnswersReadResponse.answersReadAt` (위 엔드포인트와 DTO 공유) |
| 문장 기준 위반 | 없음 (이 endpoint의 `@Operation` 자체는 준수. 같은 파일의 `@Tag` 위반은 위 절 참고) |

**Before**

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "수신 항목을 찾을 수 없습니다. (DIR-DOM-008)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
```

**After**

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없거나 수신 항목을 찾을 수 없습니다. (FED-APP-001, DIR-DOM-008)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
```

**변경 근거**

- `FeedInteractionApplicationService.markRecipientAnswersRead`(92-94행)도
  `accountEligibilityGate.require(recipientId)`를 먼저 호출한다.
- `postRecipientService.markAnswersRead`의 `load()`가
  `DirectionErrorCode.RECIPIENT_NOT_FOUND`(`DIR-DOM-008`, 404)를 던진다
  (`PostRecipientService.java:101-104`).

**`AnswersReadResponse` — DTO 제안 (두 엔드포인트 공유)**

```java
package com.dnd.qello.feed.web.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** 답변 읽음 처리 공개 모델. 질문자·수신자 두 읽음 경로가 함께 쓴다. */
public record AnswersReadResponse(
	@Schema(description = "이번 호출로 갱신된 답변 열람 시각") Instant answersReadAt
) { }
```

---

### `GET /inbox` — `list` (Inbox)

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음. `InboxQueryService.list`는 예외를 던지지 않는다(코드 확인) |
| 누락된 `@Parameter(description)` | `category`, `directionSegmentKey` |
| 누락된 `@Schema(description)` 필드 | `InboxListingResponse`(`cards`, `chips`) 및 nested `Card`(16개 필드)·`Chip`(4개 필드) |
| 스키마 이름 충돌 위험 | `Card`, `Chip`이 이름 없이 노출됨(§1 참고) |
| 문장 기준 위반 | 없음 |

**Before**

```java
@GetMapping("/inbox")
ResponseEntity<ApiResponse<InboxListingResponse>> list(
	@RequestParam(defaultValue = "UNANSWERED") InboxCategory category,
	@RequestParam(required = false) String directionSegmentKey,
	@Parameter(hidden = true) Authentication authentication);
```

**After**

```java
@GetMapping("/inbox")
ResponseEntity<ApiResponse<InboxListingResponse>> list(
	@Parameter(description = "조회할 카테고리. UNANSWERED는 아직 답변하지 않은 항목, ANSWERED는 답변을 마친 항목입니다")
	@RequestParam(defaultValue = "UNANSWERED") InboxCategory category,
	@Parameter(description = "결과를 좁힐 방향 구간 키. 생략하면 카테고리 전체를 봅니다. chips 집계는 이 값과 무관하게 항상 카테고리 전체 기준입니다")
	@RequestParam(required = false) String directionSegmentKey,
	@Parameter(hidden = true) Authentication authentication);
```

**변경 근거**

- `InboxCategory` 두 값의 뜻은 그 enum의 Javadoc에 있다(`InboxCategory.java:14-20`).
- `directionSegmentKey`가 `cards`만 좁히고 `chips`는 그대로 카테고리 전체를
  집계한다는 사실은 `InboxQueryService.list`의 Javadoc(27-29행)과
  `InboxListing`의 Javadoc에 명시돼 있다.

**`InboxListingResponse` — DTO 제안 (`detail` 응답의 `card`와도 공유)**

```java
package com.dnd.qello.feed.web.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dnd.qello.feed.view.DirectionChip;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxListing;

/** 정확 위치와 내부 사용자 식별자를 제외한 수신함 목록 공개 모델이다. */
public record InboxListingResponse(
	@Schema(description = "받은 질문 카드 목록") List<Card> cards,
	@Schema(description = "방향 구간별 집계. directionSegmentKey와 무관하게 category 전체 기준입니다") List<Chip> chips
) {
	public InboxListingResponse {
		cards = List.copyOf(cards);
		chips = List.copyOf(chips);
	}

	public static InboxListingResponse from(InboxListing listing) {
		return new InboxListingResponse(
			listing.cards().stream().map(Card::from).toList(),
			listing.chips().stream().map(Chip::from).toList());
	}

	@Schema(name = "InboxCard")
	public record Card(
		@Schema(description = "이 수신 항목의 식별자") long postRecipientId,
		@Schema(description = "이 카드가 속한 질문글 식별자") long postId,
		@Schema(description = "이 수신 항목의 현재 상태") String status,
		@Schema(description = "이 질문글이 사용한 질문 문항의 텍스트") String questionText,
		@Schema(description = "발신자가 추가로 쓴 본문") String bodyText,
		@Schema(description = "첨부된 이미지 식별자 목록") List<Long> mediaIds,
		@Schema(description = "발신자의 대략적인 지역 코드. 정확한 위치는 포함하지 않습니다") String senderCoarseRegionCode,
		@Schema(description = "이 카드를 보는 수신자 기준으로 계산한 도착 방위각(도 단위). 화면의 방향 표시는 이 값을 씁니다") BigDecimal inboundBearingDegrees,
		@Schema(description = "발신자와의 거리(미터). 근거리 구간에서는 null이고 대신 distanceBand가 채워집니다") Long distanceM,
		@Schema(description = "근거리 구간일 때만 채워지는 거리 표시 문구. 그 외에는 null이고 대신 distanceM이 채워집니다") String distanceBand,
		@Schema(description = "이 수신 항목이 매칭된 시각") Instant matchedAt,
		@Schema(description = "이 질문글이 만료되는 시각") Instant expiresAt,
		@Schema(description = "이 질문글에 달린 답변 수") long answerCount,
		@Schema(description = "조회하는 본인이 이 질문글에 공감했는지 여부") boolean reactedByMe,
		@Schema(description = "이 질문글이 받은 공감 총수") long reactionCount,
		@Schema(description = "마지막 답변 열람 이후 새로 공개된 답변 수") long unreadAnswerCount
	) {
		public Card {
			mediaIds = List.copyOf(mediaIds);
		}

		public static Card from(InboxCard card) {
			return new Card(
				card.postRecipientId(), card.postId(), card.status().name(), card.questionText(), card.bodyText(),
				card.mediaIds(), card.senderCoarseRegionCode(), card.inboundBearingDegrees(), card.distanceM(),
				card.distanceBand(), card.matchedAt(), card.expiresAt(), card.answerCount(), card.reactedByMe(),
				card.reactionCount(), card.unreadAnswerCount());
		}
	}

	@Schema(name = "DirectionChip")
	public record Chip(
		@Schema(description = "방향 구간을 식별하는 키") String segmentKey,
		@Schema(description = "그 방향 구간의 화면 표시명") String displayName,
		@Schema(description = "화면에 표시할 때 쓰는 정렬 순서") int sortOrder,
		@Schema(description = "그 방향 구간에 속한 수신 항목 수. 0인 방향은 나타나지 않습니다") long count
	) {
		public static Chip from(DirectionChip chip) {
			return new Chip(chip.segmentKey(), chip.displayName(), chip.sortOrder(), chip.count());
		}
	}
}
```

**변경 근거(필드)**

- `status`~`unreadAnswerCount`까지 전 필드의 뜻은 `InboxCard`의 클래스 Javadoc에
  이미 근거로 적혀 있다(`InboxCard.java:9-19`). 특히 `distanceM`/`distanceBand`
  상호배타 규칙과 `inboundBearingDegrees`가 "수신자 기준"이라는 점은 그대로
  옮겼다.
- `unreadAnswerCount`는 `PostRecipientService.markAnswersRead`의 Javadoc
  ("`새로운 답변 n개` 배지가 이 값으로 계산된다", 86-91행)과 대응한다.
- `Chip` 필드는 `DirectionChip`의 클래스 Javadoc(feed/view/DirectionChip.java)을
  그대로 옮겼다.
- **스키마 이름**: `SentPostListingResponse.Card`가 이미 `@Schema(name =
  "SentPostCard")`로, `AnswerListingResponse.Cursor`가 `@Schema(name =
  "AnswerCursor")`로 자신의 근원 타입(`feed.view.SentPostCard`,
  `PostAnswerQueryRepository.AnswerCursor`) 이름을 그대로 재사용해 전역 이름
  충돌을 피했다. 같은 규칙을 적용하면 `InboxListingResponse.Card`는 자신의 근원
  타입인 `feed.view.InboxCard`를 그대로 따라 `"InboxCard"`가, `Chip`은
  `feed.view.DirectionChip`을 따라 `"DirectionChip"`이 된다.

---

### `GET /inbox/{postRecipientId}` — `detail` (Inbox)

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 404에 `FED-APP-001` 누락. 현재는 `FED-DOM-001`만 있음 |
| 누락된 `@Schema(description)` 필드 | `InboxDetailResponse.openedAt`, `.skipRequestedAt` (`.card`는 위 `list`와 공유) |
| 문장 기준 위반 | 없음 |

**Before**

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "수신 자격이 있는 항목을 찾을 수 없습니다. (FED-DOM-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
```

**After**

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없거나 수신 자격이 있는 항목을 찾을 수 없습니다. (FED-APP-001, FED-DOM-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
```

**변경 근거**

- `InboxApplicationService.detail`(41-52행)도 `accountEligibilityGate.require(recipientId)`를
  가장 먼저 호출한다.
- 409(`FED-DOM-002`) 설명은 그대로 둔다. `postRecipientService.open()`이 호출하는
  도메인 메서드 `PostRecipient.open()`(`PostRecipient.java:222` 부근, "열람
  처리를 할 수 없는 상태입니다")이 `DIR-DOM-004`를 던지고,
  `InboxApplicationService.mapCommandException`이 이를 `FED-DOM-002`(409)로
  번역하는 경로가 실제로 있어 현재 문서가 맞다(재확인만 하고 변경 없음).

**`InboxDetailResponse` — DTO 제안**

```java
package com.dnd.qello.feed.web.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dnd.qello.feed.view.InboxDetail;

/** 수신함 카드 공개 모델과 열람·넘김 시각만 노출하는 상세 응답이다. */
public record InboxDetailResponse(
	@Schema(description = "받은 질문 카드. list 응답의 카드와 같은 구조입니다") InboxListingResponse.Card card,
	@Schema(description = "이 항목을 처음 연 시각") Instant openedAt,
	@Schema(description = "넘김을 요청한 시각. 넘김을 요청하지 않았으면 null입니다") Instant skipRequestedAt
) {
	public static InboxDetailResponse from(InboxDetail detail) {
		return new InboxDetailResponse(
			InboxListingResponse.Card.from(detail.card()), detail.openedAt(), detail.skipRequestedAt());
	}
}
```

**변경 근거**: `openedAt`은 `postRecipientService.open()` 호출로 기록되는 시각이고,
`skipRequestedAt`이 `PostRecipient.requestSkip(at)`에서 채워지고
`revertSkip()`에서 다시 `null`로 돌아간다는 사실은 `PostRecipient.java`의
`requestSkip`(158-166행)·`revertSkip`(169-176행) 생성자 인자 순서로 확인했다.

---

### `PUT /inbox/{postRecipientId}/skip` — `skip`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 404에 `FED-APP-001` 누락. 현재는 `FED-DOM-001`만 있음 |
| 누락된 `@Schema(description)` 필드 | `InboxCommandResponse` 4개 필드 (아래 `revertSkip`과 공유) |
| 문장 기준 위반 | 없음 |

**Before**

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "변경할 수신함 항목을 찾을 수 없습니다. (FED-DOM-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
```

**After**

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없거나 변경할 수신함 항목을 찾을 수 없습니다. (FED-APP-001, FED-DOM-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
```

**변경 근거**: `InboxApplicationService.skip`(54-63행)도 계정 게이트를 먼저
호출한다. 409는 `PostRecipient.requestSkip()`(163행, "넘김을 요청할 수 없는
상태입니다")이 실제로 던질 수 있어 현재 문서가 맞다.

---

### `DELETE /inbox/{postRecipientId}/skip` — `revertSkip`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 404에 `FED-APP-001` 누락. 현재는 `FED-DOM-001`만 있음 |
| 누락된 `@Schema(description)` 필드 | `InboxCommandResponse` 4개 필드 |
| 문장 기준 위반 | 없음 |

**Before**

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "변경할 수신함 항목을 찾을 수 없습니다. (FED-DOM-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
```

**After**

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없거나 변경할 수신함 항목을 찾을 수 없습니다. (FED-APP-001, FED-DOM-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
```

**변경 근거**: `InboxApplicationService.revertSkip`(65-78행)도 계정 게이트를 먼저
호출한다. 409는 `revertibleUntil()`과 `PostRecipient.revertSkip()`(173행,
"되돌릴 수 있는 상태가 아닙니다") 양쪽 다 실제로 던질 수 있어 현재 문서가 맞다.

**`InboxCommandResponse` — DTO 제안 (`skip`·`revertSkip` 공유)**

```java
package com.dnd.qello.feed.web.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dnd.qello.direction.domain.PostRecipient;

/** 넘김 명령 결과. 서버가 관리하는 사용자·위치·저장소 정보는 포함하지 않는다. */
public record InboxCommandResponse(
	@Schema(description = "이 수신함 항목의 식별자") long postRecipientId,
	@Schema(description = "명령 적용 후의 수신 상태") String status,
	@Schema(description = "넘김을 요청한 시각. 넘김을 요청하지 않았거나 되돌렸으면 null입니다") Instant skipRequestedAt,
	@Schema(description = "이 넘김을 되돌릴 수 있는 마감 시각. skip 응답에만 채워지고 revertSkip 응답에서는 항상 null입니다") Instant revertibleUntil
) {
	public static InboxCommandResponse from(PostRecipient recipient, Instant revertibleUntil) {
		return new InboxCommandResponse(
			recipient.getId(), recipient.getStatus().name(), recipient.getSkipRequestedAt(), revertibleUntil);
	}
}
```

**변경 근거**: `skipRequestedAt`/`revertibleUntil`의 null 조건은
`InboxController.skip`/`revertSkip`(46-60행)의 호출부와 `PostRecipient.revertSkip()`이
`skipRequestedAt`을 `null`로 재구성하는 생성자 호출(169-176행)로 확인했다.

---

### `GET /posts` — `list` (SentPost)

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음. `FED-VAL-001`·`FED-VAL-002`·`FED-APP-001`·`FED-APP-002` 전부 문서화됨 |
| 누락된 `@Parameter(description)` | `filter`, `cursorSubmittedAt`, `cursorPostId`, `limit` |
| 누락된 `@Schema(description)` 필드 | `SentPostListingResponse`(`cards`, `nextCursor`) 및 nested `Cursor` |
| 스키마 이름 충돌 위험 | `Cursor`가 이름 없이 노출됨(§1 참고). `Card`는 이미 `SentPostCard`로 지정돼 있어 문제 없음 |
| 문장 기준 위반 | 없음 |

**Before**

```java
@GetMapping("/posts")
ResponseEntity<ApiResponse<SentPostListingResponse>> list(
	@RequestParam(defaultValue = "ALL") SentPostFilter filter,
	@RequestParam(required = false) Instant cursorSubmittedAt,
	@RequestParam(required = false) Long cursorPostId,
	@RequestParam(defaultValue = "20") int limit,
	@Parameter(hidden = true) Authentication authentication);
```

**After**

```java
@GetMapping("/posts")
ResponseEntity<ApiResponse<SentPostListingResponse>> list(
	@Parameter(description = "만료 여부로 좁히는 필터. IN_PROGRESS는 아직 만료되지 않은 질문글, EXPIRED는 만료된 질문글입니다")
	@RequestParam(defaultValue = "ALL") SentPostFilter filter,
	@Parameter(description = "페이지네이션 커서: 이전 페이지 마지막 항목의 제출 시각. cursorPostId와 함께 지정하거나 함께 생략합니다")
	@RequestParam(required = false) Instant cursorSubmittedAt,
	@Parameter(description = "페이지네이션 커서: 이전 페이지 마지막 항목의 질문글 식별자. cursorSubmittedAt과 함께 지정하거나 함께 생략합니다")
	@RequestParam(required = false) Long cursorPostId,
	@Parameter(description = "한 번에 가져올 최대 개수. 1~50, 기본 20")
	@RequestParam(defaultValue = "20") int limit,
	@Parameter(hidden = true) Authentication authentication);
```

**변경 근거**

- `SentPostFilter` 값의 뜻은 그 enum의 Javadoc에 있다(`SentPostFilter.java`).
- `limit` 허용 범위(1~50)는 `FeedInteractionApplicationService.requireValidLimit`과
  `MAX_LIMIT = 50`(43, 121-126행)에서 확인했다.

**`SentPostListingResponse` — DTO 제안 (`detail` 응답의 `card`와도 공유)**

```java
package com.dnd.qello.feed.web.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dnd.qello.feed.view.SentPostCard;

/**
 * `내가 보낸 질문` 목록 공개 모델. nextCursor는 반환 건수가 요청 limit과 같을 때만
 * 채운다 — 그보다 적으면 마지막 페이지라는 뜻이라 null이다.
 */
public record SentPostListingResponse(
	@Schema(description = "내가 보낸 질문글 카드 목록") List<Card> cards,
	@Schema(description = "다음 페이지 커서. 마지막 페이지면 null입니다") Cursor nextCursor
) {
	public SentPostListingResponse {
		cards = List.copyOf(cards);
	}

	public static SentPostListingResponse from(List<SentPostCard> cards, int limit) {
		List<Card> mapped = cards.stream().map(Card::from).toList();
		Cursor next = cards.size() == limit
			? new Cursor(cards.get(cards.size() - 1).submittedAt(), cards.get(cards.size() - 1).postId())
			: null;
		return new SentPostListingResponse(mapped, next);
	}

	@Schema(name = "SentPostCard")
	public record Card(
		@Schema(description = "질문글 식별자") long postId,
		@Schema(description = "이 질문글이 사용한 질문 문항의 텍스트") String questionText,
		@Schema(description = "발신자가 추가로 쓴 본문") String bodyText,
		@Schema(description = "첨부된 이미지 식별자 목록") List<Long> mediaIds,
		@Schema(description = "이 질문글을 보낼 때 기록된 발신자의 대략적 지역 코드") String coarseRegionCode,
		@Schema(description = "이 질문글을 제출한 시각") Instant submittedAt,
		@Schema(description = "이 질문글이 만료되는 시각") Instant expiresAt,
		@Schema(description = "이 질문글에 달린 답변 수") long answerCount,
		@Schema(description = "이 질문글이 받은 공감 총수") long reactionCount,
		@Schema(description = "답변 열람 시각 이후 새로 공개된 답변 수") long unreadAnswerCount
	) {
		public Card {
			mediaIds = List.copyOf(mediaIds);
		}

		public static Card from(SentPostCard card) {
			return new Card(
				card.postId(), card.questionText(), card.bodyText(), card.mediaIds(), card.coarseRegionCode(),
				card.submittedAt(), card.expiresAt(), card.answerCount(), card.reactionCount(),
				card.unreadAnswerCount());
		}
	}

	@Schema(name = "SentPostCursor")
	public record Cursor(
		@Schema(description = "다음 페이지 조회에 쓸 제출 시각") Instant submittedAt,
		@Schema(description = "다음 페이지 조회에 쓸 질문글 식별자") long postId
	) { }
}
```

**변경 근거(필드)**: 전 필드 뜻은 `SentPostCard`의 클래스 Javadoc(feed/view/SentPostCard.java)에
근거가 있다. `unreadAnswerCount`는 그 Javadoc이 "`direction_post.answers_read_at`
이후 공개된 답변 수"라고 명시한다.

**스키마 이름**: `Cursor`는 근원 타입인
`SentPostQueryRepository.SentPostCursor`를 따라 `"SentPostCursor"`로 지정한다.
같은 파일의 `Card`는 이미 `"SentPostCard"`로 지정돼 있어 그대로 둔다.

---

### `GET /posts/{postId}` — `detail` (SentPost)

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 404에 `FED-APP-001` 누락. 현재는 `FED-DOM-003`만 있음 |
| 누락된 `@Schema(description)` 필드 | `SentPostDetailResponse.answersReadAt` (`.card`는 위 `list`와 공유) |
| 문장 기준 위반 | 없음 |

**Before**

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문글을 찾을 수 없습니다. (FED-DOM-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
```

**After**

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없거나 질문글을 찾을 수 없습니다. (FED-APP-001, FED-DOM-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
```

**변경 근거**: `FeedInteractionApplicationService.sentPostDetail`(63-68행)도
`accountEligibilityGate.require(senderId)`를 먼저 호출한 뒤
`sentPostQueryService.detail(...).orElseThrow(FED-DOM-003)`으로 이어진다. 두
조건 모두 같은 404 응답으로 나온다.

**`SentPostDetailResponse` — DTO 제안**

```java
package com.dnd.qello.feed.web.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dnd.qello.feed.view.SentPostDetail;

/** `내가 보낸 질문` 상세 공개 모델이다. */
public record SentPostDetailResponse(
	@Schema(description = "내가 보낸 질문글 카드. list 응답의 카드와 같은 구조입니다") SentPostListingResponse.Card card,
	@Schema(description = "이 질문글의 답변을 마지막으로 읽은 시각") Instant answersReadAt
) {
	public static SentPostDetailResponse from(SentPostDetail detail) {
		return new SentPostDetailResponse(SentPostListingResponse.Card.from(detail.card()), detail.answersReadAt());
	}
}
```

---

### `GET /posts/{postId}/answers` — `answers`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음. `postAnswerQueryService.answers`는 자격 없는 뷰어에게 빈 목록을 반환할 뿐 예외를 던지지 않는다(코드 확인, `SentPostApiSpec.answers`의 200 설명도 이미 이 사실을 명시함) |
| 누락된 `@Parameter(description)` | `cursorPublishedAt`, `cursorAnswerId`, `limit` |
| 누락된 `@Schema(description)` 필드 | `AnswerListingResponse`(`answers`, `nextCursor`) 및 nested `Answer`(12개 필드)·`Cursor` |
| 스키마 이름 충돌 위험 | `Answer`가 이름 없이 노출됨(§1 참고). `Cursor`는 이미 `AnswerCursor`로 지정돼 있어 문제 없음 |
| 문장 기준 위반 | 없음 |

**Before**

```java
@GetMapping("/posts/{postId}/answers")
ResponseEntity<ApiResponse<AnswerListingResponse>> answers(
	@Parameter(description = "질문글 식별자", example = "101") @PathVariable long postId,
	@RequestParam(required = false) Instant cursorPublishedAt,
	@RequestParam(required = false) Long cursorAnswerId,
	@RequestParam(defaultValue = "20") int limit,
	@Parameter(hidden = true) Authentication authentication);
```

**After**

```java
@GetMapping("/posts/{postId}/answers")
ResponseEntity<ApiResponse<AnswerListingResponse>> answers(
	@Parameter(description = "질문글 식별자", example = "101") @PathVariable long postId,
	@Parameter(description = "페이지네이션 커서: 이전 페이지 마지막 답변의 공개 시각. cursorAnswerId와 함께 지정하거나 함께 생략합니다")
	@RequestParam(required = false) Instant cursorPublishedAt,
	@Parameter(description = "페이지네이션 커서: 이전 페이지 마지막 답변의 식별자. cursorPublishedAt과 함께 지정하거나 함께 생략합니다")
	@RequestParam(required = false) Long cursorAnswerId,
	@Parameter(description = "한 번에 가져올 최대 개수. 1~50, 기본 20")
	@RequestParam(defaultValue = "20") int limit,
	@Parameter(hidden = true) Authentication authentication);
```

**`AnswerListingResponse` — DTO 제안**

```java
package com.dnd.qello.feed.web.response;

import com.dnd.qello.feed.view.AnswerCard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 답변 목록 공개 모델. 정확 좌표와 작성자 내부 식별자는 싣지 않는다(기존 수신함
 * 규칙과 동일). nextCursor는 반환 건수가 요청 limit과 같을 때만 채운다.
 */
public record AnswerListingResponse(
	@Schema(description = "그 질문글의 답변 목록. 열람 자격이 없으면 빈 목록입니다") List<Answer> answers,
	@Schema(description = "다음 페이지 커서. 마지막 페이지면 null입니다") Cursor nextCursor
) {
	public AnswerListingResponse {
		answers = List.copyOf(answers);
	}

	public static AnswerListingResponse from(List<AnswerCard> cards, int limit) {
		List<Answer> mapped = cards.stream().map(Answer::from).toList();
		Cursor next = cards.size() == limit
			? new Cursor(cards.getLast().publishedAt(), cards.getLast().answerId())
			: null;
		return new AnswerListingResponse(mapped, next);
	}

	@Schema(name = "AnswerCard")
	public record Answer(
		@Schema(description = "답변 식별자") long answerId,
		@Schema(description = "답변 작성자의 닉네임") String authorNickname,
		@Schema(description = "답변 작성자의 대략적인 지역 코드") String authorCoarseRegionCode,
		@Schema(description = "답변 본문") String bodyText,
		@Schema(description = "첨부된 이미지 식별자 목록") List<Long> mediaIds,
		@Schema(description = "발신자 기준으로 계산한 방위각(도 단위)") BigDecimal bearingFromSenderDegrees,
		@Schema(description = "발신자와의 거리(미터). 근거리 구간에서는 null이고 대신 distanceBand가 채워집니다") Long distanceM,
		@Schema(description = "근거리 구간일 때만 채워지는 거리 표시 문구. 그 외에는 null이고 대신 distanceM이 채워집니다") String distanceBand,
		@Schema(description = "이 답변이 공개된 시각") Instant publishedAt,
		@Schema(description = "이 답변을 마지막으로 수정한 시각. 수정한 적이 없으면 null입니다") Instant editedAt,
		@Schema(description = "조회하는 본인이 이 답변에 공감했는지 여부") boolean reactedByMe,
		@Schema(description = "이 답변이 받은 공감 총수") long reactionCount
	) {
		public Answer {
			mediaIds = List.copyOf(mediaIds);
		}

		public static Answer from(AnswerCard card) {
			return new Answer(
				card.answerId(), card.authorNickname(), card.authorCoarseRegionCode(), card.bodyText(),
				card.mediaIds(), card.bearingFromSenderDegrees(), card.distanceM(), card.distanceBand(),
				card.publishedAt(), card.editedAt(), card.reactedByMe(), card.reactionCount());
		}
	}

	@Schema(name = "AnswerCursor")
	public record Cursor(
		@Schema(description = "다음 페이지 조회에 쓸 공개 시각") Instant publishedAt,
		@Schema(description = "다음 페이지 조회에 쓸 답변 식별자") long answerId
	) { }
}
```

**변경 근거**: 전 필드 뜻은 `AnswerCard`의 클래스 Javadoc(feed/view/AnswerCard.java)에
근거가 있다. `editedAt`의 null 조건은 최초 리뷰 때 DTO만으로 확정하지 못해
반영 보류로 남겼으나(§4 참고), 반영 단계에서 `Answer` 도메인의 생성자 불변식을
확인해 해소했다 — `Answer.java:161-163`이 "`editCount`가 0이면 `editedAt`은
없어야 하고, 그 반대도 마찬가지"(`ANS-DOM-010`)라고 명시적으로 검증한다. 즉
수정 이력이 없는 답변은 반드시 `editedAt == null`이다.

**스키마 이름**: `Answer`는 근원 타입인 `feed.view.AnswerCard`를 따라
`"AnswerCard"`로 지정한다. 같은 파일의 `Cursor`는 이미 `"AnswerCursor"`로 지정돼
있어 그대로 둔다.

## 4. 반영하지 않은 제안

- **`docs/error-codes.md` §13(feed) 갱신은 이 리뷰에 포함하지 않았다.** 6점
  대조 4번에서 `FeedErrorCode` 9개 중 7개가 그 문서에 없다는 사실을 확인했지만,
  `docs/error-codes.md`는 `*ApiSpec`이 아니라 이 리뷰(`review` 모드)의 수정
  대상 밖이다. 도메인 담당자가 `*ApiSpec` 반영과 별도로 처리해야 한다. 아직
  미반영.
- ~~`AnswerListingResponse.Answer.editedAt`의 null 조건은 DTO만으로 100% 확정하지
  못했다.~~ **해소됨** — 반영 단계에서 `Answer.java:161-163`(`ANS-DOM-010`
  생성자 검증)을 확인해 "수정 이력이 없으면 `editedAt`은 반드시 null"이라는
  사실을 확정했다. §3의 `AnswerListingResponse` 변경 근거에 반영.

## 5. 실행하지 못한 검증

없음. Docker가 가용해 6점 대조 6번(재생성 후 diff)까지 실제로 실행했다.

```text
./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"
  → BUILD SUCCESSFUL
git diff --stat -- docs/api/openapi.json
  → (출력 없음, 변경 없음)
```

## 6. Reviewer checklist

- [x] 모든 제안 문장이 실제 서비스/DTO 코드로 근거를 확인했다 (추측 없음) —
      `editedAt`도 반영 단계에서 `ANS-DOM-010` 근거로 확정(§4)
- [x] `*ApiSpec` 원본을 수정하지 않았다 (작성 시점 기준. 반영 완료 후 상태는
      머리말 참고)
- [x] `@Schema(example)`에 비밀값·계정 식별자를 쓰지 않았다
- [x] 내부 불변식 ID(`INV-*`)가 제안 문장에 남아 있지 않다
- [x] 6점 대조를 모두 실행했다
