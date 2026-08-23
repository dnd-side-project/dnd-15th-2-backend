# API Docs Review: Answer/Media

> Created at: `2026-08-23T02:55:00+09:00`
> GitHub Issue: `#189`
> Target: `src/main/java/com/dnd/qello/answer/web/{AnswerSubmissionApiSpec,AnswerReactionApiSpec,MediaAssetApiSpec}.java`
> 문장 기준: `docs/api/OPENAPI_WRITING_GUIDE.md`

이 문서는 Answer/Media 3개 `*ApiSpec`의 문장 검토 결과다. `harness-api-docs`
스킬의 `review` 모드 산출물이며 `*ApiSpec` 원본은 이 모드에서 수정하지 않는다.

> **반영 완료 (2026-08-23).** 아래 제안은 검토를 거쳐 코드에 반영했다.
> §3의 "After"가 현재 코드 상태다.

## 1. Executive summary

- 대상 엔드포인트 수: **5** (`submit`, `react`, `cancel`, `issueUploadUrl`, `confirm`)
- 발견된 문제 수: **22**
  - 응답 DTO 필드 `@Schema(description)` 누락: **11건**
  - 오류 응답에 오류 코드 표기 누락(§7): **9건** — 이 세 파일은 `react`의 두 건을
    빼면 오류 코드를 하나도 적지 않았다
  - 404 설명이 실제 원인 하나를 빠뜨림: **1건** (`submit`의 계정 없음)
  - 낯선 단어를 쌓은 문장(§4·§8): **6건**
- 6점 대조 중 실행하지 못한 항목: **없음**
- **계약 이상 1건 발견. 코드는 고치지 않고 §4.1에 기록했다.**

## 2. 6점 대조 결과

| # | 대조 | 결과 | 근거 |
| --- | --- | --- | --- |
| 1 | Controller ↔ ApiSpec | **이상 없음** | `AnswerSubmissionController`·`AnswerReactionController`·`MediaAssetController`가 각 ApiSpec을 `implements`하고 `@Override` 5개 ↔ 매핑 5개가 1:1. 세 컨트롤러 모두 문서 애노테이션이 없고 `@RestController`·클래스 수준 `@RequestMapping`만 둔다 |
| 2 | ApiSpec ↔ DTO | **문제 11건** | 응답 `AnswerSubmissionResponse` 3, `MediaUploadResponse` 4, `MediaConfirmResponse` 2, `ReactionResponse` 2 전 필드가 무설명. 요청 `SubmitAnswerRequest`(2), `MediaUploadRequest`(3)는 이미 전 필드에 `description`이 있어 이상 없음 |
| 3 | ApiSpec ↔ Service | **문제 1건 + 계약 이상 1건** | `throw` 전수 확인. `submit`: `ensureActiveUser`가 `ACCOUNT_NOT_FOUND`(ANS-APP-002, 404)·`ACCOUNT_NOT_ELIGIBLE`(ANS-APP-003, 403), `AnswerSubmissionService:102`가 `RECIPIENT_NOT_FOUND`(ANS-DOM-011, 404) → **404 설명이 계정 없음을 빠뜨렸다.** `react`: `INVALID_ID`(400)·`INELIGIBLE_REACTOR`(403)만 → 선언과 일치하나 계약이 이상하다(§4.1). `cancel`: 자격 검사 없음(`AnswerReactionService:107-111`) → 401만 선언한 것이 정확. `issueUploadUrl`: `MEDIA_OWNER_MISMATCH`(403)를 던지지만 **HTTP 경로에서는 발생 불가** — 컨트롤러가 `new IssueUploadUrlCommand(userId, userId, ...)`로 요청자와 소유자를 같은 값으로 채운다(`MediaAssetController:41`). 403을 선언하지 않은 현재 문서가 맞다. `confirm`: `findByIdAndOwnerId`로 조회해 남의 미디어도 `MEDIA_NOT_FOUND`(404)이므로 403이 없는 것이 맞다 |
| 4 | ApiSpec ↔ `docs/error-codes.md` | **이상 없음** | 인용 대상 `ANS-*` 11개가 모두 등재돼 있고 HTTP 상태가 `AnswerErrorCode`와 일치 |
| 5 | ApiSpec ↔ SecurityConfiguration | **이상 없음** | `permitAll`은 `/api/v1/auth/devices`·`/api/v1/auth/token`뿐이고 나머지는 `anyRequest().authenticated()`. 세 파일의 인터페이스 수준 `@SecurityRequirement(APP_ACCESS_TOKEN_SCHEME)`가 정확 |
| 6 | ApiSpec ↔ `docs/api/openapi.json` | **실행함** | 반영 전 기준선 diff 없음. 반영 후 재생성 결과는 §2.1 |

### 2.1 반영 후 재대조

- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`: 통과.
- 재파싱 결과 이 도메인 응답 필드 **11개 중 무설명 0개**.
- 두 도메인 합산 `docs/api/openapi.json` diff는 100줄(73 추가/27 삭제)이고,
  변경 키 57개가 모두 Answer/Media·Direction 범위 안이었다(대상 밖 0개).
- 상세 수치는 `TASK.md`의 Validation evidence에 기록한다.

### 2.2 공통 커스터마이저가 채우는 항목

`OpenApiConventionCustomizer:84-85`가 모든 엔드포인트에 400·500을 넣는다. 기본
문구는 `요청 값이 올바르지 않습니다. 오류 코드는 docs/error-codes.md를 따른다.`
이므로, 도메인 고유의 400 원인을 알려야 할 때만 명시적으로 덮어쓴다. `cancel`처럼
도메인 400이 없는 경로는 기본값을 그대로 둔다.

## 3. 엔드포인트별 제안

### `POST /api/v1/direction/inbox/{postRecipientId}/answers` — `submit`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음(상태 코드 기준). 단 404 설명이 원인 하나를 빠뜨림 |
| 누락된 `@Schema(description)` 필드 | `AnswerSubmissionResponse` 3개 |
| 문장 기준 위반 | §4 낯선 단어(`멱등`, `moderation`, `endpoint`), §8 내부 상태값(`ALLOW`), §7 오류 코드 미표기 |

**Before**

```java
@Operation(
	summary = "답변 멱등 제출",
	description = "인증 수신자가 자신의 수신 항목에 답변을 제출합니다. 공개는 비동기 안전 검사 결과가 ALLOW일 때만"
		+ " 내부 moderation 결과 처리 경로에서 이루어지며, 이 endpoint는 공개 여부를 반환하지 않습니다.")
@ApiResponses({
	@ApiResponse(responseCode = "400", description = "본문, 미디어 또는 멱등키가 정책에 맞지 않습니다."),
	@ApiResponse(responseCode = "403", description = "미디어 소유권 또는 계정 자격이 없습니다."),
	@ApiResponse(responseCode = "404", description = "답변할 수 있는 수신 항목을 찾을 수 없습니다."),
	@ApiResponse(responseCode = "409", description = "멱등키가 다른 요청에 재사용됐거나 이미 이 항목에 답변이 등록되었습니다."),
	@ApiResponse(responseCode = "503", description = "안전 검사 접수 정책이 아직 활성화되지 않았습니다.")
})
```

**After**

```java
@Operation(
	summary = "답변 보내기",
	description = """
		내가 받은 질문에 답변을 씁니다.

		앱 로그인이 필요하고, 내게 온 질문에만 답할 수 있습니다. 한 질문에는 한 번만 \
		답할 수 있습니다.

		접수만 하고 바로 끝나는 요청입니다. 답변이 상대에게 보이려면 안전 검사를 \
		통과해야 하는데, 그 검사는 나중에 따로 진행됩니다. 그래서 이 응답만으로는 \
		공개 여부를 알 수 없습니다.

		같은 요청을 다시 보낼 때를 위해 Idempotency-Key 헤더가 필요합니다. 같은 키로 \
		같은 내용을 다시 보내면 답변이 두 개 만들어지지 않습니다.""")
@ApiResponses({
	@ApiResponse(responseCode = "400", description = "본문, 미디어 또는 Idempotency-Key가 정책에 맞지 않습니다. (ANS-VAL-002, ANS-VAL-003, ANS-VAL-007)"),
	@ApiResponse(responseCode = "403", description = "본인 소유가 아닌 미디어를 첨부했거나 답변할 수 없는 계정입니다. (ANS-DOM-008, ANS-APP-003)"),
	@ApiResponse(responseCode = "404", description = "답변할 수 있는 수신 항목이 없거나 인증 사용자 계정을 찾을 수 없습니다. (ANS-DOM-011, ANS-APP-002)"),
	@ApiResponse(responseCode = "409", description = "같은 Idempotency-Key를 다른 요청에 썼거나 이미 이 질문에 답변했습니다. (ANS-APP-001, ANS-INFRA-002)"),
	@ApiResponse(responseCode = "503", description = "안전 검사 접수 정책이 아직 준비되지 않았습니다. (ANS-APP-004)")
})
```

**변경 근거**

- **404 보강이 핵심이다.** `AnswerSubmissionApplicationService.ensureActiveUser:53-55`가
  `ACCOUNT_NOT_FOUND`(ANS-APP-002, 404)를 던지는데 기존 설명은 수신 항목만 말했다.
  Feed 도메인에서 6건 고쳤던 것과 같은 유형의 누락이다.
- `멱등`·`moderation`·`endpoint`는 §4 위반. `ALLOW`는 응답에 없는 내부 검사 결과
  값이라 §8 위반이다. 소비자에게 의미 있는 사실("이 응답으로는 공개 여부를 알 수
  없다")만 문장으로 남겼다.
- 오류 코드를 §7의 세 번째 경우로 붙였다. 근거는 각각
  `AnswerSubmissionService:154-176`(400), `MediaAttachmentService`의 소유권 검사와
  `ensureActiveUser:56-58`(403), `:102`와 `:53`(404),
  `IDEMPOTENCY_KEY_REUSED`·`DUPLICATE_ACTIVE_ANSWER`(409),
  `MODERATION_INTAKE_NOT_CONFIGURED`(503).

---

### `PUT /api/v1/direction/answers/{answerId}/reaction` — `react`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음 (문서가 코드와 일치. 단 계약 자체가 이상 — §4.1) |
| 누락된 `@Schema(description)` 필드 | `ReactionResponse` 2개 |
| 문장 기준 위반 | 400 설명이 실제 원인을 다 담지 못함 |

**Before**

```java
@ApiResponse(responseCode = "400", description = "답변 식별자가 올바르지 않습니다. (ANS-VAL-001)")
```

**After**

```java
@ApiResponse(responseCode = "400", description = "답변 식별자가 올바르지 않거나 그런 답변이 없습니다. (ANS-VAL-001)")
```

**변경 근거**

- `AnswerReactionService.requireEligibleReactor:153-162`는 답변을 못 찾을 때도,
  수신 항목을 못 찾을 때도 `INVALID_ID`(ANS-VAL-001, **400**)를 던진다. 기존 설명은
  "식별자 형식이 틀렸다"로만 읽혀서, 없는 답변에 공감했을 때 404가 아니라 400이
  오는 이유를 알 수 없었다.
- 이 문장은 **코드에 맞춘 것**이지 계약을 옳다고 본 것이 아니다. 계약 문제는
  §4.1에 별도로 남겼다.
- `summary`·`description`은 이미 §1~§8을 만족해 그대로 두었다.

---

### `DELETE /api/v1/direction/answers/{answerId}/reaction` — `cancel`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음 |
| 누락된 `@Schema(description)` 필드 | `ReactionResponse` 2개(위와 같은 스키마) |
| 문장 기준 위반 | 없음 |

**변경 없음.** `AnswerReactionService.cancel:107-111`은 자격을 검사하지 않고
`(answerId, reactorId)` 조건으로만 삭제하므로 400·403·404를 낼 수 없다. 401만
선언한 현재 문서가 정확하고, description("공감 자격을 다시 검사하지 않습니다.
공감이 없는 상태에서 호출해도 실패하지 않습니다")도 Javadoc `:100-103`과 일치한다.

---

### `POST /api/v1/media-assets/upload-requests` — `issueUploadUrl`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음 (403은 HTTP 경로에서 발생 불가 — §2 대조 3) |
| 누락된 `@Schema(description)` 필드 | `MediaUploadResponse` 4개 |
| 문장 기준 위반 | §4 낯선 단어(`presigned PUT URL`, `storage key`, `MIME type`), §7 오류 코드 미표기 |

**Before**

```java
@Operation(
	summary = "이미지 업로드 예약",
	description = "인증 사용자 소유의 JPEG/JPG 또는 PNG 한 건에 대한 presigned PUT URL을 발급합니다. storage key는 반환하지 않습니다.")
@ApiResponse(responseCode = "400", description = "MIME type 또는 파일 크기가 정책에 맞지 않습니다.")
```

**After**

```java
@Operation(
	summary = "이미지 올릴 자리 받기",
	description = """
		이미지를 올릴 임시 주소를 발급합니다.

		앱 로그인이 필요합니다. JPEG 또는 PNG 한 장만 올릴 수 있습니다.

		응답으로 받은 uploadUrl에 이미지 파일을 직접 PUT으로 올리면 됩니다. 이 주소는 \
		expiresAt이 지나면 쓸 수 없습니다.

		올리기만 해서는 끝나지 않습니다. 업로드 확인 API를 불러야 그 이미지를 답변이나 \
		질문글에 첨부할 수 있습니다.""")
@ApiResponse(responseCode = "400", description = "이미지 형식이나 파일 크기가 정책에 맞지 않습니다. (ANS-VAL-006)")
```

**변경 근거**

- `presigned PUT URL`·`storage key`는 §4가 금지하는 낯선 단어다. 소비자가 실제로
  해야 할 일("uploadUrl에 PUT으로 올린다")로 풀었다. 저장소 경로를 노출하지 않는
  것은 구현 사실이지 소비자가 알아야 할 정보가 아니라 문장에서 뺐다.
- 마지막 문단은 §3-5(주의점)에 해당한다. `MediaUploadService.confirm`을 부르지
  않으면 자산이 `UPLOADING`에 머물러 첨부할 수 없다는 사실이 근거다.
- 400 근거: `MediaUploadService.issueUploadUrl:40-49`의
  `INVALID_MEDIA_METADATA`(ANS-VAL-006) 두 갈래(허용 mime type, 최대 크기).
- `summary`는 §2의 예외 조항을 썼다. `업로드 예약`은 무엇이 예약되는지 드러나지
  않아 동사구로 풀었다.

---

### `POST /api/v1/media-assets/{mediaId}/confirm` — `confirm`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음 |
| 누락된 `@Schema(description)` 필드 | `MediaConfirmResponse` 2개 |
| 문장 기준 위반 | §4 낯선 단어(`멱등하게`, `MIME type`), §7 오류 코드 미표기 |

**Before**

```java
@Operation(
	summary = "이미지 업로드 확인",
	description = "본인 소유 업로드 객체의 실제 MIME type과 크기를 확인합니다. 이미 확정된 상태는 멱등하게 반환합니다.")
@ApiResponse(responseCode = "404", description = "미디어를 찾을 수 없습니다.")
@ApiResponse(responseCode = "503", description = "외부 저장소를 일시적으로 사용할 수 없습니다.")
```

**After**

```java
@Operation(
	summary = "이미지 업로드 확인",
	description = """
		올린 이미지가 실제로 저장됐는지 확인하고 첨부할 수 있는 상태로 바꿉니다.

		앱 로그인이 필요하고 본인이 올린 이미지만 확인할 수 있습니다.

		서버가 저장소에 있는 실제 파일의 형식과 크기를 확인합니다.

		같은 이미지를 여러 번 불러도 안전합니다. 이미 확인이 끝난 이미지면 다시 \
		확인하지 않고 지금 상태를 그대로 돌려줍니다.""")
@ApiResponse(responseCode = "404", description = "그런 이미지가 없거나 본인이 올린 이미지가 아닙니다. (ANS-DOM-007)")
@ApiResponse(responseCode = "503", description = "이미지 저장소에 연결할 수 없습니다. (ANS-EXT-001)")
```

**변경 근거**

- `멱등하게`를 풀었다(§4). 근거는 `MediaUploadService.confirm:72-75` Javadoc과
  `asset.getStatus() != UPLOADING`이면 그대로 반환하는 분기다.
- **404 설명 보강**: `findByIdAndOwnerId(mediaId, requesterId)`로 조회하므로 남의
  미디어도 404다. 기존 "미디어를 찾을 수 없습니다"는 그 사실을 감췄다. 403이
  아니라 404라는 점이 소비자에게 중요하다.
- 503 근거: `S3ObjectStoragePort`의 `STORAGE_UNAVAILABLE`(ANS-EXT-001).

---

### 응답 DTO `@Schema(description)` 11건

| 스키마 | 필드 | 제안 description | 근거 |
| --- | --- | --- | --- |
| `AnswerSubmissionResponse` | `answerId` | 접수된 답변의 식별자 | `from(Answer)` |
| | `submissionStatus` | 접수 직후의 답변 상태. 공개 여부가 아니라 접수 결과입니다 | `answer.getStatus().name()` |
| | `submittedAt` | 답변을 접수한 시각 | 같음 |
| `MediaUploadResponse` | `mediaId` | 발급된 이미지 식별자. 업로드 확인과 첨부에 씁니다 | `from(UploadUrl)` |
| | `uploadUrl` | 이미지 파일을 PUT으로 올릴 임시 주소 | `presignedUpload().url()` |
| | `contentType` | 이 주소로 올릴 수 있는 이미지 형식 | `asset.getMimeType()` |
| | `expiresAt` | 이 주소를 쓸 수 있는 마지막 시각 | `presignedUpload().expiresAt()` |
| `MediaConfirmResponse` | `mediaId` | 확인한 이미지 식별자 | `from(MediaAsset)` |
| | `status` | 확인 뒤의 이미지 상태 | `asset.getStatus().name()` |
| `ReactionResponse` | `reacted` | 이 호출 뒤 내가 공감을 남긴 상태인지 여부 | record Javadoc |
| | `reactionCount` | 반영 직후 서버가 다시 센 공감 수 | 같은 Javadoc |

`ReactionResponse`는 `feed.web.response` 패키지에 있지만 Feed의 9개 엔드포인트
응답이 아니라 공감 4개 경로(답변 2 + 질문글 2)가 공유하는 모델이다. Feed 브랜치는
이 파일을 건드리지 않았으므로 충돌하지 않는다.

## 4. 반영하지 않은 제안

### 4.1 `AnswerReactionService`가 "없는 답변"에 404 대신 400을 낸다 — 별도 이슈 필요

`AnswerErrorCode`에는 이미 적절한 404 코드가 **둘 다 정의돼 있다.**

```text
ANS-DOM-011  RECIPIENT_NOT_FOUND  404  답변할 수 있는 수신 항목을 찾을 수 없습니다.
ANS-DOM-012  ANSWER_NOT_FOUND     404  답변을 찾을 수 없습니다.
```

그런데 `AnswerReactionService.requireEligibleReactor`는 둘 다 쓰지 않는다.

```java
// :153-155  답변이 없을 때
.orElseThrow(() -> new AnswerException(AnswerErrorCode.INVALID_ID, "answerId", "답변을 찾을 수 없습니다"));
// :160-162  수신 항목이 없을 때
.orElseThrow(() -> new AnswerException(AnswerErrorCode.INVALID_ID, "postRecipientId", "수신 항목을 찾을 수 없습니다"));
```

`INVALID_ID`는 `ANS-VAL-001`이고 HTTP 400이다. 예외 메시지는 "찾을 수 없습니다"인데
상태 코드는 400이라 메시지와 상태가 어긋난다. 같은 저장소의 다른 경로
(`MediaUploadService.confirm`은 `MEDIA_NOT_FOUND` 404, `AnswerSubmissionService`는
`RECIPIENT_NOT_FOUND` 404)와도 일관되지 않는다.

**이 브랜치에서는 고치지 않는다.** 상태 코드 변경은 API 계약 변경이고 `TASK.md`가
명시적으로 제외한 범위다. 클라이언트가 400을 이미 다루고 있을 수 있어 영향 평가도
필요하다. 문서는 코드에 맞춰 "그런 답변이 없어도 400"이라고 적었다.

후속 이슈에서 다룰 것:

- `ANSWER_NOT_FOUND`·`RECIPIENT_NOT_FOUND`로 바꿀지, 아니면 존재 여부를 감추려는
  의도였다면 `INELIGIBLE_REACTOR`(403)로 합칠지 결정
- 결정 후 `AnswerReactionApiSpec`의 400·403·404 문장을 다시 맞추기

### 4.2 `issueUploadUrl`에 403을 추가하지 않았다

`MediaUploadService.issueUploadUrl:36-39`가 `MEDIA_OWNER_MISMATCH`(403)를 던지지만,
컨트롤러가 요청자와 소유자를 모두 인증 사용자로 채우므로
(`MediaAssetController:41`) HTTP 경로에서는 이 분기에 닿을 수 없다. 서비스가 다른
호출자를 위해 남긴 방어 코드다. 실제로 낼 수 없는 응답을 문서에 적으면 소비자가
대비할 필요 없는 분기를 처리하게 되므로 추가하지 않았다.

### 4.3 `submit`의 202를 200으로 바꾸자는 제안은 하지 않았다

202는 "접수만 했다"는 뜻으로 실제 동작과 맞다. 상태 코드는 계약이므로 검토 범위 밖이다.

## 5. 실행하지 못한 검증

없다. 6점 대조 6개 항목을 모두 실행했다.

## 6. Reviewer checklist

- [x] 모든 제안 문장이 실제 서비스/DTO/컨트롤러 코드로 근거를 확인했다 (추측 없음)
- [x] `review` 모드 산출 시점에는 `*ApiSpec` 원본을 수정하지 않았다
- [x] `@Schema(example)`에 비밀값·계정 식별자를 쓰지 않았다
- [x] 내부 불변식 ID가 제안 문장에 남아 있지 않다
- [x] 6점 대조를 모두 실행했다
- [x] 계약이 이상한 항목은 코드를 고치지 않고 §4.1에 근거와 함께 남겼다
