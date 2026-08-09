# 오류 코드와 예외 사용 규칙

이 문서는 API 오류 응답의 계약이다. 결정 배경은
`docs/adr/0003-global-exception-handling.md`에 있다.

성공 응답 형식과 HTTP 상태 매핑을 포함한 응답 전체 계약은
`docs/api-response.md`에 있다. 이 문서는 오류 코드 목록과 예외 사용 규칙을 다룬다.

## 1. 응답 형식

상태 코드와 무관하게 모든 오류는 같은 본문으로 나간다. 성공 응답과는 `status`,
`timestamp`를 공유한다.

```json
{
  "status": "error",
  "message": "timezone은 유효한 IANA 지역 ID여야 합니다.",
  "errorDetail": {
    "code": "ACC-VAL-004",
    "field": "timezone",
    "reason": "timezone은 유효한 IANA ID여야 합니다"
  },
  "timestamp": "2026-08-04T09:00:00Z"
}
```

| 필드 | 의미 |
| --- | --- |
| `status` | 항상 `error` |
| `message` | 오류 코드의 기본 메시지. 사용자에게 보여도 안전한 값 |
| `errorDetail.code` | `{BC}-{CATEGORY}-{SEQ}` 형식의 안정적인 식별자 |
| `errorDetail.field` | 실패한 값의 이름. 값 단위 실패가 아니면 `null` |
| `errorDetail.reason` | 어떤 규칙을 어겼는지. 같은 코드라도 값에 따라 달라진다 |
| `timestamp` | 응답을 만든 시각 |

## 2. 코드 체계

```text
{BC}-{CATEGORY}-{SEQ}
 │     │          └─ 기능 안에서 증가하는 3자리 일련번호
 │     └─ VAL | DOM | APP | INFRA | EXT
 └─ 기능 약어
```

| 약어 | 기능 패키지 |
| --- | --- |
| `CMN` | `common` (기능에 속하지 않는 공통 오류) |
| `ACC` | `account` |
| `AUT` | `auth` (인증·인가) |
| `QUE` | `question` |
| `DIR` | `direction` |
| `ANS` | `answer` |
| `SAF` | `safety` |
| `NOT` | `notification` |
| `FED` | `feed` |

| 카테고리 | 의미 | 운영 대응 |
| --- | --- | --- |
| `VAL` | 입력값 유효성 실패 (형식, 범위, null) | 요청을 고쳐야 한다 |
| `DOM` | 도메인 불변식 위반 | 같은 입력으로 재시도해도 해결되지 않는다 |
| `APP` | 유즈케이스 흐름 실패 (대상의 상태·선행 조건) | 상태가 바뀌면 해소된다 |
| `INFRA` | DB, 네트워크 문제 | 재시도 후보 |
| `EXT` | 외부 시스템 연동 문제 | 재시도 후보 |

## 3. 계층별 사용 규칙

- **도메인·서비스**: 자기 기능의 `XxxException`만 던진다. `IllegalArgumentException`,
  `IllegalStateException`, `Objects.requireNonNull`은 사용하지 않는다.
- **다른 기능 호출**: 다른 기능의 오류 코드를 직접 던지지 않는다. 자기 기능의 코드로 옮긴다.
- **controller**: 예외를 잡지 않는다. `GlobalExceptionHandler`가 전부 처리한다.
- **반복되는 값 검증**: 코드를 새로 만들지 않고 기존 코드에 `field`와 `reason`을 붙인다.
  예를 들어 `INVALID_ID`는 어떤 식별자든 쓰고, 무엇이 틀렸는지는 `field`로 구분한다.
- **메시지**: 오류 코드의 `message`에는 닉네임, 좌표, 토큰, 신고 내용처럼 개인정보나
  사용자 콘텐츠를 넣지 않는다. 상세 원인은 로그로 추적한다.
- **로깅**: 4xx는 `WARN`, 5xx는 `ERROR`와 스택트레이스로 남긴다. `APP_ERROR` 로거를 쓰고
  MDC에 `errorCode`와 `errorType`을 넣는다.

## 4. 코드를 추가할 때

1. 해당 기능의 `XxxErrorCode`에 상수를 추가한다. 일련번호는 그 카테고리의 마지막 값 다음이다.
2. enum 상수 위에 언제 발생하는지 주석으로 남긴다.
3. 이 문서의 표에 추가한다.

한 번 배포한 코드 값은 바꾸지 않는다. 더 이상 쓰지 않는 코드는 삭제하지 말고 사용을 중단한다.
일련번호를 재사용하면 과거 로그의 의미가 달라진다.

## 5. 공통 코드 (CMN)

전역 처리기가 직접 만들어내는 오류다. 기능 코드는 이 코드를 던지지 않는다.

| 코드 | 이름 | HTTP | 분류 | 메시지 |
| --- | --- | --- | --- | --- |
| `CMN-VAL-001` | INVALID_INPUT | 400 | VAL | 요청 값이 올바르지 않습니다. |
| `CMN-VAL-002` | MISSING_FIELD | 400 | VAL | 필수 입력값이 누락되었습니다. |
| `CMN-VAL-003` | UNAUTHORIZED | 401 | VAL | 인증에 실패했습니다. |
| `CMN-DOM-001` | FORBIDDEN | 403 | DOM | 접근 권한이 없습니다. |
| `CMN-DOM-002` | NOT_FOUND | 404 | DOM | 요청한 리소스를 찾을 수 없습니다. |
| `CMN-DOM-003` | CONFLICT | 409 | DOM | 중복 또는 충돌이 발생했습니다. |
| `CMN-INFRA-001` | INTERNAL_ERROR | 500 | INFRA | 서버 내부 오류가 발생했습니다. |

`CMN-VAL-003`과 `CMN-DOM-001`은 인증·인가 도입 전에 응답 형식을 고정해 두기 위해 정의했다.

낙관적 잠금 충돌(`OptimisticLockingFailureException`)도 `CMN-DOM-003`으로 나간다. 같은 행을
동시에 수정한 요청 중 뒤늦은 쪽이며, 기능과 무관하게 의미가 같아 공통 코드로 둔다.

## 6. account (ACC)

| 코드 | 이름 | HTTP | 분류 | 메시지 |
| --- | --- | --- | --- | --- |
| `ACC-VAL-001` | INVALID_ID | 400 | VAL | 계정 식별자가 올바르지 않습니다. |
| `ACC-VAL-002` | REQUIRED_VALUE_MISSING | 400 | VAL | 계정 필수 값이 없습니다. |
| `ACC-VAL-003` | TEXT_TOO_LONG | 400 | VAL | 계정 값이 허용 길이를 초과했습니다. |
| `ACC-VAL-004` | INVALID_TIMEZONE | 400 | VAL | timezone은 유효한 IANA 지역 ID여야 합니다. |
| `ACC-VAL-005` | INVALID_COUNTRY_CODE | 400 | VAL | 국가 코드가 올바르지 않습니다. |
| `ACC-DOM-001` | INVALID_AUDIT_TIMESTAMPS | 400 | DOM | 계정 생성·수정 시각이 올바르지 않습니다. |
| `ACC-DOM-002` | INVALID_DELETION_STATE | 400 | DOM | 탈퇴 상태와 탈퇴 시각이 일치하지 않습니다. |
| `ACC-DOM-003` | INVALID_PASSWORD_HASH_STATE | 400 | DOM | 계정 권한과 비밀번호 설정이 맞지 않습니다. |
| `ACC-DOM-004` | INVALID_STATUS_TRANSITION | 409 | DOM | 현재 계정 상태로는 요청을 처리할 수 없습니다. |
| `ACC-APP-001` | ACCOUNT_NOT_FOUND | 404 | APP | 계정을 찾을 수 없습니다. |

`ACC-DOM-001`은 생성·수정 시각 관리가 저장 계층으로 옮겨가면서 사용을 중단했다.

`ACC-DOM-003`은 자격증명이 `operator_credential`로 분리되면서(#72) 사용을 중단했다.
`Account`가 비밀번호를 알지 못하게 됐고, role과 자격증명의 조합은 `(user_id, role)`
복합 FK가 DB에서 거절한다.

## 7. question (QUE)

| 코드 | 이름 | HTTP | 분류 | 메시지 |
| --- | --- | --- | --- | --- |
| `QUE-VAL-001` | INVALID_ID | 400 | VAL | 질문 식별자가 올바르지 않습니다. |
| `QUE-VAL-002` | REQUIRED_VALUE_MISSING | 400 | VAL | 질문 필수 값이 없습니다. |
| `QUE-VAL-003` | TEXT_TOO_LONG | 400 | VAL | 질문 값이 허용 길이를 초과했습니다. |
| `QUE-VAL-004` | INVALID_TIME_ORDER | 400 | VAL | 질문 시각 순서가 올바르지 않습니다. |
| `QUE-VAL-005` | INVALID_VALUE_RANGE | 400 | VAL | 질문 값이 허용 범위를 벗어났습니다. |
| `QUE-DOM-001` | INVALID_PROPOSAL_STATE | 400 | DOM | 질문 제안 상태와 값이 맞지 않습니다. |
| `QUE-DOM-002` | INVALID_PROPOSAL_STATUS | 409 | DOM | 현재 제안 상태로는 요청을 처리할 수 없습니다. |
| `QUE-DOM-003` | INVALID_QUESTION_STATE | 400 | DOM | 승인 질문 상태와 값이 맞지 않습니다. |
| `QUE-DOM-004` | INVALID_AUDIT_TIMESTAMPS | 400 | DOM | 질문 생성·수정 시각이 올바르지 않습니다. |
| `QUE-APP-001` | QUESTION_NOT_ASSIGNABLE | 409 | APP | 배정 시각에 활성인 질문이 아닙니다. |
| `QUE-INFRA-001` | ASSIGNMENT_CYCLE_NOT_PERSISTED | 500 | INFRA | 질문 배정 주기를 저장하지 못했습니다. |
| `QUE-INFRA-002` | DUPLICATED_ASSIGNMENT | 409 | INFRA | 이미 배정된 질문입니다. |

## 8. direction (DIR)

| 코드 | 이름 | HTTP | 분류 | 메시지 |
| --- | --- | --- | --- | --- |
| `DIR-VAL-001` | INVALID_ID | 400 | VAL | 방향 식별자가 올바르지 않습니다. |
| `DIR-VAL-002` | REQUIRED_VALUE_MISSING | 400 | VAL | 방향 필수 값이 없습니다. |
| `DIR-VAL-003` | INVALID_TEXT | 400 | VAL | 방향 문자열 값이 올바르지 않습니다. |
| `DIR-VAL-004` | INVALID_BEARING | 400 | VAL | 방위각이 올바르지 않습니다. |
| `DIR-VAL-005` | INVALID_DISTANCE_RANGE | 400 | VAL | 거리 범위가 올바르지 않습니다. |
| `DIR-VAL-006` | INVALID_COORDINATE | 400 | VAL | 위치 좌표가 올바르지 않습니다. |
| `DIR-VAL-007` | INVALID_TIME_ORDER | 400 | VAL | 방향 시각 순서가 올바르지 않습니다. |
| `DIR-VAL-008` | INVALID_VALUE_RANGE | 400 | VAL | 방향 값이 허용 범위를 벗어났습니다. |
| `DIR-DOM-001` | INVALID_SCHEME_CONFIGURATION | 400 | DOM | 방향 구획 구성이 올바르지 않습니다. |
| `DIR-DOM-002` | SEGMENT_NOT_FOUND | 400 | DOM | 방위각을 포함하는 구획이 없습니다. |
| `DIR-DOM-003` | INVALID_POST_STATE | 400 | DOM | 게시글 상태와 값이 맞지 않습니다. |
| `DIR-DOM-004` | INVALID_RECIPIENT_STATE | 400 | DOM | 수신자 상태와 값이 맞지 않습니다. |
| `DIR-DOM-005` | LOCATION_REQUIRED | 400 | DOM | 위치 정보가 필요합니다. |
| `DIR-DOM-006` | SCHEME_NOT_FOUND | 404 | DOM | 방향 구획 체계를 찾을 수 없습니다. |
| `DIR-DOM-007` | INELIGIBLE_REACTOR | 403 | DOM | 질문글에 공감할 수 있는 수신자가 아닙니다. |
| `DIR-DOM-008` | RECIPIENT_NOT_FOUND | 404 | DOM | 수신 항목을 찾을 수 없습니다. |
| `DIR-DOM-009` | POST_NOT_FOUND | 404 | DOM | 질문글을 찾을 수 없습니다. |
| `DIR-APP-001` | QUESTION_NOT_ACTIVE | 409 | APP | 전송 시각에 활성인 질문이 아닙니다. |
| `DIR-APP-002` | PRESENCE_LOCATION_MISSING | 409 | APP | 위치 정보가 없어 수신 후보를 계산할 수 없습니다. |
| `DIR-APP-003` | PRESENCE_NOT_CURRENT | 409 | APP | 현재 위치 정보가 유효하지 않습니다. |
| `DIR-APP-004` | PRESENCE_NOT_FOUND | 409 | APP | 발신자의 위치 정보가 없습니다. |
| `DIR-INFRA-001` | DUPLICATED_POST | 409 | INFRA | 이미 전송된 게시글입니다. |
| `DIR-INFRA-002` | DUPLICATED_RECIPIENT | 409 | INFRA | 이미 등록된 수신자입니다. |

## 9. answer (ANS)

| 코드 | 이름 | HTTP | 분류 | 메시지 |
| --- | --- | --- | --- | --- |
| `ANS-VAL-001` | INVALID_ID | 400 | VAL | 답변 식별자가 올바르지 않습니다. |
| `ANS-VAL-002` | REQUIRED_VALUE_MISSING | 400 | VAL | 답변 필수 값이 없습니다. |
| `ANS-VAL-003` | INVALID_TEXT | 400 | VAL | 답변 문자열 값이 올바르지 않습니다. |
| `ANS-VAL-004` | INVALID_BEARING | 400 | VAL | 방위각이 올바르지 않습니다. |
| `ANS-VAL-005` | INVALID_MEDIA_TARGET | 400 | VAL | 미디어 첨부 대상이 올바르지 않습니다. |
| `ANS-VAL-006` | INVALID_MEDIA_METADATA | 400 | VAL | 미디어 값이 올바르지 않습니다. |
| `ANS-VAL-007` | INVALID_VALUE_RANGE | 400 | VAL | 값의 범위가 올바르지 않습니다. |
| `ANS-DOM-001` | INVALID_ANSWER_STATE | 400 | DOM | 답변 상태와 값이 맞지 않습니다. |
| `ANS-DOM-002` | INVALID_ANSWER_STATUS | 409 | DOM | 현재 답변 상태로는 요청을 처리할 수 없습니다. |
| `ANS-DOM-003` | SAFETY_CHECK_NOT_PASSED | 409 | DOM | 안전 검사를 통과한 답변만 공개할 수 있습니다. |
| `ANS-DOM-004` | INELIGIBLE_REACTOR | 403 | DOM | 그 질문글을 볼 수 있는 사람만 답변에 공감할 수 있습니다. |
| `ANS-DOM-005` | INVALID_MEDIA_STATE | 400 | DOM | 미디어 상태와 값이 맞지 않습니다. |
| `ANS-DOM-006` | INVALID_MEDIA_STATUS | 409 | DOM | 현재 미디어 상태로는 요청을 처리할 수 없습니다. |
| `ANS-DOM-007` | MEDIA_NOT_FOUND | 404 | DOM | 미디어를 찾을 수 없습니다. |
| `ANS-DOM-008` | MEDIA_OWNER_MISMATCH | 403 | DOM | 본인 소유의 미디어만 사용할 수 있습니다. |
| `ANS-DOM-009` | MEDIA_CONTENT_REQUIRED | 409 | DOM | 공개된 콘텐츠는 본문 또는 미디어가 있어야 합니다. |
| `ANS-DOM-010` | INVALID_EDIT_STATE | 400 | DOM | 수정 횟수와 수정 시각이 맞지 않습니다. |
| `ANS-INFRA-001` | DUPLICATED_ANSWER | 409 | INFRA | 이미 등록된 답변입니다. |
| `ANS-EXT-001` | STORAGE_UNAVAILABLE | 503 | EXT | 미디어 저장소에 연결할 수 없습니다. |

## 10. safety (SAF)

| 코드 | 이름 | HTTP | 분류 | 메시지 |
| --- | --- | --- | --- | --- |
| `SAF-VAL-001` | INVALID_ID | 400 | VAL | 신고 식별자가 올바르지 않습니다. |
| `SAF-VAL-002` | REQUIRED_VALUE_MISSING | 400 | VAL | 신고 필수 값이 없습니다. |
| `SAF-VAL-003` | INVALID_REPORT_TARGET | 400 | VAL | 신고 대상이 올바르지 않습니다. |
| `SAF-VAL-004` | INVALID_REASON_CODE | 400 | VAL | 신고 사유 코드가 올바르지 않습니다. |
| `SAF-VAL-005` | INVALID_TIME_ORDER | 400 | VAL | 신고 시각 순서가 올바르지 않습니다. |
| `SAF-DOM-001` | SELF_BLOCK_NOT_ALLOWED | 400 | DOM | 자기 자신을 차단할 수 없습니다. |
| `SAF-DOM-002` | INVALID_REPORT_STATUS | 400 | DOM | 신고 종결 상태가 올바르지 않습니다. |
| `SAF-APP-001` | ACTIVE_BLOCK_NOT_FOUND | 404 | APP | 활성 차단을 찾을 수 없습니다. |
| `SAF-INFRA-001` | DUPLICATED_OPEN_REPORT | 409 | INFRA | 이미 접수된 신고가 있습니다. |

## 11. notification (NOT)

| 코드 | 이름 | HTTP | 분류 | 메시지 |
| --- | --- | --- | --- | --- |
| `NOT-VAL-001` | INVALID_ID | 400 | VAL | 알림 식별자가 올바르지 않습니다. |
| `NOT-VAL-002` | REQUIRED_VALUE_MISSING | 400 | VAL | 알림 필수 값이 없습니다. |
| `NOT-VAL-003` | INVALID_TEXT | 400 | VAL | 알림 문자열 값이 올바르지 않습니다. |
| `NOT-VAL-004` | INVALID_PAYLOAD | 400 | VAL | 이벤트 payload 형식이 올바르지 않습니다. |
| `NOT-VAL-005` | INVALID_VALUE_RANGE | 400 | VAL | 알림 값이 허용 범위를 벗어났습니다. |
| `NOT-DOM-001` | INVALID_NOTIFICATION_TARGET | 400 | DOM | 알림 대상이 올바르지 않습니다. |
| `NOT-DOM-002` | INVALID_NOTIFICATION_STATE | 400 | DOM | 알림 상태와 값이 맞지 않습니다. |
| `NOT-DOM-003` | INVALID_NOTIFICATION_STATUS | 409 | DOM | 현재 알림 상태로는 요청을 처리할 수 없습니다. |
| `NOT-INFRA-001` | DUPLICATED_EVENT | 409 | INFRA | 이미 처리된 알림입니다. |

## 12. auth (AUT)

| 코드 | 이름 | HTTP | 분류 | 메시지 |
| --- | --- | --- | --- | --- |
| `AUT-VAL-001` | INVALID_LOGIN_ID | 400 | VAL | 로그인 식별자가 올바르지 않습니다. |
| `AUT-VAL-002` | REQUIRED_VALUE_MISSING | 400 | VAL | 인증 필수 값이 없습니다. |
| `AUT-VAL-003` | INVALID_INSTALLATION_ID | 400 | VAL | 기기 식별자가 올바르지 않습니다. |
| `AUT-VAL-004` | INVALID_COUNTRY_CODE | 400 | VAL | 국가 코드가 올바르지 않습니다. |
| `AUT-DOM-001` | INVALID_CREDENTIAL_STATE | 400 | DOM | 자격증명 상태가 올바르지 않습니다. |
| `AUT-APP-001` | LOGIN_FAILED | 401 | APP | 로그인 정보가 올바르지 않습니다. |
| `AUT-APP-002` | CREDENTIAL_LOCKED | 423 | APP | 잠긴 계정입니다. 잠시 후 다시 시도해 주세요. |
| `AUT-APP-003` | ACCOUNT_NOT_ACTIVE | 403 | APP | 사용할 수 없는 계정입니다. |
| `AUT-APP-004` | CREDENTIAL_NOT_FOUND | 404 | APP | 자격증명을 찾을 수 없습니다. |
| `AUT-APP-005` | DEVICE_ALREADY_REGISTERED | 409 | APP | 이미 등록된 기기입니다. |
| `AUT-APP-006` | DEVICE_CREDENTIAL_INVALID | 401 | APP | 기기 자격증명이 유효하지 않습니다. |

`AUT-APP-001`은 존재하지 않는 `login_id`와 잘못된 비밀번호를 **구분하지 않는다**. 두 경우에
다른 코드나 다른 `reason`을 주면 계정 열거에 쓰인다. 같은 이유로 자격증명이 없을 때도 더미
해시로 비밀번호 검증을 한 번 수행해 응답 시간을 맞춘다.

`AUT-APP-004`는 로그인 경로에서 쓰지 않는다. 관리 경로에서만 노출한다.

`AUT-APP-005`는 `POST /api/v1/auth/devices`에서만 쓴다. `installation_id`로 ACTIVE
자격증명이 이미 있으면 재등록이 아니라 `POST /api/v1/auth/token`을 호출해야 한다.

`AUT-APP-006`은 `POST /api/v1/auth/token`에서 `device_secret` 불일치, `installationId`
교차 검증 실패, `credential_status != ACTIVE`를 모두 같은 코드로 응답한다. `device_secret`은
256bit 랜덤이라 무차별 대입이 불가능하므로 `AUT-APP-001`과 달리 원인별 응답 시간을
맞출 필요는 없지만, 클라이언트가 재등록해야 하는 상태라는 신호는 통일한다.

필터 단계에서 끝나는 인증·인가 실패는 controller에 닿지 않아 `GlobalExceptionHandler`를
거치지 않는다. `AuthEntryPoints`가 같은 형식으로 `CMN-VAL-003`(401)과 `CMN-DOM-001`(403)을
내보낸다.

## 13. feed (FED)

| 코드 | 이름 | HTTP | 분류 | 메시지 |
| --- | --- | --- | --- | --- |
| `FED-INFRA-001` | INVALID_TEXT | 500 | INFRA | 방향 칩 데이터를 생성하지 못했습니다. |
| `FED-INFRA-002` | INVALID_VALUE_RANGE | 500 | INFRA | 방향 칩 데이터를 생성하지 못했습니다. |

`feed`는 조회 전용 기능이라 `DirectionChip`의 `segmentKey`·`displayName`·`sortOrder`·`count`는
전부 SQL 조회 결과에서 채워지고 클라이언트 입력을 거치지 않는다(`JdbcInboxQueryRepository`).
그래서 이 값들의 불변식 위반은 요청을 고쳐도 해소되지 않는 `direction_segment` 데이터나 행
매핑의 결함이며, `QUE-INFRA-001`과 같은 이유로 INFRA/500으로 분류한다. 같은 이름
`INVALID_TEXT`·`INVALID_VALUE_RANGE`를 쓰는 `DIR-VAL-003`·`DIR-VAL-008`은 실제로 클라이언트
요청 값을 검증하므로 VAL/400이 맞고, 이 둘과는 분류가 다르다.

## 14. DB 제약 매핑

`DataIntegrityViolationException`의 원인 메시지에서 제약 이름을 찾아 기능 오류 코드로 옮긴다.
매핑은 `common/error/ConstraintExceptionMapper`에 있고, 목록에 없는 제약은
`CMN-DOM-003`으로 떨어진다.

| 제약 이름 | 오류 코드 | `field` |
| --- | --- | --- |
| `uq_direction_post_idempotency` | `DIR-INFRA-001` | `idempotencyKey` |
| `uq_post_recipient_post_user` | `DIR-INFRA-002` | `recipientId` |
| `uq_answer_idempotency` | `ANS-INFRA-001` | `idempotencyKey` |
| `uq_notification_recipient_dedup` | `NOT-INFRA-001` | `dedupKey` |
| `uq_outbox_event_dedup` | `NOT-INFRA-001` | `dedupKey` |
| `uq_question_assignment_cycle_question` | `QUE-INFRA-002` | `approvedQuestionId` |
| `uq_question_assignment_cycle_order` | `QUE-INFRA-002` | `approvedQuestionId` |
| `uq_open_report_user` | `SAF-INFRA-001` | — |
| `uq_open_report_post` | `SAF-INFRA-001` | — |
| `uq_open_report_answer` | `SAF-INFRA-001` | — |
| `uq_active_device_installation` | `AUT-APP-005` | `installationId` |

Flyway 마이그레이션에서 제약 이름을 바꾸면 이 매핑과 표를 함께 고친다.
