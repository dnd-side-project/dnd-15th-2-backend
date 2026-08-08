# Test Report: ANSWER-VISIBILITY-RECIPIENTS

> Created at: `2026-08-08T15:02:14+09:00`
> GitHub Issue: `#79`
> Branch: `feat/gh-79-answer-visibility-recipients`
> Commit: `13aaa75`

## 1. Executive summary

- Result: `PASS`
- Tested scope: 답변 열람 자격 판정과 신설 `PostAnswerQueryRepository`, 공감 자격
  확대(`AnswerReactionService`)와 자기 답변 금지, 수신함 2카테고리
  (`InboxCategory`)와 카드 집계(`answerCount`/`reactionCount`/
  `unreadAnswerCount`), 수신자 기준 방향(`inboundBearingDegrees`)과 근거리
  하한 기준 정확 거리/구간 배타적 노출, 수신자별 답변 읽음 기준선
  (`PostRecipient.markAnswersRead`, `advanceAnswersReadAt`)
- Unverified scope: `./harness pr-ready --project-tests`와 `./harness check`는
  이 보고서 이후 별도 단계(§작업 6)에서 실행한다. `docs/product/data-model/*`
  문서 동기화는 JUnit 검증 대상이 아니라 SHA-256 수동 대조로 확인했다(§8).
- Release recommendation: 단위·통합 테스트 전부 통과, 완료 조건 전 항목 충족.
  `./harness pr-ready`/`check` 결과를 확인한 뒤 PR 진행 가능.

발견해 함께 고친 결함 2건(둘 다 신규 테스트 작성 중 발견, 프로덕션 코드 결함
아님):

1. `JdbcInboxQueryRepository.findInbox`에서 Java 텍스트 블록의 줄 끝 공백
   제거 규칙 때문에 동적 상태 필터를 이어붙일 때 `AND`와 다음 토큰이 공백
   없이 붙어(`ANDpr.status...`) SQL 구문 오류가 났다. 텍스트 블록 경계에
   변수를 이어붙이지 않고 일반 문자열로 분리해 고쳤다.
2. `PostAnswerQueryIntegrationTest`의 만료 경계 시나리오에서 답변 작성자를
   실제 그 `post_recipient` 행의 소유자가 아닌 다른 사용자로 잘못 지정해
   `fk_answer_recipient_author` 복합 FK 위반이 났다. 테스트 fixture만의
   문제였고 fixture를 고쳤다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | 21 (Gradle toolchain, `JavaLanguageVersion.of(21)`) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine` (통합 테스트 전용, 로컬 Docker) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| Unit (`./gradlew test`) | PASS | 156 tests, 0 failed | 2.3s | `build/test-results/test/*.xml` |
| Integration (`./gradlew integrationTest`) | PASS | 138 tests, 0 failed | 4.8s(테스트 실행) / 1m 9s(컨테이너 기동 포함 전체) | `build/test-results/integrationTest/*.xml` |

첫 실행에서 통합 테스트 13건이 실패해 원인을 진단하고 고친 뒤 재실행했다
(§1 결함 2건). 재실행 후 완료 조건 대조 중 `SKIPPED` 수신자의 답변 무자격을
직접 검증하는 테스트가 없다는 공백을 발견해 시나리오 1건을 추가했다(137→138).
최종 결과가 위 표다 — 중간 실패도 §5에 기록한다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `DirectionDomainTest.recipientMarksAnswersRead` | |
| UNIT-002 | PASS | `DirectionDomainTest.recipientMarkAnswersReadRejectsBeforeMatchedAt` | |
| UNIT-003 | PASS | `DirectionDomainTest.recipientMarkAnswersReadRejectsNull` | |
| INT-001 | PASS | `PostAnswerQueryIntegrationTest.senderAndEligibleRecipientCanViewAnswersButOutsiderCannot` | |
| INT-002 | PASS | `PostAnswerQueryIntegrationTest.timeBoundRecipientLosesEligibilityAfterExpiryRegardlessOfStatus`, `.skippedRecipientCannotViewAnswersAtAll` | 1차 실행에서 fixture FK 위반으로 실패 → 수정 후 재실행 통과(§1, §5). `skippedRecipientCannotViewAnswersAtAll`은 완료 조건 대조 중 발견한 공백을 메우려 추가 |
| INT-003 | PASS | `PostAnswerQueryIntegrationTest.answeredRecipientKeepsEligibilityAfterExpiry` | |
| INT-004 | PASS | `InboxSentPostWriteIntegrationTest.eligibleRecipientCanReactToAnotherRecipientsAnswer`, `.outsiderCannotReactToAnswer`, `.answerAuthorCannotReactToOwnAnswer`(거절 사유를 `reason` 필드로 자기 답변 금지임을 직접 확인) | |
| INT-005 | PASS | `InboxQueryIntegrationTest.hidesBlockedAnswerAuthorFromAggregateCountsOnly`, `.hidesBlockedSenderPosts`(회귀) | 두 관점(개별 답변 작성자 차단 vs 발신자 차단)을 구분해 검증 |
| INT-006 | PASS | `PostAnswerQueryIntegrationTest.listsPublishedAnswersNewestFirstWithViewerScopedReaction`, `.hidesBlockedAuthorAnswersFromTheBlockingViewerOnly` | |
| INT-007 | PASS | `InboxQueryIntegrationTest.exposesExactDistanceAtAndAboveFloorOnly` | 하한-1m/하한/하한+1m 3지점 |
| INT-008 | PASS | `InboxQueryIntegrationTest.excludesViewersOwnAnswerFromUnreadCount`, `.countsOthersUnreadAnswers` | |
| INT-009 | PASS | `InboxQueryIntegrationTest`(전체 12개), `SentPostQueryIntegrationTest`(전체 6개), `InboxSentPostWriteIntegrationTest`(전체, 카테고리 인자 반영 회귀 포함) | `excludesTerminalAndAnsweredStatusesFromUnanswered`·`answeredItemsStayInAnsweredCategoryUntilExpiry`가 옛 규칙을 뒤집은 자리를 대체 |
| INT-010 | PASS | `InboxSentPostWriteIntegrationTest.recipientMarksAnswersReadNeverRegresses` | |
| INT-011 | PASS | `InboxSentPostWriteIntegrationTest.recipientMarksAnswersRead`, `.outsiderCannotMarkRecipientAnswersRead` | |
| 회귀 | PASS | `FeedPersistenceBoundaryTest.inboxProjectionsExposeAnswerAndReactionCounts` | 옛 "노출하지 않는다" 단정을 새 규칙으로 교체(§5) |

## 5. Failures and diagnostics

1차 통합 테스트 실행에서 13건이 실패했다. 둘 다 구현이 아니라 새로 작성한
코드/테스트 자체의 결함이었고, 수정 후 재실행에서 13건 모두 통과했다.

**결함 A — `JdbcInboxQueryRepository` SQL 구문 오류(12건 영향)**

- 유형: 구현 결함(테스트가 아니라 프로덕션 SQL 조립 코드)
- 재현 조건: `InboxQueryService.list(...)`를 어떤 인자로 호출해도 항상 재현됨
  (`InboxQueryIntegrationTest` 10건, `InboxSentPostWriteIntegrationTest` 2건)
- 오류: `org.postgresql.util.PSQLException: ERROR: syntax error at or near
  "ANDpr"`
- 원인: Java 텍스트 블록은 각 줄의 끝 공백을 잘라낸다. `"""...AND """ +
  statusFilter + """...` 형태로 텍스트 블록 경계에 동적 문자열을 이어붙이면
  `AND` 뒤의 공백이 사라져 `AND` + `pr.status...`가 공백 없이 붙는다.
  기존 `JdbcSentPostQueryRepository`의 유사 패턴(`OPEN_STATUSES`)은 이어지는
  토큰이 `(`라서 우연히 문제가 없었지만, 이번 `statusFilter`는 `pr`로 시작해
  실제로 깨졌다.
- 조치: 텍스트 블록과 동적 문자열의 경계를 일반(non-text-block) 문자열
  리터럴로 옮겨 공백 손실 경로를 없앴다(`JdbcInboxQueryRepository.findInbox`).

**결함 B — 테스트 fixture의 FK 위반(1건 영향)**

- 유형: 테스트 코드 결함(프로덕션 코드 아님)
- 재현 조건: `PostAnswerQueryIntegrationTest.
  timeBoundRecipientLosesEligibilityAfterExpiryRegardlessOfStatus`
- 오류: `org.postgresql.util.PSQLException: ERROR: insert or update on table
  "answer" violates foreign key constraint "fk_answer_recipient_author"`
- 원인: `answer(post_recipient_id, author_id)`가
  `post_recipient(id, recipient_id)`를 참조하는 복합 FK다. 이 fixture는
  `recipientId` 소유의 `post_recipient` 행에 `outsiderId`를 작성자로 지정해
  삽입을 시도했다 — 그 행의 실제 수신자와 다른 사용자를 저자로 넣을 수 없다.
- 조치: `outsiderId` 전용 `post_recipient` 행을 별도로 만들어 그 행에
  `outsiderId`의 답변을 연결하도록 fixture를 고쳤다.

## 6. Potential issues

### Application code

- `answer.service.AnswerReactionService`가 이제 `feed.service.
  PostAnswerQueryService`에 의존한다(신규 `answer → feed` 패키지 의존). 순환은
  없지만(`feed`는 `answer`를 참조하지 않음) 저장소에 ArchUnit 같은 패키지
  경계 검증이 생기면 이 방향을 허용 목록에 명시적으로 올려야 한다. 현재는
  자동 검증이 없어 회귀로 이어지지 않는다.
- `InboxCard.distanceM`/`distanceBand`의 상호 배타성(하나만 non-null)은
  `JdbcInboxQueryRepository`의 SQL `CASE`에서만 강제하고, record 자체에는
  compact constructor 검증을 두지 않았다 — AGENTS.md의 "발생할 수 없는
  시나리오에 검증을 추가하지 않는다" 원칙을 따른 것이며, 이 record를 만드는
  경로가 그 mapper 하나뿐이기 때문이다. 다만 이후 두 번째 생성 경로(예: 다른
  repository 구현체)가 생기면 이 불변식이 조용히 깨질 수 있다 — 그때는
  검증을 다시 고려해야 한다.
- `PostAnswerQueryRepository.findAnswers`가 `canViewAnswers`를 내부에서 다시
  호출한 뒤 본 쿼리를 실행한다(왕복 2회). 자격 규칙을 한 곳에 두기 위한
  의도적 선택이며(계획 문서 §접근 참고), 목록 조회 빈도가 매우 높아지면
  단일 쿼리로 합치는 최적화를 고려할 수 있다.

### Infrastructure and resource limits

- 인프라 변경 없음. 기존 Testcontainers PostGIS 컨테이너(정적 공유 인스턴스)를
  그대로 사용했다.

### Database and migrations

- 이 이슈는 신규 마이그레이션이 없다 — `#78`의 `V8`을 그대로 쓴다. 스키마
  변경이나 제약 추가는 없었다.
- `application.properties`의 `qello.feed.near-distance-floor-m=10000`과
  `InboxQueryIntegrationTest`의 `NEAR_FLOOR_M = 10_000L` 상수가 값으로만
  연결돼 있고 프로퍼티 빈을 직접 주입받아 검증하지 않는다. 운영 설정값을
  바꾸면 이 테스트 상수도 함께 바꿔야 한다 — 잊으면 테스트가 조용히 틀린
  경계를 검증하게 된다(빌드 실패로는 드러나지 않음).

### Concurrency and idempotency

- `PostRecipientService.markAnswersRead`의 `advanceAnswersReadAt`(`GREATEST`
  단일 UPDATE)은 이 보고서에서 순차 호출(INT-010)로만 검증했다 — 진짜 동시
  요청(두 스레드가 같은 postRecipientId에 동시에 markAnswersRead)은 테스트
  계획에서도 명시적으로 범위 밖으로 뒀다(`DirectionPostRepository.
  advanceAnswersReadAt` 선례와 동일한 판단). `GREATEST` 단일 UPDATE 자체가
  DB 레벨에서 원자적이므로 위험은 낮다고 판단하지만 실측 검증은 아니다.
- `AnswerReactionService.toggle`의 취소 후 같은 트랜잭션 내 재반응 제약은
  이번 변경으로 바뀌지 않았다(`AnswerReactionRepository` javadoc에 기존
  문서화됨) — 자격 판정 로직만 교체했다.

### Transactions and event ordering

- `AnswerReactionService.toggle`이 answer 조회 → 자기 답변 검사 →
  post_recipient 조회 → `canViewAnswers` 조회 → reaction 조회/쓰기 순으로
  기존보다 한 단계(canViewAnswers) 더 거친다. 모두 같은 `@Transactional`
  안이라 원자성은 유지되며, 지연 트리거(`ct_answer_reaction_reactor_can_view`)
  가 최종 방어선으로 남아 있어 이 사전 검증이 놓친 경쟁 조건도 commit 시점에
  잡힌다.

### External APIs

- 해당 없음.

### Failure recovery and reconciliation

- `SKIP_PENDING` 확정 워커와 만료 전이 배치가 여전히 없다(이번 이슈의 명시적
  제외 범위). `canViewAnswers`가 `status`뿐 아니라 `expires_at`을 직접
  비교하는 것으로 이를 보완하지만, 이는 배치가 생기기 전까지 모든 열람·공감
  요청마다 그 비교를 반복한다는 뜻이다 — 배치가 도입되면 이 보완 로직을
  단순화할 여지가 있다(제거하지는 않는다 — DB 상태 전이가 실제로 지연될 수
  있는 한 시각 비교는 여전히 필요하다).

## 7. Regression and residual risk

- `SentPostQueryRepository.findAnswers`/`AnswerCursor`를 제거하고
  `PostAnswerQueryRepository`로 이관했다. controller가 아직 없어(이 이슈와
  선행 이슈 모두 service 계층까지만 제공) 외부 호출자 파손은 없음을 저장소
  전체 grep으로 확인했다.
- `AnswerErrorCode.INELIGIBLE_REACTOR`의 메시지 문구를 바꿨다
  (`docs/error-codes.md`도 함께 갱신). 코드 값(`ANS-DOM-004`)과 HTTP 상태(403)는
  바꾸지 않았다 — 문구만 바뀐 변경이라 기존 클라이언트 계약을 깨지 않는다.
- `InboxCard`의 `matchedBearingDegrees` 필드가 `inboundBearingDegrees`로
  이름과 의미가 바뀌었다. 이 record를 소비하는 곳이 아직 없어(controller
  미구현) 이번 회차에서는 파손 없음.
- 다음 항목은 `TASK.md`에 `ASSUMED`로 기록한 제품 판단이며 구현은 이 가정을
  따랐지만 리뷰에서 뒤집힐 수 있다 — 뒤집히면 코드가 아니라 판단만 바뀌므로
  영향 범위가 좁다.
  - `unreadAnswerCount`는 뷰어 본인의 답변을 제외한다.
  - `answerCount`는 뷰어 본인의 답변을 포함한다(총 답변 수).
  - 근거리 하한 10000m.
  - 수신함 카드의 `reactionCount`는 질문글 공감(`post_reaction`) 수다 — 이
    항목은 vault DBML의 2026-08-08 정정으로 `CONFIRMED`로 격상됐다(§8).

## 8. Artifacts

- Test plan: `docs/test-plans/gh-79-ANSWER-VISIBILITY-RECIPIENTS.md`
- CI run: 로컬 실행(`./harness test-run --id ANSWER-VISIBILITY-RECIPIENTS`),
  아직 PR을 열지 않아 GitHub Actions run 없음
- Related ADR: `docs/adr/0002-답변은-질문글을-받은-사람-모두에게-공개된다.md`
  (vault, superseded 0001 대체)
- 문서 동기화 증거: `docs/product/data-model/direction_communication.dbml`·
  `DIRECTION_COMMUNICATION_ERD.md`를 vault 2026-08-08 정정본으로
  byte-for-byte 재동기화했고, `schema-manifest.md` §3의 SHA-256 3개(DBML,
  ERD, target DDL)를 재계산해 `shasum -a 256`로 실제 파일과 대조 확인했다.
  `#78`이 남긴 DBML 행 SHA-256 불일치(`3b443c4b…` 기록 vs 실제
  `fb39599f…`)도 이번에 바로잡았다.
- PR: 아직 생성 전

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨(§1 Unverified scope — `pr-ready`/`check`는 후속 단계)
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨(§6 — `SKIP_PENDING`/만료 배치는
      이 이슈의 명시적 제외 범위이며 별도 워커 이슈로 추적된다)
- [ ] 실행 결과와 PR 설명이 일치함 — PR 생성 시 확인
