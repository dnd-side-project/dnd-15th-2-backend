# Test Plan: TEST-PLAN-GH-124-INBOX-READ-SKIP-API

> Created at: `2026-08-16T14:09:58+09:00`
> GitHub Issue: `#124`
> Status: APPROVED_FOR_EXECUTION

## 1. Objective

인증된 수신자가 자신의 방향 질문글만 목록·상세로 열람하고, 넘김 요청과
되돌리기를 서버 시각·유예 설정·수신 슬롯 불변식에 맞게 수행할 수 있음을
검증한다.

특히 기존 `InboxQueryService`의 조회 자격과 `PostRecipientService`의 상태 전이를
HTTP 경계에서 단순 순차 호출할 때 생길 수 있는 권한 확인/쓰기 사이의 경쟁과
generic `save()`의 stale write를 차단한다. 존재하지 않는 항목과 자격 없는 항목은
동일하게 숨기고, 차단·만료·답변·넘김 확정과 경합해도 terminal 상태나 슬롯
카운터가 되돌아가지 않는다는 증거를 남긴다.

## 2. Scope

### Included

- `GET /api/v1/direction/inbox`의 `category` 및 선택적
  `directionSegmentKey` 목록·방향 칩 계약.
- `GET /api/v1/direction/inbox/{postRecipientId}`의 상세 자격 확인과
  `AVAILABLE`/`DISCOVERED → OPENED` 최초 열람 전이.
- `PUT /api/v1/direction/inbox/{postRecipientId}/skip`의 멱등 넘김 요청,
  `SKIP_PENDING`, 최초 `skipRequestedAt`, 서버 계산 `revertibleUntil` 응답.
- `DELETE /api/v1/direction/inbox/{postRecipientId}/skip`의 유예 내 되돌리기와
  이전 `AVAILABLE`/`DISCOVERED`/`OPENED` 상태 복원.
- ACTIVE USER 계정, JWT subject 소유권, 질문글 ACTIVE·미삭제, 상태·만료,
  양방향 active/released block 스코프.
- `ANSWERED`, `SKIP_PENDING`, `SKIPPED`, `EXPIRED`, `BLOCKED`의 목록·상세·명령
  권한 매트릭스.
- 행 잠금 또는 이전 상태 조건부 갱신을 이용한 열람·넘김·되돌리기 원자성.
- JUnit 5 단위·MockMvc 및 실제 PostgreSQL/PostGIS 통합·동시성 검증.
- OpenAPI 문서와 응답 privacy 계약.

### Excluded

- 답변 제출·공개 endpoint, 답변 moderation과 답변 멱등성(`#125`).
- 만료·넘김 확정 sweep, 슬롯 해제 실행기와 처리량·메트릭(`#126`).
- `SKIP_CONFIRMATION_DUE` Outbox 생산·취소·소비. #124의 넘김 요청은
  `post_recipient` 상태·시각만 변경하며 후속 #126은 기존
  `findConfirmableSkips` batch 조회를 사용한다.
- 방향 칩 집계 알고리즘, direction scheme 또는 거리 표시 정책 변경.
- Notification/Delivery, FCM/APNs 및 외부 API 호출.
- Flyway migration, 운영 데이터 백필, 인프라·배포·프로덕션 변경.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #124 | 수신 자격 없는 사용자는 질문글을 조회할 수 없다. |
| GitHub Issue #124 | `SKIP_PENDING`은 유예 중 슬롯을 유지한다. |
| GitHub Issue #124 | 확정된 `SKIPPED`와 미답변 `EXPIRED`는 다시 열리지 않는다. |
| GitHub Issue #124 | 차단 관계는 목록과 상세에 일관되게 적용된다. |
| Issue #120/#123 | 수신 자격은 원자적으로 확정되며 Notification/Push 상태가 수신함 권한을 대체하지 않는다. |
| Issue #126 | 유예가 지난 `SKIP_PENDING`의 `SKIPPED` 확정과 슬롯 해제는 후속 sweep이 소유한다. |
| `DIRECTION_COMMUNICATION_ERD.md` T6A | 넘김 요청은 `SKIP_PENDING`과 `skip_requested_at`을 기록하되 `capacity_released_at`을 건드리지 않고, 유예 내 되돌리기는 열람 이력으로 이전 상태를 유도한다. |
| `DIRECTION_COMMUNICATION_ERD.md` 핵심 불변식 10-1/11/19-2/20 | 수신 자격 없는 답변·본문 비노출, 양방향 차단, SKIP_PENDING 슬롯 유지, terminal 상태의 정확히 한 번 슬롯 해제를 지킨다. |
| `FeedScopeSql` / `InboxQuerySql` | ACTIVE·미삭제 질문글, 명시적 `at`, 수신자 상태와 차단 조건을 목록·상세에서 공유한다. |
| `PostRecipient` | `open`, `requestSkip`, `revertSkip`의 상태·시각 불변식과 최초 열람 시각을 보존한다. |
| `PostRecipientService` / `JdbcPostRecipientRepository` | 사용자 명령은 소유권을 검사하고, 동시 terminal 전이를 generic save로 덮어쓰지 않아야 한다. |
| `SkipConfirmationProperties` | 되돌리기 마감은 고정 상수가 아니라 `qello.direction.skip-confirmation-grace-seconds`와 서버 `Clock`으로 계산한다. 정확히 마감에 도달하면 confirm 대상이므로 되돌리기는 거부한다. |
| #124 feed 오류 계약 | `INBOX_ITEM_NOT_FOUND`=`FED-DOM-001`(404), `INBOX_TRANSITION_CONFLICT`=`FED-DOM-002`(409), `INBOX_ACCOUNT_NOT_FOUND`=`FED-APP-001`(404), `INBOX_ACCOUNT_NOT_ELIGIBLE`=`FED-APP-002`(403)를 사용한다. |
| API 공통 계약 | `/api/**` 인증, `ApiResponse`, ApiSpec/Controller 분리, JWT `sub`, OpenAPI 오류 응답과 정확 좌표 비노출을 따른다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 소유권만 확인하고 차단·만료 스코프 없이 상태를 변경해 숨겨진 글을 다시 열거나 넘김 | High | High | P0 | UNIT-003~005, INT-004~007 |
| 조회 자격 확인 뒤 별도 transaction에서 generic save하여 동시 `ANSWERED`/`BLOCKED`/`EXPIRED`를 덮어씀 | High | High | P0 | UNIT-006, INT-013~015 |
| 기존 수신함의 한 방향 block만 유지해 sender→recipient 차단 글이 노출됨 | High | High | P0 | UNIT-002, INT-008 |
| 반복 `PUT skip`이 `skip_requested_at`을 갱신해 유예 시간을 무한 연장함 | High | Medium | P0 | UNIT-008, INT-010, INT-013 |
| 정확히 유예 마감 시점의 되돌리기와 #126 confirm 후보가 동시에 허용됨 | High | Medium | P0 | UNIT-009, INT-012, INT-014 |
| `SKIP_PENDING` 요청 시 슬롯을 조기 해제하거나 되돌릴 때 중복 reserve함 | High | Medium | P0 | UNIT-007, INT-009~012 |
| `ANSWERED`의 만료 후 상세 예외를 잃거나 목록까지 만료 후 노출함 | High | Medium | P0 | UNIT-004, INT-005 |
| 존재하지 않는 ID와 타인의/자격 상실한 ID가 다른 오류로 존재 여부를 노출함 | High | Medium | P0 | UNIT-010, INT-007 |
| GET 상세 재시도나 HTTP prefetch가 최초 `opened_at`을 계속 바꿈 | Medium | Medium | P0 | UNIT-005, INT-003 |
| category·direction 필터가 기존 목록/칩 스냅샷 일관성을 깨뜨림 | Medium | Medium | P1 | UNIT-001, INT-001~002 |
| 클라이언트가 유예 설정을 하드코딩하거나 응답에 사용자·좌표 내부 값이 노출됨 | High | Low | P0 | UNIT-011~012, INT-009 |
| 실패한 상태 전이의 일부 컬럼만 남거나 outbox가 범위 밖에서 생성됨 | High | Low | P0 | INT-009~015 및 DB 전후 비교 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-001 | `InboxApiSpec`과 Controller/DTO 타입 | mapping과 record component를 검사 | 목록 GET, 상세 GET, skip PUT, revert DELETE가 승인 경로에 선언되고 category·direction 외 client user/time/status 입력이 없다 | P0 | Web test executor |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-002 | `FeedScopeSql`과 목록·상세·명령 스코프 | active block SQL 조각을 비교 | recipient→sender와 sender→recipient를 모두 제외하고 `released_at IS NULL`이 공유되며 목록·상세 한쪽에만 복제된 block 조건이 없다 | P0 | Persistence-boundary test executor |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-003 | missing/OPERATOR/BLOCKED/ACTIVE USER 계정 | 각 사용자가 application facade를 호출 | missing은 `INBOX_ACCOUNT_NOT_FOUND` 404, 부적격은 `INBOX_ACCOUNT_NOT_ELIGIBLE` 403, ACTIVE USER만 하위 조회·명령으로 진행한다 | P0 | Application test executor |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-004 | 8개 recipient 상태와 만료 전/정확히 만료/만료 후 시각 | 상세 자격을 판정 | 만료 전 AVAILABLE/DISCOVERED/OPENED/SKIP_PENDING/ANSWERED 허용, 만료 후 ANSWERED만 허용, SKIPPED/EXPIRED/BLOCKED는 항상 단일 404다 | P0 | Application test executor |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-005 | AVAILABLE, DISCOVERED, OPENED, ANSWERED, SKIP_PENDING 상세 | 같은 상세를 두 번 호출 | 앞 두 상태만 최초 호출에서 OPENED가 되고 기존 OPENED의 최초 시각은 유지된다. ANSWERED/SKIP_PENDING은 상태를 바꾸지 않는다 | P0 | Application test executor |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-006 | 자격을 포함해 잠긴 recipient와 예상 previous status | open/skip/revert 전이를 저장 | 이전 상태 조건을 만족할 때만 한 행이 갱신되고 0행이면 stale aggregate를 generic save하지 않고 현재 자격에 맞는 오류/멱등 결과로 처리한다 | P0 | Persistence-boundary test executor |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-007 | AVAILABLE/DISCOVERED/OPENED와 active slot | skip 요청 | SKIP_PENDING, skipRequestedAt=서버 시각, capacityReleasedAt=null이며 receive count 및 outbox를 변경하지 않는다 | P0 | Application test executor |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-008 | 이미 SKIP_PENDING이고 최초 시각 T1 | 더 늦은 서버 시각 T2에 동일 PUT 재시도 | 성공 응답은 동일 상태·T1·T1+grace를 반환하고 저장 호출과 유예 연장이 없다 | P0 | Application test executor |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-009 | SKIP_PENDING의 deadline 직전, 정확히 deadline, 직후 | revert 요청 | 직전만 열람 이력에 따른 이전 상태로 복원하고, deadline 이상은 `INBOX_TRANSITION_CONFLICT` 409이며 상태·시각을 바꾸지 않는다 | P0 | Application test executor |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-010 | nonexistent ID, outsider ID, active block, 만료/terminal 자격 상실 | 상세 또는 명령 호출 | 모두 동일한 `FeedErrorCode.INBOX_ITEM_NOT_FOUND` 404로 외부에 매핑되어 존재 여부를 구분하지 못한다 | P0 | Application/Web test executor |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-011 | 고정 Clock과 grace 설정 5초 | 각 application 요청 | 한 요청은 Clock을 한 번 읽고 모든 자격·상태·응답 deadline 계산에 같은 Instant를 쓴다 | P0 | Application test executor |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-012 | 정상/무인증 MockMvc 요청과 응답 DTO | 네 endpoint 호출 | 무인증은 401·하위 미호출, 정상 status/body는 ApiResponse 계약이며 senderId/recipientId/정확 좌표/storage URL/outbox 값이 없다 | P0 | Web test executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-001 | 목록 API, InboxQueryService, JdbcInboxQueryRepository | 같은 수신자의 UNANSWERED 2건·ANSWERED 1건과 방향 N/S 픽스처 | category별 목록 GET | 기존 category 상태 집합과 최신순 정렬을 유지하고 각 category의 카드만 반환한다 | 전용 `TEST-INBOX124` region FK 역순 정리 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-002 | 목록 API와 방향 칩 | N/S 카드와 선택된 N filter | 무필터와 N filter 목록 GET | filter 목록은 N만, chips는 category 전체 N/S를 유지하며 기존 REPEATABLE_READ 스냅샷 계약을 보존한다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-003 | 상세 API, scoped lock, PostRecipient transition | AVAILABLE, DISCOVERED, OPENED 각 행 | 같은 ID를 두 번 상세 GET | AVAILABLE/DISCOVERED는 OPENED와 최초 discovered/opened 시각을 기록하고 반복 GET은 시각을 바꾸지 않는다. 기존 OPENED도 불필요한 UPDATE가 없다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-004 | 상세 API와 상태/시간 정책 | 8개 상태, 만료 직전·정확히 만료·이후 시각 | 각 상세 GET | UNIT-004 매트릭스와 일치하고 거절 행의 상태·timestamp는 전혀 변경되지 않는다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-005 | 목록·상세 API | 만료된 ANSWERED와 공개 답변 | 만료 후 목록과 상세 GET | ANSWERED는 목록에서 빠지지만 상세는 200이고 상태·열람 시각은 그대로다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-006 | 상세·명령 API 소유권 | 실제 outsider 행과 존재하지 않는 ID | 네 endpoint의 대상 ID로 호출 | 상세/skip/revert 모두 같은 404 code/shape이며 outsider 행은 변경되지 않는다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-007 | 목록·상세·명령 API와 post visibility | 비ACTIVE/삭제/만료 post, SKIPPED/EXPIRED/BLOCKED recipient | 조회와 skip/revert 호출 | 모두 404이고 본문·미디어·거리·상태 timestamp가 노출/변경되지 않는다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-008 | FeedScopeSql, user_block | recipient→sender active/released, sender→recipient active/released 네 픽스처 | 목록·상세·skip 호출 | 어느 방향이든 active면 모두 404/목록 제외, released면 정상 노출·명령 가능이다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-009 | skip API, post_recipient, recipient_receive_state, outbox_event | AVAILABLE와 activeUnhandledCount=1, grace=5초 | T1에 PUT skip | 200, SKIP_PENDING, T1, T1+5초이며 capacityReleasedAt=null·count=1이고 outbox 행 수는 증가하지 않는다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-010 | skip API 멱등성 | INT-009 결과 | T2>T1에 동일 PUT 재시도 | 같은 T1/deadline 응답, DB skipRequestedAt=T1, count=1, outbox 불변이다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-011 | revert API와 상태 복원 | AVAILABLE/DISCOVERED/OPENED에서 각각 시작한 SKIP_PENDING, deadline 직전 | DELETE skip | 각 행이 원래 상태로 복원되고 skipRequestedAt=null, capacityReleasedAt=null, count 불변이다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-012 | revert API와 grace 경계 | deadline 정확히 및 직후 SKIP_PENDING | DELETE skip | 409 동일 code이고 SKIP_PENDING/T1/count를 유지한다. 후속 `findConfirmableSkips(deadline)`에는 후보로 남는다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-013 | 두 동시 PUT, scoped row lock/conditional transition | AVAILABLE 한 행, 고정 T1 | latch로 같은 ID에 두 PUT 동시 실행 | 둘 다 같은 성공 snapshot을 관찰하고 최종 SKIP_PENDING 한 행·T1·count 불변이며 유예가 연장되지 않는다 | 동일, 병렬 테스트 비활성화 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-014 | revert와 #126 confirm 경계 | deadline에 도달한 SKIP_PENDING, count=1 | revert와 `ReceiveSlotReleaseService.confirmSkip`을 latch로 경쟁 | 최종 SKIPPED, capacityReleasedAt 설정, count=0이며 revert가 terminal 상태를 덮어쓰지 않는다. 슬롯은 정확히 한 번 해제된다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-015 | 사용자 open/skip과 SafetyService.block | active block 생성 전의 AVAILABLE/OPENED 행, count=1 | block과 상세 또는 PUT skip을 latch로 경쟁 | 최종 BLOCKED, block timestamp/capacityReleasedAt 유지, count=0이며 사용자 명령의 stale save가 BLOCKED를 되돌리지 않는다 | 동일 |
| TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-016 | 사용자 skip과 답변 terminal 전이 | 답변 가능한 OPENED 행, count=1 | PUT skip과 답변 publish를 latch로 경쟁 | 직렬화 순서에 따라 ANSWERED 또는 SKIP_PENDING 중 하나만 성립한다. ANSWERED면 count=0, SKIP_PENDING이면 count=1이며 terminal→open stale overwrite와 음수 count가 없다 | 동일 |

## 7. Cross-cutting scenarios

### Database and transactions

- PostgreSQL/PostGIS Testcontainers와 실제 `post_recipient`, `direction_post`,
  `recipient_receive_state`, `user_block` 제약을 사용한다. H2로 대체하지 않는다.
- 목록+칩은 기존 `REPEATABLE_READ`, read-only transaction을 유지한다.
- 상세 open과 skip/revert는 자격을 포함한 행 잠금 또는 이전 상태 조건부
  `UPDATE ... RETURNING`을 하나의 transaction에서 실행한다. 자격 확인 SELECT와
  generic `save()`를 서로 다른 transaction으로 나누면 실패다.
- `SKIP_PENDING`에서는 `capacity_released_at IS NULL`과
  `active_unhandled_count` 불변을 전후 SQL로 확인한다. #126 confirm이나 block이
  성공한 경우에만 terminal timestamp·capacityReleasedAt·count 감소가 함께
  커밋되어야 한다.
- 스키마 변경은 예상하지 않는다. Flyway 파일이 추가되면 필요성·영향·롤백을
  별도 검토하고 사람의 범위 승인을 받기 전에는 진행하지 않는다.

### Concurrency and idempotency

- INT-013~016은 `ExecutorService`, `CountDownLatch`와 별도 Spring transaction을
  사용한다. 단순 순차 호출을 동시성 증거로 인정하지 않는다.
- 반복 PUT은 최초 요청 결과를 반환하되 deadline을 연장하지 않는다. 중복 명령을
  409로 돌리거나 T2를 새 skipRequestedAt으로 저장하면 실패다.
- open은 멱등이며 최초 openedAt을 보존한다.
- revert와 confirm의 정확한 경계는 `at < skipRequestedAt + grace`만 revert 허용,
  `at >=`이면 confirm lane으로 고정한다.
- 답변/차단/만료 같은 terminal 전이는 사용자 명령보다 우선한다는 뜻이 아니라,
  DB 잠금이 정한 선형화 순서 이후 stale write가 이미 커밋된 상태를 되돌릴 수 없다는
  계약이다.

### External APIs

- 외부 API 연동은 없다. FCM/APNs, 네트워크 mock, AWS 자격 증명과 LocalStack을
  추가하지 않는다.
- Notification/Delivery/Outbox 행은 조회하거나 회귀 비교할 수 있지만 #124 요청이
  생성·취소·전송하지 않는다.

### Failure recovery and reconciliation

- 권한 또는 transition 실패는 transaction 전체를 rollback하고 recipient 상태,
  timestamp, receive count를 부분 변경하지 않는다.
- #124는 별도 비동기 작업을 만들지 않으므로 재처리 큐나 보상 transaction을
  추가하지 않는다. due SKIP_PENDING은 #126 batch 조회가 회수한다.
- DB deadlock/lock timeout을 정상 성공으로 숨기지 않는다. 발생하면 실패 명령,
  양쪽 transaction 순서와 미검증 범위를 보고하고 잠금 순서를 재검토한다.
- Testcontainers/Docker 실패는 구현 실패와 구분하되 P0 통합·동시성 검증이
  실행되지 못하면 PASS로 결론내리지 않는다.

## 8. Test data and isolation

- Fixtures: ACTIVE USER sender/recipient/outsider, ACTIVE approved question,
  direction post, 8개 PostRecipient 상태, recipient_receive_state, 양방향
  active/released user_block. 기존 inbox 테스트 helper 형태를 따르되 신규 테스트
  전용 helper를 사용한다.
- Database isolation: 전용 region `TEST-INBOX124`와 고유 idempotency key를 쓰고,
  FK 역순으로 이 테스트가 만든 행만 정리한다. 전역 데이터나 다른 테스트 region을
  broad delete하지 않는다.
- Clock/randomness: `Clock.fixed`와 명시적 `Instant.parse`를 사용한다. grace=5초,
  deadline 직전은 1ns 또는 DB 정밀도에 맞춘 1µs 전, 정확히 deadline, 직후를
  구분한다. PostgreSQL `TIMESTAMPTZ` exact 비교 fixture는 microseconds로 절삭한다.
- External API doubles: 없음. application/MockMvc 단위 테스트에서는 repository와
  하위 service만 mock/fake 처리한다.
- Cleanup: answer/notification/outbox 참조가 있으면 FK 역순으로 삭제한 뒤
  post_recipient → direction_post → approved_question → 전용 account/region을
  정리한다. 동시성 테스트 종료 시 executor 종료와 future 예외를 모두 회수한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Persistence/application executor | `src/main/java/com/dnd/qello/feed/repository/**`의 신규 command port/adapter 및 승인된 scope SQL, `src/main/java/com/dnd/qello/direction/repository/PostRecipientRepository.java`, `.../jdbc/JdbcPostRecipientRepository.java`, `.../jdbc/sql/PostRecipientSql.java`, `src/main/java/com/dnd/qello/direction/service/PostRecipientService.java`, 신규 `feed/service/InboxApplicationService.java`, `feed/error/*` | production implementation supporting UNIT-002~011, INT-003~016 | `./gradlew compileJava` |
| 2 | Web/API executor | 신규 `src/main/java/com/dnd/qello/feed/web/InboxApiSpec.java`, `InboxController.java`, `response/*` | production implementation supporting UNIT-001, UNIT-010~012 | `./gradlew compileJava` |
| 3 | Application/unit test executor | 신규 `src/test/java/com/dnd/qello/feed/service/InboxApplicationServiceTest.java`, `src/test/java/com/dnd/qello/feed/InboxPersistenceBoundaryTest.java` | UNIT-002~011 | `./gradlew test --tests "*InboxApplicationServiceTest" --tests "*InboxPersistenceBoundaryTest"` |
| 4 | Web test executor | 신규 `src/test/java/com/dnd/qello/feed/web/InboxWebContractTest.java`, `InboxApiMockMvcTest.java` | UNIT-001, UNIT-010~012 | `./gradlew test --tests "*InboxWebContractTest" --tests "*InboxApiMockMvcTest"` |
| 5 | API docs executor | `docs/api/openapi.json`; production ApiSpec annotation은 Order 2 소유 | OpenAPI path/schema/error response | `./harness api-docs --check` 또는 저장소에 구성된 동등 명령 |
| 6 | Integration test executor | 신규 `src/integrationTest/java/com/dnd/qello/InboxApiIntegrationTest.java`, `InboxCommandConcurrencyIntegrationTest.java` | INT-001~016 | `./gradlew integrationTest --tests "com.dnd.qello.InboxApiIntegrationTest" --tests "com.dnd.qello.InboxCommandConcurrencyIntegrationTest" --max-workers=1` |
| 7 | Regression verifier | production 수정 없음 | 기존 inbox/detail/chip/write/slot/fan-out 회귀 | `./gradlew test --tests "*FeedPersistenceBoundaryTest" --tests "*DirectionDomainTest"`; `./gradlew integrationTest --tests "com.dnd.qello.InboxQueryIntegrationTest" --tests "com.dnd.qello.InboxDetailScopeIntegrationTest" --tests "com.dnd.qello.InboxDirectionChipIntegrationTest" --tests "com.dnd.qello.InboxSentPostWriteIntegrationTest" --tests "com.dnd.qello.ReceiveSlotReleaseIntegrationTest" --max-workers=1` |
| 8 | Independent verifier | 수정 없음 | 전체 계획과 저장소 게이트 | `./harness check`; `./harness pr-ready --project-tests`; `npm run hooks:validate`; `git diff --check` |

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] INT-013~016 동시성 테스트 통과
- [ ] 기존 inbox/detail/chip/write/slot 회귀 통과
- [ ] OpenAPI 산출물 최신 상태 확인
- [ ] Flyway migration과 Outbox 변경이 없음을 확인
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: User approval in Codex conversation
- Decision: APPROVED
- Approved at: `2026-08-16T14:20:01+09:00`

계획 승인 후 계획 경로·시나리오 ID·소유 파일을 각 executor에 전달한다. 구현되지
않은 production contract를 테스트가 요구하는 경우에는 해당 테스트를 먼저 작성하고
실패 증거를 남긴 뒤 production 변경 승인을 별도로 받는다.
