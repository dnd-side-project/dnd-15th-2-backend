# GitHub Issue #177 Task Contract

> Generated at: `2026-08-20T18:50:09+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `알림 종류별 fan-out 확장`
- GitHub Issue: `#177` (상위 `#183`)
- Branch: `feat/gh-177-notification-fanout-expansion`
- Base branch: `feat/gh-176-notification-inbox-read` (`675be30`)
- 선행 이슈: `#176`. PR `#184`가 아직 `main`에 병합되지 않아 stacked branch로 진행한다.
- 설계: `docs/product/NOTIFICATION_INBOX_DESIGN.md`,
  `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md`의 알림 종류·수신자·이동 대상 계약.
- Test plan: `docs/test-plans/gh-177-TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION.md`
  (`Status: Approved`, 2026-08-20T19:20:22+09:00 사람 승인 완료).
- Test report: `docs/test-reports/gh-177-TEST-REPORT-GH-177-NOTIFICATION-FANOUT-EXPANSION.md`
  (`Result: BLOCKED`, 핵심 P0 일부를 보강했지만 사람 결정 또는 추가 PostgreSQL 증거가 필요한 범위 잔여).

## Objective

- 현재 인앱 알림 fan-out은 `RECIPIENTS_CONFIRMED`를 소비해
  `DIRECTION_POST_RECEIVED`만 만든다. 이미 발행되는 `ANSWER_PUBLISHED`와
  `QUESTION_PROPOSAL_REVIEWED`는 소비자가 없고, `ANSWER_REACTED`와
  `QUESTION_RECOMMENDED`는 producer도 없다.
- #176이 연 알림함에 나머지 네 종류를 적재하되, 질문자 한 명·답변 작성자·제안자·추천
  사용자를 저장된 aggregate에서 다시 판정한다.
- 알림 기록, preference 기반 delivery, outbox lease·retry를 분리해 중복 처리와 부분 실패에도
  알림 원장이 일관되게 남도록 한다.

## Scope

1. `ANSWER_PUBLISHED` 소비자: 답변의 `PostRecipient`와 `DirectionPost`를 따라 질문글
   작성자 한 명에게만 `ANSWER_RECEIVED`를 만들고 `answer_id`를 기록한다.
2. `ANSWER_REACTED` producer와 소비자: 실제 새 공감이 저장된 트랜잭션에서 outbox를
   발행하고 답변 작성자에게 `ANSWER_REACTED`를 만든다. 반복 PUT과 삽입 경합은 새
   이벤트를 만들지 않는다.
3. `QUESTION_PROPOSAL_REVIEWED` 소비자: payload의 수신자를 신뢰하지 않고 저장된
   `QuestionProposal.proposerId`로 제안자에게 대상 없는 알림을 만든다.
4. `QUESTION_RECOMMENDED` producer와 소비자: 저장된 `QuestionAssignment`마다 outbox
   이벤트 한 건을 같은 트랜잭션에서 발행하고, cycle 소유 사용자에게 대상 없는 알림을
   만든다.
5. 새 네 이벤트를 처리하는 공통 fan-out worker와 이벤트별 resolver를 둔다. #176의
   `RecipientNotificationFanOutWorker`는 변경하지 않아 stacked diff와 기존 회귀 위험을 줄인다.
6. 공통 worker는 `claimDue` lease와 generation fencing, 이벤트별 독립 트랜잭션,
   `saveIfAbsent`, retry/DEAD 분류, 실패 기록 격리, preference 직전 delivery 생성을 따른다.
7. 사용자 간 이벤트(`ANSWER_RECEIVED`, `ANSWER_REACTED`)는 양방향 활성 차단과 양쪽
   ACTIVE 계정을 알림 저장 전에 검사한다. 시스템 이벤트인 질문 제안 검토·질문 추천은
   상대 사용자가 없으므로 수신 계정 ACTIVE만 검사한다.
8. #176 목록·진입 판정에 답변 알림은 `targetKind=ANSWER`, 질문 제안 검토·추천은
   `targetKind=NONE`으로 연결한다.
9. JUnit 5 단위 테스트와 PostgreSQL 통합 테스트, 테스트 보고서를 작성한다.

## Design decisions

1. 초기 PR base는 `feat/gh-176-notification-inbox-read`다. #176 병합 후 #177을 최신
   `origin/main` 위로 rebase하고 PR base를 `main`으로 바꾼다.
2. `ANSWER_RECEIVED`와 `ANSWER_REACTED`는 `notification.answer_id`만 기록한다. 현재
   `Notification`과 DB CHECK가 이동 대상을 최대 하나로 제한하므로 두 종류 모두
   `targetKind=ANSWER`다.
3. `QUESTION_PROPOSAL_REVIEWED`와 `QUESTION_RECOMMENDED`는 별도 target FK가 없고
   스키마 변경이 범위 밖이므로 `targetKind=NONE`이다.
4. 공감 생성 이벤트는 `(answerId, reactorId, createdAt)` 발생 단위를 식별하고, 현재
   `answer_reaction`이 이미 취소된 이벤트는 소비 시 suppress한다. 소비 후 이미 생성된
   알림을 공감 취소 시 `REVOKED`로 회수하는 기능은 이 이슈에서 만들지 않는다. 같은 시각
   취소 후 재공감 충돌을 완전히 제거하려면 별도 occurrence ID 또는 schema 변경 결정이
   필요하며, 이번 범위에서는 사람 결정 필요 항목으로 남긴다.
5. preference는 알림 원장 생성을 막지 않는다. `notification` 저장 후
   `notification_delivery` 생성 직전에만 검사한다.

## Explicit exclusions

- `REPORT_RESOLVED` 소비자와 `notification.report_id` 매핑 — `#155` 소유.
- 알림 설정 조회·변경 — `#178`.
- Push provider 호출, 토큰 등록, 발송 스케줄링 — `#179`, `#182`.
- 묶음 발행·묶음 표시 — `#180`.
- 공감 취소 후 이미 생성된 알림의 `REVOKED` 전이와 delivery 취소.
- API endpoint 또는 OpenAPI 변경.
- Flyway migration과 notification/outbox CHECK 변경.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 공통 fan-out worker·resolver 계약 | Notification executor | claim type 격리, lease fencing, event별 transaction, retry/DEAD와 failure-recording isolation, preference 순서 |
| 답변 publish/reaction producer·resolver | Answer executor | 질문자 한 명 fan-out, `REQUIRES_NEW` 경합 시 outbox 1건, 취소 전 소비 suppress, 양방향 차단 |
| 질문 proposal/assignment producer·resolver | Question executor | 저장된 proposer/cycle owner 사용, assignment별 이벤트, producer와 aggregate 저장 원자성 |
| N1 목록·target 판정 회귀 | Query verifier | 답변 종류 `ANSWER`, 질문 종류 `NONE`, 삭제·숨김·차단 우선순위 불변 |

## Existing user-owned changes

- 작업 시작 시 `git status --short --branch`는 clean이었다.
- #176 작업 전체는 stacked base가 소유한다. #177은 해당 변경을 되돌리거나 재작성하지 않는다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.notification.*" --console=plain
./gradlew test --tests "com.dnd.qello.answer.*" --console=plain
./gradlew test --tests "com.dnd.qello.question.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.Notification*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.Answer*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.Question*" --console=plain
./harness test-run --id TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] 같은 outbox 이벤트를 재처리해도 `(recipient_id, dedup_key)` 기준 알림이 1건이다.
- [x] 질문글 하나의 답변 N개는 질문글 작성자에게 N건, 다른 수신자에게 0건을 만든다.
- [x] 새 답변·공감 알림은 답변을 `targetKind=ANSWER`로 노출한다.
- [x] 질문 제안 검토·추천 알림은 `targetKind=NONE`으로 노출한다.
- [ ] 사용자 간 활성 차단 또는 비활성 계정은 알림 행 자체를 막는다.
- [x] preference off는 알림 행을 남기고 delivery만 막는다.
- [x] producer 저장과 outbox 발행이 같은 트랜잭션에서 함께 성공하거나 롤백한다.
- [ ] lease 재선점·worker 경합·실패 기록 예외에도 중복이나 후속 이벤트 중단이 없다.
- [x] 종류별 단위 테스트와 PostgreSQL 통합 테스트가 있다.
- [x] 모든 테스트에 `@DisplayName`과 클래스 헤더(ISO 8601, Source scenario)가 있다.
- [ ] 승인된 테스트 계획의 필수 검증과 저장소 완료 전 검증이 통과한다.

잔여 `BLOCKED` 범위:

- `ANSWER_REACTED` dedup의 same-timestamp cancel/re-react 충돌은 occurrence ID 또는 schema 변경 결정이 필요하다.
- 신규 worker의 두 owner lease reclaim, failure-recording 후속 event 진행은 PostgreSQL 다중 이벤트 증거가 아직 부족하다.
- 비활성 계정 matrix는 단위 경계 위주로 확인되어 통합 evidence 또는 명시적 제외 승인이 필요하다.
