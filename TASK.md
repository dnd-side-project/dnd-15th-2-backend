# GitHub Issue #79 Task Contract

> Generated at: `2026-08-08T13:36:52+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `답변 열람 범위 확대 — 공감 자격, 수신함 2카테고리, 답변 조회 공통화`
- GitHub Issue: `#79`
- Branch: `feat/gh-79-answer-visibility-recipients`
- Base branch: `main`
- 선행 Issue: `#78` `마이그레이션과 매핑 갱신 — 2026-08-07 스키마 개정 반영` — PR #84로
  이미 `main`에 merge됨(`13aaa75`). 이 브랜치는 그 시점의 `origin/main`에서 분기했다.
- 설계 근거: `docs/product/data-model/direction_communication.dbml` 2026-08-07 개정
  (81~82행 열람 자격, 63~65행 `inbound_bearing_deg`·`distance_m`·`answers_read_at`,
  852행 `answer_reaction` 자격), vault ADR 0002(답변 격리 폐기).
  #78이 V8로 스키마를 이미 옮겼으므로 이번 이슈에서 마이그레이션은 만들지 않는다.

## Objective

답변이 질문자 1명 전용에서 그 질문글의 수신 자격자 전원 공개로 바뀌었다(2026-08-07
개정, ADR 0002). #78이 스키마와 매핑을 옮겼지만 조회·공감 계층은 아직 옛 전제 위에
있다 — `답변한` 카테고리가 조회되지 않고, 수신 자격자가 답변을 볼 경로가 없으며,
수신함 카드가 발송자 기준 방향을 보여준다. 이 이슈는 그 세 계층을 새 전제로 옮긴다.

`#69`와 마찬가지로 API 계층은 만들지 않고 service·repository 메서드까지만 제공한다.

## Scope

### 열람 자격 판정 — `PostAnswerQueryRepository` 신설

`JdbcSentPostQueryRepository.findAnswers`(질문자 전용)를 뷰어 기준의 신규
`feed.repository.PostAnswerQueryRepository`로 이관한다. `SentPostQueryRepository`에서
`findAnswers`와 `AnswerCursor`를 제거하고, `SentPostQueryService.answers`도 함께
옮긴다. 이관이지 병존이 아니다 — 자격 규칙이 두 곳에 남으면 갈라진다.

`canViewAnswers(viewerId, postId, at)`의 판정 규칙은 DBML 81~82행에서 온다.

- 질문글 작성자(`direction_post.sender_id`): 항상 열람 가능. 만료 후에도 유지한다
  (DBML 679행 — 만료는 새 답변만 차단한다).
- 수신자(`post_recipient`):
  - `ANSWERED`: 만료 후에도 유지한다. 답변한 사람은 자격을 잃지 않는다.
  - `AVAILABLE` / `DISCOVERED` / `OPENED` / `SKIP_PENDING`: `expires_at > at`일 때만.
    `SKIP_PENDING`은 되돌릴 수 있으므로 아직 자격을 유지한다.
  - `SKIPPED` / `EXPIRED` / `BLOCKED`: 자격 없음.
- `direction_post.deleted_at IS NULL`과 `dp.status = 'ACTIVE'`를 함께 요구한다.

시점 의존 판정이므로 DB 제약이 아니라 이 조회 계층이 강제한다(V8 상단 주석이
`#79`로 넘긴 부분이다). `SKIP_PENDING` 확정 워커와 만료 전이 배치가 아직 없으므로
`status`만 보지 않고 `expires_at`을 함께 본다 — 상태가 아직 `EXPIRED`로 전이되지
않았어도 만료된 수신자는 자격이 없다.

자격 없는 뷰어는 예외가 아니라 빈 결과를 받는다. 기존
`SentPostQueryService.answers`의 근거("질문글 존재 여부를 흘리지 않기 위함")를
그대로 승계한다.

### `AnswerReactionService.toggle` 자격 확대

- 자격을 "질문자만"에서 "질문자 또는 수신 자격자"로 교체한다.
  `PostAnswerQueryRepository.canViewAnswers`를 재사용해 판정을 한 곳에 둔다.
- 자기 답변 공감을 금지한다(`answer.author_id == reactorId`). V8의
  `ct_answer_reaction_reactor_can_view`가 최종 방어선이지만 `DEFERRABLE INITIALLY
  DEFERRED`라 commit 시점에야 드러나므로, 기존 주석의 근거대로 여기서 먼저 막는다.
- `AnswerErrorCode.INELIGIBLE_REACTOR` 문구를 새 자격으로 갱신하고
  `docs/error-codes.md` 4절(`ANS`)을 동기화한다. 신규 코드는 만들지 않는다 —
  자격 위반과 자기 답변 공감 모두 같은 코드에 `field`/`reason`으로 구분한다
  (`AnswerErrorCode` 상단 주석의 "값 단위 검증은 코드를 늘리지 않는다" 규칙).

### 수신함 2카테고리

- `feed.view.InboxCategory` 신설: `UNANSWERED` / `ANSWERED`.
- `JdbcInboxQueryRepository`의 상태 집합을 카테고리별로 나눈다.
  - `UNANSWERED`: `AVAILABLE`, `DISCOVERED`, `OPENED`, `SKIP_PENDING`
  - `ANSWERED`: `ANSWERED`
  - 두 카테고리 모두 `dp.expires_at > :at`을 요구한다 — 답변한 질문글도 만료
    전까지만 목록에 남는다.
- `InboxQueryRepository.findInbox` / `InboxQueryService.list`에 카테고리 인자를
  추가한다.
- 슬롯 계산은 건드리지 않는다. `ANSWERED`의 용량 해제는 V8 이전과 같고
  (`ct_post_recipient_capacity_release`), 이번 변경은 행을 목록에 태우는 방식만
  바꾼다. 회귀만 테스트로 확인한다.

### 수신자 기준 방향과 거리

- `InboxCard`가 `matched_bearing_deg`(발송자 기준) 대신
  `inbound_bearing_deg`(수신자 기준)를 쓴다. 필드명을
  `matchedBearingDegrees` → `inboundBearingDegrees`로 바꾼다.
- 거리는 `distance_m`(정확 거리)을 노출하되 근거리 하한 미만이면 정확 거리를 감추고
  `distance_band`만 노출한다. 두 값은 상호 배타이며 정확히 하나만 non-null이다.
  `InboxCard`의 compact constructor가 이 불변식을 강제한다.
- 하한 판정은 Java mapper가 아니라 SQL의 `CASE`에서 한다. 하한 미만이면 정확
  거리가 애초에 `ResultSet`에 실리지 않아, 조회 계층이 실수로 노출할 경로가
  구조적으로 없다. V8 백필이 `distance_m = 0`을 고른 근거("조회 계층이 조작된
  정확 거리를 보여줄 길을 원천적으로 막는다")와 같은 방향이다.
- 하한은 `qello.feed.near-distance-floor-m`(기본 10000) 설정값으로 둔다.
  `DirectionReceiveProperties`가 수신 상한을 다루는 방식과 같다 — 운영 설정값을
  코드 상수로 박지 않는다.

### 수신함 카드의 답변·공감 수

`InboxCard`·`InboxDetail`에 `answerCount`, `reactionCount`, `unreadAnswerCount`를
추가하고, 폐기된 "의도적으로 포함하지 않는다" javadoc을 갱신한다.

- `answerCount`: 그 질문글의 `PUBLISHED` 답변 수. 뷰어 본인의 답변도 포함한다.
- `reactionCount`: `post_reaction` 수(질문글 공감). DBML 840행이 "공감 수는 질문글
  작성자에게만 노출한다"고 했으나 이슈 #79가 수신함 카드에 `reactionCount`를
  명시하므로 이슈를 따른다. 판단 근거가 갈리는 지점이라 아래 `Assumptions`에
  적어 리뷰 대상으로 남긴다.
- `unreadAnswerCount`: `post_recipient.answers_read_at` 이후 공개된 답변 수.
  **뷰어 본인의 답변은 세지 않는다** — 자기가 쓴 답변이 자기 배지에 `새 답변`으로
  잡히면 안 된다.
- 세 집계 모두 **수신자 관점** 차단 필터를 적용한다
  (`ub.blocker_id = pr.recipient_id`). 기존 `SentPostCard`의 집계가 질문자 관점
  (`blocker_id = dp.sender_id`)을 쓰는 것과 대칭이다.

### `AnswerCard` 뷰어 기준화

- `reactionCount`(그 답변이 받은 공감 수)와 `editedAt`을 추가한다.
- `reactedByMe`를 질문자 기준에서 뷰어 기준으로 바꾼다. V8이 PK를
  `(answer_id, reactor_id)` 복합으로 바꿨으므로 "답변당 한 행, 그 주체는 질문자뿐"
  이라던 기존 javadoc 근거는 폐기됐다.
- 차단 필터를 뷰어 관점(`ub.blocker_id = :viewerId`)으로 바꾼다.

### 수신자별 답변 읽음 기준선

- `PostRecipient.markAnswersRead(at)` 도메인 메서드. `ck_post_recipient_answers_read_at`
  (`answers_read_at >= matched_at`)을 도메인에서도 검증한다.
- `PostRecipientRepository.advanceAnswersReadAt(id, at)` — `GREATEST` 단일 UPDATE.
  `DirectionPostRepository.advanceAnswersReadAt`과 같은 이유다(순서가 뒤바뀌어
  도착한 요청이 이미 기록된 더 늦은 시각을 덮어쓰지 않게 한다).
- `PostRecipientService.markAnswersRead(recipientId, postRecipientId, at)` — 소유권을
  쿼리 조건에 포함하는 기존 `load()` 경로를 재사용한다.

## Explicit exclusions

- 수신함 방향 칩 집계 (#80)
- 답변 수정·삭제 쓰기 경로 — V8이 만든 `edited_at`/`edit_count`는 읽기만 한다.
- controller, DTO, API 문서, endpoint — 이번 회차도 service 계층까지다.
- 만료 전이 배치, `SKIP_PENDING` 확정 워커
- 신규 마이그레이션 — #78의 V8을 그대로 쓴다. DB 변경 없음.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Assumptions

구현을 위해 확정한 값이며 리뷰에서 뒤집힐 수 있다.

- `CONFIRMED` — 수신함 카드의 `reactionCount`는 질문글 공감(`post_reaction`) 수다.
  저장소 DBML 840행("공감 수는 질문글 작성자에게만 노출한다")은 낡은 사본이었다.
  vault 원본이 2026-08-08자로 정정됐다: "공감 수는 그 질문글을 볼 수 있는 사람
  전원(질문자 + 수신 자격자)에게 노출한다"(ADR 0002). 이슈 #79와 일치한다.
  §5(문서 재동기화)에서 저장소 DBML·ERD를 이 정정본으로 갱신한다.
- `ASSUMED` — `unreadAnswerCount`에서 뷰어 본인의 답변을 제외한다. 이슈에 명시가
  없으나 `새 답변 n개` 배지의 목적상 자기 답변은 새 답변이 아니다.
- `ASSUMED` — `answerCount`는 뷰어 본인의 답변을 포함한다(총 답변 수).
- `ASSUMED` — 근거리 하한 10000m. DBML 31·753·754행의 "근거리 하한(10km)"에서
  왔고 설정값으로 뺐다.
- `CONFIRMED` — 열람 자격 규칙은 DBML 81~82행에 명시돼 있다.
- `CONFIRMED` — `schema-manifest.md`의 DBML 행 SHA-256(`3b443c4b…`)이 실제 커밋된
  파일(`fb39599f…`)과 불일치한다. #78이 남긴 결함이다. §5에서 재계산해 바로잡는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `PostAnswerQueryRepository` 자격 판정과 답변 조회 이관 | Feed executor | 시점 의존 자격(SKIPPED·만료)·차단 필터 관점 리뷰 |
| `AnswerReactionService` 자격 확대 | Answer executor | 자기 답변 금지·지연 trigger 사전 검증 일치 리뷰 |
| 수신함 2카테고리와 카드 집계 | Feed executor | 슬롯 회귀·`ANSWERED` 만료 경계 리뷰 |
| 방향·거리 노출 | Feed executor | 정확 거리 하한 노출 경계 리뷰 |
| 수신자별 읽음 기준선 | Direction executor | `GREATEST` 경합·`ck_..._answers_read_at` 리뷰 |
| 단위/통합 테스트 | Test orchestrator | 자격 상실·재공감·집계 회귀 리뷰 |

## Existing user-owned changes

- `./harness start` 직전 `git status --short`는 비어 있었다. 보존할 다른 사람의
  미커밋 변경이 없다. `TASK.md`는 `h task-init --replace`로 #79 계약을 새로 썼다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

- Docker가 사용 가능하므로 Testcontainers 기반 통합 테스트를 로컬에서 실행한다.
  `./gradlew test`와 `./gradlew integrationTest`를 모두 통과시킨다.

## Completion criteria

- [x] 질문자와 수신 자격자가 같은 답변에 각각 공감할 수 있고, 자기 답변에는 불가하다
      (`InboxSentPostWriteIntegrationTest.eligibleRecipientCanReactToAnotherRecipientsAnswer`,
      `.answerAuthorCannotReactToOwnAnswer`)
- [x] 답변한 질문글이 만료 전까지 `답변한` 카테고리에 남는다
      (`InboxQueryIntegrationTest.answeredItemsStayInAnsweredCategoryUntilExpiry`)
- [x] 수신함 카드의 방향이 수신자 기준이며 `matched_bearing_deg`와 다르다
      (`InboxQueryIntegrationTest.listsOnlyUnhandledPostsInUnansweredCategory`)
- [x] 10km 미만은 정확 거리 대신 `distance_band`가 나온다
      (`InboxQueryIntegrationTest.exposesExactDistanceAtAndAboveFloorOnly`)
- [x] 수신 자격자가 그 질문글의 답변 전체를 조회할 수 있다
      (`PostAnswerQueryIntegrationTest.senderAndEligibleRecipientCanViewAnswersButOutsiderCannot`)
- [x] `SKIPPED`이거나 답변 없이 만료된 수신자는 답변 내용도 개수도 받지 못한다
      (`PostAnswerQueryIntegrationTest.skippedRecipientCannotViewAnswersAtAll`,
      `.timeBoundRecipientLosesEligibilityAfterExpiryRegardlessOfStatus`)
- [x] 슬롯은 여전히 `답변 안 한` 것만 센다(`ANSWERED`는 용량 해제 유지) — 슬롯 로직은
      건드리지 않았고 기존 `RecipientReceiveState` 관련 통합 테스트가 회귀 없이 통과
- [x] controller와 endpoint를 추가하지 않는다
- [x] `./gradlew test`와 `./gradlew integrationTest`가 통과한다 (단위 156개, 통합 138개)
- [x] `./harness pr-ready --project-tests`가 통과한다
