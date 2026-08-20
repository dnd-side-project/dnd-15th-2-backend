# Test Report: TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION

> Created at: `2026-08-20T19:22:00+09:00`
> GitHub Issue: `#177`
> Branch: `feat/gh-177-notification-fanout-expansion`
> Commit: `working tree; commit not created`
> Last revalidated at: `2026-08-20T19:47:24+09:00`

## 1. Executive summary

- Result: `PARTIAL`
- Tested scope: 네 가지 notification producer/consumer, 질문자 단일 fan-out, reaction 경합,
  PostgreSQL replay dedup, account/block/preference gate, N1 targetKind, 기존 관련 회귀
- Unverified scope: 승인 계획의 26개 단위·14개 통합 시나리오를 각각 별도 테스트 메서드로
  모두 구현한 것은 아니다. lease 두 worker의 동시 reclaim, failure-recording 후속 event의
  PostgreSQL 증거와 일부 세부 매트릭스는 단위 또는 기존 회귀 테스트로만 확인했다.
- Release recommendation: 핵심 #177 동작은 병합 검토 가능하다. 커밋·push·PR 전에는 위
  미검증 시나리오를 추가 실행하거나 명시적 제외 승인을 기록해야 한다.

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
| `./gradlew test --console=plain` | PASS | 825 | 9s | Gradle `BUILD SUCCESSFUL` |
| `./gradlew integrationTest --tests ...` | PASS | 82 | 28s | fan-out expansion plus notification/answer/question regression classes |
| `./harness test-run --id TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION` | PASS | unit + integration task | 32s | Gradle unit/integration tasks completed successfully |
| `./gradlew test --tests NotificationFanOutWorkerTest --tests NotificationFanOutResolverTest --tests AnswerReactionServiceTest --tests QuestionAssignmentServiceTest --console=plain` | PASS | focused unit | 3s | refactor revalidation; Gradle `BUILD SUCCESSFUL` |
| `./gradlew integrationTest --tests com.dnd.qello.NotificationFanOutExpansionIntegrationTest --console=plain` | PASS | focused integration | 11s | refactor revalidation; Gradle `BUILD SUCCESSFUL` |
| `./harness check` | PASS | policy checks | <1s | secret, JUnit, convention, workflow, label, Husky checks passed |
| `./harness pr-ready --project-tests` | PASS | project validation | 5m 1s | refactor revalidation; project test gate completed successfully |
| `npm run hooks:validate` | PASS | Husky validation | <1s | validation passed |
| `git diff --check` | PASS | whitespace | <1s | no whitespace errors |

## 4. Scenario results

| Scenario | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| 질문자 한 명 `ANSWER_RECEIVED` | PASS | `NotificationFanOutExpansionIntegrationTest.fansOutAllNotificationTypesWithCorrectRecipientsAndTargets` | 다른 recipient 0건 확인 |
| 네 종류 producer/consumer | PASS | same integration test | answer, reaction, proposal review, recommendation 모두 processed |
| 동일 event replay dedup | PASS | `deduplicatesRepeatedAnswerPublishedEvent` | 같은 outbox row를 PENDING으로 재현 후 notification 1건 |
| 동시 answer reaction | PASS | `concurrentReactionCreatesOneReactionAndOneOutbox` | reaction 1건, ANSWER_REACTED outbox 1건 |
| targetKind mapping | PASS | same integration test, `NotificationInboxQueryIntegrationTest` regression | answer=`ANSWER`, question=`NONE` |
| preference and block gates | PASS | `NotificationFanOutWorkerTest` | preference off는 row 유지/delivery 억제, block은 row 억제 |
| retry/DEAD/failure recording isolation | PASS | `NotificationFanOutWorkerTest` | unit boundary verified; PostgreSQL reclaim matrix remains partial |
| 기존 #176 notification/answer/question contracts | PASS | selected integration regression classes | no regression observed |

## 5. Failures and diagnostics

- 초기 RED 단계에서 신규 worker/resolver와 producer 계약이 없어 컴파일·검증 실패를
  확인했다. 테스트가 요구한 최소 계약을 구현한 뒤 동일 테스트가 통과했다.
- 최종 실행에서 재현 가능한 테스트 실패는 없었다.
- 2026-08-20T19:47:24+09:00 리팩터링 후 집중 단위·통합 테스트와 repository gate를
  다시 실행했고 재현 가능한 실패는 없었다.

## 6. Potential issues

### Application code

- `ANSWER_REACTED` notification dedup은 outbox event ID를 사용하고, producer outbox dedup은
  answer/reactor/createdAt 조합을 사용한다. 같은 timestamp로 취소 후 재공감하는 호출은
  동일 occurrence로 취급될 수 있으므로 API 호출자가 reaction 시각을 임의로 재사용하지
  않는지 후속 계약 검토가 필요하다.

### Infrastructure and resource limits

- Testcontainers 기반 실행은 Docker 자원과 PostgreSQL startup에 의존한다. 이번 실행에서는
  환경 실패가 없었다.

### Database and migrations

- 새 Flyway migration은 추가하지 않았다. 기존 notification/outbox CHECK와 unique constraint를
  실제 PostgreSQL에서 통과했다.
- 질문 assignment 조회를 위해 repository에 `findById` 계약을 추가했고 JPA 구현을 연결했다.

### Concurrency and idempotency

- 동시 reaction 삽입과 sequential lease replay는 확인했다.
- 서로 다른 worker owner가 같은 expired event를 동시에 reclaim하는 PostgreSQL 시나리오는
  이번 구현 단계에서 별도 실행하지 않았다.

### Transactions and event ordering

- reaction/assignment producer는 aggregate 저장과 outbox 발행을 같은 service transaction에
  둔다. answer reaction의 `REQUIRES_NEW` 경합은 통합 테스트에서 확인했다.
- failure 상태 기록 실패 후 후속 claimed event 진행은 기존 #123 worker 계약과 신규 worker
  단위 테스트로 확인했지만, 신규 worker의 다중 event PostgreSQL 증거는 미실행이다.

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
- 커밋, push, PR 생성은 아직 하지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-177-TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION.md`
- Test report: this file
- CI run: not created
- Related design: `docs/product/NOTIFICATION_INBOX_DESIGN.md`
- PR: not created

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제에 후속 검토 항목이 기록됨
- [x] 실행 결과와 구현 범위가 구분됨
