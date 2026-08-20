# Test Plan: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ

> Created at: `2026-08-20T14:42:25+09:00`
> GitHub Issue: `#176`
> Status: Approved

## 1. Objective

알림함 읽기 경로 5개를 노출하면서 다음 네 가지 사용자 가치를 지킨다.

1. **사용자는 자기 알림만 본다.** 목록·읽음·진입 세 경로 중 하나라도 남의 알림에
   닿으면 상대가 누구에게 무엇을 받았는지가 새어 나간다. 알림 자체가 익명 문구라도
   `notificationId`를 훑는 것만으로 타인의 수신 이력을 셀 수 있다.
2. **알림함은 기록이고 점은 상태다.** 두 기준선(`notification_seen_state.seen_at`과
   `notification.status`)이 섞이면 알림함을 한 번 여는 순간 어떤 줄이 새 것이었는지
   잃거나, 반대로 점이 영원히 사라지지 않는다.
3. **서버가 이동 가능 여부를 판정한다.** 클라이언트가 만료된 질문글로 이동해 빈 상세
   화면을 보는 일이 없어야 하고, 삭제된 대상에 차단 이유를 노출해 상대의 차단 사실이
   새면 안 된다.
4. **푸시 설정이 기록을 막지 않는다.** preference를 전부 꺼도 알림함은 채워져야 한다.
   현재 fan-out 워커는 이 둘을 한 게이트에 묶어 두었고, 이 이슈가 그것을 푼다 —
   푸는 과정에서 반대로 꺼진 사용자에게 푸시가 나가면 더 큰 사고다.

실패 시 위험은 개인정보 노출(1·3)과 알림 신뢰 상실(2·4)이다. 1과 3은 되돌릴 수 없다.

## 2. Scope

### Included

- `notification.web` 경로 5개의 HTTP 계약과 오류 코드 매핑
- `notification.service.NotificationInboxService`의 인가·검증·시각 처리
- `notification.repository`의 cursor 페이징, 대상 상태 판정 SQL, `seen_at` upsert
- `notification.view` 불변식
- `V23__add_notification_inbox_read_state.sql` 적용과
  `notification_recipient_feed_idx` 사용 여부
- `account.service.AccountEligibilityGate` 승격 후 `FED-APP-001`·`FED-APP-002` 불변
- `RecipientNotificationFanOutWorker`의 preference 게이트 재배치와 그로 인해 계약이
  바뀌는 기존 테스트 3종
- `docs/api/openapi.json` 재생성 결과

### Excluded

- 푸시 발송, 토큰 등록, provider 연동 — `#179`. 이 계획은 `notification_delivery`
  **행이 생기는지**만 보고 실제 전송은 보지 않는다
- 알림 설정 조회·변경 API — `#178`. preference는 fan-out 재배치 시나리오에서
  fixture로만 쓰고 읽기 API는 만들지 않는다
- `DIRECTION_POST_RECEIVED` 외 5종의 fan-out — `#177`. 다른 `notification_type`은
  fixture로 직접 INSERT해서 목록 정렬·필터만 검증한다
- 묶음 표시, 카드 배지, 탭 카운터 — `#180`, `#181`
- 워커 주기 실행 — `#182`. 워커는 테스트에서 직접 호출한다
- `notification.report_id`와 `REPORT` 대상 — `#155`
- 알림 보존 기간 정책 — 설계 §12-4에서 `UNKNOWN`

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue `#176` | 엔드포인트 5개 동작, 남의 알림 404, `REVOKED`·`DISMISSED` 제외, `seen_at` 역행 없음, `targetState` 우선순위, preference off에서 `notification` 1건·`delivery` 0건, `FED-APP-*` 불변, 응답에 본문·닉네임·계정 식별자·위치 없음 |
| `TASK.md` | 확정 결정 12개. 특히 결정 3(게이트 supplier 형태), 결정 11(`unreadCount` 정확한 수), 결정 12(목록에도 `BLOCKED`) |
| `NOTIFICATION_INBOX_DESIGN.md` §5 | 읽음 모델 두 계층. `seen_at`은 `GREATEST`로만 전진 |
| 〃 §7.2 | 판정 우선순위 `GONE > BLOCKED > HIDDEN > EXPIRED > AVAILABLE` |
| 〃 §8.2 | `limit` 기본 20·상한 50, `nextCursor`는 반환 건수 == `limit`일 때만, `expiresAt`은 `DIRECTION_POST` + `AVAILABLE`에서만 |
| 〃 §8.5 | `NOT-APP-001`(404), `NOT-APP-002`(403), `NOT-DOM-004`(404), `NOT-DOM-003`(409), `NOT-VAL-006`(400), `NOT-VAL-007`(400) |
| 〃 §9 | `notification_seen_state` PK `user_id`, `notification_recipient_feed_idx`는 `status IN ('UNREAD','READ')` 부분 인덱스 |
| 〃 §10.2 | 게이트 재배치 표. 차단은 알림 행 자체를 막고 preference는 delivery만 막는다 |
| 〃 §15.3 S1 | `requireActiveUser(long, Supplier, Supplier)` |
| `V1` 스키마 | `ck_notification_status`가 `UNREAD, READ, DISMISSED, REVOKED`, `ck_notification_read_at`이 `read_at >= created_at AND status IN ('READ','DISMISSED')` |
| `V1`/`V8` 스키마 | `answer.status`에 `HIDDEN` 존재, `user_block`의 활성 조건은 `released_at IS NULL` |
| `AGENTS.md` §3 | `@DisplayName` 필수, 클래스 헤더에 ISO 8601 생성 시각과 `Source scenario` |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| R1 남의 알림에 목록·읽음·진입 중 한 경로로 닿는다 | 타인 수신 이력 노출. 되돌릴 수 없다 | 중 | P0 | 세 경로 각각에 대한 타인 계정 접근 시나리오와 부작용 부재 확인 |
| R2 `REVOKED`·`DISMISSED` 줄이 목록·카운트에 실린다 | 운영이 회수한 기록이 되살아난다 | 중 | P0 | 목록·`unreadCount` 양쪽에서 제외 확인 |
| R3 삭제된 대상에 `BLOCKED`가 잡혀 차단 사실이 샌다 | 상대의 차단 여부 노출 | 중 | P0 | 삭제+차단 동시 상황에서 `GONE` 확인 |
| R4 응답에 본문·닉네임·계정 식별자·좌표가 실린다 | 익명 전제 붕괴 | 중 | P0 | 응답 record component 목록과 실제 JSON 양쪽 확인 |
| R5 preference 재배치가 반대로 뒤집혀 꺼둔 사용자에게 delivery가 생긴다 | 사용자가 끈 푸시가 나간다 | 중 | P0 | preference on/off 각각에서 `notification`과 `notification_delivery` 건수 |
| R6 재배치 과정에서 차단 게이트까지 delivery 쪽으로 밀린다 | 차단한 상대의 알림이 알림함에 남는다 | 중 | P0 | 차단 상황에서 `notification` 0건 |
| R7 게이트 승격이 `FED-APP-001`·`FED-APP-002`를 바꾼다 | `#170` API의 기존 계약 파손 | 중 | P0 | 두 코드의 HTTP 상태·코드 문자열 회귀 고정 |
| R8 cursor 페이징이 같은 `created_at` 다건에서 줄을 흘리거나 중복시킨다 | 알림이 조용히 사라진다 | 상 | P0 | 동일 시각 다건 3페이지 순회에서 집합 동일성 |
| R9 `seen_at`이 과거로 되돌아간다 | 읽은 알림의 점이 되살아난다 | 중 | P0 | 반복·역순·동시 호출 후 기준선 불변 |
| R10 읽음 처리가 멱등하지 않다 | `read_at`이 톱할 때마다 갱신돼 기록이 흔들린다 | 중 | P1 | 반복 호출 후 `read_at` 불변 |
| R11 만료된 줄에 `expiresAt`이 실려 살아 있는 줄로 오해된다 | 빈 상세 화면 진입 | 중 | P1 | view 불변식과 목록 응답 확인 |
| R12 목록 쿼리가 `notification_recipient_feed_idx`를 쓰지 않는다 | 알림이 쌓일수록 목록이 느려진다 | 중 | P1 | `EXPLAIN` 결과에 인덱스 이름 |
| R13 `limit`·cursor 검증이 controller와 service로 갈라진다 | `#177` 이후 진입점이 늘면 검증이 새 경로에서만 빠진다 | 중 | P1 | service 계층 단독 호출에서 검증 동작 |
| R14 `hasUnseen`과 `unreadCount`의 기준이 같아진다 | 알림함을 열어도 점이 안 사라지거나, 카운터가 0이 된다 | 중 | P1 | 두 값이 갈리는 상태를 만들어 동시 확인 |
| R15 `V23` 부분 인덱스의 술어와 쿼리 술어가 어긋난다 | 인덱스가 있어도 선택되지 않는다 | 중 | P1 | R12와 같은 증거로 확인 |

## 5. Unit scenarios

`src/test`. 외부 의존은 Mockito mock 또는 고정 `Clock`을 쓰고 데이터베이스를 띄우지
않는다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| …-UNIT-001 | `kind = ANSWER` 또는 `state != AVAILABLE` | `NotificationCard`를 `expiresAt`과 함께 만든다 | 거부한다. `expiresAt`은 `DIRECTION_POST` + `AVAILABLE`에서만 허용된다 | P1 | E2 |
| …-UNIT-002 | `state`의 5개 값 각각 | `NotificationTargetDecision`을 만든다 | `navigable == (state == AVAILABLE)`이고 `reason`은 그 반대일 때만 채워진다. 둘이 어긋나면 거부한다 | P1 | E2 |
| …-UNIT-003 | `kind = NONE` | 대상 `id`를 함께 준다 | 거부한다. `NONE`이면 `id`는 `null`이다 | P1 | E2 |
| …-UNIT-004 | 게이트가 계정 없음으로 실패 | `list`·`unreadSignal`·`markSeen`·`markRead`·`target`을 각각 호출 | 전부 `NOT-APP-001`. repository는 호출되지 않는다 | P0 | E2 |
| …-UNIT-005 | 게이트가 `USER` 아님 또는 `ACTIVE` 아님으로 실패 | 위 5개 호출 | 전부 `NOT-APP-002` | P0 | E2 |
| …-UNIT-006 | `limit`이 `null`, `0`, `-1`, `50`, `51` | `list` 호출 | `null`은 20으로 채우고, `50`은 통과, `0`·`-1`·`51`은 `NOT-VAL-006` | P1 | E2 |
| …-UNIT-007 | cursor 두 값 중 한쪽만 지정 | `list` 호출 | `NOT-VAL-007`. 둘 다 생략은 첫 페이지, 둘 다 지정은 이어보기로 repository에 그대로 전달 | P1 | E2 |
| …-UNIT-008 | repository가 `limit`과 같은 건수 / 그보다 적은 건수를 반환 | `list` 호출 | 같으면 마지막 줄의 `(createdAt, id)`로 `nextCursor`를 채우고, 적으면 `null` | P1 | E2 |
| …-UNIT-009 | 고정 `Clock` | `list`와 `markSeen` 호출 | 판정과 저장에 `Clock`에서 한 번 읽은 같은 `at`을 넘긴다. `Instant.now()`를 직접 부르지 않는다 | P1 | E2 |
| …-UNIT-010 | 이미 `READ`인 알림 | `markRead`를 두 번 호출 | 상태와 `read_at`을 바꾸지 않고 현재 값을 반환한다 | P1 | E2 |
| …-UNIT-011 | `REVOKED` 알림 | `markRead` 호출 | `NOT-DOM-003`(409) | P0 | E2 |
| …-UNIT-012 | repository가 빈 결과 반환(없는 알림 또는 남의 알림) | `markRead`·`target` 호출 | 둘 다 `NOT-DOM-004`(404). 두 경우의 응답이 구별되지 않는다 | P0 | E2 |
| …-UNIT-013 | 임의의 `at` | `markSeen` 호출 | repository의 `GREATEST` upsert에 위임하고, 응답의 `seenAt`은 upsert가 돌려준 값이지 요청한 `at`이 아니다 | P0 | E2 |
| …-UNIT-014 | `ACTIVE`인 `USER` / 없는 계정 / `OPERATOR` / `SUSPENDED` | `AccountEligibilityGate.requireActiveUser(id, notFound, notEligible)` 호출 | 첫 경우 `Account`를 반환하고, 나머지는 각각 `notFound`·`notEligible` supplier가 만든 예외를 그대로 던진다. `account`는 어떤 오류 코드도 알지 못한다 | P0 | E1 |
| …-UNIT-015 | 승격된 게이트를 쓰는 `feed` 어댑터 | `require(accountId)` 호출 | 계정 없음은 `FED-APP-001`(404), 자격 없음은 `FED-APP-002`(403). 코드 문자열과 HTTP 상태를 리터럴로 고정한다 | P0 | E1 |
| …-UNIT-016 | — | `NotificationApiSpec`과 `NotificationController` 반사 검사 | 둘이 분리돼 있고 `@RestController` + `@RequestMapping("/api/v1/notifications")`이며, 경로 5개가 `GET` 3·`PUT` 2로 선언되고 `limit` 기본값이 20이다 | P1 | E3 |
| …-UNIT-017 | 응답 record 5종 | record component 이름과 타입을 훑는다 | 본문(`bodyText`), 닉네임, 발신자·작성자 계정 식별자, 좌표·거리·지역 코드에 해당하는 component가 하나도 없다 | P0 | E3 |
| …-UNIT-018 | standalone MockMvc + mock service | 경로 5개를 호출하고 service가 각 오류를 던지게 한다 | 성공 응답의 JSON 필드와 `NOT-APP-001`→404, `NOT-APP-002`→403, `NOT-DOM-004`→404, `NOT-DOM-003`→409, `NOT-VAL-006`·`NOT-VAL-007`→400 매핑을 확인한다 | P0 | E3 |
| …-UNIT-019 | preference off인 수신자 | fan-out 워커 배치 실행 | `notification` 저장 1회, `notification_delivery` 저장 0회 | P0 | E5 |
| …-UNIT-020 | preference on이고 활성 기기 2대 | 같은 실행 | `notification` 1회, `delivery` 2회 | P0 | E5 |
| …-UNIT-021 | 발신자와 수신자 사이 활성 차단 | 같은 실행 | preference 값과 무관하게 `notification` 저장 0회. 차단은 delivery가 아니라 기록 자체를 막는다 | P0 | E5 |

## 6. Integration scenarios

`src/integrationTest`. `PostgisContainerIntegrationTestSupport`를 상속해 실제
PostgreSQL에 붙고, `#170`의 `Feed170MutableClock` 패턴대로 주입 가능한 가변 `Clock`을
쓴다. 저장소 관례에 따라 HTTP가 아니라 application service를 직접 호출한다 —
HTTP 계약은 UNIT-016~018이 담당한다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| …-INT-001 | service + repository + DB | 알림 5건, `limit = 2` | cursor로 3페이지 순회 | 5건이 중복·누락 없이 나오고 3페이지의 `nextCursor`는 `null` | `@BeforeEach` 전체 DELETE |
| …-INT-002 | 〃 | `created_at`이 완전히 같은 알림 4건 | `limit = 2`로 2페이지 순회 | `(created_at, id)` 튜플 비교로 4건이 정확히 한 번씩 나온다. `created_at`만 비교하면 실패하는 배치 | 〃 |
| …-INT-003 | 〃 | `UNREAD` 2, `READ` 1, `DISMISSED` 1, `REVOKED` 1 | 목록 조회 | `UNREAD`·`READ` 3건만 나온다 | 〃 |
| …-INT-004 | 〃 | 뷰어와 타인에게 각각 알림 2건 | 뷰어로 목록 조회 | 자기 2건만. 타인의 `notificationId`가 응답에 없다 | 〃 |
| …-INT-005 | 〃 | 알림 정확히 2건, `limit = 2` | 목록 조회 | `nextCursor`가 채워진다. 이어서 호출하면 0건과 `null` | 〃 |
| …-INT-006 | 〃 | 알림 60건 | `limit = 50` 조회 | 50건. `limit = 51`은 `NOT-VAL-006` | 〃 |
| …-INT-007 | 〃 + `direction_post` | `expires_at`이 현재 `Clock`보다 과거인 질문글 알림 | 목록 조회 | `state = EXPIRED`이고 `expiresAt`은 `null` | 〃 |
| …-INT-008 | 〃 | `direction_post.deleted_at`이 채워진 알림 | 목록 조회 | `state = GONE` | 〃 |
| …-INT-009 | 〃 + `answer` | `answer.status = 'HIDDEN'`인 답변 알림, 그리고 미공개(`published_at IS NULL`) 답변 알림 | 목록 조회 | 둘 다 `state = HIDDEN` | 〃 |
| …-INT-010 | 〃 + `user_block` | 뷰어→상대 차단, 상대→뷰어 차단을 각각 따로 | 목록 조회 | 두 방향 모두 `state = BLOCKED`. `released_at`이 채워진 해제된 차단은 `AVAILABLE` | 〃 |
| …-INT-011 | 〃 | 질문글이 삭제됐고 **동시에** 차단 관계도 있다 | 목록 조회 | `state = GONE`. `BLOCKED`가 아니다 — 삭제된 대상에 차단 이유를 노출하면 상대의 차단 사실이 샌다 | 〃 |
| …-INT-012 | 〃 | 질문글이 만료됐고 **동시에** 차단 관계도 있다 | 목록 조회 | `state = BLOCKED`. `EXPIRED`가 아니다 | 〃 |
| …-INT-013 | service + `notification_seen_state` | `seen_at`보다 이전의 `UNREAD` 2건, 이후의 `UNREAD` 1건 | `unread-count` 조회 | `hasUnseen = true`, `unreadCount = 3`. 두 값의 기준이 다르다 | 〃 |
| …-INT-014 | 〃 | `seen_at`이 없고 `UNREAD` 1건 | 〃 | `hasUnseen = true`, `seenAt = null` | 〃 |
| …-INT-015 | 〃 | `UNREAD` 1, `REVOKED` 1, `DISMISSED` 1 | 〃 | `unreadCount = 1` | 〃 |
| …-INT-016 | 〃 | `seen_at` 없음 | 같은 `at`으로 `markSeen` 3회 | `seen_at`이 3회 후에도 첫 값과 같다 | 〃 |
| …-INT-017 | 〃 | `seen_at = T` | `T - 1시간`으로 `markSeen` | `seen_at`이 여전히 `T`다. 응답의 `seenAt`도 `T`다 | 〃 |
| …-INT-018 | 〃 (동시성) | `seen_at` 없음 | 서로 다른 두 시각으로 `markSeen`을 동시 호출 | 두 호출 모두 성공하고 최종 `seen_at`은 더 늦은 시각이다. 행은 1개다(PK 중복 없음) | 〃 |
| …-INT-019 | service + `notification` | `UNREAD` 알림 | `markRead` 2회 | 첫 호출에서 `READ`와 `read_at`이 정해지고 두 번째 호출이 `read_at`을 바꾸지 않는다 | 〃 |
| …-INT-020 | 〃 | `REVOKED` 알림 | `markRead` | `NOT-DOM-003`. 행의 `status`는 `REVOKED` 그대로 | 〃 |
| …-INT-021 | 〃 | 타인 소유의 `UNREAD` 알림 | 뷰어로 `markRead` | `NOT-DOM-004`. **그 행의 `status`와 `read_at`이 그대로다** — 거부 전에 부작용이 없다 | 〃 |
| …-INT-022 | service + DB (`Clock` 이동) | 목록 조회 시 `AVAILABLE`이던 질문글 알림 | `Clock`을 만료 이후로 옮기고 `target` 조회 | `navigable = false`, `reason = EXPIRED`, `state = EXPIRED`. 목록의 판정을 그대로 믿지 않는다 | 〃 |
| …-INT-023 | 〃 | 살아 있는 알림 / 삭제·차단된 알림 / 만료된 수신 질문글 알림 | 각각 `target` 조회 | `fallback`이 `NONE` / `FEED_HOME` / `INBOX` | 〃 |
| …-INT-024 | repository + DB | 알림 200건 | 목록 쿼리를 `EXPLAIN`으로 실행 | 계획에 `notification_recipient_feed_idx`가 나타난다. 나타나지 않으면 이 시나리오는 실패이고 설계 §11-1의 대안 전환을 검토한다 | 〃 |
| …-INT-025 | service + DB | 본문·닉네임·좌표가 채워진 질문글과 답변의 알림 | 목록·`target` 조회 후 응답을 JSON으로 직렬화 | 직렬화 문자열에 질문 본문, 답변 본문, 닉네임, 발신자·작성자 계정 식별자, 좌표·거리 값이 하나도 없다 | 〃 |
| …-INT-026 | 〃 | 6종 `notification_type`을 fixture로 직접 INSERT | 목록 조회 | 6종 모두 정렬·필터에서 동일하게 처리되고 `type` 문자열이 그대로 실린다. 대상 없는 종류는 `kind = NONE` | 〃 |
| …-INT-027 | fan-out 워커 + DB | preference off인 수신자와 활성 기기 1대 | 워커 배치 직접 실행 | `notification` 1건, `notification_delivery` 0건. **기존 계약 변경** | 〃 |
| …-INT-028 | 〃 | preference on, 활성 기기 2대 | 〃 | `notification` 1건, `delivery` 2건 | 〃 |
| …-INT-029 | 〃 | 활성 차단 + preference on | 〃 | `notification` 0건, `delivery` 0건 | 〃 |
| …-INT-030 | `feed` application service + DB | 없는 계정 / `OPERATOR` / `SUSPENDED` 계정 | `#170`·`#124`의 수신함·발송함 경로 호출 | `FED-APP-001`(404)과 `FED-APP-002`(403)의 코드 문자열과 HTTP 상태가 게이트 승격 전과 같다 | 〃 |
| …-INT-031 | `notification` service + DB | 위와 같은 3종 계정 | 알림함 5개 경로 호출 | `NOT-APP-001`(404)과 `NOT-APP-002`(403). 같은 게이트가 모듈별로 다른 코드로 번역된다 | 〃 |
| …-INT-032 | springdoc | S7까지 구현 완료 | `OpenApiSpecificationIntegrationTest` 실행 | `docs/api/openapi.json`에 `/api/v1/notifications` 계열 경로 5개와 operation 5개(`GET` 3, `PUT` 2)가 나타난다 | — |

## 7. Cross-cutting scenarios

### Database and transactions

- `markSeen`은 단일 `INSERT ... ON CONFLICT DO UPDATE SET seen_at = GREATEST(...)`
  문장 하나로 끝난다. 읽고-비교하고-쓰는 세 단계로 나누면 INT-018이 잡는 경합이
  생긴다. 구현이 단일 문장인지 SQL 상수로 확인한다.
- `markRead`는 조회와 상태 전이가 같은 트랜잭션이어야 INT-021의 "거부 전 부작용
  없음"이 성립한다.
- `notification_seen_state`의 FK는 `ON DELETE CASCADE`다. 계정 삭제 시 기준선이 함께
  사라지는 것이 의도인지 INT 시나리오에서는 다루지 않되, `V23` 적용 후
  `FlywayMigrationIntegrationTest`가 통과하는지 확인한다.
- 목록 쿼리의 상태 술어(`status IN ('UNREAD','READ')`)와 부분 인덱스의 술어가
  문자열 수준에서 같아야 한다(R15). INT-024가 이 어긋남을 잡는다.
- fan-out의 `notification`과 `notification_delivery` 저장은 같은 트랜잭션에 있다.
  preference 게이트를 옮겨도 이 경계는 그대로여야 하며, INT-027이 부분 반영
  (알림만 있고 롤백된 delivery가 남는 상태)이 아님을 건수로 확인한다.

### Concurrency and idempotency

- INT-018 — `markSeen` 동시 호출. `notification_seen_state`의 PK가 `user_id`
  하나이므로 두 스레드가 같은 행을 노린다. 별도 concurrency 테스트 파일로 둔다.
- INT-019 — `markRead` 반복. 같은 알림에 대한 동시 `markRead` 2회도 함께 넣어
  `read_at`이 하나로 정해지는지 본다.
- 목록 조회 도중 새 알림이 도착하는 경합은 설계 §11-5가 정상 동작으로 규정했다.
  시나리오로 만들지 않고 계획에만 기록한다.

### External APIs

- 없다. 푸시 provider 호출은 `#179` 범위이고, 이 이슈의 fan-out 시나리오는
  `notification_delivery` 행 생성까지만 본다. 외부 API double이 필요 없다.

### Failure recovery and reconciliation

- fan-out 워커의 lease·재시도·stale 처리는 기존
  `RecipientNotificationFanOutWorkerConcurrencyIntegrationTest`와 `OutboxLease
  IntegrationTest`가 이미 덮는다. preference 게이트 이동이 그 경로를 건드리지
  않는지 확인하기 위해 두 테스트를 **수정 없이 재실행**하는 것을 검증에 포함한다.
- `V23`은 테이블·인덱스 추가만 하므로 롤백 시나리오가 없다. 되돌리려면 `DROP`이고
  데이터 손실은 읽음 기준선뿐이다 — 다음 열람에서 복구된다. 이 사실을 테스트
  보고서에 기록한다.

## 8. Test data and isolation

- **Fixtures**: `Notification176IntegrationFixtures`(신규, `src/integrationTest`).
  `#170`의 `Feed170IntegrationFixtures`를 본떠 `JdbcTemplate`으로 계정·질문글·
  수신 항목·답변·차단·알림·preference·기기를 만든다. 통합 테스트 3개 파일이
  공유하므로 별도 파일로 둔다 — `Feed170`처럼 테스트 파일 하단에 두면 공유할 수 없다.
- **Database isolation**: `PostgisContainerIntegrationTestSupport`. 각 테스트의
  `@BeforeEach`가 `notification_delivery` → `notification` →
  `notification_seen_state` → `notification_preference` → `push_device` →
  `answer` → `post_recipient` → `direction_post` → `user_block` → `user_account`
  순서로 DELETE한다(FK 역순).
- **Region 코드**: 다른 통합 테스트와 겹치지 않도록 `TEST-GH176-*` 접두사를 쓴다.
- **Clock/randomness**: `Notification176MutableClock`(주입 가능한 가변 `Clock`)과
  `Notification176TestClockConfiguration`. 기준 시각은 `2026-08-20T06:00:00Z`
  고정. INT-022가 시각을 앞으로 옮긴다. 단위 테스트는 `Clock.fixed`.
- **동일 `created_at` 만들기**: INT-002는 `clock_timestamp()` 기본값에 의존하면
  값이 갈리므로 fixture가 `created_at`을 명시적으로 같은 값으로 INSERT한다.
- **External API doubles**: 없다.
- **Cleanup**: 각 테스트가 자기 `@BeforeEach`에서 정리한다. 클래스 간 상태를
  넘기지 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다. 계정 식별자는 fixture가 만든 값을
변수로만 다루고 계획·보고서·`@DisplayName`에 리터럴로 적지 않는다.

## 9. Execution contracts

시나리오 ID와 소유 파일이 겹치지 않는다. `Order`는 구현 순서
(`NOTIFICATION_INBOX_DESIGN.md` §15.2의 S1~S9)와 대응한다.

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | E1 (게이트 승격) | `src/test/.../account/service/AccountEligibilityGateTest.java` (신규)<br>`src/test/.../feed/service/InboxApplicationServiceTest.java` (조립 갱신)<br>`src/test/.../feed/service/FeedInteractionApplicationServiceTest.java` (조립 갱신)<br>`src/integrationTest/.../AccountEligibilityGateIntegrationTest.java` (신규) | UNIT-014, UNIT-015, INT-030 | `./gradlew test --tests "com.dnd.qello.account.*" --tests "com.dnd.qello.feed.*" --console=plain`<br>`./gradlew integrationTest --tests "com.dnd.qello.AccountEligibilityGateIntegrationTest" --console=plain` |
| 2 | E2 (view + service 단위) | `src/test/.../notification/view/NotificationCardTest.java`<br>`src/test/.../notification/view/NotificationTargetDecisionTest.java`<br>`src/test/.../notification/service/NotificationInboxServiceTest.java` | UNIT-001 ~ UNIT-013 | `./gradlew test --tests "com.dnd.qello.notification.view.*" --tests "com.dnd.qello.notification.service.*" --console=plain` |
| 3 | E3 (web 단위) | `src/test/.../notification/web/NotificationWebContractTest.java`<br>`src/test/.../notification/web/NotificationApiMockMvcTest.java` | UNIT-016 ~ UNIT-018 | `./gradlew test --tests "com.dnd.qello.notification.web.*" --console=plain` |
| 4 | E4 (알림함 통합) | `src/integrationTest/.../Notification176IntegrationFixtures.java` (신규, 공유 fixture)<br>`src/integrationTest/.../NotificationInboxQueryIntegrationTest.java`<br>`src/integrationTest/.../NotificationInboxCommandIntegrationTest.java`<br>`src/integrationTest/.../NotificationInboxConcurrencyIntegrationTest.java` | INT-001 ~ INT-026, INT-031 | `./gradlew integrationTest --tests "com.dnd.qello.NotificationInbox*" --console=plain` |
| 5 | E5 (fan-out 재배치) | `src/test/.../notification/fanout/RecipientNotificationFanOutWorkerTest.java` (계약 변경)<br>`src/integrationTest/.../NotificationFanOutPersistenceIntegrationTest.java` (계약 변경)<br>`src/integrationTest/.../RecipientNotificationFanOutWorkerIntegrationTest.java` (계약 변경) | UNIT-019 ~ UNIT-021, INT-027 ~ INT-029 | `./gradlew test --tests "*RecipientNotificationFanOutWorkerTest" --console=plain`<br>`./gradlew integrationTest --tests "com.dnd.qello.NotificationFanOut*" --tests "com.dnd.qello.RecipientNotificationFanOutWorker*" --console=plain` |
| 6 | E6 (스펙) | `src/integrationTest/.../OpenApiSpecificationIntegrationTest.java`<br>`docs/api/openapi.json` (생성 산출물) | INT-032 | `./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --console=plain` |

**소유권 경계**

- `Notification176IntegrationFixtures`는 E4만 만들고 고친다. E5는 기존 테스트의
  자체 fixture를 그대로 쓰고 이 파일을 건드리지 않는다.
- E5가 손대는 3개 파일은 **기존 계약이 바뀌는 유일한 파일**이다. 다른 실행자가
  이 파일에 시나리오를 추가하지 않는다.
- `RecipientNotificationFanOutWorkerConcurrencyIntegrationTest`와
  `OutboxLeaseIntegrationTest`는 **누구도 수정하지 않고** 재실행만 한다. 수정이
  필요하다고 판단되면 그 자체가 회귀 신호이므로 보고한다.
- E2·E3은 서로 독립이고 E1 완료 후 병렬로 진행할 수 있다. E4는 E2가 정의한 view
  타입에 의존하므로 E2 뒤에 온다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현 (UNIT-004, 005, 011, 012, 013, 014, 015, 017, 018,
      019, 020, 021 / INT-001 ~ 006, 010 ~ 012, 016 ~ 018, 021, 025, 027 ~ 032)
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] INT-024의 `EXPLAIN` 결과를 보고서에 기록. 인덱스가 선택되지 않으면 설계
      §11-1의 대안 전환 여부를 사람에게 올린다
- [ ] `RecipientNotificationFanOutWorkerConcurrencyIntegrationTest`와
      `OutboxLeaseIntegrationTest`를 수정 없이 재실행해 통과
- [ ] 잠재 문제 분석 (애플리케이션·인프라·DB·동시성·트랜잭션·외부 API·장애 복구)
- [ ] 테스트 보고서 생성 (`templates/test-report.md`)
- [ ] `./harness check`와 `./harness pr-ready --project-tests` 통과

## 11. Human approval

- Reviewer: `Byuntil`
- Decision: Approved
- Approved at: `2026-08-20T14:47:35+09:00`
