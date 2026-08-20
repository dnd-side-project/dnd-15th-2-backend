# Test Report: TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION

> Created at: `2026-08-20T19:22:00+09:00`
> GitHub Issue: `#177`
> Branch: `feat/gh-177-notification-fanout-expansion`
> Commit: `working tree; review-fix commit not created`
> Last revalidated at: `2026-08-20T22:51:52+09:00`

## 1. Executive summary

- Result: `BLOCKED`
- Tested scope: 네 가지 notification producer/consumer, 답변 N개 x 수신자 M명 fan-out,
  assignment rollback, reaction 경합, PostgreSQL replay dedup, account/block/preference gate,
  기존 direction 포함 5종 targetKind, 기존 관련 회귀
- Unverified scope: `ANSWER_REACTED` same-timestamp cancel/re-react dedup 충돌은 occurrence ID
  또는 schema 변경 결정이 필요하다. 신규 worker의 두 owner lease reclaim, failure-recording
  후속 event 진행과 비활성 계정 matrix는 PostgreSQL 증거가 아직 부족하다.
- Release recommendation: merge 전 남은 `BLOCKED` 범위를 구현하거나 사람의 명시적 제외
  승인을 기록해야 한다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 25.0.3 LTS |
| Spring Boot | 3.5.16 |
| Database | PostgreSQL Testcontainers with PostGIS support |
| Test runner | JUnit 5 / Gradle |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests com.dnd.qello.notification.fanout.NotificationFanOutWorkerTest --tests com.dnd.qello.answer.service.AnswerReactionServiceTest --tests com.dnd.qello.question.service.QuestionAssignmentServiceTest --console=plain` | PASS | 18 | 3s | review-fix focused unit; Gradle `BUILD SUCCESSFUL` |
| `./gradlew integrationTest --tests com.dnd.qello.NotificationFanOutExpansionIntegrationTest --console=plain` | PASS | 6 | 26s | review-fix focused PostgreSQL integration; Gradle `BUILD SUCCESSFUL` |
| `./harness check` | PASS | policy checks | <1s | secret, JUnit, convention, workflow, label, Husky checks passed |
| `./harness pr-ready --project-tests` | PASS | 826 unit + 581 integration | 5m 33s | project test gate completed successfully; failures=0, errors=0 |
| `npm run hooks:validate` | PASS | Husky validation | <1s | validation passed |
| `git diff --check` | PASS | whitespace | <1s | no whitespace errors |

## 4. Scenario results

| Scenario | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| 질문자 한 명 `ANSWER_RECEIVED` | PASS | `NotificationFanOutExpansionIntegrationTest.fansOutAllNotificationTypesWithCorrectRecipientsAndTargets` | 다른 recipient 0건 확인 |
| 답변 N개 x 수신자 M명 fan-out | PASS | `answerPublishedFansOutPerAnswerOnlyToQuestionAuthor` | 질문자 2건, 답변 작성자와 bystander 0건 |
| 네 종류 producer/consumer | PASS | `fansOutAllNotificationTypesWithCorrectRecipientsAndTargets` | answer, reaction, proposal review, recommendation 모두 processed |
| assignment 중간 실패 rollback | PASS | `assignmentFailureRollsBackCycleAssignmentsAndOutbox` | cycle, assignment, `QUESTION_RECOMMENDED` outbox 0건 |
| 동일 event replay dedup | PASS | `deduplicatesRepeatedAnswerPublishedEvent` | 같은 outbox row를 PENDING으로 재현 후 notification 1건 |
| 동시 answer reaction | PASS | `concurrentReactionCreatesOneReactionAndOneOutbox` | reaction 1건, ANSWER_REACTED outbox 1건 |
| 5종 targetKind mapping | PASS | `listsFiveFanOutTypesWithExpectedTargetKinds`, `NotificationInboxQueryIntegrationTest` regression | direction=`DIRECTION_POST`, answer/reaction=`ANSWER`, question 2종=`NONE` |
| preference and block gates | PASS | `NotificationFanOutWorkerTest` | preference off는 row 유지/delivery 억제, block은 row 억제 |
| retry/DEAD/failure recording isolation | PASS | `NotificationFanOutWorkerTest` | transient/recoverable retry unit verified; PostgreSQL reclaim matrix remains partial |
| 기존 #176 notification/answer/question contracts | PASS | selected integration regression classes | no regression observed |

## 5. Failures and diagnostics

- 초기 RED 단계에서 신규 worker/resolver와 producer 계약이 없어 컴파일·검증 실패를
  확인했다. 테스트가 요구한 최소 계약을 구현한 뒤 동일 테스트가 통과했다.
- 2026-08-20T22:51:52+09:00 리뷰 수정 후 집중 단위·통합 테스트와 repository gate를
  다시 실행했고 재현 가능한 테스트 실패는 없었다.

## 6. Potential issues

### Application code

- `ANSWER_REACTED` producer outbox dedup은 answer/reactor/createdAt 조합을 사용한다.
  same-timestamp cancel/re-react를 별도 occurrence로 보장하려면 발생 단위 ID 또는 schema
  변경이 필요하다. 이번 범위에서는 migration이 제외되어 사람 결정 항목으로 남긴다.

### Infrastructure and resource limits

- Testcontainers 기반 실행은 Docker 자원과 PostgreSQL startup에 의존한다. 이번 실행에서는
  환경 실패가 없었다.

### Database and migrations

- 새 Flyway migration은 추가하지 않았다. 기존 notification/outbox CHECK와 unique constraint를
  실제 PostgreSQL에서 통과했다.
- 질문 assignment 중간 실패는 PostgreSQL 제약 위반으로 재현했고 cycle, assignment, outbox가
  함께 롤백됨을 확인했다.

### Concurrency and idempotency

- 동시 reaction 삽입과 sequential lease replay는 확인했다.
- 서로 다른 worker owner가 같은 expired event를 동시에 reclaim하는 PostgreSQL 시나리오는
  이번 review-fix 단계에서도 별도 실행하지 않았다.

### Transactions and event ordering

- reaction/assignment producer는 aggregate 저장과 outbox 발행을 같은 service transaction에
  둔다. answer reaction의 `REQUIRES_NEW` 경합은 통합 테스트에서 확인했다.
- failure 상태 기록 실패 후 event ID/type 로그와 `FAILURE_RECORDING_FAILED` 결과는 단위
  테스트와 코드 경계로 확인했지만, 신규 worker의 다중 event PostgreSQL 증거는 미실행이다.

### External APIs

- 외부 API 없음. Push provider는 #179 범위이므로 호출하지 않았다.

### Failure recovery and reconciliation

- malformed payload→DEAD, transient 저장 실패→RETRYABLE, failure-recording 예외 격리는
  단위 테스트로 확인했다.
- 실행 후 남은 신규 scenario prefix의 중복 notification을 검사했으며, 운영 환경 대사는
  별도 실행하지 않았다.

## 7. Regression and residual risk

- `RecipientNotificationFanOutWorker`는 변경하지 않아 #176의 기존 `RECIPIENTS_CONFIRMED`
  흐름과 분리된다.
- 스케줄러·Push delivery 실제 전송·report 알림은 범위 밖이며 검증하지 않았다.
- 리뷰 수정 커밋과 push는 아직 하지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-177-TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION.md`
- Test report: this file
- CI run: PR #185의 원격 check는 review-fix local changes를 아직 포함하지 않는다.
- Related design: `docs/product/NOTIFICATION_INBOX_DESIGN.md`
- PR: `#185` (review-fix changes are local only)

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제에 후속 검토 항목이 기록됨
- [x] 실행 결과와 구현 범위가 구분됨
