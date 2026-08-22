# API Docs Review: Direction/Post Reaction

> Created at: `2026-08-23T03:00:00+09:00`
> GitHub Issue: `#189`
> Target: `src/main/java/com/dnd/qello/direction/web/{DirectionPostApiSpec,PostReactionApiSpec,ActiveUserPresenceApiSpec}.java`
> 문장 기준: `docs/api/OPENAPI_WRITING_GUIDE.md`

이 문서는 Direction/Post Reaction 3개 `*ApiSpec`의 문장 검토 결과다.
`harness-api-docs` 스킬의 `review` 모드 산출물이다.

> **반영 완료 (2026-08-23).** 아래 제안은 검토를 거쳐 코드에 반영했다.
> §3의 "After"가 현재 코드 상태다.

## 1. Executive summary

- 대상 엔드포인트 수: **5** (`preview`, `submit`, `react`, `cancel`, `update`)
- 발견된 문제 수: **26**
  - **누락된 오류 응답: 2건** — `preview`의 403·404. 이 도메인에서 가장 중요한 발견
  - 응답·요청 DTO 필드 `@Schema(description)` 누락: **15건**
  - 오류 응답에 오류 코드 표기 누락(§7): **4건**
  - 문장 종결 위반(§1 한다체): **1건** (`ActiveUserPresenceApiSpec.update`, 3문장)
  - 낯선 단어를 쌓은 문장(§4): **4건**
- 6점 대조 중 실행하지 못한 항목: **없음**

## 2. 6점 대조 결과

| # | 대조 | 결과 | 근거 |
| --- | --- | --- | --- |
| 1 | Controller ↔ ApiSpec | **이상 없음** | `DirectionPostController`·`PostReactionController`·`ActiveUserPresenceController`가 각 ApiSpec을 `implements`하고 `@Override` 5개 ↔ 매핑 5개가 1:1. 세 컨트롤러 모두 문서 애노테이션 없음 |
| 2 | ApiSpec ↔ DTO | **문제 15건** | 응답 `DirectionPreviewResponse` 4 + 중첩 `SegmentCount` 4, `DirectionPostSubmissionResponse` 4, `UpdateActiveUserPresenceResponse` 1 전 필드 무설명. 요청 `UpdateActiveUserPresenceRequest`는 5개 중 `receiveAllowed`·`observedAt` 2개 무설명. `SubmitDirectionPostRequest`(5)는 이상 없음 |
| 3 | ApiSpec ↔ Service | **문제 2건** | `throw` 전수 확인. **`preview`가 403·404를 낼 수 있는데 선언이 없다** — `DirectionPostApplicationService.preview:60-64`가 `ensureActiveUser`와 `activeConfiguredScheme`을 부르고, `ensureActiveUser:98-105`는 `PRESENCE_ACCOUNT_NOT_FOUND`(DIR-APP-006, **404**)·`PRESENCE_ACCOUNT_NOT_ELIGIBLE`(DIR-APP-007, **403**)를, `activeConfiguredScheme:121-128`은 `SCHEME_NOT_FOUND`(DIR-DOM-006, **404**)를 던진다. `submit`은 같은 경로를 쓰면서 403·404를 모두 선언해 대비된다. `update`: `DirectionPresenceService`가 400 3종(`REQUIRED_VALUE_MISSING`·`INVALID_VALUE_RANGE`·`INVALID_TIME_ORDER`)·403·404 → 400 설명이 기본값이라 도메인 원인을 알 수 없다. `react`: `PostReactionService:107-111`의 `INELIGIBLE_REACTOR`(403)만 → 선언과 일치. `cancel`: 자격 검사 없음 → 401만 선언한 것이 정확 |
| 4 | ApiSpec ↔ `docs/error-codes.md` | **이상 없음** | 인용 대상 `DIR-*` 10개가 모두 등재돼 있고 HTTP 상태가 `DirectionErrorCode`와 일치 |
| 5 | ApiSpec ↔ SecurityConfiguration | **이상 없음** | 세 경로 모두 인증 필요이고 인터페이스 수준 `@SecurityRequirement`가 정확 |
| 6 | ApiSpec ↔ `docs/api/openapi.json` | **실행함** | 반영 전 기준선 diff 없음. 반영 후 결과는 §2.1 |

### 2.1 반영 후 재대조

- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`: 통과.
- 재파싱 결과 이 도메인 필드 **15개 중 무설명 0개**(응답 13 + 요청 2).
- `GET /api/v1/direction/preview`의 응답 코드가
  `200,400,401,409,500` → `200,400,401,403,404,409,500`으로 바뀐 것을 산출물에서
  직접 확인했다.
- `PUT /api/v1/direction/presence`의 400 설명이 커스터마이저 기본 문구에서
  도메인 문구로 바뀐 것도 확인했다.
- 상세 수치는 `TASK.md`의 Validation evidence에 기록한다.

## 3. 엔드포인트별 제안

### `GET /api/v1/direction/preview` — `preview`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | **403 (DIR-APP-007), 404 (DIR-APP-006, DIR-DOM-006)** |
| 누락된 `@Schema(description)` 필드 | `DirectionPreviewResponse` 4개, `SegmentCount` 4개 |
| 문장 기준 위반 | §4 낯선 단어(`presence`, `방향 구간`) |

**Before**

```java
@Operation(
	summary = "방향별 후보 수 미리보기",
	description = "인증 사용자의 현재 presence와 서버 정책으로 모든 활성 방향 구간의 참고 후보 수를 반환합니다. 사용자 ID와 정확 위치는 반환하지 않습니다.")
@ApiResponses({
	@ApiResponse(responseCode = "200", description = "미리보기를 반환합니다."),
	@ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다."),
	@ApiResponse(responseCode = "409", description = "현재 위치 또는 방향 정책을 사용할 수 없습니다.")
})
```

**After**

```java
@Operation(
	summary = "방향별로 받을 사람 수 미리보기",
	description = """
		지금 내 위치를 기준으로 방향마다 질문을 받을 수 있는 사람이 몇 명인지 보여줍니다.

		앱 로그인이 필요하고, 위치를 먼저 갱신해 둬야 합니다.

		질문을 보내기 전에 어느 방향으로 보낼지 고르는 데 씁니다. 여기서 세어 준 수는 \
		참고값이며 실제로 받는 사람 수와 다를 수 있습니다.

		상대의 계정 식별자나 정확한 위치는 돌려주지 않습니다.""")
@ApiResponses({
	@ApiResponse(responseCode = "200", description = "방향별 후보 수를 반환합니다."),
	@ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다."),
	@ApiResponse(responseCode = "403", description = "현재 계정은 방향 기능을 사용할 수 없습니다. (DIR-APP-007)"),
	@ApiResponse(responseCode = "404", description = "인증 사용자 계정 또는 현재 활성 방향 구획 체계를 찾을 수 없습니다. (DIR-APP-006, DIR-DOM-006)"),
	@ApiResponse(responseCode = "409", description = "저장된 위치가 없거나 너무 오래됐습니다. (DIR-APP-003, DIR-APP-004)")
})
```

**변경 근거**

- **403·404 추가가 핵심이다.** `preview`는 `submit`과 똑같이
  `ensureActiveUser` → `currentPresence` → `activeConfiguredScheme` 순서로 호출하는데
  (`DirectionPostApplicationService:60-64`), `submit`만 403·404를 선언하고 `preview`는
  빠뜨렸다. 실제로 탈퇴·정지 계정은 403을, 계정이나 활성 구획 체계를 못 찾으면
  404를 받는다.
- 409 설명을 구체화했다. 근거는 `currentPresence:109-118`의
  `PRESENCE_NOT_FOUND`(DIR-APP-004, "발신자의 위치 정보가 없습니다")와
  `PRESENCE_NOT_CURRENT`(DIR-APP-003, "현재 위치 정보가 유효하지 않습니다") 두 갈래다.
  기존 "현재 위치 또는 방향 정책을 사용할 수 없습니다"는 원인이 모호했다.
- `presence`는 §4 위반이라 "위치"로 풀었다. "위치를 먼저 갱신해 둬야 합니다"는
  409의 원인을 §3-2(선행 조건)에 미리 알려 주는 문장이다.
- "참고값" 표현의 근거는 원문의 "참고 후보 수"이며, 매칭 시점에 다시 계산한다는
  사실은 `submit`이 별도로 수신자를 확정하는 구조에서 나온다.

---

### `POST /api/v1/direction/posts` — `submit`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음 (403·404·409 모두 선언됨) |
| 누락된 `@Schema(description)` 필드 | `DirectionPostSubmissionResponse` 4개 |
| 문장 기준 위반 | §4 낯선 단어(`비동기 worker`, `멱등키`), §7 오류 코드 미표기 |

**Before**

```java
@Operation(
	summary = "방향 질문글 비동기 제출",
	description = "텍스트만, JPEG/PNG 이미지 한 장만 또는 둘을 조합해 제출합니다. 수신자 확정은 비동기 worker가 수행합니다.")
@ApiResponse(responseCode = "400", description = "본문, 미디어 또는 멱등키가 정책에 맞지 않습니다.")
@ApiResponse(responseCode = "403", description = "미디어 소유권 또는 계정 자격이 없습니다.")
@ApiResponse(responseCode = "404", description = "질문 또는 방향 구획을 찾을 수 없습니다.")
@ApiResponse(responseCode = "409", description = "멱등키가 다른 요청에 재사용됐거나 현재 정책을 사용할 수 없습니다.")
```

**After**

```java
@Operation(
	summary = "방향 질문글 보내기",
	description = """
		고른 방향에 있는 사람들에게 질문글을 보냅니다.

		앱 로그인이 필요하고, 위치를 먼저 갱신해 둬야 합니다. 글만, 이미지 한 장만, \
		또는 둘 다 보낼 수 있습니다.

		접수만 하고 바로 끝나는 요청입니다. 누가 받을지는 서버가 나중에 정하므로 \
		이 응답에는 수신자가 들어 있지 않습니다.

		같은 요청을 다시 보낼 때를 위해 Idempotency-Key 헤더가 필요합니다. 같은 키로 \
		같은 내용을 다시 보내면 질문글이 두 개 만들어지지 않고 처음 결과를 그대로 돌려줍니다.""")
@ApiResponse(responseCode = "400", description = "본문, 미디어 또는 Idempotency-Key가 정책에 맞지 않습니다. (DIR-VAL-002, DIR-VAL-003, DIR-VAL-008)")
@ApiResponse(responseCode = "403", description = "본인 소유가 아닌 미디어를 첨부했거나 방향 기능을 쓸 수 없는 계정입니다. (DIR-APP-007)")
@ApiResponse(responseCode = "404", description = "질문, 방향 구획 체계 또는 인증 사용자 계정을 찾을 수 없습니다. (DIR-DOM-006, DIR-APP-006)")
@ApiResponse(responseCode = "409", description = "같은 Idempotency-Key를 다른 요청에 썼거나 저장된 위치가 없거나 너무 오래됐습니다. (DIR-APP-005, DIR-APP-003, DIR-APP-004)")
```

**변경 근거**

- `비동기 worker`·`멱등키`는 §4 위반. 소비자에게 의미 있는 사실("이 응답에는
  수신자가 없다", "같은 키로 다시 보내도 두 개가 안 생긴다")로 풀었다.
- "처음 결과를 그대로 돌려줍니다"의 근거는
  `DirectionPostApplicationService.submit:73-76`의 `replayIfExists` 분기다.
- 404 설명에 계정 없음을 더했다. `ensureActiveUser:101-103`이
  `PRESENCE_ACCOUNT_NOT_FOUND`(DIR-APP-006, 404)를 던진다.
- 409에 위치 관련 두 코드를 더했다. `submit`도 `currentPresence`를 부르므로
  `PRESENCE_NOT_FOUND`·`PRESENCE_NOT_CURRENT`가 날 수 있는데 기존 설명의
  "현재 정책을 사용할 수 없습니다"로는 알 수 없었다.

---

### `PUT /api/v1/direction/posts/{postId}/reaction` — `react`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음 |
| 누락된 `@Schema(description)` 필드 | `ReactionResponse` 2개 (Answer 보고서에서 함께 처리) |
| 문장 기준 위반 | 없음 |

**변경 없음.** `PostReactionService.requireEligibleReactor:107-111`은
`findByPostIdAndRecipientId`가 비면 `INELIGIBLE_REACTOR`(DIR-DOM-007, 403) 하나만
던진다. 없는 질문글도 403이 되는데, 이는 수신자가 아닌 사람에게 질문글 존재
여부를 알리지 않는 설계로 읽힌다. 403 설명("수신 자격이 없는 사용자는 공감할 수
없습니다")이 그 동작과 어긋나지 않아 그대로 두었다.

---

### `DELETE /api/v1/direction/posts/{postId}/reaction` — `cancel`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음 |
| 누락된 `@Schema(description)` 필드 | `ReactionResponse` 2개 |
| 문장 기준 위반 | 없음 |

**변경 없음.** description이 "만료나 넘김 확정으로 자격을 잃은 뒤에도 자기가 남긴
공감은 거둘 수 있습니다"까지 이미 정확히 적고 있고, 자격 검사를 하지 않는 서비스
구현과 일치한다.

---

### `PUT /api/v1/direction/presence` — `update`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음(상태 코드 기준). 400 설명이 기본값이라 도메인 원인을 못 알려줌 |
| 누락된 `@Schema(description)` 필드 | `UpdateActiveUserPresenceResponse` 1개, 요청 `receiveAllowed`·`observedAt` 2개 |
| 문장 기준 위반 | **§1 종결어미 위반 3문장(한다체)**, §4 낯선 단어 |

**Before**

```java
@Operation(
	summary = "현재 위치 갱신",
	description = """
		인증 사용자의 최신 위치와 질문 수신 허용 상태를 갱신한다.
		사용자와 지역은 서버에서 결정하고, 관측 시각이 같거나 더 오래된 재시도는 적용하지 않는다.
		정확 좌표는 성공 응답에 반환하지 않는다.""")
```

**After**

```java
@Operation(
	summary = "현재 위치 갱신",
	description = """
		내 최신 위치와 질문을 받을지 여부를 저장합니다.

		앱 로그인이 필요합니다. 방향 미리보기와 질문글 보내기가 이 위치를 씁니다.

		이미 저장된 것보다 오래되거나 같은 시각의 위치는 적용하지 않고 applied를 \
		false로 돌려줍니다. 이때도 요청은 성공(200)입니다.

		정확한 좌표는 응답에 돌려주지 않습니다.""")
@ApiResponse(responseCode = "400", description = "위치 값이 없거나 정확도 또는 관측 시각이 허용 범위를 벗어났습니다. (DIR-VAL-002, DIR-VAL-007, DIR-VAL-008)")
```

**변경 근거**

- **§1 위반이 명확하다.** `갱신한다`·`않는다`·`않는다` 세 문장이 모두 한다체다.
  이 저장소 24개 `*ApiSpec` 중 이 파일만 `description` 전체가 한다체다.
- 400 설명 추가: 기존에는 `OpenApiConventionCustomizer:84`의 기본 문구
  ("요청 값이 올바르지 않습니다. 오류 코드는 docs/error-codes.md를 따른다.")만
  나왔다. `DirectionPresenceService.validateCommand:53-69`가 실제로 세 갈래의
  400을 던지므로 명시했다.
- "applied를 false로 돌려줍니다. 이때도 요청은 성공(200)입니다"는 소비자가 가장
  오해하기 쉬운 지점이다(§3-5). 근거는 `update`가
  `presenceRepository.saveIfNewer(presence)`의 boolean을 그대로 반환하고
  컨트롤러가 200으로 감싸는 구조다.
- "방향 미리보기와 질문글 보내기가 이 위치를 씁니다"는 `preview`·`submit`이
  `currentPresence`를 요구한다는 사실이 근거다. 두 API의 409를 미리 이해시킨다.

---

### DTO `@Schema(description)` 15건

| 스키마 | 필드 | 제안 description | 근거 |
| --- | --- | --- | --- |
| `DirectionPreviewResponse` | `schemeId` | 이 미리보기를 계산한 방향 구획 체계의 식별자 | `from(DirectionPreviewResult)` |
| | `schemeCode` | 방향 구획 체계의 코드 | 같음 |
| | `schemeVersion` | 방향 구획 체계의 판 번호 | 같음 |
| | `segments` | 방향별 후보 수 목록 | 같음 |
| `SegmentCount` | `segmentKey` | 질문글을 보낼 때 지정할 방향 키 | `SubmitDirectionPostRequest.segmentKey`와 같은 값 |
| | `displayName` | 화면에 표시할 방향 이름 | `segment.displayName()` |
| | `sortOrder` | 화면에 늘어놓을 순서 | `segment.sortOrder()` |
| | `count` | 이 방향에서 질문을 받을 수 있는 사람 수. 참고값입니다 | `segment.count()` |
| `DirectionPostSubmissionResponse` | `postId` | 접수된 질문글의 식별자 | `from(SendResult)` |
| | `submissionStatus` | 접수 결과. 항상 SUBMITTED입니다 | `"SUBMITTED"` 상수 |
| | `submittedAt` | 질문글을 접수한 시각 | `post.getSubmittedAt()` |
| | `expiresAt` | 이 질문글이 만료되는 시각 | `post.getExpiresAt()` |
| `UpdateActiveUserPresenceResponse` | `applied` | 보낸 위치가 실제로 저장됐는지 여부. 더 오래된 위치라 반영하지 않았으면 false입니다 | `presenceRepository.saveIfNewer` 반환값 |
| `UpdateActiveUserPresenceRequest` | `receiveAllowed` | 이 위치에서 질문을 받을지 여부 | `toCommand()` |
| | `observedAt` | 기기가 이 위치를 관측한 시각. 서버 시각 기준 허용 범위를 벗어나면 저장하지 않습니다 | `validateCommand:64-69` |

## 4. 반영하지 않은 제안

### 4.1 `SegmentCount`의 스키마 이름을 바꾸지 않았다

`SegmentCount`는 `DirectionPreviewResponse`의 중첩 record라 springdoc이 단순
이름으로 등록한다. Feed 도메인에서 `Card`·`Cursor`·`Chip`·`Answer`를
`@Schema(name = ...)`로 명시한 것과 같은 위험 구조다.

다만 산출물 전체를 파싱해 확인한 결과 현재 저장소에 `SegmentCount`는 **하나뿐이라
실제 충돌이 없다.** Feed의 네 건은 서로 다른 도메인이 같은 이름을 쓰고 있어 실제로
덮어쓸 위험이 있었지만, 여기서 이름을 바꾸면 이득 없이 클라이언트가 참조하는 스키마
이름만 달라진다. 그대로 두고, 다른 도메인이 같은 이름을 새로 만들 때 함께 정리하는
편이 낫다.

### 4.2 `react`의 "없는 질문글에 403" 동작은 문제로 보지 않았다

`PostReactionService`는 질문글이 없어도 수신 자격이 없어도 똑같이 403을 낸다.
Answer 도메인의 같은 자리(`AnswerReactionService`가 400을 내는 것, Answer 보고서
§4.1)와 달리, 여기서는 상태 코드와 메시지가 어긋나지 않고 존재 여부를 감추는
설계로 일관되게 읽힌다. 별도 이슈로 올리지 않았다.

### 4.3 `preview`의 400을 명시하지 않았다

`ensureActiveUser:96-97`이 `INVALID_ID`(400)를 던지지만 `senderId <= 0` 조건이라
인증을 통과한 요청에서는 닿을 수 없다. 커스터마이저의 기본 400 문구를 그대로 둔다.

## 5. 실행하지 못한 검증

없다. 6점 대조 6개 항목을 모두 실행했다.

## 6. Reviewer checklist

- [x] 모든 제안 문장이 실제 서비스/DTO/컨트롤러 코드로 근거를 확인했다 (추측 없음)
- [x] `review` 모드 산출 시점에는 `*ApiSpec` 원본을 수정하지 않았다
- [x] `@Schema(example)`에 비밀값·계정 식별자·좌표를 쓰지 않았다
- [x] 내부 불변식 ID가 제안 문장에 남아 있지 않다
- [x] 6점 대조를 모두 실행했다
- [x] 누락된 오류 응답은 서비스의 `throw`를 근거로만 추가했다
