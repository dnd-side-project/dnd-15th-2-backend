# Test Plan: TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT

> Created at: `2026-08-14T15:14:47+09:00`
> Refreshed against PR #140 main at: `2026-08-14T17:21:55+09:00`
> GitHub Issue: `#123`
> Status: Approved

## 1. Objective

#120이 확정한 수신 자격을 사용자 설정과 처리 시점 권한에 맞는 인앱 알림으로
원자적으로 변환하는지 검증한다. Outbox 재처리, worker 경합, 설정·차단·상태 변경,
기기별 fan-out 일부 실패가 있어도 같은 수신자·질문글 알림을 중복 생성하지 않고,
Push 상태가 `PostRecipient`와 수신 슬롯 또는 인앱 알림의 진실을 훼손하지 않아야 한다.

## 2. Scope

### Included

- `RECIPIENTS_CONFIRMED` 전용 batch claim, event별 Clock과 독립 transaction
- `POST_RECIPIENT` aggregate ID를 권위값으로 쓰는 event 검증
- `DIRECTION_POST_RECEIVED` 설정의 활성·비활성·행 없음(default enabled) 판정
- sender/recipient account, direction post, deadline, 양방향 block과 PostRecipient 상태 재검증
- PostRecipient row lock을 fan-out 자격 판정의 선형화 지점으로 사용하고 preference/device는
  각 조회 시점에 commit된 snapshot을 적용
- Notification dedup, ACTIVE PushDevice 조회와 PENDING Delivery dedup
- source Outbox owner/generation/lease fencing과 retry/DEAD/stale 처리
- 설정 억제·기기 0개·중복·부분 실패·batch 격리·동시성·privacy 검증
- Notification/Delivery와 `PostRecipient`·수신 슬롯·수신함 권한의 분리 회귀
- #140의 GLOBAL 후보 선정 뒤에도 수신자별 confirmed event를 그대로 소비하는 회귀

### Excluded

- FCM/APNs 호출, token 복호화, provider message ID 처리와 실제 Push 발송
- delivery dispatch worker, Push backoff 숫자, rate limit, quiet hours와 Push 예산
- 인앱 알림 목록/읽음/숨김 API, #124 수신함 API와 과거 알림 일괄 revoke
- `ANSWER_PUBLISHED` 등 다른 event type의 범용 알림 handler 구현
- #140의 preview·submit·media API와 GLOBAL 후보 선정 로직 변경
- scheduler/polling activation, 배포, 신규 인프라와 운영 자격 증명 검증
- #127의 10,000명 합성 데이터 처리량·`EXPLAIN ANALYZE`

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #123 | 수신자별 Notification, notification dedup, Outbox replay 멱등성, 차단·자격 상실 억제, notification과 post_recipient 권한 분리, fan-out 중복·부분 실패 검증 |
| 2026-08-14 사용자 결정 | `DIRECTION_POST_RECEIVED` 인앱 알림 비활성 사용자는 Notification과 Delivery를 모두 생성하지 않고 source Outbox를 정상 처리한다. |
| GitHub Issue #120 / `DirectionMatchingWorker` | 신규 PostRecipient와 수신자별 `RECIPIENTS_CONFIRMED` Outbox는 같은 transaction으로 이미 확정된다. #123은 payload 후보를 다시 선정하지 않는다. |
| PR #140 / Issue #122 merged main | preview·submit API와 GLOBAL matching이 추가됐지만 confirmed event의 `POST_RECIPIENT` aggregate 계약은 유지된다. #123은 지역·거리·방향을 다시 계산하지 않고 기존 GLOBAL matching 회귀를 보존한다. |
| #119 Outbox foundation | `claimDue(eventTypes, ...)`, event type 필터, lease owner/generation/expiry fencing과 retry/DEAD 계약을 재사용한다. |
| `notification` schema | `(recipient_id, dedup_key)` unique, `DIRECTION_POST_RECEIVED`, UNREAD/READ/DISMISSED/REVOKED, direction_post target과 source outbox FK를 지킨다. |
| `notification_delivery` schema | `(notification_id, push_device_id)` unique와 PENDING/PROCESSING/SENT/FAILED/CANCELLED/DEAD 상태를 지킨다. |
| `notification_preference` schema/domain | `(notification_type, user_id)` 설정을 사용하며 행이 없으면 schema default 의미와 동일하게 enabled로 해석한다. |
| `FeedScopeSql` / inbox query | ACTIVE·미삭제 post, recipient→sender 활성 차단 없음, 미종결 상태의 deadline과 ANSWERED 열람 예외가 현재 수신함 권한 기준이다. Notification 행은 이 권한을 대체하지 않는다. |
| #120 matching boundary | 최초 확정은 양방향 active block을 제외한다. #123도 알림 생성 시점에는 더 강한 양방향 억제를 적용하되 기존 inbox query의 한 방향 권한을 이 Issue에서 변경하지 않는다. |
| AGENTS.md | JUnit 5, 단위/통합 분리, 모든 `@DisplayName`, 테스트 클래스 ISO 8601/source scenario, 잠재 문제·미검증 범위·민감정보 비기록 계약을 지킨다. |

### Proposed eligibility table requiring plan approval

| Condition at event processing | Notification | Delivery | Source Outbox |
| --- | --- | --- | --- |
| Preference row absent or enabled, sender/recipient account ACTIVE, post ACTIVE·미삭제, no active block, recipient `AVAILABLE/DISCOVERED/OPENED/SKIP_PENDING`, deadline 전 | create once | ACTIVE device별 create once | PROCESSED |
| Preference disabled | suppress | suppress | PROCESSED |
| Recipient `ANSWERED/SKIPPED/EXPIRED/BLOCKED` | suppress | suppress | PROCESSED |
| Sender/recipient account non-ACTIVE, post non-ACTIVE/deleted, deadline 도달, 어느 방향이든 active block | suppress | suppress | PROCESSED |
| PostRecipient/source contract 없음·손상 | none | none | permanent failure → DEAD |
| Transient DB failure | rollback | rollback | retry policy → FAILED/DEAD |
| Stale lease fencing failure | rollback | rollback | 현재 owner 상태를 덮어쓰지 않음 |

`ANSWERED`는 기존 feed 계약상 만료 후에도 열람할 수 있지만 이미 질문을 처리했으므로
늦은 “새 질문” 알림은 억제한다. 이 억제는 열람 권한을 회수하지 않는다. `SKIP_PENDING`은
되돌리기 가능한 미처리 수신 자격이므로 deadline 전까지 허용한다. 양방향 active block
알림 억제, 상태 판정표와 PENDING Delivery 생성 경계는 이 테스트 계획 승인으로 확정한다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| preference 미조회 또는 행 없음 처리 오류 | 수신 거부 사용자에게 알림을 만들거나 기본 사용자를 누락 | High | P0 | absent/enabled/disabled 세 fixture에서 Notification·Delivery·Outbox 결과 비교 |
| payload ID를 권위값으로 사용 | 변조된 payload가 다른 사용자에게 알림·Push를 연결 | Medium | P0 | payload ID 불일치 fixture에서도 aggregate PostRecipient만 사용하는 증거 |
| recipient/post/account/block 재검증 누락 | 자격을 잃은 사용자에게 민감한 수신 사실 노출 | High | P0 | 전체 상태·deadline·계정·post·양방향 block 판정 통합 테스트 |
| 같은 논리 알림 중복 | 중복 인앱 배지와 중복 Push 작업 | High | P0 | replay와 서로 다른 source event 경합 후 unique row count |
| 기기 상태 필터 누락 | 철회·무효 token에 전달 작업 생성 | High | P0 | ACTIVE/INVALID/REVOKED 혼합 fixture별 Delivery 대상 ID 검사 |
| 기기 0개를 실패로 처리 | 인앱 알림까지 누락되고 Outbox가 무한 재시도 | Medium | P0 | Notification 1, Delivery 0, source PROCESSED |
| Delivery 일부 실패가 Notification만 남김 | 다음 retry에서 불완전 fan-out 또는 영구 누락 | High | P0 | test trigger failure 후 Notification/Delivery 0과 source FAILED/DEAD 확인 |
| source complete와 domain write 분리 | stale worker write가 남아 재처리 중복 발생 | High | P0 | lease reclaim 뒤 old worker complete 0행과 전체 rollback |
| 한 event 실패가 batch 전체에 전파 | 정상 알림도 재처리되어 backlog·중복 증가 | Medium | P0 | 정상/실패 event 혼합 batch의 독립 최종 상태 |
| 억제를 retryable 실패로 분류 | 정책상 정상 no-op가 Outbox backlog/DEAD로 누적 | Medium | P0 | 모든 suppression 경로가 PROCESSED인지 확인 |
| Notification/Delivery가 PostRecipient를 변경 | Push 실패가 수신함 자격·슬롯을 파괴 | High | P0 | Delivery FAILED/DEAD 전후 recipient/status/count 불변 비교 |
| Notification 존재가 조회 권한을 우회 | 차단·만료 뒤 콘텐츠 접근 복원 | High | P0 | Notification 존재 상태에서 AVAILABLE 만료는 detail을 거절하고 ANSWERED 만료는 기존 권한만 유지하는 상태별 검사 |
| event/notification에 본문·위치·token 기록 | 개인정보·credential 노출 | High | P0 | payload/row/result/log의 허용 key와 금지 값 검사 |
| eligibility 조회 뒤 설정·차단·상태·account/post가 변경됨 | 서로 다른 transaction 순서에서 알림 정책이 비결정적 | High | P0 | PostRecipient lock과 account/post/preference 조회를 기준으로 commit ordering을 제어한 동시성 테스트 |
| ACTIVE device 조회 직후 revoke | 철회된 기기의 PENDING 작업이 생기거나 provider가 token을 사용 | Medium | P0 | device snapshot 순서와 후속 dispatch 재검증 경계를 고정한 concurrency test |
| 활성 기기·알림 증가로 transaction이 길어짐 | Outbox lease 만료와 lock 경합 | Unknown | P1 | 기능 테스트 실행 시간·기기 수 기록, 대규모 성능은 #127로 이관 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-UNIT-001 | due matching/answer/confirmed event와 유효 batch command | worker가 batch를 claim | repository에 정확히 `{RECIPIENTS_CONFIRMED}`만 전달하고 event마다 handler를 호출한다. | P0 | Notification worker executor |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-UNIT-002 | aggregate type/event type/ID/status가 잘못된 claimed event | handler 입력을 검증 | Notification SQL 전 영구 실패로 분류하고 payload의 recipient/post ID를 권위값으로 사용하지 않는다. | P0 | Notification worker executor |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-UNIT-003 | 정상 PostRecipient와 source event | Notification을 구성 | `DIRECTION_POST_RECEIVED`, `UNREAD`, recipient/post/source ID, `direction-post-received:{postRecipientId}` dedup과 event 처리 시각만 가진다. | P0 | Notification worker executor |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-UNIT-004 | preference absent/enabled/disabled | fan-out 여부를 판정 | absent와 enabled는 진행하고 disabled는 성공 suppression으로 처리한다. | P0 | Notification worker executor |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-UNIT-005 | 8개 PostRecipient 상태 | eligibility를 판정 | AVAILABLE/DISCOVERED/OPENED/SKIP_PENDING은 허용하고 ANSWERED/SKIPPED/EXPIRED/BLOCKED는 억제한다. | P0 | Notification worker executor |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-UNIT-006 | ACTIVE/비활성 sender·recipient account, post 상태·삭제·deadline, 양방향 block | 현재 자격을 판정 | 승인 표의 허용 조건만 fan-out으로 진행하고 억제는 retry 없이 성공 처리한다. | P0 | Notification worker executor |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-UNIT-007 | recognized dedup unique, `TransientDataAccessException`, non-dedup `DataIntegrityViolationException`, 손상 aggregate, suppression, stale complete | failure classifier를 실행 | 각각 PROCESSED, RETRYABLE, PERMANENT, PERMANENT, PROCESSED, STALE_LEASE로 분리한다. | P0 | Notification worker executor |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-UNIT-008 | 고정 at이 없는 batch와 event 2개 | batch를 처리 | claim 시각과 각 event 처리 시각을 Clock에서 읽고 한 event의 시각을 다음 event에 재사용하지 않는다. | P0 | Notification worker executor |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-UNIT-009 | 정상 event와 retryable/permanent event가 같은 batch에 존재 | batch 처리 | 정상은 PROCESSED, 실패는 해당 event만 FAILED/DEAD이며 outcome 수와 순서가 claimed event와 일치한다. | P0 | Notification worker executor |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-UNIT-010 | null/0 limit/blank owner/잘못된 lease/retry policy 없음 | command 생성 | feature boundary의 NotificationException/error code로 fail-fast하고 repository를 호출하지 않는다. | P0 | Notification worker executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-001 | worker, Outbox, Notification JDBC, PostgreSQL | eligible AVAILABLE recipient, preference 행 없음, ACTIVE device 없음, due confirmed event | batch 1회 처리 | Notification 1, Delivery 0, source PROCESSED이며 PostRecipient/state는 불변이다. | notification child → outbox → recipient → post 등 FK 역순 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-028 | #140 GLOBAL matching 결과와 #123 fan-out worker | post 표시 지역 A, 실제 recipient/matched region B, 방향·거리 조건을 만족하는 `PostRecipient`와 `RECIPIENTS_CONFIRMED` event | #123 worker가 event를 처리 | fan-out은 지역·거리·방위를 재계산하거나 payload 지역을 사용하지 않고 저장된 `POST_RECIPIENT`를 권위값으로 Notification/Delivery에 연결한다. Notification 1, 필요한 Delivery, source PROCESSED이며 `matched_region_code=B`가 유지된다. | notification child → outbox → recipient → post 등 FK 역순 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-002 | preference repository와 worker | 같은 eligible recipient에 absent/enabled/disabled 세 fixture | 각각 event 처리 | absent/enabled만 Notification을 만들고 disabled는 Notification/Delivery 0, source PROCESSED다. | preference 포함 FK 역순 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-003 | Notification/PushDevice/Delivery JDBC | ACTIVE 2, INVALID 1, REVOKED 1 기기 | event 처리 | Notification 1과 ACTIVE 기기 Delivery 2만 PENDING으로 생성되고 token 값은 결과에 없다. | delivery → notification → device 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-004 | worker, recipient/post/account eligibility | 8개 recipient 상태와 독립 confirmed event, ANSWERED의 deadline 전·경계·후 | batch 처리 | 4개 미처리 상태만 deadline 전에 Notification을 만들고 ANSWERED 포함 4개 완료/상실 상태는 시각과 무관하게 source만 PROCESSED다. | 상태별 fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-005 | post/account/deadline gate | non-ACTIVE sender/recipient account, non-ACTIVE/deleted post, deadline 직전·정확히 경계·직후 fixture | worker 실행 | 양쪽 account ACTIVE·post ACTIVE·미삭제·deadline 직전만 생성하고 경계/이후와 비활성 조건은 성공 억제한다. | account/post fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-006 | SafetyRepository, worker, inbox query | recipient→sender, sender→recipient active/released block | event 처리와 기존 inbox detail 조회 | 어느 방향이든 active block은 새 알림을 억제한다. 기존 inbox 권한은 recipient→sender 차단만 거절하며 sender→recipient 방향의 현재 동작은 변경하지 않는다. released block은 알림을 생성한다. | block fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-007 | aggregate authority와 privacy | aggregate의 PostRecipient와 다른 payload post/recipient ID·추가 좌표/본문 key | event 처리 | payload 값은 파싱하지 않고 aggregate PostRecipient 기준 Notification만 만들며 다른 사용자/글·payload 값은 결과에 반영하지 않는다. | payload fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-008 | Notification/Delivery unique | 같은 source event 재처리와 기존 logical Notification/Delivery fixture | worker 재실행 | recipient/post logical Notification과 device별 Delivery가 각각 1개이며 source가 종료된다. | dedup fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-009 | 서로 다른 Outbox와 logical dedup | 다른 source dedup key 두 개가 같은 PostRecipient를 가리킴 | 두 event 처리 | Notification/Delivery는 1개씩이고 두 source는 중복 부수효과 없이 terminal 처리된다. | duplicate source fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-010 | event transaction, failure injection | ACTIVE 기기 2개와 더 큰 ID의 특정 `push_device_id` Delivery insert에서 SQLSTATE `40001`을 발생시키는 test trigger | event 처리 | Notification/Delivery/source complete가 모두 rollback되고 source만 retry policy의 nextAttempt를 가진 FAILED가 된다. | trigger를 finally에서 제거, fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-011 | event transaction, failure injection | Notification insert에서 non-dedup check/FK violation을 발생시키는 deterministic test trigger | event 처리 | Notification/Delivery/source complete가 모두 rollback되고 source는 permanent DEAD가 된다. | trigger/function 제거, fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-012 | batch isolation | 정상, disabled suppression, retryable failure, permanent damage event | 한 batch 처리 | 앞의 둘은 PROCESSED, 나머지는 독립 FAILED/DEAD이고 정상 결과가 rollback되지 않는다. | batch fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-013 | two workers, same source event | due event 하나, barrier, 독립 transaction | 동시에 claim·처리 | 한 worker만 claim하고 Notification/Delivery logical 결과가 1개다. | executor 종료, fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-014 | stale lease fencing | A claim 후 lease 만료, B generation 증가 reclaim, A/B 처리 순서 제어 | A가 늦게 complete하고 B 처리 | A의 complete 0행과 Notification/Delivery가 함께 rollback되고 B만 한 번 commit한다. | lease fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-015 | duplicate logical event concurrency | 서로 다른 source event가 같은 PostRecipient를 가리키고 두 worker가 각각 claim | 동시에 처리 | unique conflict를 정상 멱등 경로로 흡수해 Notification/Delivery가 1개이고 두 transaction이 timeout/deadlock 없이 끝난다. | executor 종료, fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-016 | preference/block/status commit ordering | barrier로 (A) 변경 commit→eligibility read, (B) worker snapshot/row lock→변경 대기→worker commit, (C) preference read→disable commit→worker commit 순서를 만든다 | worker 처리 | A는 억제한다. B/C는 snapshot 시점에 허용된 Notification 하나를 commit하고 변경은 이후 event부터 적용한다. block/status writer는 PostRecipient lock 뒤에 직렬화된다. | transition fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-029 | account/post commit ordering | barrier로 (A) account 비활성화 또는 post 만료/비활성화 commit→eligibility read, (B) worker가 PostRecipient lock과 account/post snapshot을 확보→변경 대기→worker commit 순서를 만든다 | worker 처리 | A는 Notification/Delivery 없이 source를 PROCESSED한다. B는 lock/snapshot 시점에 허용된 결과를 한 번 commit하고, 이후 변경은 후속 event/조회부터 적용한다. account/post writer와 fan-out의 선형화 지점을 최종 row로 대사한다. | transition fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-017 | delivery failure independence | 생성 완료된 Notification/Delivery/PostRecipient/state | 기존 domain/repository 또는 직접 fixture로 Delivery를 PROCESSING→FAILED→DEAD로 전이 | 새 dispatch production 코드를 추가하지 않고 Notification status, PostRecipient 모든 필드와 active/recent count가 변하지 않음을 검증한다. | delivery fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-018 | notification/recipient 권한 분리 | Notification 존재 후 AVAILABLE recipient block/expiry, 만료 후 ANSWERED recipient, Notification READ/REVOKED | 기존 inbox detail 조회와 상태 변경 | AVAILABLE block/expiry는 detail을 거절하고 ANSWERED는 만료 후 기존 열람 권한만 유지한다. 어느 경우도 Notification이 권한을 새로 부여하지 않고 Notification 상태 변경도 recipient/slot을 바꾸지 않는다. | notification/recipient fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-019 | privacy regression | 실제 좌표·거리·방위·region·본문·합성 token을 가진 fixture | source Outbox, Notification, Delivery, worker result와 capture log 검사 | PushDevice fixture 자체를 제외한 검사 대상에 허용 식별자·상태 외 좌표·본문·token material이 없다. | 민감 fixture 값을 출력하지 않고 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-020 | preference JDBC contract | absent/enabled/disabled `DIRECTION_POST_RECEIVED` preference | recipient별 설정 조회 | absent는 enabled 기본값, 저장된 두 행은 각 boolean을 정확히 반환한다. | preference fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-021 | PushDevice JDBC contract | ACTIVE 2, INVALID 1, REVOKED 1 기기 | active device ID 조회 | 대상 사용자의 ACTIVE ID 2개만 안정적인 순서로 반환하고 token material은 반환하지 않는다. | device fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-022 | Notification insert-if-absent | 같은 recipient/dedup, 동일·상이 source ID 입력 | 멱등 저장 반복 | 최초 Notification ID가 유지되고 row는 1개이며 기존 strict `save` duplicate 예외 계약도 유지된다. | notification fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-023 | Delivery insert-if-absent | 같은 notification/device PENDING 입력 | 멱등 저장 반복 | 최초 Delivery ID/row 하나만 유지되고 기존 strict `saveDelivery` duplicate 예외 계약도 유지된다. | delivery fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-024 | partial fan-out reconciliation | 기존 logical Notification, ACTIVE 기기 2개, 그중 Delivery 1개만 존재, retry 가능한 source event | worker 재처리 | 기존 Notification/Delivery ID는 유지하고 누락된 Delivery 1개만 보충한 뒤 source를 PROCESSED로 끝낸다. | partial fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-025 | failure retry oracle | transient DB failure event와 non-dedup FK/check failure event, max-attempt 직전 event | worker 반복 처리 | transient는 attempt 증가와 승인 backoff의 FAILED 후 성공하고, non-dedup integrity는 즉시 DEAD, max-attempt transient는 DEAD이며 nextAttempt/status가 retry policy와 일치한다. | failure fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-026 | PushDevice revoke snapshot | barrier로 (A) revoke commit→active device query, (B) active device query→revoke commit→Delivery insert 순서를 만든다 | worker 처리 | A는 Delivery를 만들지 않는다. B는 snapshot의 PENDING Delivery를 만들 수 있으며 실제 dispatch가 device 상태를 다시 확인해야 한다는 미검증 후속 경계를 기록한다. | device fixture 삭제 |
| TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-027 | source complete SQL failure | source complete UPDATE가 transient SQLSTATE `40001`을 던지게 하는 deterministic failure injection | event 처리 | Notification/Delivery/complete가 rollback되고 source는 retry policy의 nextAttempt를 가진 FAILED가 된다. complete 0행 stale lease 결과와 혼동하지 않는다. | failure injection 제거, fixture 삭제 |

## 7. Cross-cutting scenarios

### Database and transactions

- 기존 PostgreSQL/PostGIS Testcontainers에서 실제 unique/FK/check, Outbox
  `FOR UPDATE SKIP LOCKED` claim과 transaction rollback을 실행한다.
- batch claim은 먼저 commit하고, 각 event의 eligibility read·Notification insert·ACTIVE
  device query·Delivery inserts·source complete는 event별 한 transaction으로 묶는다.
- source complete는 owner, generation, PROCESSING과 unexpired lease를 모두 fence한다.
  0행이면 domain write 전체를 rollback한다.
- Notification/Delivery unique conflict는 expected idempotency 경로로 흡수한다. 기존
  `save`/`saveDelivery`의 duplicate 예외 계약은 바꾸지 않고 별도 insert-if-absent
  repository 계약으로 검증한다.
- 신규 migration은 추가하지 않는다. 기존 제약으로 원자적 멱등성을 충족하지 못한다는
  실행 증거가 생기면 구현을 멈추고 별도 승인 범위로 반환한다.

### Concurrency and idempotency

- 같은 source event claim 경합, stale lease reclaim, 서로 다른 source event의 같은
  logical Notification 경합을 분리한다.
- account·post·preference·block·status 변경은 worker의 선형화 지점보다 먼저 commit된
  변경을 반드시 반영한다. PostRecipient row lock과 그 뒤의 자격 snapshot을 fan-out
  선형화 지점으로 사용하고, preference/device도 조회 시점 snapshot을 사용한다. 그 뒤
  commit된 변경은 후속 event/dispatch에서 적용하며 lock/commit 순서를 test barrier로
  제어한다.
- 동시 테스트는 2 thread, 유한 timeout, 독립 transaction과 `finally` executor 종료를
  사용한다. 성공 뒤 Notification/Delivery/source event 집합을 DB에서 대사한다.
- dedup key에는 mutable preference·상태·event attempt나 처리 시각을 포함하지 않는다.

### External APIs

- FCM/APNs 또는 다른 외부 API는 호출하지 않는다. 별도 Provider mock을 성공시켜 놓고
  통과했다고 표현하지 않는다.
- #123은 ACTIVE device별 PENDING Delivery까지만 생성한다. token ciphertext/fingerprint를
  worker 결과, Outbox payload, 로그와 테스트 보고서에 기록하지 않는다.
- device가 active snapshot 조회 뒤 revoke될 수 있으므로 실제 dispatch는 Provider 호출
  직전에 device 상태를 다시 확인해야 한다. 이 후속 경계를 #123 성공으로 오인하지 않는다.
- quiet hours, provider rate limit과 Push retry 실행은 미검증 범위로 명시한다.

### Failure recovery and reconciliation

- recognized Notification/Delivery unique 충돌은 멱등 성공으로 흡수한다.
  `TransientDataAccessException`은 #119 retry policy로 FAILED/backoff 또는 최대 시도
  DEAD가 되고, non-dedup FK/check `DataIntegrityViolationException`과 손상된
  aggregate/source 참조는 permanent DEAD가 된다.
- preference disabled와 자격 상실은 오류가 아니라 성공 suppression이므로 source를
  PROCESSED로 종료한다.
- stale worker는 complete/fail 0행이면 현재 owner 결과를 덮어쓰거나 재조회해 강제
  완료하지 않는다.
- failure 뒤 `count(notification by recipient/dedup)`, `count(delivery by notification/device)`,
  source status, PostRecipient snapshot과 receive-state counter를 비교한다.

## 8. Test data and isolation

- Fixtures: scenario별 고유 region/account/post/recipient/outbox/dedup, 8개 recipient 상태,
  sender/recipient 각각의 ACTIVE/BLOCKED/DELETED account, ACTIVE/비활성/삭제 post, deadline 직전·경계·직후,
  양방향 active/released block, absent/enabled/disabled preference, ACTIVE/INVALID/REVOKED
  PushDevice와 0/1/N device.
- Database isolation: `PostgisContainerIntegrationTestSupport`와 scenario별 고유 dedup/token
  fingerprint를 사용하며 notification_delivery → notification → preference/device → outbox →
  recipient → audience/post → block/state → account/region FK 역순으로 정리한다.
- Clock/randomness: claim과 event 처리에 고정 또는 순서 제어 가능한 Clock을 사용한다.
  DB 현재 시각, random 순서와 실제 sleep을 assertion oracle로 쓰지 않는다.
- External API doubles: 없음. repository/transaction failure는 mock 또는 test-only
  PostgreSQL trigger/function으로 주입한다.
- Cleanup: latch timeout과 `finally` executor 종료를 사용하고 test trigger/function은
  반드시 제거한다. 테스트 token은 합성 byte/fingerprint만 사용하고 출력하지 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Notification persistence executor | `NotificationRepository.java`, `JdbcNotificationRepository.java`, `NotificationSql.java`, `PostRecipientRepository.java`, `JdbcPostRecipientRepository.java`, `PostRecipientSql.java`, 신규 `src/integrationTest/java/com/dnd/qello/NotificationFanOutPersistenceIntegrationTest.java` | INT-020~023 | insert-if-absent, preference/default, ACTIVE device query, PostRecipient lock과 기존 duplicate 예외 회귀 |
| 2 | Notification worker executor | 신규 `src/main/java/com/dnd/qello/notification/fanout/RecipientNotificationFanOutWorker.java`, 신규 `src/test/java/com/dnd/qello/notification/fanout/RecipientNotificationFanOutWorkerTest.java` | UNIT-001~010 | mock unit에서 claim/filter/factory/Clock/failure outcome 검증 |
| 3 | Notification integration executor | 신규 `src/integrationTest/java/com/dnd/qello/RecipientNotificationFanOutWorkerIntegrationTest.java`만 소유 | INT-001~012, INT-017~019, INT-024~025, INT-027~029 | 실제 PostgreSQL에서 eligibility·preference·transaction·권한/privacy·부분 복구·#140 GLOBAL handoff 검증 |
| 4 | Concurrency executor | 신규 `src/integrationTest/java/com/dnd/qello/RecipientNotificationFanOutWorkerConcurrencyIntegrationTest.java`만 소유 | INT-013~016, INT-026 | barrier, 독립 transaction, timeout, final DB reconciliation |
| 5 | Regression verifier | production 수정 없음 | #119/#120/#140, 기존 notification/수신함 회귀 | `OutboxLeaseIntegrationTest`, `DirectionMatchingWorkerTest`, `DirectionMatchingWorkerIntegrationTest`, `DirectionMatchingWorkerConcurrencyIntegrationTest`, `AnswerSafetyNotificationPersistenceIntegrationTest`, inbox detail suite 실행 |
| 6 | Independent reviewer | production 수정 없음, `docs/reports/tests/gh-123-TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT.md` | 모든 P0와 잠재 문제 | Issue/TASK/승인 계획 대비 diff·결과·미검증 범위 독립 리뷰 |

실행자는 서로의 owned file을 수정하지 않는다. 한 파일이 둘 이상의 역할에 필요하면
구현 전에 소유자를 한 역할로 재배정하고 이 표를 갱신한다. 실제 구현은 이 계획이
승인된 뒤에만 시작한다.

## 10. Completion criteria

- [x] 모든 P0 시나리오 구현
- [x] 모든 테스트 메서드에 `@DisplayName`
- [x] 모든 신규 테스트 클래스 헤더에 정확한 ISO 8601 생성 시각과 source scenario 기록
- [x] 대상 단위 테스트 통과
- [x] 실제 PostgreSQL/PostGIS 통합·동시성·rollback 테스트 통과
- [x] absent/enabled/disabled preference와 8개 recipient 상태 판정표 통과
- [x] ACTIVE device N개와 Notification/Delivery/source Outbox 집합 대사 통과
- [x] #119 event filter/retry/lease fencing, #120 confirmed event와 #140 GLOBAL matching 결과의 #123 fan-out handoff 회귀 통과
- [x] account 비활성화·post 만료/비활성화의 선형화 경합과 후속 event 적용 시점 통과
- [x] Push failure가 Notification/PostRecipient/receive state를 변경하지 않는 분리 회귀 통과
- [x] `./harness check`, `./harness pr-ready --project-tests`,
      `npm run hooks:validate`, `git diff --check` 통과
- [x] 애플리케이션, DB, 동시성, transaction, 외부 API, 장애 복구 잠재 문제 분석
- [x] `templates/test-report.md` 기반 테스트 보고서 생성
- [x] 미실행 Provider/production activation/#127 성능 범위와 남은 위험 기록

## 11. Human approval

- Reviewer: User
- Decision: Approved — PENDING Delivery 경계, 양방향 block, recipient 상태 판정표,
  account/post/block/status와 preference/device 조회 시점 snapshot 선형화 포함
- Approved at: 2026-08-14 (사용자 승인)
