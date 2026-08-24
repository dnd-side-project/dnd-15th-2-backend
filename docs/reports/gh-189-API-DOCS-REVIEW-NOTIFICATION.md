# API Docs Review: Notification

> Created at: `2026-08-23T02:40:00+09:00`
> GitHub Issue: `#189`
> Target: `src/main/java/com/dnd/qello/notification/web/NotificationApiSpec.java`
> 문장 기준: `docs/api/OPENAPI_WRITING_GUIDE.md`

이 문서는 `NotificationApiSpec`의 문장 검토 결과다. `harness-api-docs` 스킬의
`review` 모드 산출물이며 `*ApiSpec` 원본은 이 모드에서 수정하지 않는다. 여기 담긴 제안을 도메인 담당자가 검토·승인한 뒤 직접 코드에 반영한다.

> **반영 완료 (2026-08-23).** 아래 제안은 검토를 거쳐 이미 코드에 반영했다.
> 반영 대상은 `NotificationApiSpec` 1개와 응답 DTO 9개 파일이다.
>
> - `@Tag` 설명 교체 1건 (이슈 번호 `(#176)` 제거)
> - `@Operation` `summary`·`description` 7건 전면 재작성
> - `@ApiResponse` 문장 개선 4건 (404 계정 경로 보강 2건, 409 괄호 정리 1건,
>   200 문구 정리 1건)
> - query parameter `@Parameter(description)` 3건 추가
> - 응답 필드 `@Schema(description)` 31건 추가, enum 문자열 필드
>   `allowableValues` 4건 추가
> - **근거 없는 문장 1건 삭제** (`REVOKED·DISMISSED 줄은 제외됩니다` — 근거는 §4.1)
>
> §3의 "After" 블록이 현재 코드 상태다. 재생성한 `docs/api/openapi.json`으로
> 반영 여부를 다시 확인했다 (§2.1).

## 1. Executive summary

- 대상 엔드포인트 수: **7** (`NotificationApiSpec` 1개 파일)
- 발견된 문제 수: **48**
    - 응답 DTO 필드 `@Schema(description)` 누락: **31건** (응답 스키마 10개, 전 필드)
    - query parameter `@Parameter(description)` 누락: **3건** (`list`)
    - 내부 상태값만으로 설명한 문장 (가이드 §8 명시 위반): **4건**
    - 낯선 단어를 쌓은 `summary`·`description`(§2·§4·§5): **6건**
    - `@Tag`·`@ApiResponse`의 괄호 규칙 위반 (§7): **3건**
    - 오류 코드를 `description`에서 반복 (§3-4): **1건**
- 6점 대조 중 실행하지 못한 항목: **없음**

**Feed 도메인과 다른 점 두 가지.**

1. **오류 응답은 이미 정확하다.** 7개 엔드포인트 전부 서비스가 실제로 던지는 예외와 일치했고, Feed에서 6건 누락됐던 계정 게이트 404 (`NOT-APP-001`)도 이미 모두 선언돼 있다.
   `docs/error-codes.md`의 `NOT-*` 15개도 누락 없이 등재돼 있다. 이 도메인에서 고칠 것은 **문장**이지 계약이 아니다.
2. **스키마 이름 충돌 위험이 없다.** 중첩 record는
   `NotificationListingResponse.Cursor` 하나뿐이고 이미
   `@Schema(name = "NotificationCursor")`가 지정돼 있다.

**가장 중요한 발견**은 §4에 있다. `list()`의 `REVOKED·DISMISSED 줄은
제외됩니다`는 가이드 §8이 나쁜 예로 **직접 인용한 바로 그 문장**인데, 동시에 현재 코드로는 두 상태가 **만들어지지 않는다**. 가이드가 제시한 좋은 예 ("신고로 내려간 알림은…")로 고치면 근거 없는
사실을 지어내게 된다. 이 항목은 문장을 바꾸는 대신 **삭제**를 제안하며, 그 판단 근거를 §4에 남긴다.

## 2. 6점 대조 결과

`docs/api/OPENAPI_WRITING_GUIDE.md` §9 기준.

| # | 대조                              | 결과                         | 근거                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|---|-----------------------------------|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Controller ↔ ApiSpec              | **이상 없음**                | `NotificationController`가 `implements NotificationApiSpec`이고 `@Override` 7개 ↔ ApiSpec의 매핑 7개(`@GetMapping` 4, `@PutMapping` 3)가 1:1. 컨트롤러에는 문서 애노테이션이 하나도 없고 `@RestController`·`@RequestMapping("/api/v1")`만 있어 `docs/api-response.md` §5를 지킨다                                                                                                                                                                                                                                               |
| 2 | ApiSpec ↔ DTO                     | **문제 31건**                | 재생성한 `docs/api/openapi.json`의 응답 스키마 10개 필드 31개가 전부 `description` 없음. 요청 DTO 3개(`UpdateNotificationPreferencesRequest`, `NotificationTypePreferenceRequest`, `QuietHoursRequest`)는 이미 전 필드에 `description`·`requiredMode`가 있어 이상 없음                                                                                                                                                                                                                                                          |
| 3 | ApiSpec ↔ Service                 | **이상 없음**                | `throw new NotificationException` 전수 확인. `NotificationInboxService`: `INVALID_LIMIT`(:109), `INVALID_CURSOR`(:116), `ACCOUNT_NOT_FOUND`/`ACCOUNT_NOT_ELIGIBLE`(:95-96), `NOTIFICATION_NOT_FOUND`(:89,:103). `Notification.markRead`(:37): `INVALID_NOTIFICATION_STATUS`. `NotificationPreferenceService`는 게이트 2종 + `JdbcNotificationPreferenceRepository.lockUser`(:52) `ACCOUNT_NOT_FOUND`. 요청 DTO 3개와 `NotificationQuietHours`는 전부 `INVALID_PREFERENCE`. **선언된 응답과 완전히 일치하며 누락도 과잉도 없다** |
| 4 | ApiSpec ↔ `docs/error-codes.md`   | **이상 없음**                | `docs/error-codes.md:237-251`에 `NOT-*` 15개 전부 등재. ApiSpec이 인용한 6개(`NOT-VAL-006`·`NOT-VAL-007`·`NOT-VAL-008`·`NOT-DOM-003`·`NOT-DOM-004`·`NOT-APP-001`·`NOT-APP-002`)의 HTTP 상태가 `NotificationErrorCode`와 문서 표 양쪽에서 일치                                                                                                                                                                                                                                                                                   |
| 5 | ApiSpec ↔ SecurityConfiguration   | **이상 없음**                | `SecurityConfiguration:131-132`의 `permitAll`은 `/api/v1/auth/devices`·`/api/v1/auth/token`뿐이고 `anyRequest().authenticated()`. `/api/v1/notifications/**`는 전부 인증 필요이므로 인터페이스 수준 `@SecurityRequirement(APP_ACCESS_TOKEN_SCHEME)` 1개가 정확하다                                                                                                                                                                                                                                                              |
| 6 | ApiSpec ↔ `docs/api/openapi.json` | **실행함. 기준선 diff 없음** | `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"` 통과, `git diff --stat -- docs/api/openapi.json` 비어 있음 → 현재 산출물이 코드와 이미 동기 상태. 위 2번의 31건은 이 재생성 산출물을 직접 파싱해 센 값이다                                                                                                                                                                                                                                                                                           |

### 2.1 반영 후 재대조 (2026-08-23)

제안을 반영한 뒤 6번을 다시 실행했다.

- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`: 통과.
- `git diff --stat -- docs/api/openapi.json`: **122줄 변경 (95 추가/27 삭제).**
- 재파싱 결과 응답 필드 **31개 중 description 없는 필드 0개**,
  `list`의 query parameter 3개 전부 description 채워짐.

## 3. 엔드포인트별 제안

### 인터페이스 수준 — `@Tag`

| 항목                               | 내용                                                          |
|------------------------------------|---------------------------------------------------------------|
| 누락된 오류 응답                   | 없음                                                          |
| 누락된 `@Schema(description)` 필드 | 해당 없음                                                     |
| 문장 기준 위반                     | §7 괄호 규칙(허용된 3경우가 아닌 `(#176)`), §4 낯선 명사 쌓기 |

**Before**

```java
@Tag(name = "알림함", description = "알림함 목록·미읽음 신호 조회, 열람 기준선 전진, 줄 단위 읽음, 진입 판정 (#176)")
```

**After**

```java
@Tag(name = "알림함", description = "받은 알림을 확인하고, 알림 점을 끄고, 알림에서 원래 글로 넘어갈 수 있는지 확인합니다. 알림을 어떻게 받을지 설정하는 것도 여기서 합니다.")
```

**변경 근거**

- `(#176)`은 GitHub Issue 번호다. §7이 허용하는 괄호는 결과 상태값 `(→ STATUS)`, 팀 용어 `(원어)`, 오류 코드 `(코드)` 셋뿐이고 이 중 어디에도 해당하지 않는다. §8의
  "미번역 내부 ID를 노출하지 않는다"와 같은 취지로 제거한다.
- "열람 기준선 전진", "줄 단위 읽음", "진입 판정"은 전부 내부 구현 용어다. §5 ("분류 이름 대신 하는 일로 부른다")에 따라 각 엔드포인트가 실제로 하는 일로 바꿨다: `markSeen`=알림 점
  끄기, `markRead`=읽음 처리, `target`=넘어갈 수 있는지 확인, `preferences`/`replacePreferences`=설정.

---

### `GET /api/v1/notifications` — `list`

| 항목                               | 내용                                                                                                                                 |
|------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| 누락된 오류 응답                   | 없음 (400·401·403·404 전부 서비스 코드와 일치)                                                                                       |
| 누락된 `@Schema(description)` 필드 | `NotificationListingResponse` 2개, `NotificationCursor` 2개, `NotificationCardResponse` 7개, `NotificationTargetSummaryResponse` 3개 |
| 누락된 `@Parameter(description)`   | `cursorCreatedAt`, `cursorNotificationId`, `limit`                                                                                   |
| 문장 기준 위반                     | **§8 내부 상태값만으로 설명(가이드가 직접 인용한 문장)**, §3 문단 순서                                                               |

**Before**

```java
@Operation(
	summary = "알림함 목록 조회",
	description = "인증 사용자의 알림을 최신순으로 조회합니다. REVOKED·DISMISSED 줄은 제외됩니다. "
		+ "cursorCreatedAt과 cursorNotificationId는 둘 다 지정하거나 둘 다 생략해야 합니다.")
@GetMapping("/notifications")
ResponseEntity<ApiResponse<NotificationListingResponse>> list(
	@RequestParam(required = false) Instant cursorCreatedAt,
	@RequestParam(required = false) Long cursorNotificationId,
	@RequestParam(defaultValue = "20") int limit,
	@Parameter(hidden = true) Authentication authentication);
```

**After**

```java
@Operation(
	summary = "알림함 목록 조회",
	description = """
		받은 알림을 최신순으로 보여줍니다.

		앱 로그인이 필요하며 본인이 받은 알림만 나옵니다.

		한 번에 1개에서 50개까지 받을 수 있고 기본값은 20개입니다.

		다음 쪽을 부를 때는 앞 응답 nextCursor의 두 값을 cursorCreatedAt과 \
		cursorNotificationId에 함께 넣습니다. 둘 중 하나만 넣으면 오류입니다.""")
@GetMapping("/notifications")
ResponseEntity<ApiResponse<NotificationListingResponse>> list(
	@Parameter(description = "다음 쪽 조회에 쓸 알림 도착 시각. 앞 응답 nextCursor.createdAt을 그대로 넣습니다. cursorNotificationId와 함께 지정해야 합니다")
	@RequestParam(required = false) Instant cursorCreatedAt,
	@Parameter(description = "다음 쪽 조회에 쓸 알림 식별자. 앞 응답 nextCursor.notificationId를 그대로 넣습니다. cursorCreatedAt과 함께 지정해야 합니다")
	@RequestParam(required = false) Long cursorNotificationId,
	@Parameter(description = "한 번에 받을 알림 수. 1 이상 50 이하이며 기본값은 20입니다")
	@RequestParam(defaultValue = "20") int limit,
	@Parameter(hidden = true) Authentication authentication);
```

**변경 근거**

- `REVOKED·DISMISSED 줄은 제외됩니다`를 **삭제**했다. 다른 말로 바꾸지 않은 이유는 §4에 따로 적었다. 요약하면 (a) 가이드 §8:110이 이 문장을 나쁜 예로 직접 인용하고, (b) 응답
  `NotificationCardResponse`에 `status` 필드가 없어 클라이언트는 두 값을 본 적이 없으며, (c) **현재 코드로는 두 상태가 만들어지지 않아** 쉬운 말로 바꾸면 없는 사실을 지어내게
  된다.
- limit 범위: `NotificationInboxService.MAX_LIMIT = 50`(:35),
  `requireValidLimit`(:106-112)이 `limit < 1 || limit > 50`을 거부. 기본값 20은 `@RequestParam(defaultValue = "20")`(:53).
- cursor 왕복: 요청 `cursorCreatedAt`/`cursorNotificationId` ↔ 응답
  `NotificationListingResponse.Cursor(createdAt, notificationId)`. 한쪽만 지정하면 `cursor()`(:114-120)가 `INVALID_CURSOR`.
- "최신순": `NotificationInboxQuerySql`의 목록 정렬과
  `NotificationListing` Javadoc이 근거.

---

### `GET /api/v1/notifications/unread-count` — `unreadCount`

| 항목                               | 내용                                                                                                    |
|------------------------------------|---------------------------------------------------------------------------------------------------------|
| 누락된 오류 응답                   | 없음                                                                                                    |
| 누락된 `@Schema(description)` 필드 | `UnreadSignalResponse` 3개                                                                              |
| 문장 기준 위반                     | §5 분류 이름(`미읽음 신호`), §4 낯선 단어(`톱하지 않은`), §7 한 줄에 괄호 3개, §8 내부 상태값(`UNREAD`) |

**Before**

```java
@Operation(
	summary = "미읽음 신호 조회",
	description = "지도 홈의 알림 점(hasUnseen)과 카운터(unreadCount)를 조회합니다. 두 값의 기준선이 다릅니다 — "
		+ "hasUnseen은 열람 기준선(seenAt) 이후 도착한 UNREAD 존재 여부이고, unreadCount는 톱하지 않은 줄의 개수입니다.")
```

**After**

```java
@Operation(
	summary = "알림 점과 안 읽은 알림 수 조회",
	description = """
		지도 홈에 띄울 알림 점과 아직 읽지 않은 알림 개수를 함께 돌려줍니다.

		앱 로그인이 필요합니다.

		두 값은 기준이 달라 서로 어긋나 보일 수 있습니다.
		알림 점(hasUnseen)은 알림함을 마지막으로 연 뒤에 새 알림이 왔는지만 봅니다.
		개수(unreadCount)는 아직 읽지 않은 알림을 전부 셉니다.

		알림함을 열기만 하고 아무것도 읽지 않으면 점은 꺼지지만 개수는 그대로입니다.""")
```

**변경 근거**

- `미읽음 신호`는 범주 이름이다 (§5). 실제로 돌려주는 두 값의 이름으로 바꿨다.
- `톱하지 않은 줄`은 §4가 금지하는 낯선 표현이다. `unreadCount`의 실제 정의는
  `NotificationInboxQuerySql:93`의 `count(*) ... WHERE status = 'UNREAD'`이므로
  "아직 읽지 않은 알림을 전부 셉니다"가 정확하다.
- `UNREAD` 노출을 제거했다 (§8). 응답에 상태 필드가 없어 소비자가 대조할 수 없다.
- 괄호를 §7대로 줄당 하나로 분리하고, 첫 문단에서는 아예 뺐다. 남긴 두 개는 §7의 두 번째 경우 (팀이 영어로 부르는 개념 — 실제 응답 필드명)에 해당한다.
- 마지막 문단의 사실 근거: `markSeen`은 `seenStateRepository.advance`만 호출하고 (`NotificationInboxService:62-65`) 알림 상태를 바꾸지 않는다. 따라서
  점은 꺼지지만 `countUnread`는 그대로다. `hasUnseen`은
  `existsUnseen(recipientId, seenAt)`이고 SQL (`:102`)은
  `status = 'UNREAD' AND (:seenAt IS NULL OR created_at > :seenAt)`.

---

### `GET /api/v1/notifications/preferences` — `preferences`

| 항목                               | 내용                                                                                                     |
|------------------------------------|----------------------------------------------------------------------------------------------------------|
| 누락된 오류 응답                   | 없음                                                                                                     |
| 누락된 `@Schema(description)` 필드 | `NotificationPreferenceResponse` 4개, `NotificationTypePreferenceResponse` 2개, `QuietHoursResponse` 3개 |
| 문장 기준 위반                     | §4 낯선 명사 쌓기(`전역 push`, `6종별 push`, `알림함 원장 정책`)                                         |

**Before**

```java
@Operation(
	summary = "알림 설정 조회",
	description = "인증 사용자 본인의 전역 push, 6종별 push, 방해 금지 시간과 알림함 원장 정책을 조회합니다.")
```

**After**

```java
@Operation(
	summary = "알림 설정 조회",
	description = """
		지금 저장된 알림 설정을 보여줍니다. 앱 푸시를 전부 받을지, 알림 6종을 \
		각각 받을지, 알림을 받지 않을 시간대를 언제로 둘지가 들어 있습니다.

		앱 로그인이 필요합니다.

		설정을 한 번도 저장한 적이 없어도 기본값이 채워져 돌아옵니다. \
		기본값은 전부 켜짐이고 알림을 받지 않을 시간대는 없습니다.

		푸시를 꺼도 알림함에는 그대로 쌓입니다. 이 설정은 푸시를 보낼지만 정합니다.""")
```

**변경 근거**

- `알림함 원장 정책`은 내부 용어다. 실제 필드는 `inboxRecordingPolicy`이고 값은
  `ALWAYS_RECORD` 하나뿐이며 (`InboxRecordingPolicy`), 설계 문서
  `../../.superpowers/plans/2026-08-21-notification-preferences.md:5`가 그 의도를
  "설정과 무관하게 알림함 원장을 보존한다"로 적었다. 이를 소비자 관점 문장
  "푸시를 꺼도 알림함에는 그대로 쌓입니다"로 풀었다.
- 기본값 문단은 지어낸 것이 아니다. `NotificationPreferenceSql
  .FIND_PREFERENCE_SNAPSHOT`은 6종 CTE에 `LEFT JOIN` 후
  `COALESCE(nus.push_enabled, TRUE)`·`COALESCE(np.enabled, TRUE)`를 쓰므로 저장 행이 없어도 6행이 전부 `TRUE`로 나오고 `quiet_*`는 `NULL`이다.
  테스트 계획
  `docs/test-plans/gh-178-...:92`(INT-004)가 같은 기대값을 명시한다.

---

### `PUT /api/v1/notifications/preferences` — `replacePreferences`

| 항목                               | 내용                                       |
|------------------------------------|--------------------------------------------|
| 누락된 오류 응답                   | 없음                                       |
| 누락된 `@Schema(description)` 필드 | (조회와 같은 응답 스키마)                  |
| 문장 기준 위반                     | §4 낯선 영어 단어(`snapshot`, `canonical`) |

**Before**

```java
@Operation(
	summary = "알림 설정 전체 교체",
	description = "인증 사용자 본인의 전역 push, 6종별 push와 방해 금지 시간 snapshot을 완전 교체하고 canonical 응답을 반환합니다.")
```

**After**

```java
@Operation(
	summary = "알림 설정 통째로 바꾸기",
	description = """
		알림 설정을 보낸 값으로 통째로 바꿉니다.

		앱 로그인이 필요합니다.

		일부만 보내 고칠 수 없습니다. 푸시 전체 허용 여부와 알림 6종 설정을 매번 \
		전부 보내야 합니다. 6종을 빠뜨리거나 같은 종류를 두 번 보내면 저장하지 않습니다.

		알림을 받지 않을 시간대는 보내지 않거나 null로 두면 꺼집니다. 켜려면 시작 \
		시각, 종료 시각, 시간대를 모두 채워야 하고 시작과 종료가 같으면 안 됩니다.

		저장한 뒤에는 조회 API와 같은 형식으로 저장된 설정을 돌려줍니다.""")
```

**변경 근거**

- `snapshot`·`canonical`은 §4가 금지하는 낯선 단어다. `canonical 응답`이 실제로 뜻하는 바는 "저장 결과를 조회 API와 같은 형식으로 되돌려준다"이며, 컨트롤러가
  `NotificationPreferenceResponse.from(...)`으로 조회와 같은 타입을 반환하는 것이 근거다 (`NotificationController:59-63`).
- 완전 교체 규칙: `UpdateNotificationPreferencesRequest.toCompleteEnumMap`이
  `preferences == null || size != 6`(:37), 중복 (:49), 부분 집합 (:55)을 각각
  `INVALID_PREFERENCE`로 거부한다. `pushEnabled == null`도 거부 (:25).
- quietHours 규칙: `quietHours == null ? null : toDomain()`(:31)이 끄기 경로이고,
  `NotificationQuietHours` 생성자 (:12)가 `start == null || end == null ||
  zoneId == null || start.equals(end)`를 거부한다.
- `summary`를 동사구로 바꾼 것은 §2의 예외 조항을 쓴 것이다. `전체 교체`는 익숙하지만 `알림 설정 전체 교체`가 실제로 오해를 부르는 지점 ("일부만 못 보낸다")을 드러내지 못한다. 동사구가
  §3-5 (주의점)를 `summary`에서 이미 암시한다.

---

### `PUT /api/v1/notifications/seen` — `markSeen`

| 항목                               | 내용                                                                                                          |
|------------------------------------|---------------------------------------------------------------------------------------------------------------|
| 누락된 오류 응답                   | 없음                                                                                                          |
| 누락된 `@Schema(description)` 필드 | `NotificationSeenResponse` 1개                                                                                |
| 문장 기준 위반                     | §5 분류 이름(`열람 기준선 전진`), §7 허용되지 않은 괄호(`(GREATEST)` — SQL 함수명), §4 낯선 표현(`역순 호출`) |

**Before**

```java
@Operation(
	summary = "알림함 열람 기준선 전진",
	description = "지도 홈의 알림 점을 해제합니다. 서버 시각으로만 전진하며(GREATEST), 반복·역순 호출이 "
		+ "기준선을 과거로 되돌리지 않습니다. 목록의 줄 자체는 지워지지 않습니다.")
```

**After**

```java
@Operation(
	summary = "알림 점 끄기",
	description = """
		지도 홈에 떠 있는 알림 점을 끕니다.

		앱 로그인이 필요합니다.

		알림함을 마지막으로 연 시각을 지금 시각으로 올립니다. 이 시각은 서버가 \
		정하므로 요청 본문이 없고, 여러 번 불러도 시각이 과거로 돌아가지 않습니다.

		알림을 읽음으로 바꾸지는 않습니다. 목록의 알림은 그대로 남고 안 읽은 알림 \
		개수도 줄지 않습니다. 읽음으로 바꾸려면 읽음 처리 API를 따로 부릅니다.""")
```

**변경 근거**

- `(GREATEST)`는 PostgreSQL 함수명이다. §7이 허용하는 세 경우 어디에도 속하지 않고, `JdbcNotificationSeenStateRepository:24`가 이미 그 선택 이유를 코드
  주석으로 남기고 있으므로 API 소비자 문서에는 필요 없다. 소비자에게 의미 있는 사실 ("과거로 돌아가지 않는다")만 문장으로 남겼다.
- `열람 기준선 전진`은 구현 용어다 (§5). 사용자가 실제로 얻는 결과는 알림 점이 꺼지는 것이다.
- "요청 본문이 없다": 컨트롤러 시그니처가 `markSeen(Authentication)`뿐이다 (`NotificationController:66`).
- "서버 시각": `markSeen`이 `clock.instant()`를 넘긴다 (`NotificationInboxService:64`, Javadoc :60이 의도를 명시).
- 마지막 문단: `advance`만 호출하므로 `notification.status`는 그대로다. 따라서
  `countUnread`(= `status = 'UNREAD'` 개수)도 변하지 않는다.

---

### `PUT /api/v1/notifications/{notificationId}/read` — `markRead`

| 항목                               | 내용                                                                                                                                                |
|------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| 누락된 오류 응답                   | 없음 (404·409 전부 서비스·도메인 코드와 일치)                                                                                                       |
| 누락된 `@Schema(description)` 필드 | `NotificationCardResponse` 7개                                                                                                                      |
| 문장 기준 위반                     | §4 낯선 단어(`멱등`), §8 내부 상태값(`READ`·`REVOKED`), §3-4 오류를 `description`에서 반복(`REVOKED 줄은 409입니다`), §7 한 줄에 괄호 2개(409 문장) |

**Before**

```java
@Operation(
	summary = "알림 읽음 처리",
	description = "알림 한 줄을 읽음 처리합니다. 멱등입니다 — 이미 READ면 상태를 바꾸지 않고 현재 값을 반환합니다. "
		+ "REVOKED 줄은 409입니다.")
@ApiResponses({
	...
	@ApiResponse(responseCode = "404", description = "알림이 없거나 남의 알림입니다. (NOT-APP-001, NOT-DOM-004)", ...),
	@ApiResponse(responseCode = "409", description = "취소된(REVOKED) 알림은 읽음 처리할 수 없습니다. (NOT-DOM-003)", ...)
})
```

**After**

```java
@Operation(
	summary = "알림 읽음 처리",
	description = """
		알림 하나를 읽음으로 표시합니다.

		앱 로그인이 필요하고 본인이 받은 알림만 바꿀 수 있습니다.

		읽음으로 바뀐 알림과 읽은 시각을 돌려줍니다. 안 읽은 알림 개수도 그만큼 줄어듭니다.

		같은 알림을 여러 번 불러도 안전합니다. 이미 읽은 알림이면 읽은 시각을 \
		다시 쓰지 않고 지금 값을 그대로 돌려줍니다.""")
@ApiResponses({
	...
	@ApiResponse(responseCode = "404", description = "그런 알림이 없거나 본인이 받은 알림이 아닙니다. 인증 사용자 계정을 찾을 수 없을 때도 같습니다. (NOT-APP-001, NOT-DOM-004)", ...),
	@ApiResponse(responseCode = "409", description = "내려간 알림이라 읽음으로 바꿀 수 없습니다. (NOT-DOM-003)", ...)
})
```

**변경 근거**

- `멱등`을 "같은 알림을 여러 번 불러도 안전합니다"로 풀었다 (§4). 실제 동작 근거는
  `NotificationInboxService.markRead:77-79` — `status != READ`일 때만 `update`를 호출하므로 `read_at`이 다시 쓰이지 않는다.
- `REVOKED 줄은 409입니다`를 `description`에서 제거했다. §3-4가 "오류 코드 자체는
  `@ApiResponse`에 적고 여기서는 반복하지 않는다"고 정한다.
- 409 문장에서 괄호 두 개 (`(REVOKED)`와 `(NOT-DOM-003)`) 중 상태값 괄호를 없앴다 (§7 "한 줄에 괄호는 하나만"). `REVOKED`는 응답 어디에도 나타나지 않아 §7 첫 번째
  경우 (응답 `status` 필드와 일치하는 상태값)에 해당하지도 않는다.
- 404 문장에 계정 경로를 덧붙였다. 근거는 두 갈래가 같은 404로 합쳐지기 때문이다:
  `requireEligibleAccount` → `ACCOUNT_NOT_FOUND`(`NOT-APP-001`)와
  `requireOwnedNotification` → `NOTIFICATION_NOT_FOUND`(`NOT-DOM-004`). 후자의 Javadoc (:99)이 "존재하지 않는 알림과 남의 알림을 구분하지
  않는다"고 적는다.
- "안 읽은 개수가 줄어든다": `countUnread`가 `status = 'UNREAD'`를 세므로 `READ`
  전이 후 감소한다.

---

### `GET /api/v1/notifications/{notificationId}/target` — `target`

| 항목                               | 내용                                                                |
|------------------------------------|---------------------------------------------------------------------|
| 누락된 오류 응답                   | 없음                                                                |
| 누락된 `@Schema(description)` 필드 | `NotificationTargetResponse` 4개                                    |
| 문장 기준 위반                     | §5 분류 이름(`진입 판정`), §4 낯선 단어(`대상 생존 상태`, `톱했을`) |

**Before**

```java
@Operation(
	summary = "알림 진입 판정",
	description = "알림 한 줄의 대상 생존 상태를 다시 평가합니다. 알림함을 열고 수 분 뒤 톱했을 수 있으므로 "
		+ "목록의 판정을 그대로 믿지 않습니다.")
```

**After**

```java
@Operation(
	summary = "알림에서 원래 글로 갈 수 있는지 확인",
	description = """
		이 알림이 가리키는 글로 지금 넘어갈 수 있는지 확인합니다.

		앱 로그인이 필요하고 본인이 받은 알림만 확인할 수 있습니다.

		넘어갈 수 있으면 navigable이 true입니다.
		갈 수 없으면 false와 함께 갈 수 없는 이유가 reason에 담기고, \
		대신 보여줄 화면이 fallback에 담깁니다.

		알림함을 연 뒤 시간이 지나 원래 글이 지워지거나 기간이 끝났을 수 있습니다. \
		목록에 담긴 판정을 그대로 쓰지 말고 누르는 순간 이 API로 다시 확인합니다.""")
```

**변경 근거**

- `진입 판정`은 범주 이름이다 (§5). §2의 예외 조항대로 낯선 명사가 쌓였으므로 동사구로 풀었다.
- `대상 생존 상태`·`톱했을`은 §4 위반. 원래 Javadoc (`NotificationInboxService:84`)이 담고 있던 의도는 그대로 살렸다.
- `navigable`·`reason`·`fallback`의 정의는 `NotificationTargetDecision`의 파생 메서드에서 확인했다:
  `navigable() = targetState == AVAILABLE`(:29),
  `reason() = navigable() ? null : targetState`(:34),
  `fallback()`(:43-48)은 `AVAILABLE→NONE`, `EXPIRED→INBOX`, 나머지→`FEED_HOME`.
- 문단 3의 괄호는 아예 쓰지 않고 필드명을 문장에 녹였다 (§7 "한 줄에 하나").

---

### 응답 DTO `@Schema(description)` 31건

`*ApiSpec` 밖이지만 6점 대조 2번의 직접 결과이므로 함께 제안한다. 모든 문장은 해당 record의 매핑 코드·도메인 불변식·SQL에서 근거를 확인했다.

| 스키마                               | 필드                   | 제안 description                                                                                                                                                                  | 근거                                                                         |
|--------------------------------------|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| `NotificationListingResponse`        | `notifications`        | 받은 알림 목록. 최신순입니다                                                                                                                                                      | `NotificationListing` Javadoc                                                |
|                                      | `nextCursor`           | 다음 쪽 커서. 마지막 쪽이면 null입니다                                                                                                                                            | 같은 Javadoc: 반환 건수가 limit과 같을 때만 채움                             |
| `NotificationCursor`                 | `createdAt`            | 다음 쪽 조회에 쓸 알림 도착 시각                                                                                                                                                  | `Cursor.from`                                                                |
|                                      | `notificationId`       | 다음 쪽 조회에 쓸 알림 식별자                                                                                                                                                     | 같음                                                                         |
| `NotificationCardResponse`           | `notificationId`       | 알림 식별자                                                                                                                                                                       | —                                                                            |
|                                      | `type`                 | 알림 종류 (+ `allowableValues` 6종)                                                                                                                                               | `NotificationType`                                                           |
|                                      | `createdAt`            | 알림이 도착한 시각                                                                                                                                                                | —                                                                            |
|                                      | `readAt`               | 이 알림을 읽은 시각. 아직 읽지 않았으면 null입니다                                                                                                                                | `Notification:29` 불변식 (readAt은 READ/DISMISSED에만)                       |
|                                      | `unread`               | 아직 읽지 않은 알림인지 여부                                                                                                                                                      | `NotificationCard`                                                           |
|                                      | `target`               | 이 알림이 가리키는 글의 요약                                                                                                                                                      | —                                                                            |
|                                      | `expiresAt`            | 이 알림이 가리키는 질문글이 만료되는 시각. 아직 볼 수 있는 질문글 알림에만 채워지고 그 밖에는 null입니다                                                                          | `NotificationCard:42-47` 불변식 + `NotificationInboxQuerySql:30-31`의 `CASE` |
| `NotificationTargetSummaryResponse`  | `kind`                 | 알림이 가리키는 대상의 종류. 가리키는 대상이 없으면 NONE입니다 (+ `allowableValues`)                                                                                              | `NotificationTargetKind`                                                     |
|                                      | `id`                   | 대상 식별자. kind가 NONE이면 null입니다                                                                                                                                           | `NotificationCard:29-35`                                                     |
|                                      | `state`                | 대상의 현재 상태. AVAILABLE이면 지금 볼 수 있고, EXPIRED는 기간이 끝난 질문글, HIDDEN은 숨겨진 글, BLOCKED는 차단한 상대의 글, GONE은 지워진 글입니다. kind가 NONE이면 null입니다 | `NotificationInboxQuerySql:44-60` + `blockedBetween`(양방향 차단)            |
| `UnreadSignalResponse`               | `hasUnseen`            | 지도 홈에 알림 점을 띄울지 여부. 알림함을 마지막으로 연 뒤 새 알림이 왔으면 true입니다                                                                                            | `NotificationInboxQuerySql:96-102`                                           |
|                                      | `unreadCount`          | 아직 읽지 않은 알림 개수. 알림함을 열기만 해서는 줄지 않습니다                                                                                                                    | 같은 파일 :93                                                                |
|                                      | `seenAt`               | 알림함을 마지막으로 연 시각. 한 번도 연 적이 없으면 null입니다                                                                                                                    | `NotificationInboxService:54` `orElse(null)`                                 |
| `NotificationSeenResponse`           | `seenAt`               | 새로 기록된, 알림함을 마지막으로 연 시각                                                                                                                                          | `advance` 반환값                                                             |
| `NotificationTargetResponse`         | `navigable`            | 이 알림에서 원래 글로 넘어갈 수 있는지 여부                                                                                                                                       | `NotificationTargetDecision:29`                                              |
|                                      | `reason`               | 넘어갈 수 없는 이유. 넘어갈 수 있으면 null입니다 (+ 상태 5종 뜻풀이)                                                                                                              | 같은 파일 :34                                                                |
|                                      | `target`               | 이 알림이 가리키는 글의 요약                                                                                                                                                      | —                                                                            |
|                                      | `fallback`             | 넘어갈 수 없을 때 대신 보여줄 화면. 넘어갈 수 있으면 NONE, 기간이 끝난 질문글이면 INBOX, 그 밖에는 FEED_HOME입니다                                                                | 같은 파일 :43-48                                                             |
| `NotificationPreferenceResponse`     | `pushEnabled`          | 앱 푸시 알림 전체 허용 여부                                                                                                                                                       | —                                                                            |
|                                      | `quietHours`           | 알림을 받지 않을 시간대. 설정하지 않았으면 null입니다                                                                                                                             | `from`(:19)의 null 분기                                                      |
|                                      | `preferences`          | 알림 6종별 허용 여부. 항상 6개가 모두 들어 있습니다                                                                                                                               | `toResponses`가 `NotificationType.values()` 전체 순회                        |
|                                      | `inboxRecordingPolicy` | 알림함 기록 정책. 푸시를 꺼도 알림함에는 항상 쌓이므로 값은 언제나 ALWAYS_RECORD입니다                                                                                            | `InboxRecordingPolicy` + 설계 문서 :5                                        |
| `NotificationTypePreferenceResponse` | `type`                 | 알림 종류                                                                                                                                                                         | 기존 `allowableValues` 유지                                                  |
|                                      | `enabled`              | 이 종류의 푸시 알림을 받을지 여부                                                                                                                                                 | —                                                                            |
| `QuietHoursResponse`                 | `start`                | 알림을 받지 않기 시작하는 시각                                                                                                                                                    | `QuietHoursResponse.from`                                                    |
|                                      | `end`                  | 알림을 다시 받기 시작하는 시각                                                                                                                                                    | 같음                                                                         |
|                                      | `zoneId`               | 위 두 시각을 해석할 시간대                                                                                                                                                        | 같음                                                                         |

## 4. 반영하지 않은 제안

### 4.1 `REVOKED·DISMISSED`를 쉬운 말로 "번역"하지 않고 문장을 삭제했다

가이드 §8:110은 이렇게 적는다.

```text
REVOKED 줄은 제외됩니다 ✗ → 신고로 내려간 알림은 목록에 나오지 않습니다 ○
```

왼쪽이 `NotificationApiSpec.list()`에 있는 문장 그대로다. 그런데 오른쪽으로 고치는 것은 이 저장소의 현재 코드로 **근거를 댈 수 없다.**

- `Notification.revoke()`(`Notification.java:49`)는 **정의만 있고 호출부가 없다.** `grep -rn "revoke()" src/main`의 결과가 정의 한 줄뿐이다.
- `DISMISSED`로 전이하는 코드는 **아예 없다.** 이 값은 `NotificationStatus`
  enum, DB `CHECK` 제약 (`V1__...sql:775`), 목록 제외 술어 (`NotificationInboxQuerySql:84`)에만 나타난다.
- 즉 **현재 API로는 두 상태가 만들어지지 않는다.** 소비자가 마주칠 수 없는 분기를 설명하는 문장이다.
- `Notification.revoke()`의 Javadoc은 "전역 숨김"이라고만 쓰고 신고 (`#155`)를 후속 이슈로 가리킨다. "신고로 내려간"이라고 쓰면 §8의 세 번째 금지 ("DTO에 없는 사실을
  지어내지 않는다")를 어기게 된다.
- 응답 `NotificationCardResponse`에는 `status` 필드가 없다. 소비자는 어떤 알림이 걸러졌는지 확인할 방법 자체가 없다.

따라서 이번 반영에서는 문장을 **삭제**했다. `#155`가 신고 기반 revoke를 실제로 붙이면 그때 소비자 관점 문장을 근거와 함께 되살리는 것이 맞다.

### 4.2 `docs/api/OPENAPI_WRITING_GUIDE.md` §8 예시가 저장소 코드와 어긋난다 — 별도 후속

§8:110의 좋은 예 ("신고로 내려간 알림은 목록에 나오지 않습니다")는 위 4.1대로 현재 코드에서 참이 아니다. 가이드를 그대로 따르면 없는 사실을 쓰게 되므로, 예시를 다른 도메인 사례로 교체하거나 단서를
붙이는 편이 좋다. 다만
`docs/api/**` 가이드 본문 수정은 이 브랜치의 범위 (Notification `*ApiSpec` 문장 개선)를 벗어나므로 **고치지 않고 여기 기록만 남긴다.** `#191`이 가이드를 소유하므로 후속은
그쪽에 붙이는 것이 맞다.

### 4.3 계약 자체는 손대지 않았다

6점 대조 3·4번에서 오류 응답 불일치가 **하나도 나오지 않았다.** 상태 코드나 오류 코드를 바꿀 이유가 없어 별도 이슈로 분리할 계약 문제도 없다. Feed에서 발견했던 `docs/error-codes.md`
누락도 이 도메인에는 없다 (`NOT-*` 15개 전부 등재).

### 4.4 응답 enum 문자열에 `allowableValues`를 붙이되 값 집합만 나열하지 않았다

`type`·`kind`·`state`·`reason`·`fallback`은 전부 `String`이라 스펙만으로는 값 집합을 알 수 없다. `allowableValues`를 붙이는 것은 실제 매핑 코드가
`enum.name()`을 넣으므로 사실과 일치한다. 다만 §8이 "상태값만으로 의미를 설명하지 않는다"고 정하므로, 값을 나열하는 데 그치지 않고 각 값이 무슨 뜻인지
`description`에 함께 풀어 썼다.

## 5. 실행하지 못한 검증

없다. 6점 대조 6개 항목을 모두 실행했다.

- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`:
  반영 전 (기준선 확인)과 반영 후 두 번 실행, 모두 통과.
- `./harness pr-ready --project-tests`: 반영 후 실행 결과는 `TASK.md`의 Validation evidence에 기록한다.

## 6. Reviewer checklist

- [x] 모든 제안 문장이 실제 서비스/DTO/SQL 코드로 근거를 확인했다 (추측 없음)
- [x] `review` 모드 산출 시점에는 `*ApiSpec` 원본을 수정하지 않았다. §3의 "After"는 이후 승인을 받아 반영한 상태다
- [x] `@Schema(example)`에 비밀값·계정 식별자를 쓰지 않았다
- [x] 내부 불변식 ID·이슈 번호 (`(#176)`)가 제안 문장에 남아 있지 않다
- [x] 6점 대조를 모두 실행했고 실행하지 못한 항목이 없다
- [x] 근거를 댈 수 없는 문장은 쉬운 말로 바꾸지 않고 삭제한 뒤 §4에 이유를 남겼다
