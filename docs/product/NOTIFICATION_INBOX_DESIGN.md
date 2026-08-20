# 알림함 읽기 설계 (F07-N1)

> 작성일: 2026-08-20
>
> 상태: 작업 게이트 완료 (2026-08-20) — 브랜치 `feat/gh-176-notification-inbox-read`,
> `TASK.md` 계약 생성. 다음 단계는 `/harness-test-plan`.
>
> 관련 자산: `notification` 패키지, `notification`/`notification_delivery`/
> `notification_preference`/`push_device`/`outbox_event` 테이블(V1, V2),
> `feed.service.AccountEligibilityGate`, `RecipientNotificationFanOutWorker`
>
> 선행 문서: `docs/product/ANSWER_REPORT_DESIGN.md` §5.4, §8.3, §8.4

## 0. 작업 게이트

`AGENTS.md` §1에 따라 이 문서는 **설계와 작업 분해까지만** 다룬다. 구현은 다음이
갖춰진 뒤에 시작한다.

1. ~~GitHub Issue 생성~~ — 완료. #176(`type: feature`, `area: api`), 상위 #183
2. ~~`./harness start --issue 176 --type feat --slug notification-inbox-read`~~ — 완료
3. ~~`./harness task-init`으로 `TASK.md` 계약 생성~~ — 완료
4. `/harness-test-plan`으로 `TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ` 확정 — 계획 작성
   완료(`docs/test-plans/gh-176-TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ.md`, 단위 21·통합 32).
   **사람 승인 남음** — 계획 §11
5. ~~이 문서 §12의 미결 항목에 대한 사람의 결정~~ — §12-1과 §11-1 확정, §12-3·§12-4는
   이 이슈를 막지 않는 것으로 판정(§12 참조)

§2의 실측은 2026-08-20 `origin/main`(`028a863`)에서 재확인했다. 마이그레이션 최신은
`V22`이므로 `V23` 자리가 비어 있고, `NotificationErrorCode`에 `NOT-APP-*`·`NOT-VAL-006`·
`NOT-VAL-007`이 없으며, `feed.service.AccountEligibilityGate`는 여전히 package-private
`void require(long)`이고, `RecipientNotificationFanOutWorker.isEligible`이 여전히
`isPreferenceEnabled`를 물고 있다. 설계의 전제가 모두 유효하다.

## 1. 요구사항 → 설계 매핑

F07은 알림을 세 자리로 나눈다. 이 문서는 그중 ①과 ②의 일부만 다룬다.

| F07 자리 | 성질 | 이 문서의 범위 |
| --- | --- | --- |
| ① 알림함 | 기록. 한 번 생기면 남는다 | **전체** — 목록, 읽음, 진입 판정 |
| ② 인앱 신호 | 상태. 조건이 풀리면 사라진다 | **알림 버튼 위 점만**. 카드 배지는 N6 |
| ③ 푸시 | 전달 시도. 안 갈 수도 있다 | 제외 — N4·N5 |

F07의 "①②③은 서로를 대신하지 않는다. ③이 억제되거나 실패해도 ①은 그대로 남는다"가
이 문서의 §10 게이트 재배치를 요구하는 문장이다.

## 2. 현재 상태 (2026-08-20 실측)

| 계층 | 상태 |
| --- | --- |
| domain | `Notification`, `NotificationDelivery`, `NotificationPreference`, `PushDevice`, `NotificationType`(6종) 완비. V2가 `ANSWER_REACTED`·`QUESTION_RECOMMENDED`를 DB CHECK까지 반영 |
| repository | `JdbcNotificationRepository` — `saveIfAbsent`, `claimDelivery`, `findActiveDeviceIdsByUserId`, `isPreferenceEnabled`, preference upsert. **목록 조회 쿼리 없음** |
| fan-out | `RecipientNotificationFanOutWorker`가 `RECIPIENTS_CONFIRMED` → `DIRECTION_POST_RECEIVED` 하나만 처리 |
| outbox producer | `ANSWER_PUBLISHED`(`AnswerNotificationService`), `QUESTION_PROPOSAL_REVIEWED`(`QuestionReviewService`) 발행됨. **소비자 없음**. `ANSWER_REACTED`·`REPORT_RESOLVED`·`QUESTION_RECOMMENDED`는 producer도 없음 |
| 푸시 | FCM/APNs 연동 코드 없음. `push_device`·`notification_preference` **쓰기 경로가 운영 코드에 없음**(테스트에서만 호출) |
| service·web | 없음 |
| 워커 trigger | 저장소 전체에 `@Scheduled` 0개. `RecipientExpirationSweepWorker`의 주석이 이 관례를 명시한다 — "운영 주기 실행 활성화는 이 이슈의 범위 밖이다" |

알림함 6종 중 1종만 원장에 쌓이고, 읽을 API도 설정할 API도 발송기도 없는 상태다.

## 3. F07 작업 분해

각 조각은 자기 Issue와 자기 설계·테스트 계획 주기를 갖는다.

| 순서 | ID | Issue | 범위 | 선행 |
| --- | --- | --- | --- | --- |
| **A** | **N1** | #176 | 알림함 읽기 — 목록, 점, 읽음, 진입 판정. preference 게이트 재배치 | 없음 |
| B | N2 | #177 | fan-out 확장 — `ANSWER_PUBLISHED`→`ANSWER_RECEIVED`(질문자만), `ANSWER_REACTED`, `QUESTION_PROPOSAL_REVIEWED`, `QUESTION_RECOMMENDED` 소비자 | #176 |
| C | N3 | #178 | 알림 설정 API — 종별 on/off, 방해 금지 시간. **스키마 변경 필요**(§12-2) | #176 |
| D | N4 | #179 | 푸시 토큰 등록 + 발송 파이프라인. **인프라 설계 승인 선행** | #178 |
| E | N5 | #180 | 묶음·일 상한·방해 금지 억제 | #179 |
| F | N6 | #181 | 인앱 신호 — 카드 배지(수신자별 읽음 기준선), 탭 상단 카운터 | #177 |
| G | W1 | #182 | 워커 주기 실행 활성화 — fan-out·매칭·만료 스윕·moderation 워커 공통 | 없음 |
| — | N7 | — | N1에 흡수. 알림 진입 판정 | — |

상위 Issue는 #183이고 A~G가 그 sub-issue다. `REPORT_RESOLVED` 소비자는 #155가
소유하므로 B의 범위에서 제외했다.

`W1`을 N1에서 분리하는 이유는 §11-4에 있다.

## 4. N1 범위와 명시적 제외

### 4.1 범위

1. 알림함 목록 조회(cursor)
2. 미읽음 신호 조회(지도 홈의 점)
3. 알림함 열람 기준선 전진
4. 줄 단위 읽음 처리
5. 알림 진입 시 대상 생존 판정
6. `RecipientNotificationFanOutWorker`의 preference 게이트를 delivery 생성 직전으로 이동
7. `AccountEligibilityGate`를 `account.service`로 승격

### 4.2 제외

* **묶음(`새 답변 4개`)** — `uq_notification_recipient_dedup`이 1행=1알림을 강제한다.
  묶음은 표시 계층이 아니라 발행 계층의 설계 대상이므로 N5에서 다룬다. N1의 목록은
  1행=1줄이다.
* **알림 설정 조회·변경** — N3. N1은 preference를 **읽지도 않는다**(§10).
* **푸시 발송, 토큰 등록, 잠금화면 문구** — N4.
* **카드 배지, 탭 상단 카운터** — N6. `새 답변 n개`는 수신자별 읽음 기준선이 필요하고
  그 기준선은 `post_recipient.answers_read_at`(#170)이 이미 소유한다.
* **알림 삭제·전체 읽음 버튼** — F07에 없다.
* **`DISMISSED` 전이** — 상태값은 이미 있지만 이를 만드는 UI가 F07에 없다.

## 5. 읽음 모델은 두 계층이다

F07은 서로 다른 두 소멸 조건을 같은 표에 적어 둔다.

* 알림 버튼 위 점 — "알림함을 열면 사라진다. **목록의 줄 자체는 남는다**"
* 알림함의 줄 — 기록이므로 남는다

줄 단위 `notification.status`만으로 점을 표현하면, 알림함을 한 번 여는 순간 모든 줄이
`READ`가 되어 어떤 줄이 새 것이었는지 알 수 없다. 반대로 점을 `UNREAD` 존재 여부로만
계산하면 알림함을 열어도 톱하지 않은 줄이 남아 점이 사라지지 않는다.

두 기준선을 분리한다.

| 계층 | 저장 위치 | 전진 조건 |
| --- | --- | --- |
| 점 | `notification_seen_state.seen_at` | `PUT /notifications/seen` |
| 줄 | `notification.status`, `read_at` | `PUT /notifications/{id}/read` |

`seen_at` 전진은 `GREATEST`로만 앞으로 간다.

```sql
INSERT INTO notification_seen_state (user_id, seen_at)
VALUES (:userId, :at)
ON CONFLICT (user_id) DO UPDATE
    SET seen_at = GREATEST(EXCLUDED.seen_at, notification_seen_state.seen_at)
```

반복 호출과 순서 역전이 기준선을 과거로 되돌리지 않는다 — #170이
`advanceAnswersReadAt`에서 확립한 계약과 같다.

`notification_seen_state`를 `user_account` 컬럼으로 두지 않는 이유는
`notification` 모듈이 `account` 테이블에 쓰기를 갖게 되기 때문이다.
`recipient_receive_state`가 같은 이유로 분리돼 있다.

## 6. 모듈 경계

### 6.1 알림함은 `notification`이 소유한다

알림 6종 중 4종(답변 공감·제안 검토·신고 처리·질문 추천)은 방향 수신함과 무관하다.
`feed`에 두면 N2에서 `feed → question`·`feed → safety` 참조가 새로 생긴다.

```text
notification/
  view/         NotificationCard, NotificationTargetKind, NotificationTargetState,
                NotificationListing, NotificationTargetDecision, UnreadSignal
  repository/   NotificationInboxQueryRepository (+ jdbc/, sql/NotificationInboxQuerySql)
                NotificationSeenStateRepository  (+ jdbc/)
  service/      NotificationInboxService
  web/          NotificationApiSpec, NotificationController, web/response/*
  error/        NotificationErrorCode에 코드 5개 추가 (§8.5)
```

차단 판정 때문에 `notification.service → safety.repository` 참조가 필요하다.
`RecipientNotificationFanOutWorker`가 이미 `SafetyRepository`를 참조하므로 새 방향은
생기지 않는다.

### 6.2 계정 자격 게이트 승격

`feed.service.AccountEligibilityGate`는 package-private이다. #170 결정 7이 "계정 자격
게이트가 한 곳에만 있어야 갈라지지 않는다"고 못박았으므로 복제하지 않고 옮긴다.

```text
account/service/AccountEligibilityGate   (이동, public)
  Account requireActiveUser(long accountId)   → AccountException(계정 없음·자격 없음)
feed/service/AccountEligibilityGate      (유지, package-private)
  void require(long accountId)                → FeedErrorCode.INBOX_ACCOUNT_* 로 번역
```

게이트가 예외를 직접 던지는 대신 `Account`를 반환하고, 각 모듈이 자기 오류 코드로
번역한다. `FED-APP-001`·`FED-APP-002` 응답 계약이 그대로 유지되고 `account`가
`feed.error`를 참조하는 역방향 의존도 생기지 않는다.

## 7. 대상 생존 판정

F07: "이동 가능 여부는 클라이언트가 캐시로 판단하지 않고 서버가 현재 상태를 확인한 뒤
결정한다."

### 7.1 두 자리로 나눈다

| 자리 | 무엇을 판정하나 | 왜 |
| --- | --- | --- |
| 목록 | `state` — 각 줄의 현재 상태 | 만료된 줄과 살아있는 줄을 목록에서 구분해 보여줘야 한다 |
| 진입 | `navigable`·`reason`·`fallback` | 알림함을 열고 수분 뒤 톱하면 그 사이 만료됐을 수 있다 |

목록에서 끝내면 지연 사이 만료를 놓치고, 목록에서 생략하면 만료된 줄을 표시할 수
없다. 두 자리 모두 같은 판정 규칙을 쓰고 진입 API가 한 줄만 재판정한다.

### 7.2 판정 규칙

`NotificationTargetState`:

| 값 | `DIRECTION_POST` | `ANSWER` |
| --- | --- | --- |
| `GONE` | 행 없음 또는 `deleted_at IS NOT NULL` | 행 없음 또는 삭제됨 |
| `EXPIRED` | `status <> 'ACTIVE'` 또는 `expires_at <= now` | 해당 없음 |
| `HIDDEN` | 해당 없음 | `status = 'HIDDEN'`(운영 숨김) 또는 미공개 |
| `BLOCKED` | 뷰어와 발신자 사이 활성 `user_block` | 뷰어와 작성자 사이 활성 `user_block` |
| `AVAILABLE` | 위 어느 것도 아님 | 위 어느 것도 아님 |

우선순위는 `GONE` > `BLOCKED` > `HIDDEN` > `EXPIRED` > `AVAILABLE`이다. 삭제된 대상에
차단 이유를 노출하면 상대의 차단 사실이 새어 나간다.

`NotificationTargetKind`는 N1에서 `DIRECTION_POST`, `ANSWER`, `NONE` 셋이다.
`ANSWER_REPORT_DESIGN.md` §8.4가 `notification.report_id` 컬럼 추가를 예정하므로
`REPORT`를 넣을 자리를 열어 두되 **컬럼과 매핑은 #155가 소유한다** — N1은 추가하지
않는다.

`REVOKED` 줄은 목록에서 제외한다. `ANSWER_REPORT_DESIGN.md` §5.4가 전역 숨김
트랜잭션에서 알림을 `REVOKED`로 전이시키므로, 그 줄은 "볼 수 없게 된 대상"이 아니라
"회수된 기록"이다. `DISMISSED`도 같은 이유로 제외한다.

## 8. API 계약

### 8.1 엔드포인트

| 메서드 | 경로 | 하는 일 |
| --- | --- | --- |
| `GET` | `/api/v1/notifications` | cursor 목록 |
| `GET` | `/api/v1/notifications/unread-count` | 점과 카운터 |
| `PUT` | `/api/v1/notifications/seen` | 열람 기준선 전진 |
| `PUT` | `/api/v1/notifications/{notificationId}/read` | 줄 단위 읽음 |
| `GET` | `/api/v1/notifications/{notificationId}/target` | 진입 판정 |

### 8.2 목록

요청: `cursorCreatedAt`(ISO-8601), `cursorNotificationId`, `limit`(기본 20, 상한 50).

cursor는 불투명 토큰이 아니라 명시적 두 파라미터다 — 정렬 키가 이미 응답에 공개된
값이고, 인코딩·검증 계층을 새로 만들 이유가 없다(#170 결정 2). 두 파라미터는 함께
지정하거나 함께 생략한다. 한쪽만 오면 `NOT-VAL-007`.

정렬은 `created_at DESC, id DESC`. `nextCursor`는 반환 건수가 `limit`과 같을 때만
채우고 그보다 적으면 `null`이다(#170 결정 6).

한 줄:

```json
{
  "notificationId": 1042,
  "type": "DIRECTION_POST_RECEIVED",
  "createdAt": "2026-08-20T11:03:41Z",
  "readAt": null,
  "unread": true,
  "target": { "kind": "DIRECTION_POST", "id": 771, "state": "AVAILABLE" },
  "expiresAt": "2026-08-20T12:03:41Z"
}
```

`expiresAt`은 `kind = DIRECTION_POST`이고 `state = AVAILABLE`일 때만 채운다. F07의
"질문글 도착 알림에는 곧 만료된다는 사실을 함께 담는다"를 알림함에서도 지킨다.

**싣지 않는 것**: 질문 본문, 답변 본문, 상대 닉네임, 상대 계정 식별자, 정확 위치,
대략 지역·거리, 사진 미리보기. F07의 잠금화면 규칙과 기존 수신함 규칙을 알림함까지
같은 기준으로 올린다. 알림 문구는 6종 모두 익명이므로 `type`만으로 클라이언트가
조립한다.

### 8.3 점

```json
{ "hasUnseen": true, "unreadCount": 7, "seenAt": "2026-08-20T09:12:00Z" }
```

`hasUnseen`은 `seen_at`보다 나중에 만들어진 `UNREAD` 줄의 존재 여부다(`seen_at`이
없으면 `UNREAD` 줄의 존재 여부). `unreadCount`는 `UNREAD` 줄의 개수로 두 값의 기준이
다르다 — F07의 점은 열람 기준선으로 사라지고, 카운터는 톱하지 않은 줄을 센다.

목록과 별도 엔드포인트로 두는 이유는 지도 홈이 목록 없이 점만 필요하기 때문이다.

### 8.4 읽음·진입

`PUT /{id}/read`는 멱등이다. 이미 `READ`면 상태를 바꾸지 않고 현재 값을 반환한다.
`REVOKED`는 `Notification.markRead`가 거부하므로 `NOT-DOM-003`(409)이다.

`GET /{id}/target`:

```json
{
  "navigable": false,
  "reason": "EXPIRED",
  "target": { "kind": "DIRECTION_POST", "id": 771, "state": "EXPIRED" },
  "fallback": "FEED_HOME"
}
```

`reason`은 `state`가 `AVAILABLE`이 아닐 때만 채운다. `fallback`은 `NONE`(이동 가능),
`FEED_HOME`(삭제·차단), `INBOX`(만료된 수신 질문글) 셋이다. F07의 "빈 상세 화면으로
보내지 않는다"를 서버가 결정한다.

### 8.5 인가와 오류 코드

모든 경로는 인증된 `USER` 계정만 호출한다. 남의 알림은 존재를 노출하지 않고 404다.

| 코드 | HTTP | 언제 |
| --- | --- | --- |
| `NOT-APP-001` | 404 | 계정 없음 |
| `NOT-APP-002` | 403 | `USER`가 아니거나 `ACTIVE`가 아님 |
| `NOT-DOM-004` | 404 | 알림 없음 또는 남의 알림 |
| `NOT-DOM-003` | 409 | `REVOKED` 줄 읽음 시도 (기존 코드 재사용) |
| `NOT-VAL-006` | 400 | `limit` 범위 이탈 |
| `NOT-VAL-007` | 400 | cursor 파라미터 한쪽만 지정 |

`docs/error-codes.md` §11에 함께 반영한다.

## 9. 스키마 변경

`V23__add_notification_inbox_read_state.sql`

```sql
CREATE TABLE notification_seen_state (
    user_id  BIGINT PRIMARY KEY,
    seen_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_notification_seen_state_user
        FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE
);

-- 알림함 목록은 (recipient_id, created_at DESC, id DESC)로만 정렬한다.
-- uq_notification_recipient_dedup은 (recipient_id, dedup_key)라 이 정렬을 커버하지 못한다.
-- REVOKED·DISMISSED는 목록에서 제외되므로 부분 인덱스로 좁힌다.
CREATE INDEX notification_recipient_feed_idx
    ON notification (recipient_id, created_at DESC, id DESC)
    WHERE status IN ('UNREAD', 'READ');
```

`notification` 테이블 자체는 바꾸지 않는다. `report_id` 추가는 #155가 소유한다.

## 10. preference 게이트 재배치

### 10.1 현재의 결함

`RecipientNotificationFanOutWorker.isEligible()`이 차단·계정·만료와 함께
`isPreferenceEnabled`를 검사해서 **`notification` 행 생성 자체를 건너뛴다.** F07의
"사용자가 푸시를 전부 꺼두어도 알림함은 계속 채워진다"와 정면으로 충돌한다.

`ANSWER_REPORT_DESIGN.md` §8.3이 신고 결과 알림에 대해 같은 결론에 독립적으로
도달했다. N1은 그 예외를 일반 규칙으로 승격한다.

### 10.2 재배치

| 게이트 | 현재 위치 | N1 이후 | 근거 |
| --- | --- | --- | --- |
| 수신 항목 상태 | `isEligible` | 그대로 | 자격 없는 수신 항목은 기록도 남기지 않는다 |
| 질문글 만료·삭제 | `isEligible` | 그대로 | 같음 |
| 계정 `ACTIVE` | `isEligible` | 그대로 | 같음 |
| 활성 차단 | `isEligible` | 그대로 | "차단한 상대와 관련된 알림은 설정과 무관하게 나가지 않는다" |
| **preference** | `isEligible` | `persistPendingDeliveries` 직전 | 푸시만 막고 기록은 남긴다 |

차단은 알림함 줄도 만들지 않는 것이 맞고, preference는 푸시만 막는다. 성격이 다른
두 검사를 한 게이트에 묶어 둔 것이 결함이었다.

### 10.3 계약이 바뀌는 기존 테스트

"preference off면 아무것도 생기지 않음"이 "알림함 행은 생기고 `delivery`만 0건"으로
바뀐다.

* `src/test/java/.../notification/fanout/RecipientNotificationFanOutWorkerTest.java`
* `src/integrationTest/java/.../NotificationFanOutPersistenceIntegrationTest.java`
* `src/integrationTest/java/.../RecipientNotificationFanOutWorkerIntegrationTest.java`

PR 본문에 이 계약 변경을 명시한다.

## 11. 실패 모드와 위험

1. **목록 쿼리 비용** — 뷰어별 차단 판정에 `user_block` `EXISTS` 2개가 붙는다.
   `limit` 상한 50과 `notification_recipient_feed_idx`로 제한하되 통합 테스트에서
   실행 계획을 확인한다. 인덱스가 선택되지 않으면 차단 판정을 진입 API로만 옮기는
   대안이 있다(그 경우 목록의 `state`에서 `BLOCKED`가 빠진다).
   **`CONFIRMED` (2026-08-20): 목록에도 `BLOCKED`를 싣는다.** 진입 API에만 두면 차단한
   상대의 알림 줄이 목록에서 `AVAILABLE`로 보였다가 톱할 때 막히는 어긋남이 생긴다.
   실행 계획 확인을 완료 조건에 넣고, 인덱스가 선택되지 않을 때만 대안으로 전환한다.
   전환하는 경우 그 결정을 이 절에 기록한다.
2. **게이트 승격이 #170 코드를 건드린다** — `FED-APP-001`·`FED-APP-002` 응답 불변을
   회귀 테스트로 고정한다.
3. **N1 완료 후에도 알림함에는 `DIRECTION_POST_RECEIVED` 한 종류만 실린다** —
   클라이언트가 6종 UI를 만들려면 N2가 필요하다. Issue 본문에 명시한다.
4. **워커 trigger 부재** — 저장소 전체에 `@Scheduled`가 없고 이는 의도된 관례다
   (`RecipientExpirationSweepWorker` 주석). 따라서 N1을 배포해도 운영 알림함은 비어
   있다. 통합 테스트는 워커를 직접 호출하므로 검증에는 지장이 없다. N1에서 fan-out
   하나만 예외로 활성화하면 단일 인스턴스 가정과 lease 프로파티 결정이 N1으로
   들어오므로, 워커 5종을 한 번에 다루는 `W1` Issue로 분리한다.
5. **`seen_at`과 목록 조회의 경합** — 두 쿼리 사이에 새 알림이 도착하면 점이 남는다.
   이는 정상 동작이며 다음 열람에서 해소된다.
6. **`ANSWER_REACTED` 알림의 지연 도착** — F07은 며칠 뒤 도착을 정상으로 규정한다.
   목록 정렬은 `created_at` 단일 기준이므로 오래된 알림을 별도 취급하지 않는다.

## 12. 미결 항목

1. **`unreadCount` 상한** — 미읽음이 수백 건이면 정확한 수를 세는 비용이 는다.
   `99+`처럼 절단할지, 절단 임계값을 서버가 정할지.
   **`CONFIRMED` (2026-08-20): N1은 정확한 수를 반환하고 절단은 클라이언트가 한다.**
   `notification_recipient_feed_idx`가 `(recipient_id, created_at DESC, id DESC)` 부분
   인덱스라 `COUNT`가 인덱스만으로 끝나고, 초기 사용량에서 한 사용자의 미읽음이 수백
   건까지 쌓이지 않는다. 서버 절단이 필요해지면 응답에 필드를 더하는 후속 변경으로
   처리한다 — 기존 필드의 의미를 바꾸지 않으므로 호환된다.
2. **방해 금지 시간의 저장 위치** (N3의 결정, 여기 기록만) — `notification_preference`
   PK가 `(notification_type, user_id)`라서 `quiet_start`/`quiet_end`가 종별로 6벌
   생긴다. F07은 사용자당 한 쌍이다. N3에서 사용자 단위 테이블로 분리해야 한다.
   `BLOCKED`: N3 설계에서 결정.
3. **`fallback = INBOX`의 대상** — 만료된 수신 질문글의 알림을 톱했을 때 수신함
   목록으로 보낼지 만료 상세로 보낼지는 디자인 결정이다. `UNKNOWN`.
   **이 이슈를 막지 않는다**: 서버는 `fallback` 값만 반환하고 그 값이 가리키는 화면은
   클라이언트가 정한다. 결정이 나도 서버 응답은 바뀌지 않는다.
4. **알림 보존 기간** — `notification` 행을 영구 보관할지. 기록이라는 성질과 목록
   쿼리 비용이 부딪힌다. `UNKNOWN`: N1 범위 밖. cursor 페이징이 오래된 줄을 목록
   첫 화면에서 자동으로 밀어내므로 읽기 경로는 보존 정책 없이도 성립한다.

## 13. 테스트 관점

### 13.1 단위

* `NotificationInboxService` — cursor 경계(같은 `created_at`), `limit` 상한 절단,
  cursor 한쪽만 지정, `targetState` 우선순위 5종, `seen` 전진 멱등·역순
* `NotificationCard`·`NotificationTargetDecision` 불변식
* 재배치된 fan-out 게이트 — preference off에서 `notification` 1건, `delivery` 0건

### 13.2 PostgreSQL 통합

* 목록 cursor 페이징 정렬 안정성(같은 시각 동시 도착 다건)
* 남의 알림 404, 존재 비노출
* `REVOKED`·`DISMISSED` 목록 제외
* `seen_at` `GREATEST` — 반복·역순 호출 후 기준선 불변
* 대상 상태 4종 판정 — 만료, 삭제, 운영 숨김, 차단
* preference off 사용자에게 `notification` 1건 + `delivery` 0건
* `feed` 게이트 회귀 — `FED-APP-001`·`FED-APP-002` 응답 불변
* 응답에 본문·닉네임·계정 식별자·위치가 실리지 않음
* `OpenApiSpecificationIntegrationTest` 재생성

### 13.3 실행하지 못하면 기록할 것

`AGENTS.md` §3에 따라 테스트 환경 문제로 실행하지 못한 검증은 실패한 명령, 오류 요약,
재현 조건, 미검증 범위, 남은 위험을 기록한다.

## 14. 검증 명령

```bash
./gradlew test --tests "com.dnd.qello.notification.*" --console=plain
./gradlew test --tests "com.dnd.qello.feed.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.Notification*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --console=plain
./harness test-run --id TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## 15. 구현 순서

§4~§14가 무엇을 만들지 정했다면 이 절은 어떤 순서로 만들고 어디서 커밋을 끊을지
정한다. 브랜치는 `feat/gh-176-notification-inbox-read`이고 `AGENTS.md` §7에 따라 모든
커밋의 type은 브랜치와 같은 `feat`이며 `(#176)`을 단다.

### 15.1 커밋 분할 원칙

"하나의 커밋에는 하나의 검토 목적만 담는다"(`AGENTS.md` §7)를 이 이슈에서는 다음으로
읽는다.

* 계층 하나가 커밋 하나다. 각 커밋은 자기 계층의 테스트를 함께 담아 단독으로 검증된다.
* 기존 코드의 계약을 바꾸는 변경(S1의 게이트 승격, S8의 preference 재배치)은 신규 계층
  커밋과 섞지 않는다. 리뷰어가 회귀 위험을 한 커밋에서만 보게 한다.
* 생성 산출물(`docs/api/openapi.json`)은 그것을 만들어 낸 코드와 같은 커밋에 두지 않고
  마지막에 한 번만 갱신한다. 중간 커밋마다 스펙이 흔들리면 diff를 읽을 수 없다.

### 15.2 단계

| 단계 | 커밋 | 선행 | 성격 |
| --- | --- | --- | --- |
| S0 | — | — | 테스트 계획 승인. 계획 §9의 실행자 E1~E6이 S1~S9에 대응한다 |
| S1 | `feat(account): 계정 자격 게이트를 account.service로 승격한다 (#176)` | S0 | 기존 계약 유지 리팩터링 |
| S2 | `feat(notification): 알림함 읽기 상태 스키마를 추가한다 (#176)` | S0 | 신규 |
| S3 | `feat(notification): 알림함 오류 코드를 추가한다 (#176)` | S0 | 신규 |
| S4 | `feat(notification): 알림함 view 모델을 추가한다 (#176)` | S3 | 신규 |
| S5 | `feat(notification): 알림함 조회 repository를 추가한다 (#176)` | S2, S4 | 신규 |
| S6 | `feat(notification): 알림함 application service를 추가한다 (#176)` | S1, S3, S5 | 신규 |
| S7 | `feat(notification): 알림함 읽기 API를 노출한다 (#176)` | S6 | 신규 |
| S8 | `feat(notification): preference 게이트를 delivery 생성 직전으로 옮긴다 (#176)` | S0 | 기존 계약 변경 |
| S9 | `feat(notification): 알림함 API 스펙을 반영한다 (#176)` | S7, S8 | 생성 산출물 |

S1·S2·S3·S8은 서로 의존하지 않는다. S8은 S1~S7과 완전히 독립이므로 순서를 앞당겨도
되지만, 기존 테스트 3종의 계약이 바뀌는 유일한 커밋이라 리뷰 동선을 위해 신규 계층
뒤에 둔다.

### 15.3 단계별 상세

#### S1 — 계정 자격 게이트 승격

`feed.service.AccountEligibilityGate`가 package-private이라 `notification`이 쓸 수 없다.
#170 결정 7("계정 자격 게이트가 한 곳에만 있어야 갈라지지 않는다")에 따라 복제하지
않고 옮긴다.

만들거나 고치는 파일:

```text
+ account/service/AccountEligibilityGate.java     (public)
~ feed/service/AccountEligibilityGate.java        (package-private 어댑터로 축소)
  feed/service/InboxApplicationService.java       (호출부 무변경 — 어댑터가 시그니처 유지)
  feed/service/FeedInteractionApplicationService.java (무변경)
+ src/test/java/.../account/service/AccountEligibilityGateTest.java
~ src/test/java/.../feed/service/InboxApplicationServiceTest.java        (조립만 갱신)
~ src/test/java/.../feed/service/FeedInteractionApplicationServiceTest.java (조립만 갱신)
```

**결정이 필요한 지점.** §6.2는 승격된 게이트가 `Account`를 반환하고 각 모듈이 자기
오류 코드로 번역한다고만 정했다. 번역을 어떻게 붙일지는 세 갈래다.

| 안 | 형태 | 대가 |
| --- | --- | --- |
| A (권장) | `Account requireActiveUser(long, Supplier<RuntimeException> notFound, Supplier<RuntimeException> notEligible)` | 호출부가 자기 예외를 직접 준다. 새 `ACC` 코드도, catch-and-remap도 없다. 인자 2개가 는다 |
| B | `AccountException`을 던지고 각 모듈이 catch해 코드로 분기 | `AccountErrorCode`에 403 코드(`ACC-APP-003`)를 새로 만들어야 한다. 오류 코드 값으로 분기하는 catch는 코드가 바뀌면 조용히 깨진다 |
| C | `Optional<Account> findEligibleUser(long)` | 계정 없음(404)과 자격 없음(403)을 구분하지 못한다. `FED-APP-001`·`FED-APP-002` 계약이 깨지므로 채택 불가 |

**`CONFIRMED` (2026-08-20): A로 간다.** `AccountErrorCode`에는 현재
`ACCOUNT_NOT_FOUND`(`ACC-APP-001`)만 있고 "USER가 아니거나 ACTIVE가 아님"에 해당하는
403 코드가 없다 — B는 그 코드를 새로 만들어야 하고, 만든 뒤에도 각 모듈이 코드 값으로
분기하는 catch를 갖는다. A는 호출부가 던질 예외를 직접 넘기므로 `account`가 어떤
오류 코드 체계도 알 필요가 없다.

승격된 게이트의 형태:

```java
public Account requireActiveUser(long accountId,
    Supplier<RuntimeException> notFound, Supplier<RuntimeException> notEligible)
```

호출부는 다음과 같다.

```text
feed 어댑터        → FeedException(INBOX_ACCOUNT_NOT_FOUND) / FeedException(INBOX_ACCOUNT_NOT_ELIGIBLE)
NotificationInboxService → NotificationException(NOT-APP-001) / NotificationException(NOT-APP-002)
```

`feed` 어댑터는 기존 `void require(long accountId)` 시그니처를 그대로 유지하므로
`InboxApplicationService`와 `FeedInteractionApplicationService`의 호출부는 한 줄도
바뀌지 않는다.

검증:

```bash
./gradlew test --tests "com.dnd.qello.account.*" --tests "com.dnd.qello.feed.*" --console=plain
```

`FED-APP-001`·`FED-APP-002`의 HTTP 상태와 코드 문자열이 그대로인지가 이 단계의 완료
조건이다.

#### S2 — 스키마

```text
+ src/main/resources/db/migration/V23__add_notification_inbox_read_state.sql
```

내용은 §9 그대로다. `notification_seen_state` 테이블과
`notification_recipient_feed_idx` 부분 인덱스만 만들고 `notification` 테이블은
건드리지 않는다. 현재 최신 마이그레이션이 `V22`이므로 번호 충돌이 없다.

검증은 Flyway가 실제로 적용되는지이므로 통합 테스트 부팅으로 확인한다.

```bash
./gradlew integrationTest --tests "com.dnd.qello.NotificationEventIntegrationTest" --console=plain
```

#### S3 — 오류 코드

```text
~ notification/error/NotificationErrorCode.java   (+5: NOT-APP-001, NOT-APP-002, NOT-DOM-004, NOT-VAL-006, NOT-VAL-007)
~ docs/error-codes.md                             (§11 표에 5행 추가)
```

`NOT-DOM-003`(409, `REVOKED` 줄 읽음 시도)은 이미 있으므로 재사용한다. 기존 코드 값은
바꾸지 않는다.

#### S4 — view 모델

```text
+ notification/view/NotificationCard.java
+ notification/view/NotificationTargetKind.java      (DIRECTION_POST, ANSWER, NONE)
+ notification/view/NotificationTargetState.java     (GONE, BLOCKED, HIDDEN, EXPIRED, AVAILABLE)
+ notification/view/NotificationListing.java
+ notification/view/NotificationTargetDecision.java
+ notification/view/UnreadSignal.java
+ notification/view/package-info.java
+ src/test/java/.../notification/view/NotificationCardTest.java
+ src/test/java/.../notification/view/NotificationTargetDecisionTest.java
```

불변식으로 고정할 것:

* `expiresAt`은 `kind = DIRECTION_POST`이고 `state = AVAILABLE`일 때만 채워진다. 그 밖의
  조합에서 값이 들어오면 거부한다 — 만료된 줄에 만료 시각을 실어 보내면 클라이언트가
  살아 있는 줄로 오해한다.
* `NotificationTargetDecision.reason`은 `state != AVAILABLE`일 때만 채워지고
  `navigable`은 `state == AVAILABLE`과 같은 값이다. 두 필드가 어긋날 수 없게 한다.
* `NotificationTargetKind.NONE`이면 `id`는 `null`이다.

`REPORT`는 `#155`가 소유하므로 이 enum에 넣지 않는다.

#### S5 — 조회 repository

```text
+ notification/repository/NotificationInboxQueryRepository.java
+ notification/repository/NotificationSeenStateRepository.java
+ notification/repository/jdbc/JdbcNotificationInboxQueryRepository.java
+ notification/repository/jdbc/JdbcNotificationSeenStateRepository.java
+ notification/repository/jdbc/NotificationRowMappers.java
+ notification/repository/jdbc/sql/NotificationInboxQuerySql.java
+ src/integrationTest/java/.../NotificationInboxQueryIntegrationTest.java
```

`NotificationInboxQuerySql`이 담는 것:

* `SELECT_CARD` — 한 줄과 그 줄의 `target.state`를 한 쿼리에서 함께 판정한다.
  `feed`의 `InboxQuerySql.SELECT_CARD`가 `reacted_by_me`를 목록·상세가 공유하게 만든
  것과 같은 이유로, 목록과 진입 판정이 같은 SELECT를 공유해야 판정 규칙이 갈라지지
  않는다.
* 대상 상태는 `CASE`로 `GONE > BLOCKED > HIDDEN > EXPIRED > AVAILABLE` 순서를 그대로
  적는다. `CASE`의 평가 순서가 곧 우선순위이므로 §7.2의 표와 `CASE` 절의 순서가
  1:1로 대응한다.
* 차단은 `user_block ... released_at IS NULL`을 양방향으로 본다 —
  `FeedScopeSql.ACTIVE_POST_VISIBILITY`와 같은 판정을 쓴다.
* cursor 조건은 `(created_at, id) < (:cursorCreatedAt, :cursorNotificationId)` 튜플
  비교로 쓴다. `created_at`만으로 비교하면 같은 시각 다건에서 줄이 새거나 중복된다.
* 상태 필터는 `status IN ('UNREAD','READ')`로 고정한다 — `REVOKED`·`DISMISSED` 제외가
  `notification_recipient_feed_idx`의 부분 조건과 같은 술어라야 인덱스가 잡힌다.
* `COUNT_UNREAD`, `EXISTS_UNSEEN`, `UPSERT_SEEN_AT`(§5의 `GREATEST` upsert).

통합 테스트에서 `EXPLAIN`으로 목록 쿼리가 `notification_recipient_feed_idx`를 쓰는지
확인한다(§11-1 확정 사항).

#### S6 — application service

```text
+ notification/service/NotificationInboxService.java
+ notification/service/package-info.java
+ src/test/java/.../notification/service/NotificationInboxServiceTest.java
```

`account.service.AccountEligibilityGate`를 호출하고 `NOT-APP-001`·`NOT-APP-002`로
번역한다. `limit` 범위(`NOT-VAL-006`)와 cursor 짝(`NOT-VAL-007`) 검증도 이 계층에
둔다 — controller에 두면 `#177` 이후 다른 진입점이 생겼을 때 검증이 갈라진다.

`InboxApplicationService`와 같이 `Clock`에서 시각을 한 번만 읽어 판정과 저장에 같은
`at`을 쓴다.

#### S7 — web

```text
+ notification/web/NotificationApiSpec.java
+ notification/web/NotificationController.java
+ notification/web/response/NotificationListingResponse.java
+ notification/web/response/NotificationCardResponse.java
+ notification/web/response/UnreadSignalResponse.java
+ notification/web/response/NotificationReadResponse.java
+ notification/web/response/NotificationTargetResponse.java
+ notification/web/response/package-info.java
+ notification/web/package-info.java
+ src/integrationTest/java/.../NotificationInboxApiIntegrationTest.java
```

`InboxController`와 같은 형태다 — `AuthenticatedUserId.require(authentication)`로
subject만 꺼내 application 경계에 넘기고, `ApiResponseFactory`로 감싼다.

`SecurityConfiguration`은 **바꾸지 않는다**. `appApiSecurityFilterChain`이
`/api/**`를 `anyRequest().authenticated()`로 이미 덮고 있고, `USER` 역할과 `ACTIVE`
판정은 S6의 자격 게이트가 한다.

응답 record에는 §8.2의 금지 목록(본문, 닉네임, 계정 식별자, 위치)에 해당하는 필드를
아예 선언하지 않는다. mapper가 실수로 노출할 경로를 타입으로 없앤다.

#### S8 — preference 게이트 재배치

```text
~ notification/fanout/RecipientNotificationFanOutWorker.java
~ src/test/java/.../notification/fanout/RecipientNotificationFanOutWorkerTest.java
~ src/integrationTest/java/.../NotificationFanOutPersistenceIntegrationTest.java
~ src/integrationTest/java/.../RecipientNotificationFanOutWorkerIntegrationTest.java
```

`isEligible`에서 `isPreferenceEnabled(target.recipientId())` 한 줄을 떼어
`persistPendingDeliveries` 직전으로 옮긴다. `isEligible`에 남는 네 검사(수신 항목
상태, 질문글 만료·삭제, 계정 `ACTIVE`, 활성 차단)는 그대로다.

계약이 바뀌는 테스트는 위 3종이며 PR 본문에 명시한다. `RecipientNotificationFanOut
WorkerConcurrencyIntegrationTest`는 preference를 켠 상태만 다루므로 영향이 없다 —
착수 시 재확인한다.

검증:

```bash
./gradlew test --tests "*RecipientNotificationFanOutWorkerTest" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.NotificationFanOut*" --tests "com.dnd.qello.RecipientNotificationFanOutWorker*" --console=plain
```

#### S9 — 스펙 재생성

`OpenApiSpecificationIntegrationTest`가 springdoc 출력을 `docs/api/openapi.json`으로
쓴다. S7까지 끝난 뒤 한 번만 실행해 커밋한다.

```bash
./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --console=plain
```

경로 5개와 operation 5개(`GET` 3, `PUT` 2)가 스펙에 나타나는지 확인한다.

### 15.4 하지 않는 변경

작업 중 손이 갈 수 있지만 이 이슈에서 건드리지 않을 파일을 미리 못박는다.

| 파일 | 이유 |
| --- | --- |
| `auth/config/SecurityConfiguration.java` | `/api/**`가 이미 `authenticated()`다. 경로별 matcher를 늘리면 인가 판정이 두 곳으로 갈라진다 |
| `notification/domain/*` | `Notification.markRead`와 상태 전이는 이미 필요한 계약을 갖췄다 |
| `notification/repository/jdbc/sql/NotificationSql.java` | fan-out 쓰기 SQL이다. 읽기 SQL은 새 클래스에 둔다 |
| `notification` 테이블 DDL | `report_id` 추가는 `#155`가 소유한다 |
| `notification_preference` 스키마 | 방해 금지 시간 재설계는 `#178`(§12-2) |
| 워커의 `@Scheduled` | `#182`가 워커 5종을 한 번에 다룬다(§11-4) |

### 15.5 완료 후 순서

```text
S9 커밋
→ ./harness test-run --id TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ
→ templates/test-report.md 기반 보고서 작성
→ ./harness sync
→ ./harness check && ./harness pr-ready --project-tests
→ /harness-pr
```

PR 본문에는 §10.3의 계약 변경, §11-1의 실행 계획 확인 결과, §12-3·§12-4의 미결 항목,
그리고 실행하지 못한 검증이 있다면 `AGENTS.md` §10의 5개 항목을 기록한다.
