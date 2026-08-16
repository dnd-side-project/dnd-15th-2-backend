# Test Plan: TEST-PLAN-GH-145-QUESTION-PROPOSAL-NOTIFICATION

> Created at: `2026-08-17T00:22:46+09:00`
> GitHub Issue: `#145`
> Status: Approved

## 1. Objective

질문 제안 검토 판정이 제안자에게 **누락 없이, 중복 없이** 통지되는지 검증한다.
`QuestionReviewService.approve()`/`reject()`는 판정과 같은 transaction에서
`QUESTION_PROPOSAL_REVIEWED` outbox event를 남긴다. 이 결합이 깨지면 두 가지
방향의 실패가 생긴다.

- **판정은 됐는데 이벤트가 없다** — 제안자는 자기 제안이 반려된 사실을 영영
  모른다. 재제출 경로가 없는 정책(`question_proposal_revision` 테이블 폐기)
  때문에 사용자는 아무 피드백 없이 방치된다.
- **이벤트는 갔는데 판정이 롤백됐다** — 제안자는 "반려됨" 알림을 받지만 제안은
  여전히 `UNDER_REVIEW`다. 운영자가 다시 판정하면 두 번째 알림이 간다.

같은 계획에서 `#144`가 정식 계획 없이 예외 승인으로 병합한 API 표면
(제출·조회·검토)의 검증 부채도 함께 정리한다.

## 2. Scope

### Included

- `QuestionReviewService.propose()` / `approve()` / `reject()`의 transaction
  경계와 outbox event 발행
- `publishReviewed()`의 dedupKey 기반 중복 억제와 그 경쟁 조건
- `QuestionProposalApplicationService`의 계정 자격(ACTIVE `USER`) 판정과
  제안자별 조회 격리
- 사용자 API(`POST /api/v1/questions/proposals`,
  `GET /api/v1/questions/proposals/me`)와 운영자 API
  (`POST /admin/questions/proposals/{id}/review|approve|reject`)
- 위 경로의 DB 제약·trigger·동시성·트랜잭션 원자성

### Excluded

- `QUESTION_PROPOSAL_REVIEWED` event를 인앱 알림·push로 fan-out하는 worker —
  `TASK.md`의 "Explicit exclusions"에 따라 producer까지만 다룬다. 이 계획은
  이벤트가 `PENDING`으로 남고 **기존 worker가 잘못 소비하지 않는지**까지만
  확인한다.
- `filtering` 도메인 연동 — `TASK.md`의 "Filtering integration decision"에
  따라 미연동 결정. 이 경로에는 외부 moderation 호출이 없다.
- `question_assignment_cycle`(질문 배정·추천) 로직 — 별도 이슈.
- Slack 등 알림 채널 확장.
- `docs/api/openapi.json` 정확성 — `#144`에서 springdoc이 재생성했고
  `OpenApiSpecificationIntegrationTest`가 이미 최신성을 검사한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue `#145` | 반려 시 사유가 기록되고 `QUESTION_PROPOSAL_REVIEWED` 알림이 제안자에게 실제로 발행된다 |
| GitHub Issue `#145` | 정식 테스트 계획 승인과 통합 테스트, 테스트 보고서가 존재한다 |
| GitHub Issue `#144` | 제출 API가 `QuestionProposal`을 생성하고 DRAFT→SUBMITTED로 전이한다 |
| GitHub Issue `#144` | 승인 시 `ApprovedQuestion`이 생성되고 `QuestionProposalReview`가 append-only로 기록된다 |
| GitHub Issue `#144` | 인증되지 않은 사용자는 제안 제출·조회를 할 수 없다 |
| schema `V1` | `uq_outbox_event_dedup UNIQUE (dedup_key)` — 같은 dedupKey 중복 삽입을 DB가 최종 거부한다 |
| schema `V1` | `uq_approved_question_source_proposal UNIQUE (source_proposal_id)` — 제안 하나가 승인 질문 둘을 만들 수 없다 |
| schema `V1` | `ck_question_proposal_review_reason` — `REJECTED` 판정에는 사유가 반드시 있다 |
| schema `V1` | `question_proposal_review`에는 `proposal_id` unique 제약이 **없다** — 중복 판정 행을 DB가 막지 않는다 |
| schema `V1` | `tr_question_proposal_text_immutable_after_submit` — 제출 후 문구 변경을 거부한다 |
| `ERD` 문서 | `QUESTION_PROPOSAL_REVIEWED`의 수신자는 제안자이며 이동 대상이 없다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| R1. `publishReviewed()`가 `findByDedupKey` 후 `save`하는 TOCTOU 구조라, 동시 판정 시 후발 transaction이 `uq_outbox_event_dedup` 위반으로 실패한다. `AnswerModerationJobIntakeService`는 같은 경쟁을 `DataIntegrityViolationException` catch로 처리하지만 이 경로는 처리하지 않는다 | 높음 — 운영자에게 원인 불명 500 | 낮음 — 두 운영자가 같은 제안을 동시 판정 | P0 | 동시 반려/승인 통합 테스트에서 성공 1건·실패 1건의 최종 상태 |
| R2. `question_proposal_review`에 `proposal_id` unique가 없어, 동시 판정이 append-only 이력에 중복 행을 남길 수 있다 | 중간 — 감사 이력 오염, 판정자 2명 기록 | 낮음 | P0 | 동시 판정 후 `question_proposal_review` 행 수 |
| R3. outbox 저장 실패가 판정 transaction을 롤백하지 않으면 "알림 없는 판정" 또는 "판정 없는 알림"이 생긴다 | 높음 — 사용자 신뢰 직결 | 낮음 | P0 | dedupKey 선점 상태에서 반려 시도 후 proposal·review·outbox 상태 |
| R4. `findMine`이 제안자 필터를 놓치면 타인의 제안 문구가 노출된다. 단위 테스트는 mock repository라 실제 SQL 필터를 검증하지 못한다 | 높음 — 프라이버시 침해 | 낮음 | P0 | 두 계정 데이터가 있는 DB에서 조회 결과 |
| R5. `propose()`가 `save(create)` → `save(submit)` 2단계라, 두 번째 실패 시 고아 DRAFT 행이 남을 수 있다 | 중간 — 사용자에게 안 보이는 유령 제안 | 낮음 | P1 | 실패 주입 후 `question_proposal` 행 수 |
| R6. 이미 판정된 제안을 재판정할 때 상태 기계가 막지 못하면 두 번째 알림이 발행된다 | 중간 — 중복 알림 | 중간 — 운영자 재클릭 | P1 | 순차 재반려 시 예외 코드와 outbox 행 수 |
| R7. `QUESTION_PROPOSAL_REVIEWED`를 소비할 worker가 아직 없는데, 기존 fan-out worker가 이 event를 잘못 claim하면 처리 불가 상태로 `DEAD` 전이한다 | 중간 — 이벤트 유실 | 낮음 | P1 | 기존 worker 실행 후 event status |
| R8. outbox payload를 `String.format`으로 수동 조립한다. 지금은 숫자·enum만이라 안전하지만 텍스트 필드가 추가되면 JSON 이스케이프가 깨진다 | 낮음 — 현재 미발생 | 낮음 | P2 | payload가 유효 JSON object로 저장되는지(JSONB 컬럼 파싱) |
| R9. `findAllByProposerIdOrderByCreatedAtDesc`가 `created_at` 동률일 때 순서를 보장하지 못한다 | 낮음 — 목록 순서 흔들림 | 중간 | P2 | 같은 시각 제안 2건의 조회 순서 결정성 |

## 5. Unit scenarios

### 5.1 이 계획에서 신규 구현

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-145-QUESTION-PROPOSAL-NOTIFICATION-UNIT-001 | `UNDER_REVIEW` 제안, outbox 저장이 예외를 던지도록 stub | `reject()` 호출 | 예외가 삼켜지지 않고 그대로 전파된다(호출자가 실패를 인지할 수 있다) | P0 | Executor 1 |
| TEST-PLAN-GH-145-QUESTION-PROPOSAL-NOTIFICATION-UNIT-002 | `UNDER_REVIEW` 제안, outbox 저장이 예외를 던지도록 stub | `approve()` 호출 | 예외가 그대로 전파된다 | P0 | Executor 1 |
| TEST-PLAN-GH-145-QUESTION-PROPOSAL-NOTIFICATION-UNIT-003 | 이미 `REJECTED`인 제안 | `reject()` 재호출 | `INVALID_PROPOSAL_STATUS`가 발생하고 outbox 저장이 호출되지 않는다 | P1 | Executor 1 |
| TEST-PLAN-GH-145-QUESTION-PROPOSAL-NOTIFICATION-UNIT-004 | 이미 `APPROVED`인 제안 | `approve()` 재호출 | `INVALID_PROPOSAL_STATUS`가 발생하고 승인 질문·outbox가 저장되지 않는다 | P1 | Executor 1 |

### 5.2 `#144` 예외 승인으로 이미 구현된 시나리오 (이 계획이 승계)

`#144`는 정식 계획 없이 병합하는 예외를 승인받았고(`TASK.md`의 "Test plan
exception"), 그때 작성한 테스트는 잠정 식별자
`TEST-PLAN-GH-144-QUESTION-PROPOSAL-API-*`를 헤더에 갖고 있다. 이 계획이
그 식별자를 정식으로 승계하므로 **기존 파일의 헤더는 수정하지 않는다.**
재작성 churn 없이 추적성만 확보한다.

| 기존 식별자 | 대상 | 커버 위험 |
| --- | --- | --- |
| `...-GH-144-...-UNIT-001`~`004` | `QuestionProposalApplicationService` 계정 자격·위임·조회 | R4(부분 — mock 한계) |
| `...-GH-144-...-UNIT-005`~`007` | `QuestionReviewService` propose·조회 실패 | R5(부분) |
| `...-GH-144-...-UNIT-008`,`009` | ApiSpec/Controller 분리, 경로 prefix | — |
| `...-GH-144-...-INT-001`~`005` | 사용자 API MockMvc(201/400/401/200) | — |
| `...-GH-144-...-INT-006`~`010` | 운영자 API MockMvc 인자 전달·검증 실패 | R6(부분) |
| `...-GH-144-...-INT-011`~`015` | PostgreSQL 제출·조회·승인·반려 흐름 | R3(부분) |
| `#145` 기존 3건 | outbox payload·중복 억제(단위) | R1(부분 — 단일 스레드) |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| `...-NOTIFICATION-INT-001` | `QuestionReviewService`, PostgreSQL, outbox | `UNDER_REVIEW` 제안 1건, 운영자 2명 | 두 스레드가 동시에 `reject()` | 정확히 1건 성공. `question_proposal` = `REJECTED`, `question_proposal_review` 행 **1개**, outbox 행 **1개**. 실패한 쪽은 상태를 오염시키지 않는다 | 테이블 DELETE |
| `...-NOTIFICATION-INT-002` | `QuestionReviewService`, PostgreSQL, outbox | `UNDER_REVIEW` 제안 1건, 운영자 2명 | 두 스레드가 동시에 `approve()` | 정확히 1건 성공. `approved_question` 행 1개(`uq_approved_question_source_proposal`), review 1개, outbox 1개 | 테이블 DELETE |
| `...-NOTIFICATION-INT-003` | `QuestionReviewService`, outbox | 같은 dedupKey(`question-proposal-reviewed:{id}`) outbox 행을 미리 삽입 | `reject()` 호출 | 판정 자체는 성공하고 outbox 행은 1개로 유지된다(중복 억제가 DB까지 일관). 만약 실패한다면 proposal·review가 **함께** 롤백된다 | 테이블 DELETE |
| `...-NOTIFICATION-INT-004` | `QuestionProposalApplicationService`, PostgreSQL | 서로 다른 계정 2명이 각각 제안 2건·1건 제출 | 계정 A로 `findMine()` | A의 2건만 반환. B의 제안 id·문구가 결과에 없다 | 테이블 DELETE |
| `...-NOTIFICATION-INT-005` | outbox, `RecipientNotificationFanOutWorker` | 반려로 `QUESTION_PROPOSAL_REVIEWED` event 1건 생성 | 기존 fan-out worker의 `processBatch` 실행 | 이 event는 claim되지 않고 `PENDING`으로 남는다(worker는 `RECIPIENTS_CONFIRMED`만 claim) | 테이블 DELETE |
| `...-NOTIFICATION-INT-006` | `QuestionReviewService`, PostgreSQL | 정상 계정 | `propose()`가 두 번째 저장에서 실패하도록 유도(문구 길이 초과 등 제약 위반) | `question_proposal`에 고아 DRAFT 행이 남지 않는다(행 수 0) | 테이블 DELETE |
| `...-NOTIFICATION-INT-007` | `QuestionReviewService`, PostgreSQL, outbox | 이미 반려된 제안 | `reject()` 재호출 | `INVALID_PROPOSAL_STATUS`(409). review 행과 outbox 행이 각각 1개로 유지된다 | 테이블 DELETE |
| `...-NOTIFICATION-INT-008` | outbox JSONB | 승인·반려로 event 생성 | `payload::jsonb ->> 'decision'` 조회 | JSONB로 파싱 가능한 object이고 `decision`, `proposalId`, `proposerId` 키를 갖는다 | 테이블 DELETE |

`...-NOTIFICATION-`은 `TEST-PLAN-GH-145-QUESTION-PROPOSAL-NOTIFICATION-`의 축약
표기다. 테스트 클래스 헤더에는 전체 식별자를 적는다.

## 7. Cross-cutting scenarios

### Database and transactions

- `QuestionReviewService`의 `approve`/`reject`/`propose`는 모두
  `@Transactional`이다. review 저장, proposal 상태 전이, 승인 질문 생성,
  outbox 삽입이 **한 커밋 단위**여야 한다(INT-001~003).
- `uq_outbox_event_dedup`와 `uq_approved_question_source_proposal`이 동시성의
  최종 중재자다. 애플리케이션의 사전 조회(`findByDedupKey`)는 최적화일 뿐
  보장이 아니다 — 이 전제를 INT-001/002가 검증한다.
- `question_proposal_review`에 `proposal_id` unique가 없다는 사실이 설계
  의도(한 제안에 여러 판정 이력 허용)인지, 아니면 누락인지 이 계획이
  드러낸다. INT-001에서 행이 2개 나오면 **결함으로 보고**하고 스키마 대응을
  후속 이슈로 올린다. 테스트를 통과시키려고 구현을 고치지 않는다.

### Concurrency and idempotency

- dedupKey는 `question-proposal-reviewed:{proposalId}`로 고정된다. 제안은
  `UNDER_REVIEW`에서 한 번만 전이하므로(재검토 경로 없음) 정상 흐름에서
  중복이 생기지 않는다. 이 불변식이 동시 호출에서도 유지되는지가 INT-001의
  핵심이다.
- 동시성 테스트는 기존 패턴을 따른다:
  `DirectionMatchingWorkerConcurrencyIntegrationTest`,
  `RecipientNotificationFanOutWorkerConcurrencyIntegrationTest`. `CountDownLatch`로
  두 스레드를 같은 지점에서 출발시키고, 각 스레드의 예외를 수집해 성공/실패
  건수를 단정한다.

### External APIs

**해당 없음.** `filtering` 미연동 결정(`TASK.md`)에 따라 질문 제안 경로에는
OpenAI moderation을 포함한 어떤 외부 호출도 없다. 이 계획은 외부 API double을
사용하지 않는다. 향후 `filtering` 프로덕션 배선 이슈에서 이 항목이 열린다.

### Failure recovery and reconciliation

- fan-out worker가 없으므로 발행된 event는 `PENDING`으로 적체된다. 이는 의도된
  상태이며(TTL 적용 금지 대상), INT-005가 기존 worker의 오소비만 배제한다.
- 판정 transaction이 롤백되면 event도 함께 사라져야 한다 — 재시도 시 같은
  dedupKey로 다시 발행되므로 최종 상태는 event 1건이다(INT-003).
- 운영자 재판정으로 인한 중복 알림은 상태 기계가 1차 방어, dedupKey가 2차
  방어다(INT-007).

## 8. Test data and isolation

- **Fixtures**: `Account.createUser("KR", REGION_CODE, "ko-KR", "Asia/Seoul", <nickname>)`.
  운영자는 `Account.createOperator(...)`. `REGION_CODE`는 클래스별로 다른 상수를
  써서 병렬 실행 시 충돌을 피한다(신규 클래스는 `TEST-QUESTION-145`).
- **Database isolation**: `PostgisContainerIntegrationTestSupport`(Testcontainers
  PostGIS)를 상속하고 `@ActiveProfiles({"test", <profile>})`을 붙인다.
  `@BeforeEach`에서 `outbox_event`(`aggregate_type = 'QUESTION_PROPOSAL'`),
  `approved_question`, `question_proposal_review`, `question_proposal`,
  `user_account`, `region_code` 순으로 DELETE한다(FK 역순).
- **Clock/randomness**: `MutableClock`을 `@Primary` 빈으로 주입해 시각을
  고정한다. 동률 정렬(R9)을 볼 때만 같은 instant를 유지하고, 순서를 봐야 하는
  경우 `clock.setInstant(...)`로 명시적으로 벌린다. 랜덤 값은 쓰지 않는다.
- **External API doubles**: 없음(위 "External APIs" 참고).
- **Cleanup**: 각 테스트는 `@BeforeEach` DELETE로 자기 상태를 만든다.
  동시성 테스트는 스레드 풀을 `try`/`finally`에서 `shutdownNow()`한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다. 계정 식별자는 테스트가 생성한
surrogate id만 사용한다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor 1 (unit) | `src/test/java/com/dnd/qello/question/service/QuestionReviewServiceTest.java` (기존 확장) | UNIT-001 ~ UNIT-004 | `./gradlew test --tests "com.dnd.qello.question.service.QuestionReviewServiceTest"` |
| 2 | Executor 2 (integration, 단일 스레드) | `src/integrationTest/java/com/dnd/qello/QuestionProposalApiIntegrationTest.java` (기존 확장) | INT-003, INT-004, INT-006, INT-007, INT-008 | `./gradlew integrationTest --tests "com.dnd.qello.QuestionProposalApiIntegrationTest"` |
| 3 | Executor 3 (integration, 동시성) | `src/integrationTest/java/com/dnd/qello/QuestionProposalReviewConcurrencyIntegrationTest.java` (**신규**) | INT-001, INT-002, INT-005 | `./gradlew integrationTest --tests "com.dnd.qello.QuestionProposalReviewConcurrencyIntegrationTest"` |

세 실행자의 소유 파일은 서로 겹치지 않는다. 순서 1 → 2 → 3은 실패 원인을 좁히기
위한 권장 순서이며, 파일이 분리돼 있으므로 병렬 실행도 가능하다.

**구현 코드를 수정하지 않는다.** 테스트가 실패하면 그 실패를 보고서에 기록하고
구현 결함 여부를 판단한다. 특히 R1(동시 판정 시 원인 불명 예외)과 R2(중복 review
행)는 실패가 곧 결함 발견이므로 후속 이슈로 올린다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현 (UNIT-001, 002 / INT-001, 002, 003, 004)
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성 (`templates/test-report.md` 기반,
      `docs/reports/tests/gh-145-TEST-PLAN-GH-145-QUESTION-PROPOSAL-NOTIFICATION.md`)

### 실패 판단 기준

- P0 시나리오가 하나라도 실패하면 `#145`를 병합하지 않는다.
- P1 실패는 후속 이슈와 위험 기록을 남기면 병합할 수 있다(사람 판단).
- P2 실패는 보고서에 기록만 한다.
- 테스트 환경 문제(컨테이너 기동 실패 등)와 구현 결함을 구분해 기록한다.
  환경 문제라도 실패 명령, 오류 요약, 재현 조건, 미검증 범위를 남긴다.

## 11. Human approval

- Reviewer: `tkv00`
- Decision: Approved — 계획대로 구현·실행한다. 수정 요청 없음.
- Approved at: `2026-08-17`
