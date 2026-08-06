# 오류 코드와 예외 사용 규칙

이 문서는 API 오류 응답의 계약이다. 결정 배경은
`docs/adr/0003-global-exception-handling.md`에 있다.

## 1. 응답 형식

상태 코드와 무관하게 모든 오류는 같은 본문으로 나간다.

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
| `QUE` | `question` |
| `DIR` | `direction` |
| `ANS` | `answer` |
| `SAF` | `safety` |
| `NOT` | `notification` |

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

## 6. account (ACC)

| 코드 | 이름 | HTTP | 분류 | 메시지 |
| --- | --- | --- | --- | --- |
| `ACC-VAL-001` | INVALID_ID | 400 | VAL | 계정 식별자가 올바르지 않습니다. |
| `ACC-VAL-002` | REQUIRED_VALUE_MISSING | 400 | VAL | 계정 필수 값이 없습니다. |
| `ACC-VAL-003` | TEXT_TOO_LONG | 400 | VAL | 계정 값이 허용 길이를 초과했습니다. |
| `ACC-VAL-004` | INVALID_TIMEZONE | 400 | VAL | timezone은 유효한 IANA 지역 ID여야 합니다. |
| `ACC-DOM-001` | INVALID_AUDIT_TIMESTAMPS | 400 | DOM | 계정 생성·수정 시각이 올바르지 않습니다. |
| `ACC-DOM-002` | INVALID_DELETION_STATE | 400 | DOM | 탈퇴 상태와 탈퇴 시각이 일치하지 않습니다. |

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
| `ANS-DOM-001` | INVALID_ANSWER_STATE | 400 | DOM | 답변 상태와 값이 맞지 않습니다. |
| `ANS-DOM-002` | INVALID_ANSWER_STATUS | 409 | DOM | 현재 답변 상태로는 요청을 처리할 수 없습니다. |
| `ANS-DOM-003` | SAFETY_CHECK_NOT_PASSED | 409 | DOM | 안전 검사를 통과한 답변만 공개할 수 있습니다. |
| `ANS-DOM-004` | INELIGIBLE_REACTOR | 403 | DOM | 답변에 공감할 수 있는 질문 작성자가 아닙니다. |
| `ANS-DOM-005` | INVALID_MEDIA_STATE | 400 | DOM | 미디어 상태와 값이 맞지 않습니다. |
| `ANS-DOM-006` | INVALID_MEDIA_STATUS | 409 | DOM | 현재 미디어 상태로는 요청을 처리할 수 없습니다. |
| `ANS-DOM-007` | MEDIA_NOT_FOUND | 404 | DOM | 미디어를 찾을 수 없습니다. |
| `ANS-DOM-008` | MEDIA_OWNER_MISMATCH | 403 | DOM | 본인 소유의 미디어만 사용할 수 있습니다. |
| `ANS-DOM-009` | MEDIA_CONTENT_REQUIRED | 409 | DOM | 공개된 콘텐츠는 본문 또는 미디어가 있어야 합니다. |
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

## 12. DB 제약 매핑

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

Flyway 마이그레이션에서 제약 이름을 바꾸면 이 매핑과 표를 함께 고친다.
