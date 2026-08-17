# Test Plan: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API

> Created at: `2026-08-17T14:59:12+09:00`
> GitHub Issue: `#125`
> Status: Approved — approved `2026-08-17` in the current Claude Code conversation;
> implemented and executed, see `docs/reports/tests/gh-125-TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API.md`

## 1. Objective

인증된 수신자의 답변 제출을 수신 자격·멱등성·비동기 안전 검사·슬롯 해제·공개
Outbox 계약에 맞게 검증한다. 동일 요청 재시도가 답변이나 moderation 작업을 늘리지
않고, 만료·차단·넘김·동시 제출과 moderation 결과 순서 역전 아래에서도 미검사 답변이
공개되거나 수신 슬롯이 중복 해제되지 않아야 한다.

실패하면 타인이 답변을 생성하거나, 만료 직전 정상 제출이 시스템 검사 지연으로
사라지거나, BLOCK/deadline 답변이 공개되거나, 한 사용자 슬롯 카운터와 실제
`post_recipient` 상태가 어긋날 수 있다. 따라서 HTTP 계약만이 아니라 실제
PostgreSQL/PostGIS의 FK·partial unique·deferred trigger·행 잠금과 Outbox lease
재처리를 함께 증명한다.

## 2. Scope

### Included

- `POST /api/v1/direction/inbox/{postRecipientId}/answers`, 앱 액세스 인증과
  `Idempotency-Key` 제출 계약.
- 사용자 의도인 본문·미디어 입력과 서버 소유 author/time/region/bearing/distance
  스냅샷의 분리.
- 동일 `(authorId, idempotencyKey)` 요청 fingerprint 재생과 다른 요청 재사용 거절.
- ACTIVE USER, 수신자 소유권, ACTIVE·미삭제 질문글, 양방향 활성 차단,
  `AVAILABLE`·`DISCOVERED`·`OPENED`, 서버 만료 시각의 작성 자격.
- 비동기 text moderation을 위한 비공백 본문과 선택적 미디어의 소유권·준비·안전 상태.
- answer·media attachment·filter job·`MODERATION_EXECUTION_REQUESTED` Outbox의
  원자적 제출과 rollback.
- `MODERATION_VERDICT_READY`의 ALLOW/BLOCK과 `MODERATION_DEADLINE_ELAPSED`의
  fail-closed 적용.
- ALLOW 공개 transaction의 Answer PUBLISHED, PostRecipient ANSWERED,
  `active_unhandled_count` 1회 감소, `ANSWER_PUBLISHED` Outbox 1건.
- 만료 전에 제출된 검사 중 답변의 자격 보존과 늦은 ALLOW 공개.
- Outbox claim·lease fencing·중복 결과·실패 기록 실패를 포함한 재처리 격리.
- JUnit 5 단위·MockMvc와 실제 PostgreSQL/PostGIS 통합·동시성 검증.
- 오류 코드·OpenAPI·공개 응답·로그·Outbox privacy 계약.

### Excluded

- 답변 편집·삭제, 수정 재검사, 공감과 답변 조회 기능 변경.
- `ANSWER_PUBLISHED`의 인앱 Notification/Delivery 또는 FCM/APNs fan-out.
- moderation provider 구현, retry/backoff·manual review 정책과 snapshot health 변경.
- 외부 moderation API의 실제 호출. 승인된 fake/기존 pipeline test double만 사용한다.
- `deadlineWindow` 실제 운영 숫자. 계획은 주입과 경계만 검증하며 승인 전 기본값을
  만들지 않는다.
- `AnswerFormat.TEXT/PHOTO/BOTH`가 요구하는 정확한 본문·사진 조합. 매핑이 별도로
  승인되기 전에는 비동기 pipeline에 전달할 본문을 필수로 하고 형식별 차등을 만들지 않는다.
- 본문 없는 사진 전용 답변. 현재 moderation pipeline은 공백 `rawContent`를 거절하므로
  사진만으로 공개하는 흐름은 AnswerFormat 정책과 함께 별도 승인 후 설계한다.
- Flyway migration과 운영 데이터 백필. 기존 schema로 불가능하면 구현을 멈추고
  별도 승인을 받는다.
- 인프라 apply, 배포, 프로덕션 변경.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #125 | 답변 endpoint·멱등성, 만료/차단/넘김 권한, 공개 시 슬롯 해제, 한 수신자 한 답변, 공개 Outbox |
| `TASK.md` | 제출 전용 HTTP, 비동기 공개, 제출 시점 자격 보존, fail-closed, privacy, 제외 범위와 승인 게이트 |
| `AGENTS.md` | JUnit 5, 단위/통합 분리, `@DisplayName`, 정확한 ISO 8601/source scenario 헤더, 잠재 문제·미검증 범위 보고 |
| `Answer.java` / `AnswerNotificationService.java` | SUBMITTED→SAFETY_CHECKING→PUBLISHED/REJECTED, 공개·ANSWERED·slot·Outbox 동일 transaction과 멱등성 |
| `PostRecipient.java` / `PostRecipientRepository.java` | 열린 상태 집합, 조건부 ANSWERED/EXPIRED/SKIPPED/BLOCKED 전이와 stale write 방어 |
| `AnswerModerationJobIntakeService` / #107·#108 계획 | durable filter job, 실행 요청·verdict·deadline 이벤트, deadline은 승인 아님, late verdict 전달 |
| Flyway V1/V2/V8/V12~V14 | recipient-author FK, answer idempotency, `uq_answer_one_per_recipient`, capacity deferred trigger, Outbox dedup·lease fencing |
| #124 수신함 계약 | ANSWERED는 사후 상세 자격 유지, terminal 상태·양방향 차단은 새 답변 자격을 주지 않음 |
| `docs/api-response.md` / `docs/error-codes.md` | 공통 성공/오류 wrapper, 기능 경계의 안정적인 오류 코드와 DB 제약 매핑 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 소유권·ACTIVE USER·양방향 차단을 저장 전에 확인하지 않는다. | 타인 답변·차단 우회 | Medium | P0 | 소유권을 SQL 조건에 포함한 lock + HTTP/DB integration |
| 클라이언트가 author/time/region/bearing/distance를 주입한다. | 권한·위치 privacy·스냅샷 변조 | Medium | P0 | request DTO 부재 필드와 저장값 source 검증 |
| 동일 키의 다른 본문·미디어·recipient를 기존 답변으로 조용히 재생한다. | 사용자 의도 손실·잘못된 답변 연결 | High | P0 | fingerprint replay/reuse unit + concurrent integration |
| 체크 후 INSERT 경쟁에서 answer/filter job/Outbox가 중복된다. | 중복 답변·검사 비용·중복 공개 | High | P0 | DB unique + 두 transaction latch 검증 |
| 한 recipient에 서로 다른 키의 활성 답변 두 건이 생성된다. | 1인 1답변 불변식 위반 | Medium | P0 | `uq_answer_one_per_recipient` 경쟁과 오류 매핑 |
| 답변 저장만 commit되고 moderation job/Outbox가 유실된다. | 영구 미공개·운영 복구 불가 | High | P0 | forced failure rollback + count reconciliation |
| BLOCK 또는 deadline이 ALLOW처럼 공개된다. | 유해·미검사 콘텐츠 노출 | High | P0 | result worker 분기와 실제 DB 상태/Outbox 부재 |
| 만료 전에 제출했지만 검사 중 expiry sweep가 먼저 EXPIRED로 만든다. | 정상 답변 유실·정책 위반 | High | High | pending answer 제외 query + late ALLOW integration |
| ALLOW와 expiry/block/skip이 동시에 슬롯을 해제한다. | 카운터 음수·상한 무력화 | High | Medium | 조건부 전이 + latch concurrency + deferred trigger |
| 중복 verdict/reclaim이 release와 `ANSWER_PUBLISHED`를 반복한다. | 슬롯 중복 해제·알림 중복 | High | Medium | answer/outbox lock·dedup·lease replay integration |
| result worker 한 이벤트 실패가 이후 claim 전체를 중단한다. | 배치 정체·영구 지연 | Medium | Medium | per-event failure isolation + lease reclaim |
| 미디어 READY가 업로드 완료만 의미하는데 안전 통과로 오인한다. | 미검사 이미지 공개 | High | Medium | owner/status/exif/moderation 조건과 rollback integration |
| raw answer/좌표/내부 ID가 응답·로그·알림 Outbox에 포함된다. | 개인정보·콘텐츠 노출 | High | Low | DTO/payload source scan + integration assertions |
| 현재 ERD의 제출 즉시 슬롯 해제 설명을 그대로 구현한다. | Issue 계약과 실제 흐름 불일치 | Medium | Medium | TASK 우선 계약·문서 동기화 review |
| deadline·AnswerFormat 값을 임의 기본값으로 고정한다. | 미승인 제품·운영 정책 배포 | Medium | Medium | configuration/plan scan; 값 미승인 시 BLOCKED 판정 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-001 | null command, 유효하지 않은 recipient ID·멱등키 | 제출 command 생성/검증 | 필수값·양수·1~200자 규칙을 Answer 기능 오류로 거절한다. | P0 | Answer/API executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-002 | 본문 없음/공백, 정상 Unicode 본문, 선택 미디어 0/1/2개 | 콘텐츠 정규화 | 비공백 본문과 미디어 최대 1개를 적용하되 미승인 AnswerFormat 매핑은 고정하지 않는다. | P0 | Answer/API executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-003 | 인증 subject와 ACTIVE/비활성/운영자/없는 계정 | submission facade 호출 | ACTIVE USER만 진행하고 요청 body의 author 입력 경로는 존재하지 않는다. | P0 | Persistence executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-004 | 본인 recipient의 AVAILABLE/DISCOVERED/OPENED와 정확한 만료 전 시각 | 자격 판정 | 세 상태만 새 답변 자격을 가지며 서버 Clock을 사용한다. | P0 | Persistence executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-005 | 타인 recipient, SKIP_PENDING/SKIPPED/EXPIRED/BLOCKED/ANSWERED, 양방향 활성 차단 | 자격 판정 | 내부 원인을 공개하지 않는 동일한 not-found 계열 계약으로 저장 전에 거절한다. | P0 | Persistence executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-006 | PostRecipient의 region/bearing/distance와 상충하는 사용자 입력 시도 | Answer 생성 | author/time/region/bearing/distance는 인증·Clock·recipient snapshot만 사용한다. | P0 | Persistence executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-007 | 같은 author/key/recipient/body/media fingerprint | 재제출 | 기존 answer를 반환하고 attach/intake를 호출하지 않는다. | P0 | Persistence executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-008 | 같은 author/key에 recipient/body/media 중 하나가 다름 | 재제출 | `IDEMPOTENCY_KEY_REUSED` 계열 409를 반환한다. | P0 | Persistence executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-009 | 다른 key지만 같은 recipient에 live answer가 있음 | 새 제출 | 한 recipient 한 활성 답변 오류로 변환하고 raw DB 예외를 노출하지 않는다. | P0 | Persistence executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-010 | 저장된 answer ID·본문과 승인된 deadline 주입값 | moderation intake 호출 | ANSWER target, 안정적 idempotency key, 원문을 안전하게 직렬화한 실행 요청을 한 번 만든다. | P0 | Moderation executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-011 | SAFETY_CHECKING answer와 ALLOW verdict | 결과 처리 | PASSED/PUBLISHED 전이를 요청하고 BLOCK/deadline 경로를 실행하지 않는다. | P0 | Moderation executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-012 | SAFETY_CHECKING answer와 BLOCK verdict | 결과 처리 | Answer를 REJECTED로 만들고 recipient/slot/ANSWER_PUBLISHED를 변경하지 않는다. | P0 | Moderation executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-013 | deadline elapsed와 이후 ALLOW | 순서대로 결과 처리 | deadline은 공개 승인이 아니며 fail-closed 상태를 유지하고, authoritative late ALLOW는 중복 없이 공개 경로로 전달한다. | P0 | Moderation executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-014 | 이미 PUBLISHED/REJECTED 또는 targetVersion 불일치 결과 | 동일/오래된 이벤트 재처리 | terminal 결과는 멱등이고 stale version은 현재 답변을 덮어쓰지 않는다. | P0 | Moderation executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-015 | 같은 answer 공개를 두 번 호출 | publish | 두 번째 호출은 slot·Outbox를 추가 변경하지 않고 기존 공개 답변을 반환한다. | P0 | Persistence executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-016 | 정상 Controller 요청 | MockMvc POST | 인증 subject·header·path·body를 service command로 전달하고 202 공통 wrapper를 반환한다. | P0 | Answer/API executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-017 | 인증 없음, 잘못된 header/body/path | MockMvc POST | 401 또는 400이며 application service를 호출하지 않는다. | P0 | Answer/API executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-018 | 타인/terminal/중복/fingerprint 충돌 service 예외 | MockMvc POST | 문서화된 404/409 오류 코드와 공통 오류 wrapper를 보존한다. | P0 | Answer/API executor |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-019 | 성공 응답과 공개 Outbox DTO | 직렬화 | 정확 좌표·내부 사용자 ID·본문·moderation 세부값이 포함되지 않는다. | P0 | Answer/API executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-001 | Answer API, account, recipient/post lock, answer/media/filter/outbox repositories | ACTIVE USER, ACTIVE post, OPENED recipient, count=1, 활성 filter release | 유효 POST 제출 | 202; SAFETY_CHECKING answer 1, attachment 0/1, filter_job 1, EXECUTION_REQUESTED 1이 commit되고 slot은 아직 1이다. | 전용 `TEST-ANSWER125` fixture FK 역순 삭제 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-002 | 제출 transaction 전체 | INT-001 fixture, outbox 또는 intake 저장 강제 실패 | POST 제출 | HTTP 실패; answer·attachment·filter_job·history·outbox가 모두 0이고 recipient/count는 원상태다. | transaction rollback |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-003 | API + fingerprint reconstruction | 기존 SAFETY_CHECKING 또는 PUBLISHED answer와 attachment/job/outbox | 같은 key·동일 payload 재제출 | 기존 answer ID/status를 반환하고 모든 관련 row count와 최초 submittedAt/deadlineAt이 불변이다. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-004 | API + idempotency unique | 같은 author/key, recipient/body/media 중 하나가 다른 요청 | 재제출 | 409; 기존 row 불변, 새 attachment/job/outbox 없음. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-005 | 두 API transaction + `uq_answer_idempotency` | 같은 key·payload, latch | 동시 제출 | 둘 다 같은 answer를 관측하고 answer/filter_job/EXECUTION_REQUESTED는 정확히 각 1건이다. | executor 종료 + 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-006 | 두 API transaction + `uq_answer_one_per_recipient` | 같은 recipient, 서로 다른 key·body, latch | 동시 제출 | 한 요청만 생성되고 다른 요청은 기능 409다. partial unique 원문이나 SQL은 응답에 노출되지 않는다. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-007 | answerable lock SQL + API | 타인/없는 recipient, 양방향 block, SKIP_PENDING/SKIPPED/EXPIRED/BLOCKED/ANSWERED | 각 POST | 모두 동일한 404 계약이고 answer/filter/outbox/slot 변화가 없다. | block 포함 역순 삭제 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-008 | 서버 Clock + post expiry | expiresAt 직전/정확히 같은 시각/직후 | 제출 | 직전만 202이며 경계 이상은 404/도메인 거절, client time은 판정에 영향이 없다. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-009 | pending answer + expiry candidate query | 만료 전 제출된 SAFETY_CHECKING answer, 이후 post expiresAt 경과 | 만료 후보 조회/전이 시도 | 해당 recipient는 미답변 만료 후보로 선점되지 않고 slot을 유지한다. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-010 | moderation result worker + publish transaction | INT-009 상태와 late ALLOW verdict | 만료 뒤 worker 처리 | answer PUBLISHED, recipient ANSWERED, count 0, ANSWER_PUBLISHED 1이며 제출 시점 자격이 보존된다. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-011 | ALLOW publish + capacity deferred trigger | SAFETY_CHECKING answer, open recipient, count=1 | ALLOW 처리 | Answer PASSED/PUBLISHED, recipient ANSWERED/capacityReleasedAt, count=0, published Outbox가 한 transaction으로 commit된다. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-012 | publish transaction rollback | INT-011 fixture, ANSWER_PUBLISHED 저장 강제 실패 | ALLOW 처리 | answer/recipient/count가 전부 원상태이며 부분 공개와 ghost slot release가 없다. | rollback |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-013 | 두 result worker + Outbox lease | 같은 VERDICT_READY, 두 owner/latch | 동시 claim·ALLOW | 한 worker만 authoritative 처리하고 slot은 1회, ANSWER_PUBLISHED는 1건이다. | executor 종료 + 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-014 | result replay/reclaim | 첫 ALLOW 성공 뒤 같은 payload 재전달 또는 lease reclaim | 재처리 | PUBLISHED/result Outbox/slot count가 변하지 않고 처리 이벤트만 terminal을 유지한다. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-015 | ALLOW vs block transition | open recipient/count=1, 두 transaction latch | ALLOW와 block 동시 실행 | 직렬화 순서에 따라 ANSWERED 또는 BLOCKED 하나만 성립하고 count는 정확히 0, stale overwrite가 없다. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-016 | 제출 vs skip/expiry/block | 유효 open recipient/count=1, latch | 새 제출과 각 terminal 경로 경쟁 | 자격 잠금 순서에 따라 제출이 원자적으로 성립해 pending 보호를 얻거나 terminal 전이가 먼저 성립해 제출 전체가 rollback한다. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-017 | BLOCK verdict | SAFETY_CHECKING answer와 open recipient/count=1 | BLOCK 처리 | answer REJECTED/moderation REJECTED, recipient/count 불변, ANSWER_PUBLISHED 없음; 새 key 재제출 가능하다. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-018 | deadline + late verdict | pending answer, DEADLINE_ELAPSED 뒤 ALLOW | 순차 worker 처리 | deadline만으로 공개·slot 해제하지 않고 late ALLOW는 INT-011과 같은 단일 공개 결과를 만든다. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-019 | media ownership/safety + deferred content trigger | 비공백 본문과 본인/타인, UPLOADING/READY, moderation PENDING/PASSED, exif 미제거/제거 media fixture | text-only 또는 text+media 제출·공개 | 승인된 본인 미디어만 선택적으로 attach되고 미검사·타인 미디어는 전체 rollback한다. 본문 없는 요청은 저장되지 않는다. | attachment→media→answer 역순 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-020 | #124 inbox/detail/query | 공개 완료된 ANSWERED와 만료된 post | 수신함 목록·상세·답변 조회 | ANSWERED는 slot 없이 수신 자격을 유지하고 기존 #124 사후 상세 계약이 회귀하지 않는다. | 동일 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-021 | result worker batch isolation | 첫 이벤트의 domain 처리 또는 failure 기록이 예외, 뒤에 정상 이벤트 | 한 batch 처리 | 첫 이벤트 실패가 뒤 이벤트 공개를 막지 않고 원본은 lease 만료 후 reclaim 가능하며 중복 공개가 없다. | lease fixture 정리 |
| TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-022 | API/OpenAPI/privacy | 실제 endpoint와 생성된 OpenAPI | schema 생성·응답/Outbox 조회·민감 문자열 source scan | path/header/status/error schema가 일치하고 공개 응답·ANSWER_PUBLISHED payload에 좌표·user ID·본문이 없다. | 문서 산출물 비교 |

## 7. Cross-cutting scenarios

### Database and transactions

- H2로 대체하지 않고 PostgreSQL/PostGIS Testcontainers에서 composite FK,
  `uq_answer_idempotency`, V8의 `uq_answer_one_per_recipient`,
  `ct_post_recipient_capacity_release`, content deferred trigger와 Outbox JSONB를 실행한다.
- 잠금 순서는 제출에서 `post_recipient`와 소속 `direction_post`, 공개에서 Answer와
  `post_recipient`의 승인된 순서로 고정하고, 동시성 테스트가 deadlock과 stale write를
  모두 실패로 판정한다.
- answer·attachment·filter job·실행 Outbox는 제출 transaction의 한 결과다.
  PUBLISHED·ANSWERED·receive state release·공개 Outbox도 별도 한 transaction의 결과다.
- Flyway 기존 migration은 수정하지 않는다. 신규 schema가 필요하면 테스트를 억지로
  통과시키지 않고 `BLOCKED`로 보고한다.

### Concurrency and idempotency

- 동일 키 동시 요청과 서로 다른 키의 동일 recipient 요청을 각각 실제 두 connection과
  latch로 실행한다. application의 선조회뿐 아니라 DB unique가 최종 중재자임을 확인한다.
- fingerprint는 recipient/body/정렬된 media ID를 포함하고 author/key/server time은
  비교 대상에서 제외한다. 같은 요청의 현재 moderation 상태가 변해도 재생은 기존 결과다.
- ALLOW, expiry, skip confirmation, block은 이전 상태 조건부 전이와 affected-row 재조회로
  승자를 판별한다. count는 어떤 직렬화 순서에서도 음수가 되지 않는다.
- claim/reclaim은 owner·generation·PROCESSING·유효 lease fencing을 사용하며 stale worker는
  답변 상태나 처리 결과를 덮어쓰지 못한다.

### External APIs

- 답변 제출 요청 thread에서 moderation provider를 호출하지 않는다. 테스트는 filter job과
  `MODERATION_EXECUTION_REQUESTED`의 durable 기록까지만 실제로 검증한다.
- moderation 판정은 ALLOW/BLOCK/timeout을 결정적으로 반환하는 fake pipeline 또는 이미
  저장된 verdict/deadline Outbox fixture로 대체한다.
- FCM/APNs, object storage 네트워크와 실제 moderation provider는 호출하지 않는다.
  MediaAsset은 저장된 ownership·status·EXIF·moderation 결과만 검증한다.

### Failure recovery and reconciliation

- answer 저장 직후 attachment/filter job/outbox 실패, ALLOW 중 recipient/receive state/
  published Outbox 실패를 각각 주입하고 전후 row count를 비교한다.
- 배치의 domain 처리 실패와 실패 상태 기록 실패를 구분한다. 후자는 원본 PROCESSING
  lease가 만료될 때까지 남을 수 있지만 뒤 이벤트는 계속 처리되고 reclaim 뒤 한 번만
  결과를 만든다.
- 검증 후 `active_unhandled_count`와
  `count(post_recipient where capacity_released_at is null)`의 drift, orphan filter job,
  execution/published Outbox 누락·중복을 reconciliation query로 확인한다.
- deadline이나 retry 소진 상태가 오래 유지되면 pending answer가 슬롯을 점유할 수 있다.
  실제 운영 deadline/manual review SLA는 미승인 위험으로 보고서에 남기고 임의 해제하지 않는다.

## 8. Test data and isolation

- Fixtures: 전용 region, ACTIVE/비활성 USER, post sender, 양방향 block, ACTIVE/EXPIRED
  direction post, 8개 PostRecipient 상태, recipient receive state, answer 본문·미디어,
  promoted filter release/job/history, verdict/deadline/execution/published Outbox.
- Database isolation: `TEST-ANSWER125` 식별 접두사와 고유 idempotency/dedup key를 사용한다.
  일반 통합은 transaction rollback, deferred trigger·다중 connection·lease 시나리오는
  명시적 commit 후 FK 역순 정리 또는 container lifecycle로 격리한다.
- Clock/randomness: 고정 `Clock`, microsecond 정밀도로 truncate한 `Instant`, 결정적 worker
  owner/generation을 사용한다. 만료 경계는 `expiresAt-1μs`, `expiresAt`, `expiresAt+1μs`다.
- External API doubles: moderation ALLOW/BLOCK/timeout fake, 저장 실패 repository adapter,
  실제 네트워크를 사용하지 않는 object storage double.
- Cleanup: notification delivery가 생기지 않는 범위이며, outbox → filter history/job →
  media attachment → answer → block → post recipient → receive state → post → account → region
  순서로 삭제한다. 병렬 테스트는 executor를 반드시 종료한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Answer/API executor | `src/main/java/com/dnd/qello/answer/domain/**`, `src/main/java/com/dnd/qello/answer/web/**`, `src/test/java/com/dnd/qello/answer/domain/**`, `src/test/java/com/dnd/qello/answer/web/**` | UNIT-001~002, UNIT-016~019 | Answer domain + MockMvc targeted tests |
| 2 | Persistence executor | `src/main/java/com/dnd/qello/answer/service/**`(moderation worker 제외), `src/main/java/com/dnd/qello/answer/repository/**`, `src/main/java/com/dnd/qello/direction/repository/PostRecipientRepository.java`, `src/main/java/com/dnd/qello/direction/repository/jdbc/JdbcPostRecipientRepository.java`, `src/main/java/com/dnd/qello/direction/repository/jdbc/sql/PostRecipientSql.java`, `src/test/java/com/dnd/qello/answer/service/AnswerSubmissionServiceTest.java`, `src/test/java/com/dnd/qello/answer/service/AnswerPublicationServiceTest.java` | UNIT-003~009, UNIT-015 | application/persistence targeted unit tests |
| 3 | Moderation executor | `src/main/java/com/dnd/qello/filtering/moderation/**` 중 공개 intake/event contract, `src/main/java/com/dnd/qello/answer/moderation/**`, `src/test/java/com/dnd/qello/answer/moderation/**` | UNIT-010~014 | moderation result/claim/failure isolation unit tests |
| 4 | Integration test executor | `src/integrationTest/java/com/dnd/qello/AnswerSubmissionApiIntegrationTest.java`, `src/integrationTest/java/com/dnd/qello/AnswerSubmissionConcurrencyIntegrationTest.java`, `src/integrationTest/java/com/dnd/qello/AnswerModerationPublicationIntegrationTest.java` | INT-001~022 | actual PostgreSQL/PostGIS targeted integration tests |
| 5 | Documentation/verifier | `TASK.md`, `docs/test-plans/gh-125-*`, `docs/reports/tests/gh-125-*`, `docs/error-codes.md`, `docs/api/openapi.json`, 관련 ERD 설명 | 전체 추적성·privacy·정책 정합성 | diff/source scan + full Harness gates |

각 executor는 소유 경로 밖의 파일을 수정하지 않는다. 공유 파일 변경이 필요하면 먼저
오케스트레이터에 보고해 소유권을 재배정한다. 실행 순서는 1→2→3→4→5이며, production
구현과 테스트 구현은 이 계획 승인 후에만 시작한다.

## 10. Completion criteria

- [ ] P0 단위/MockMvc 19개와 통합·동시성·장애 복구 22개 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 모든 테스트 클래스 상단에 정확한 ISO 8601 생성 시각과 원본 scenario ID 기록
- [ ] 동일 멱등키·동일 recipient·ALLOW/expiry/block/skip·lease reclaim 실제 동시성 통과
- [ ] PostgreSQL/PostGIS FK·partial unique·deferred trigger·JSONB·lock 검증 통과
- [ ] 제출/공개 양 transaction의 forced failure rollback 및 reconciliation 통과
- [ ] 공개 응답·로그·ANSWER_PUBLISHED payload의 좌표·사용자 ID·본문 비노출 검증
- [ ] `deadlineWindow`와 AnswerFormat 매핑의 사람 결정 또는 명시적 미구현/BLOCKED 기록
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성 및 잠재 문제 분석
- [ ] 다음 명령 실행 결과 기록

```bash
./gradlew test --tests 'com.dnd.qello.answer.*'
./gradlew integrationTest --tests 'com.dnd.qello.AnswerSubmissionApiIntegrationTest' --tests 'com.dnd.qello.AnswerSubmissionConcurrencyIntegrationTest' --tests 'com.dnd.qello.AnswerModerationPublicationIntegrationTest'
./gradlew test
./gradlew integrationTest
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

실패는 구현 결함과 환경 결함으로 구분한다. 환경 결함도 실패 명령, 오류 요약, 재현 조건,
미검증 범위와 남은 위험을 보고서에 남긴다. 실행하지 않은 명령은 통과로 표시하지 않는다.

## 11. Human approval

- Reviewer: User
- Decision: Approved
- Approved at: `2026-08-17` (current Claude Code conversation)
- Approval scope: test scenarios, execution ownership, late-ALLOW policy and explicit blockers
