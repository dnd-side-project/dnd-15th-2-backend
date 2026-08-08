# Test Plan: TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS

> Created at: `2026-08-08T14:19:39+09:00`
> GitHub Issue: `#79`
> Status: Approved

## 1. Objective

2026-08-07 제품 개정(ADR 0002)으로 답변이 질문자 1명 전용에서 그 질문글의 수신
자격자 전원 공개로 바뀌었다. #78(PR #84)이 스키마(V8)와 매핑을 이미 옮겼지만
조회·공감 계층은 아직 옛 전제 위에 있다 — 수신 자격자가 답변을 볼 경로가 없고,
`답변한` 질문글이 수신함에서 사라지며, 카드가 발송자 기준 방향을 보여준다.

이 계획이 검증하는 실패 모드는 세 가지다.

1. **열람 자격 판정이 잘못된다** — 시점 의존 규칙(넘김·만료로 자격 상실)을 DB
   제약이 아니라 조회 계층이 강제하므로, `status`만 보고 `expires_at`을 놓치면
   이미 만료됐지만 아직 `EXPIRED`로 전이되지 않은 수신자에게 계속 답변이 보인다.
2. **관점이 뒤바뀐다** — 수신함 카드 집계(`answerCount` 등)는 수신자 관점 차단
   필터를, `AnswerCard`는 뷰어 관점 필터를 써야 한다. 기존
   `JdbcSentPostQueryRepository`의 질문자 관점 필터를 그대로 복사하면 관점이
   섞인다.
3. **기존 테스트가 옛 규칙을 고정한 채로 통과한다** — `SentPostQueryIntegrationTest.
   onlySenderCanReadAnswers`와 `InboxQueryIntegrationTest.
   excludesTerminalRecipientStatuses`는 지금 "수신자는 답변을 못 본다"와
   "`ANSWERED`는 수신함에서 빠진다"를 명시적으로 검증하고 있다. 이 두 시나리오를
   놓치고 넘어가면 회귀가 아니라 요구사항 미반영인데도 그린으로 보고된다.

## 2. Scope

### Included

- `feed.repository.PostAnswerQueryRepository` 신설 — 열람 자격 판정
  (`canViewAnswers`)과 답변 조회(`findAnswers`)를 한 곳에 둔다.
  `SentPostQueryRepository.findAnswers`/`AnswerCursor`를 이관(제거)한다.
- `AnswerReactionService.toggle`의 자격을 "질문자만"에서 "질문자 또는 수신
  자격자, 자기 답변 제외"로 확장. `AnswerErrorCode.INELIGIBLE_REACTOR` 문구 갱신.
- `feed.view.InboxCategory`(`UNANSWERED`/`ANSWERED`) 신설과
  `JdbcInboxQueryRepository`의 카테고리별 상태 분리.
- `InboxCard`/`InboxDetail` — `inboundBearingDegrees`(수신자 기준 방향, 기존
  `matchedBearingDegrees` 대체), 근거리 하한 기준 정확 거리/구간 배타적 노출,
  `answerCount`/`reactionCount`/`unreadAnswerCount`.
- `AnswerCard` — `reactionCount`, `editedAt`, 뷰어 기준 `reactedByMe`와 차단 필터.
- `PostRecipient.markAnswersRead`(도메인), `PostRecipientRepository.
  advanceAnswersReadAt`(`GREATEST` 단일 UPDATE), `PostRecipientService.
  markAnswersRead`.
- `docs/error-codes.md` `ANS-DOM-004` 문구 동기화.
- `docs/product/data-model/*` 3종 재동기화(vault 2026-08-08 정정본 반영,
  `schema-manifest.md`의 기존 DBML SHA-256 불일치 수정 포함) — JUnit 검증 대상은
  아니며 §10에 수동 확인 항목으로 남긴다.

### Excluded

- 수신함 방향 칩 집계 — #80
- 답변 수정·삭제 쓰기 경로 — `edited_at`/`edit_count`는 읽기만 한다
- controller, DTO, API 문서, endpoint
- 만료 전이 배치, `SKIP_PENDING` 확정 워커 — 없는 상태를 전제로 `expires_at`을
  조회 시점에 직접 비교한다
- 신규 마이그레이션 — V8을 그대로 쓴다. DB 변경 없음

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #79 | 공감 자격 확대, `InboxCategory`, 수신자 기준 방향·거리, 카드 집계, `answers_read_at` 기준선, `PostAnswerQueryRepository` 신설, `AnswerCard` 뷰어 기준화, 수신자 관점 차단 필터, controller 미추가 |
| TASK.md 완료 조건 | 질문자·수신 자격자 동시 공감 및 자기 답변 금지 / `답변한`이 만료 전까지 유지 / 수신함 방향이 수신자 기준 / 10km 미만 `distance_band` / 수신 자격자의 답변 전체 조회 / `SKIPPED`·만료 후 무자격 수신자는 내용·개수 모두 차단 / 슬롯은 `답변 안 한`만 집계 / controller 미추가 |
| vault DBML(2026-08-07, 81~82행) | 답변을 볼 수 있는 주체는 질문글 작성자와 그 질문글의 수신 자격자. 넘겼거나(`SKIPPED`) 답변 없이 만료된 수신자는 열람 자격을 잃는다. 시점 의존 판정은 조회 계층이 강제한다 |
| vault DBML(2026-08-08 정정, 840행) | 공감 수는 그 질문글을 볼 수 있는 사람 전원(질문자+수신 자격자)에게 노출한다. 저장소 사본(840행)의 "질문자에게만 노출"은 낡은 서술이며 이번 문서 재동기화로 함께 고친다 |
| V8 migration 상단 주석 | "지금 이 답변을 볼 수 있는가"는 DB가 아니라 조회 계층이 강제한다. DB가 강제하는 것은 "수신자 집합에 속하는가"라는 정적 사실뿐이다 |
| `ct_answer_reaction_reactor_can_view`(V8) | `DEFERRABLE INITIALLY DEFERRED` — 자격 위반이 commit 시점에야 드러난다. 서비스가 사전 검증해야 호출자가 원인을 추적할 수 있는 위치에서 실패한다(기존 `AnswerReactionService` 클래스 javadoc 근거 승계) |
| `direction_post.expires_at`(DBML 679행) | 만료 후에도 질문자의 열람과 공감은 계속 가능하다 — 만료는 새 답변 작성만 차단한다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| `SentPostQueryIntegrationTest.onlySenderCanReadAnswers`(205행)와 `InboxQueryIntegrationTest.excludesTerminalRecipientStatuses`가 지금 옛 규칙("수신자는 못 본다", "`ANSWERED`는 빠진다")을 정상 통과로 고정하고 있다 | High — 이 두 시나리오를 놓치면 요구사항 미반영이 그린으로 보고된다 | High — 확실히 존재 | P0 | 두 파일 모두 새 규칙에 맞게 뒤집혀 있고, 리네임된 서비스로 이관된 것을 diff로 확인(§6 INT-001, INT-009) |
| `SKIP_PENDING` 확정 워커와 만료 전이 배치가 없다. `status`만으로 자격을 판정하면, 실제로는 만료됐지만 아직 `EXPIRED`로 전이되지 않은 `AVAILABLE`/`OPENED` 수신자가 계속 답변을 볼 수 있다 | High — 만료 후 자격 상실이라는 핵심 요구사항이 조용히 깨진다 | Medium — 배치가 없는 한 항상 발생 가능한 상태 | P0 | `expires_at <= at`인 비-`ANSWERED` 수신자가 `canViewAnswers`에서 거절되는지(§6 INT-002), 반대로 `ANSWERED`는 만료 후에도 유지되는지(§6 INT-003) |
| 자기 답변 공감 금지가 두 곳에 있다 — 서비스의 사전 검증과 V8의 지연 트리거. 서비스 사전 검증이 트리거보다 느슨하면(예: `author_id` 비교 누락) 트리거가 잡아도 사용자에게는 원인 불명 500/트랜잭션 롤백으로 보인다 | Medium — 클래스 javadoc이 명시한 "호출자가 원인을 추적할 수 없는 위치에서 실패하지 않게 한다"는 목적이 무력화된다 | Medium | P0 | 서비스 호출 단계에서 `INELIGIBLE_REACTOR`로 즉시 거절되는지 확인(§6 INT-004). 트리거 자체는 이미 #78에서 검증됐으므로 재검증하지 않는다 |
| 수신함 카드 집계와 `AnswerCard`가 관점이 다른 차단 필터를 요구한다 — 수신함은 `blocker_id = pr.recipient_id`(보는 사람 기준), `AnswerCard`는 `blocker_id = :viewerId`. 기존 `JdbcSentPostQueryRepository`의 질문자 관점 필터(`blocker_id = dp.sender_id`)를 복사하면 관점이 섞인다 | High — 차단 회피 경로가 생긴다(차단한 사람의 콘텐츠가 그대로 보임) | Medium — 새 코드를 기존 패턴에서 복사할 때 흔한 실수 | P0 | 수신자가 차단한 발신자의 질문글은 집계에서 빠지고(§6 INT-005), 뷰어가 차단한 답변 작성자의 답변은 `AnswerCard` 목록에서 빠짐(§6 INT-006, 관점 반전 확인 포함) |
| 근거리 하한(10km) 경계값 — `distance_m`과 `distance_band`는 DB에서 둘 다 NOT NULL이므로, "하한 미만이면 정확 거리 대신 구간만" 노출은 SQL `CASE`/mapper가 전적으로 책임진다. 정확히 하한값(10000m)일 때 이상/미만 어느 쪽으로 처리하는지 코드와 무관하게 우연히 맞을 수 있다 | Medium — 완료 조건에 명시된 항목("10km 미만은 distance_band")이므로 경계에서 틀리면 인수 실패 | Medium | P0 | 하한-1m/하한/하한+1m 세 지점에서 노출되는 필드가 정확히 하나이고 방향이 맞는지 확인(§6 INT-007) |
| `unreadAnswerCount`가 뷰어 본인의 답변을 세면 안 된다는 규칙은 이슈에 명시가 없는 가정(TASK.md `ASSUMED`)이다. 구현 시 `SentPostCard`의 기존 `unread_answer_count` 서브쿼리를 그대로 복사하면 작성자 필터가 없어 자기 답변도 세어버릴 수 있다 | Medium — 배지가 실제 새 답변이 아닌데도 뜬다 | Medium | P1 | 본인이 쓴 답변만 있는 상태에서 `unreadAnswerCount`가 0인지 확인(§6 INT-008) |
| 수신함 2카테고리 전환이 기존 슬롯(용량 해제) 로직에 영향을 주면 안 된다 — `ANSWERED`의 `capacity_released_at`는 V8 이전과 동일하게 트리거가 강제한다. 목록에 태우는 방식만 바뀌어야 하는데, 카테고리 필터링을 잘못 구현하면 슬롯 회수 자체를 건드릴 위험이 있다 | Medium — 수신 용량 계산이 깨지면 다른 기능(신규 매칭)에 파급된다 | Low — 이번 변경은 조회 계층만 건드리므로 직접 원인이 되긴 어렵다 | P1 | `RecipientReceiveState`/`recipient_receive_state` 관련 기존 통합 테스트가 이번 변경 후에도 그대로 통과(§6 INT-009는 회귀 확인만, 신규 assertion 없음) |
| `PostRecipientRepository.advanceAnswersReadAt`이 `DirectionPostRepository.advanceAnswersReadAt`과 같은 `GREATEST` 단일 UPDATE 패턴을 안 따르고 단순 `SET answers_read_at = :at`로 구현되면, 순서가 뒤바뀌어 도착한 요청이 이미 기록된 더 늦은 시각을 덮어쓸 수 있다 | Low — 배지 표시가 일시적으로 부정확해질 뿐 데이터 손실은 아니다 | Low | P2 | 늦은 시각을 먼저 기록한 뒤 이른 시각으로 재호출해도 값이 후퇴하지 않음(§6 INT-010) |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-UNIT-001 | `PostRecipient`가 `matchedAt` 이후 임의 시각으로 존재 | `markAnswersRead(at)` 호출(`at`이 `matchedAt` 이후) | 새 인스턴스가 `answersReadAt == at`을 가지며 `status`는 그대로 유지 — `DirectionPost.markAnswersRead`와 동일 패턴 | P0 | Executor 2 |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-UNIT-002 | 위와 동일 | `markAnswersRead(at)`에 `matchedAt`보다 이른 시각 전달 | `DirectionException`(`INVALID_TIME_ORDER`) — 기존 생성자의 `validateTimestamp(answersReadAt, ...)` 규칙과 새 메서드가 일치함을 메서드 호출 경로로 재확인 | P1 | Executor 2 |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-UNIT-003 | `markAnswersRead(at)` | `at`에 `null` 전달 | `DirectionException`(`REQUIRED_VALUE_MISSING`) — 다른 도메인 메서드(`open`, `answered`)의 `requireValue(at, "at")` 관례와 일치 | P2 | Executor 2 |

자격 판정(`canViewAnswers`)과 공감 확장 로직은 JOIN 기반 SQL과 여러 테이블의 상태
조합에 의존해 in-memory fake로는 실제 위험(§4 두 번째 행)을 재현할 수 없다. #78의
`AnswerReactionService` 선례(INT-011)와 동일하게 통합 시나리오로만 검증한다 — 이
계획에 서비스 계층 mock 기반 UNIT 시나리오를 두지 않는다.

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-001 | `PostAnswerQueryService`(신규), `PostAnswerQueryRepository` | 질문글 1건, 수신 자격자(`AVAILABLE`) 1명, 그 수신자의 `PUBLISHED` 답변 1건 | 질문자·수신 자격자·무관한 outsider 각각이 `answers(viewerId, postId, ...)` 호출 | 질문자·수신 자격자는 답변 1건을 받고, outsider는 빈 목록 — `SentPostQueryIntegrationTest.onlySenderCanReadAnswers`를 대체하는 신규 서비스의 정규 시나리오 | `@BeforeEach` delete |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-002 | 위와 동일 | 수신자 상태를 `AVAILABLE`로 두고 `expires_at`을 과거로 설정(아직 `EXPIRED`로 전이되지 않은 상태를 시뮬레이션) | 그 수신자가 `at > expires_at` 시점에 `answers(...)` 호출 | 빈 목록 — `status`가 아직 터미널이 아니어도 시점 기준으로 자격을 잃는다(§4 두 번째 리스크) | 동일 |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-003 | 위와 동일 | 수신자 상태 `ANSWERED`, `expires_at`을 과거로 설정 | 만료 이후 시점에 그 수신자가 `answers(...)` 호출 | 답변이 조회된다 — 답변한 사람은 만료돼도 자격을 잃지 않는다(완료 조건 "답변한 질문글이 만료 전까지 답변한 카테고리에 남는다"의 조회 계층 대응) | 동일 |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-004 | `AnswerReactionService`(재작성), `AnswerReactionRepository` | 질문글 1건, 질문자·수신 자격자(둘 다 자격 있음), 수신 자격자가 작성한 `PUBLISHED` 답변 1건 | (a) 질문자 `toggle`, (b) 수신 자격자 본인이 자기 답변에 `toggle`, (c) 자격 없는 outsider가 `toggle` | (a) 성공(true/false 토글), (b) `AnswerException`(`INELIGIBLE_REACTOR`, 자기 답변 사유)이 트랜잭션 commit 전에 즉시 발생, (c) 같은 에러코드로 즉시 거절 — `INT-004`가 §4 세 번째 리스크(서비스 사전 검증이 트리거보다 느슨하면 원인 불명 실패)를 직접 확인 | 동일 |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-005 | 신규/재작성 `InboxQueryIntegrationTest` 시나리오, `JdbcInboxQueryRepository` | 수신자 A가 질문글의 발신자를 차단, 다른 수신자 B는 차단하지 않음 | A와 B 각각 수신함 목록 집계(`answerCount` 등) 조회 | A는 그 항목 자체가 이미 빠지거나(기존 `hidesBlockedSenderPosts` 로직) 집계가 노출되지 않는다 — 관점이 `blocker_id = pr.recipient_id`(보는 사람 기준)임을 확인, 질문자 관점 필터를 복사한 경우와 구분되는 assertion | 동일 |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-006 | `AnswerCard`, `PostAnswerQueryRepository` | 질문글의 수신 자격자 중 한 명(뷰어)이 답변 작성자를 차단 | 뷰어가 `answers(...)` 호출 | 그 작성자의 답변이 목록에서 빠진다. 같은 fixture에서 차단하지 않은 다른 뷰어가 호출하면 보인다 — 관점이 `viewerId` 기준임을 두 뷰어를 대조해 확인 | 동일 |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-007 | `JdbcInboxQueryRepository` | `post_recipient.distance_m`을 하한-1m, 하한(10000m), 하한+1m 세 값으로 각각 삽입 | `inboxQueryService.list(...)` 조회 | 하한-1m: `distanceBand`만 채워지고 `distanceM`은 null. 하한과 하한+1m: `distanceM`만 채워지고 `distanceBand`는 null — 완료 조건 "10km 미만은 distance_band"의 정확한 경계 확인 | 동일 |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-008 | `JdbcInboxQueryRepository`(`ANSWERED` 카테고리) | 뷰어 본인이 작성한 답변만 있는 질문글(다른 사람 답변 없음), `answers_read_at`은 null | 그 항목의 `unreadAnswerCount` 조회 | 0 — 뷰어 본인의 답변은 세지 않는다(§4 다섯 번째 리스크, TASK.md `ASSUMED` 항목의 실제 동작 고정) | 동일 |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-009 | `InboxQueryIntegrationTest`(재작성), `SentPostQueryIntegrationTest`, `InboxSentPostWriteIntegrationTest`(기존 3개 파일) | 기존 fixture에 카테고리 인자·새 자격 규칙 반영 | 각 파일의 기존 스위트 재실행 | `excludesTerminalRecipientStatuses`(뒤집혀서 `ANSWERED`가 이제 `ANSWERED` 카테고리로 조회됨), `onlySenderCanReadAnswers`(이관된 서비스에서 수신 자격자도 조회됨), `nonSenderCannotReactToAnswer`(표시 문구만 갱신)·`answerAuthorCannotReactToOwnAnswer`(거절 사유가 자기 답변 금지로 재확인)를 제외한 나머지 assertion은 순수 회귀로 그대로 통과 | 동일 |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-010 | `PostRecipientRepository.advanceAnswersReadAt` | `post_recipient` 1건 | `advanceAnswersReadAt(id, t2)` 호출 후(`t2`가 늦은 시각) `advanceAnswersReadAt(id, t1)`(`t1 < t2`) 재호출 | 최종 `answersReadAt == t2`(후퇴하지 않음) — `DirectionPostRepository.advanceAnswersReadAt`과 동일한 `GREATEST` 보장 | 동일 |
| TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-011 | `PostRecipientService.markAnswersRead` | 수신 자격자 A의 수신 항목 1건, 무관한 outsider B | A가 `markAnswersRead(A, postRecipientId, at)` 호출은 성공, B가 같은 `postRecipientId`로 호출 | A는 갱신되고, B는 소유권 검증 실패(`DirectionException`, `RECIPIENT_NOT_FOUND`) — 기존 `load()` 소유권 경로 재사용 확인 | 동일 |

## 7. Cross-cutting scenarios

### Database and transactions

- `ct_answer_reaction_reactor_can_view`는 이미 #78에서 검증됐으므로 이 계획은
  재검증하지 않는다. 대신 INT-004가 서비스 계층의 **사전** 검증이 트리거가
  거절할 상황(outsider, 자기 답변)에서 트랜잭션 commit을 시도하지도 않고
  즉시 예외를 던지는지 확인한다 — `TransactionTemplate`으로 감싸지 않고
  서비스 메서드 호출만으로 예외가 나는지가 핵심이다(트리거까지 도달하면 이미
  서비스 사전 검증이 실패한 것).
- `advanceAnswersReadAt`(INT-010)은 `DirectionPostRepository.advanceAnswersReadAt`과
  동일하게 단일 `UPDATE ... SET answers_read_at = GREATEST(...)`문이어야 하며,
  애플리케이션 레벨 read-then-write(비교 후 조건부 UPDATE)로 구현하면 동시
  요청에서 경쟁 조건이 생긴다. 이 계획은 순차 호출로 최종 값만 확인하고 진짜
  동시 요청 테스트는 만들지 않는다(기존 `advanceAnswersReadAt` 선례도 동시성
  테스트를 두지 않았다).

### Concurrency and idempotency

- `AnswerReactionService.toggle`의 진짜 동시 toggle(같은 사용자가 두 트랜잭션에서
  동시 호출)은 이번 계획의 범위가 아니다 — #78 계획이 이미 이 경계를 repository
  계층 단위로 한정했고, 이번 변경은 자격 판정 로직만 바꾸지 toggle의 동시성
  보장 자체는 바꾸지 않는다.

### External APIs

- 해당 없음.

### Failure recovery and reconciliation

- 해당 없음 — 이 이슈는 마이그레이션이나 배치 복구를 다루지 않는다. `SKIP_PENDING`
  확정 워커와 만료 전이 배치가 없는 현재 상태를 전제로 조회 계층이 `expires_at`을
  직접 비교하는 것이 이 계획의 핵심 검증 대상이다(INT-002, INT-003).

## 8. Test data and isolation

- Fixtures: `PostgisContainerIntegrationTestSupport`(공유 Testcontainers PostGIS
  컨테이너)를 그대로 사용한다. 파일별 고유 `region_code`(`TEST-INBOXQ`,
  `TEST-REACT` 등 기존 관행)로 파일 간 오염을 막는다.
- Database isolation: 기본(public) schema, `@BeforeEach`에서 자식→부모 순서로
  관련 테이블 delete(기존 관행 유지).
- Clock/randomness: 고정 `Instant` 상수(`NOW` 등)만 사용. `Clock.systemUTC()`를
  테스트에서 직접 호출하지 않는다.
- External API doubles: 해당 없음.
- Cleanup: 전부 JDBC 기반 raw INSERT/DELETE(기존 `InboxQueryIntegrationTest`,
  `SentPostQueryIntegrationTest` 패턴 유지). `answer_reaction`은 JPA
  `saveAndFlush`/`deleteById`로 정리하는 기존 관행을 유지한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor 1 (자격 판정 신설과 답변 조회 이관) | `src/main/java/com/dnd/qello/feed/repository/PostAnswerQueryRepository.java`(신규), `.../feed/repository/jdbc/JdbcPostAnswerQueryRepository.java`(신규), `.../feed/service/PostAnswerQueryService.java`(신규), `.../feed/repository/SentPostQueryRepository.java`(findAnswers/AnswerCursor 제거), `.../feed/repository/jdbc/JdbcSentPostQueryRepository.java`(answerCard/findAnswers 제거), `.../feed/service/SentPostQueryService.java`(answers 제거), `.../feed/view/AnswerCard.java`, `src/integrationTest/java/com/dnd/qello/SentPostQueryIntegrationTest.java`(답변 조회 시나리오 이관), 신규 `src/integrationTest/java/com/dnd/qello/PostAnswerQueryIntegrationTest.java` | INT-001, INT-002, INT-003, INT-006 | `./gradlew integrationTest --tests "com.dnd.qello.PostAnswerQueryIntegrationTest" --tests "com.dnd.qello.SentPostQueryIntegrationTest"` |
| 2 | Executor 2 (공감 자격 확대와 수신자별 읽음 기준선, Executor 1의 `PostAnswerQueryRepository` 전제) | `src/main/java/com/dnd/qello/answer/service/AnswerReactionService.java`, `.../answer/error/AnswerErrorCode.java`, `.../direction/domain/PostRecipient.java`, `.../direction/repository/PostRecipientRepository.java`, `.../direction/repository/jdbc/JdbcPostRecipientRepository.java`, `.../direction/service/PostRecipientService.java`, `src/test/java/com/dnd/qello/direction/domain/DirectionDomainTest.java`, `src/integrationTest/java/com/dnd/qello/InboxSentPostWriteIntegrationTest.java`, `docs/error-codes.md` | UNIT-001, UNIT-002, UNIT-003, INT-004, INT-010, INT-011 | `./gradlew test --tests "com.dnd.qello.direction.domain.DirectionDomainTest"`, `./gradlew integrationTest --tests "com.dnd.qello.InboxSentPostWriteIntegrationTest"` |
| 3 | Executor 3 (수신함 2카테고리·카드 집계·방향/거리, Executor 1의 자격 판정 전제) | `src/main/java/com/dnd/qello/feed/view/InboxCategory.java`(신규, 작성 완료), `.../feed/view/InboxCard.java`, `.../feed/view/InboxDetail.java`, `.../feed/repository/InboxQueryRepository.java`, `.../feed/repository/jdbc/JdbcInboxQueryRepository.java`, `.../feed/config/FeedDistanceProperties.java`(신규, 작성 완료), `.../feed/service/InboxQueryService.java`, `src/main/resources/application.properties`(작성 완료), `src/integrationTest/java/com/dnd/qello/InboxQueryIntegrationTest.java` | INT-005, INT-007, INT-008, INT-009 | `./gradlew integrationTest --tests "com.dnd.qello.InboxQueryIntegrationTest"` |
| 4 | Executor 4 (문서 재동기화, JUnit 검증 대상 아님) | `docs/product/data-model/direction_communication.dbml`, `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md`, `docs/product/data-model/schema-manifest.md` | 해당 없음(§10 수동 확인 항목) | `shasum -a 256`으로 세 파일과 manifest 기록값 일치 확인 |

Executor 1이 먼저 `PostAnswerQueryRepository`를 만들어야 Executor 2(`AnswerReactionService`가
그 자격 판정을 재사용)와 Executor 3(수신함 집계가 같은 규칙을 따르는지 리뷰 시
대조)이 뒤따를 수 있다 — 진짜 병렬 실행은 Executor 2와 3 사이에서만 가능하다.
Executor 4는 나머지와 파일이 겹치지 않아 아무 때나 병렬 진행 가능하다.

## 10. Completion criteria

- [x] 모든 P0 시나리오 구현
- [x] 모든 테스트 메서드에 `@DisplayName`
- [x] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [x] 단위 테스트 통과 (156 tests, 0 failed)
- [x] 통합 테스트 통과 (137 tests, 0 failed)
- [x] 잠재 문제 분석 (`docs/reports/tests/gh-79-ANSWER-VISIBILITY-RECIPIENTS.md` §6)
- [x] 테스트 보고서 생성
- [x] (수동) `docs/product/data-model/schema-manifest.md`의 SHA-256 3개가 실제
      파일과 일치 — `#78`이 남긴 DBML 행 불일치(`3b443c4b…` vs 실제
      `fb39599f…`)를 `shasum -a 256`로 재확인하고 바로잡았다

## 11. Human approval

- Reviewer: Byuntil
- Decision: Approved
- Approved at: 2026-08-08
