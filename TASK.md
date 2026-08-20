# GitHub Issue #176 Task Contract

> Generated at: `2026-08-20T14:35:08+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `알림함 읽기 API`
- GitHub Issue: `#176` (상위 `#183`)
- Branch: `feat/gh-176-notification-inbox-read`
- Base branch: `main` (`028a863`)
- 선행 이슈: 없음. `#183`의 sub-issue A이며 B~G(`#177`~`#182`)가 이 이슈를 선행으로 둔다.
- 설계: `docs/product/NOTIFICATION_INBOX_DESIGN.md` §4~§15. 이 문서가 계약의 상세다.
- Test plan: `docs/test-plans/gh-176-TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ.md`
  — 단위 21개, 통합 32개. `2026-08-20T14:47:35+09:00` `Byuntil` 승인 완료
  (`Status: Approved`). 다음 단계는 §15.2 S1부터 순서대로 구현.

## Objective

- `notification` 패키지에 service·web 계층이 없다. `RecipientNotificationFanOutWorker`가
  만드는 `DIRECTION_POST_RECEIVED` 알림이 원장에는 쌓이지만 HTTP로 노출된 적이 없어,
  지도 홈의 알림 버튼과 알림함 화면이 호출할 경로가 하나도 없다.
- F07은 알림을 ①알림함(기록) ②인앱 신호(상태) ③푸시(전달 시도) 세 자리로 나누고
  "①②③은 서로를 대신하지 않는다"고 규정한다. 현재 fan-out 워커는 preference가 꺼진
  사용자에게 `notification` 행 자체를 만들지 않아 ①과 ③을 한 게이트에 묶어 두었다.
- 알림함 읽기 경로 5개를 열고, preference 게이트를 delivery 생성 직전으로 옮겨
  "푸시를 전부 꺼도 알림함은 채워진다"는 규칙을 성립시킨다.

## Scope

1. **`notification.view` 신규** — `NotificationCard`, `NotificationTargetKind`,
   `NotificationTargetState`, `NotificationListing`, `NotificationTargetDecision`,
   `UnreadSignal`.
2. **`notification.repository` 신규** — `NotificationInboxQueryRepository`,
   `NotificationSeenStateRepository`와 각 jdbc 구현,
   `repository/jdbc/sql/NotificationInboxQuerySql`.
3. **`notification.service.NotificationInboxService` 신규** — 목록, 미읽음 신호,
   열람 기준선 전진, 줄 단위 읽음, 진입 판정.
4. **`notification.web` 신규** — `NotificationApiSpec`, `NotificationController`,
   `web/response/*`.
   - `GET /api/v1/notifications` — `cursorCreatedAt`·`cursorNotificationId`·`limit`
   - `GET /api/v1/notifications/unread-count`
   - `PUT /api/v1/notifications/seen`
   - `PUT /api/v1/notifications/{notificationId}/read`
   - `GET /api/v1/notifications/{notificationId}/target`
5. **`V23__add_notification_inbox_read_state.sql`** — `notification_seen_state` 테이블,
   `notification_recipient_feed_idx` 부분 인덱스. `notification` 테이블 컬럼은
   바꾸지 않는다.
6. **`NotificationErrorCode` 확장** — `NOT-APP-001`, `NOT-APP-002`, `NOT-DOM-004`,
   `NOT-VAL-006`, `NOT-VAL-007`. `docs/error-codes.md` §11 반영.
7. **계정 자격 게이트 승격** — `feed.service.AccountEligibilityGate`를
   `account.service`로 옮기고 `Account requireActiveUser(long)`를 반환하게 바꾼다.
   `feed.service`에는 `FeedErrorCode`로 번역하는 package-private 어댑터를 남긴다.
8. **preference 게이트 재배치** — `RecipientNotificationFanOutWorker.isEligible`에서
   `isPreferenceEnabled`를 떼어 `persistPendingDeliveries` 직전으로 옮긴다.
9. 단위 테스트, PostgreSQL 통합 테스트, 테스트 계획·보고서, `docs/api/openapi.json`
   재생성.

## Design decisions (2026-08-20 확정)

설계 근거는 `NOTIFICATION_INBOX_DESIGN.md`의 해당 절에 있다. 아래는 이 브랜치에서
고정된 결정만 옮긴 것이다.

1. **읽음 모델은 두 계층이다**(§5). 점은 `notification_seen_state.seen_at`, 줄은
   `notification.status`·`read_at`. 하나로 합치면 알림함을 한 번 여는 순간 모든 줄이
   `READ`가 되어 어떤 줄이 새 것이었는지 잃는다. `seen_at`은 `GREATEST`로만 전진한다
   (#170 `advanceAnswersReadAt`과 같은 계약).
2. **알림함은 `notification`이 소유한다**(§6.1). 알림 6종 중 4종이 방향 수신함과
   무관하므로 `feed`에 두면 N2에서 `feed → question`·`feed → safety` 참조가 새로 생긴다.
3. **계정 자격 게이트는 복제하지 않고 승격한다**(§6.2, §15.3 S1). 승격된 게이트는
   `Account requireActiveUser(long, Supplier<RuntimeException> notFound,
   Supplier<RuntimeException> notEligible)` 형태로, 호출부가 던질 예외를 직접 넘긴다.
   `account`가 어떤 오류 코드 체계도 알 필요가 없고 `AccountErrorCode`에 403 코드를
   새로 만들지 않아도 된다. `feed` 어댑터는 기존 `void require(long)` 시그니처를
   유지하므로 `InboxApplicationService`·`FeedInteractionApplicationService` 호출부는
   바뀌지 않고, `FED-APP-001`·`FED-APP-002` 응답 계약도 그대로다.
4. **대상 생존 판정은 목록과 진입 두 자리에 둔다**(§7.1). 목록에서 끝내면 알림함을 열고
   수분 뒤 톱할 때의 만료를 놓치고, 목록에서 생략하면 만료된 줄을 표시할 수 없다.
   두 자리가 같은 판정 규칙을 쓰고 진입 API가 한 줄만 재판정한다.
5. **판정 우선순위는 `GONE > BLOCKED > HIDDEN > EXPIRED > AVAILABLE`**(§7.2).
   삭제된 대상에 차단 이유를 노출하면 상대의 차단 사실이 새어 나간다.
6. **`REVOKED`·`DISMISSED` 줄은 목록에서 제외한다**(§7.2). `ANSWER_REPORT_DESIGN.md`
   §5.4의 전역 숨김 트랜잭션이 만드는 `REVOKED`는 "볼 수 없게 된 대상"이 아니라
   "회수된 기록"이다.
7. **cursor는 불투명 토큰이 아니라 명시적 두 파라미터다**(§8.2, #170 결정 2). 두
   파라미터는 함께 지정하거나 함께 생략한다. 한쪽만 오면 `NOT-VAL-007`.
   `limit` 기본 20·상한 50, `nextCursor`는 반환 건수가 `limit`과 같을 때만 채운다.
8. **응답에 본문·닉네임·계정 식별자·위치를 싣지 않는다**(§8.2). 알림 문구는 6종 모두
   익명이므로 `type`만으로 클라이언트가 조립한다. F07의 잠금화면 규칙과 기존 수신함
   규칙을 알림함까지 같은 기준으로 올린다.
9. **남의 알림은 존재를 노출하지 않고 404**(§8.5, `NOT-DOM-004`).
10. **preference는 푸시만 막고 기록은 남긴다**(§10). 차단은 알림함 줄도 만들지 않는
    것이 맞고 preference는 성격이 다르다. 둘을 한 게이트에 묶어 둔 것이 결함이었다.
11. **`unreadCount`는 정확한 수를 반환하고 절단은 클라이언트가 한다**(§12-1 확정).
    `notification_recipient_feed_idx` 부분 인덱스가 `COUNT`를 커버하고, 초기 사용량에서
    미읽음이 수백 건까지 쌓이지 않는다. 서버 절단이 필요해지면 응답에 필드를 더하는
    후속 변경으로 처리한다.
12. **목록의 `state`에도 `BLOCKED`를 싣는다**(§11-1 확정). 목록 쿼리에 `user_block`
    `EXISTS` 2개가 붙는다. `limit` 상한 50과 부분 인덱스로 제한하고 통합 테스트에서
    실행 계획을 확인한다. 인덱스가 선택되지 않으면 차단 판정을 진입 API로만 옮기는
    대안이 있으며, 그 전환은 별도 결정으로 기록한다.

## Explicit exclusions

- **묶음 표시**(`새 답변 4개`) — [E] `#180`. `uq_notification_recipient_dedup`이
  1행=1알림을 강제하므로 묶음은 표시 계층이 아니라 발행 계층의 설계 대상이다.
  N1의 목록은 1행=1줄이다.
- **알림 설정 조회·변경** — [C] `#178`. 이 이슈는 preference를 읽지도 않는다.
- **푸시 토큰 등록과 발송 파이프라인, 잠금화면 문구** — [D] `#179`.
- **카드 배지와 탭 상단 카운터** — [F] `#181`.
- **fan-out 확장**(`ANSWER_RECEIVED`, `ANSWER_REACTED`, `QUESTION_PROPOSAL_REVIEWED`,
  `QUESTION_RECOMMENDED` 소비자) — [B] `#177`. N1 완료 후에도 알림함에는
  `DIRECTION_POST_RECEIVED` 한 종류만 실린다.
- **워커 주기 실행 활성화** — [G] `#182`. 저장소 전체에 `@Scheduled`가 0개이고 이는
  의도된 관례다(`RecipientExpirationSweepWorker` 주석). N1은 `@Scheduled`를 추가하지
  않는다.
- **`notification.report_id` 컬럼과 `REPORT` 대상 매핑** — `#155`가 소유한다.
  `NotificationTargetKind`에 자리만 열어 두고 값은 추가하지 않는다.
- **알림 삭제·전체 읽음 버튼, `DISMISSED` 전이 UI** — F07에 없다.
- **알림 보존 기간 정책**(§12-4) — N1 범위 밖. `UNKNOWN`으로 남긴다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Open questions

- **§12-3 `fallback = INBOX`의 착지점** — 만료된 수신 질문글 알림을 톱했을 때 수신함
  목록으로 보낼지 만료 상세로 보낼지는 디자인 결정이다. `UNKNOWN`. 서버는
  `fallback` 값만 반환하고 라우팅은 클라이언트가 정하므로 이 이슈를 막지 않는다.
- **§12-2 방해 금지 시간의 저장 위치** — `notification_preference` PK가
  `(notification_type, user_id)`라 `quiet_start`/`quiet_end`가 종별로 6벌 생긴다.
  F07은 사용자당 한 쌍이다. `BLOCKED`: N3(`#178`) 설계에서 결정한다. N1은 건드리지
  않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `notification.view`·`repository`·`service`·`web` 신규, `V23` 마이그레이션, `NotificationErrorCode` 확장 | Feature executor | 남의 알림에 닿는 경로가 없는지(404, 존재 비노출), `REVOKED`·`DISMISSED`가 목록에서 빠지는지, `seen` 반복·역순 호출이 기준선을 되돌리지 않는지, 응답에 본문·닉네임·계정 식별자·위치가 실리지 않는지, `targetState` 우선순위가 차단 사실을 새게 하지 않는지 |
| `AccountEligibilityGate` 승격과 `feed` 번역 어댑터 | Feature executor | `FED-APP-001`·`FED-APP-002` 응답 계약 불변, `account → feed` 역방향 의존 부재 |
| `RecipientNotificationFanOutWorker` preference 게이트 재배치 | Feature executor | preference off에서 `notification` 1건·`notification_delivery` 0건, 차단 게이트는 그대로 알림 자체를 막는지, 기존 테스트 3종의 계약 변경이 PR에 명시됐는지 |

## Existing user-owned changes

- `origin/main`(`028a863`)에서 새로 분기했다. 분기 시점의 유일한 미추적 파일은
  `docs/product/NOTIFICATION_INBOX_DESIGN.md`이며, `./harness start`가 clean worktree를
  요구해 잠시 옮겼다가 브랜치 생성 후 같은 경로에 되돌렸다. 내용은 바뀌지 않았다.

## Contract changes to existing tests

preference 게이트 재배치로 "preference off면 아무것도 생기지 않음"이 "알림함 행은
생기고 `delivery`만 0건"으로 바뀐다. 아래 3종을 갱신하고 PR 본문에 계약 변경을
명시한다.

- `src/test/java/.../notification/fanout/RecipientNotificationFanOutWorkerTest.java`
- `src/integrationTest/java/.../NotificationFanOutPersistenceIntegrationTest.java`
- `src/integrationTest/java/.../RecipientNotificationFanOutWorkerIntegrationTest.java`

## Validation

```bash
./gradlew test --tests "com.dnd.qello.notification.*" --console=plain
./gradlew test --tests "com.dnd.qello.feed.*" --console=plain
./gradlew test --tests "com.dnd.qello.account.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.Notification*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.Inbox*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --console=plain
./harness test-run --id TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] 엔드포인트 5개가 동작하고 `docs/api/openapi.json`에 반영된다.
- [x] 남의 알림 조회와 읽음이 `NOT-DOM-004`(404)를 반환하고 존재를 노출하지 않는다.
- [x] `REVOKED`·`DISMISSED` 줄이 목록과 미읽음 카운트에 실리지 않는다.
- [x] `PUT /notifications/seen`을 반복·역순 호출해도 `notification_seen_state.seen_at`이
      뒤로 가지 않는다.
- [x] `PUT /notifications/{id}/read`가 멱등이고, `REVOKED` 줄에는 `NOT-DOM-003`(409)이다.
- [x] `targetState`가 만료·삭제·운영 숨김·차단을
      `GONE > BLOCKED > HIDDEN > EXPIRED > AVAILABLE` 우선순위로 판정한다.
- [x] 목록 cursor 페이징이 같은 `created_at` 다건에서도 정렬이 안정적이고 중복·누락이 없다.
- [x] 목록 쿼리가 `notification_recipient_feed_idx`를 사용한다(통합 테스트에서 실행 계획 확인).
- [x] preference가 꺼진 사용자에게 `notification` 1건이 생기고 `notification_delivery`는 0건이다.
- [x] 차단 관계가 있으면 preference와 무관하게 `notification` 행 자체가 생기지 않는다.
- [x] `FED-APP-001`·`FED-APP-002` 응답이 게이트 승격 후에도 바뀌지 않는다.
- [x] 응답에 질문·답변 본문, 닉네임, 계정 식별자, 정확 위치, 대략 지역·거리가 실리지 않는다.
- [x] 모든 테스트에 `@DisplayName`과 클래스 헤더(ISO 8601 생성 시각, `Source scenario`)가 있다.
- [x] `./harness check`와 `./harness pr-ready --project-tests`가 통과한다.
