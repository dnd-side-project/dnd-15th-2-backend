# Test Report: TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE

> Created at: `2026-10-05T18:24:03+09:00`
> GitHub Issue: `#310`
> Branch: `feat/gh-310-question-proposal-delete-mute`
> Commit: `5fc8621d` (미커밋 작업 트리 기준. 커밋 후 SHA는 PR에서 확인한다)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 승인된 계획의 단위 시나리오 UNIT-001~UNIT-014, 통합 시나리오 INT-001~INT-008 전부. 기존 단위·통합 전체 회귀.
- Unverified scope: 실제 FCM/APNs 발송(계획상 제외). INT-008이 두 실행 순서(삭제 우선·승인 우선)를 각각 한 번 이상 밟았는지는 기록하지 않았다.
- Release recommendation: 병합 가능. 6절의 잠재 문제는 후속 판단 대상이며 병합 차단 사유는 아니다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain Java 21 |
| Spring Boot | 3.5.16 |
| Database | Testcontainers PostgreSQL 16 + PostGIS 3.5 (local Docker) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` (전체 단위) | PASS | 1143 | 약 33s (테스트 실행 합계) | `build/test-results/test` |
| `./gradlew integrationTest` (전체 통합) | PASS | 771 | 약 30s (테스트 실행 합계, 컨테이너 기동 제외) | `build/test-results/integrationTest` |
| 신규 단위 4개 클래스 | PASS | 26 | - | 4절 |
| 신규 통합 3개 클래스 | PASS | 12 | - | 4절 |
| `./gradlew javaConventionCheck` | PASS | - | - | baseline 검증·TX/CTOR ratchet 통과 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `QuestionProposalDeleteMuteTest#deleteRecordsTimeWithoutChangingStatus` | SUBMITTED·UNDER_REVIEW·APPROVED·REJECTED 4개 파라미터 |
| UNIT-002 | PASS | `QuestionProposalDeleteMuteTest#deleteRequiresTime` | |
| UNIT-003 | PASS | `QuestionProposalDeleteMuteTest#deleteIsIdempotent` | |
| UNIT-004 | PASS | `QuestionProposalDeleteMuteTest#deletedProposalRejectsReviewTransitions` | |
| UNIT-005 | PASS | `QuestionProposalDeleteMuteTest#changeNotificationMutedTogglesOnlyFlag` | |
| UNIT-006 | PASS | `QuestionProposalDeleteMuteTest#changeNotificationMutedIsIdempotent` | |
| UNIT-007 | PASS | `QuestionProposalDeleteMuteTest#deletedProposalRejectsNotificationChange`, `#deletedProposalIsPushMuted` | D2, D1 |
| UNIT-008 | PASS | `QuestionProposalDeleteMuteServiceTest#deleteRejectsOtherUsersProposal`, `#changeNotificationRejectsOtherUsersProposal`, `#deleteSavesOwnedProposalWithDeletedAt`, `#deleteSkipsSaveWhenAlreadyDeleted` | 소유자 확인은 `QuestionReviewService`의 잠금 transaction 안에서 수행 |
| UNIT-009 | PASS | `QuestionProposalDeleteMuteServiceTest#applicationServiceRejectsIneligibleAccount`, `#applicationServiceDelegatesDeleteWithClock` | |
| UNIT-010 | PASS | `QuestionProposalDeleteMuteServiceTest#reviewRejectsDeletedProposal` | |
| UNIT-011 | PASS | `QuestionProposalDeleteMuteApiMockMvcTest` 6개 메서드 | 204, 404, 401, 200, 400 |
| UNIT-012 | PASS | `QuestionProposalMuteFanOutTest#mutedProposalKeepsInboxWithoutDelivery` | 실제 resolver + worker |
| UNIT-013 | PASS | `QuestionProposalMuteFanOutTest#unmutedProposalCreatesDeliveries` | |
| UNIT-014 | PASS | `QuestionProposalDeleteMuteApiMockMvcTest#changeNotificationReturnsMutedProposal` | 응답 `notificationMuted` |
| INT-001 | PASS | `QuestionProposalDeleteMuteIntegrationTest#deletedProposalLeavesListButKeepsRow` | 승인 이력·승인 질문 행 유지 확인 |
| INT-002 | PASS | `QuestionProposalDeleteMuteIntegrationTest#otherUserCannotDeleteOrMute` | |
| INT-003 | PASS | `QuestionProposalDeleteMuteIntegrationTest#secondDeleteSucceedsAndKeepsFirstTime` | |
| INT-004 | PASS | `QuestionProposalDeleteMuteIntegrationTest#deletedUnderReviewProposalCannotBeJudged` | |
| INT-005 | PASS | `QuestionProposalDeleteMuteIntegrationTest#mutedProposalSkipsDeliveryOnly` | |
| INT-006 | PASS | `QuestionProposalDeleteMuteIntegrationTest#proposalDeletedBeforeFanOutStillCompletesEvent` | event `PROCESSED`, delivery 0 |
| INT-007 | PASS | `QuestionProposalDeleteMuteMigrationIntegrationTest#existingProposalGetsDefaultsAfterV30` | 독립 schema에 V29 → V30 |
| INT-008 | PASS | `QuestionProposalDeleteReviewConcurrencyIntegrationTest#deleteAndApproveSerialize` | `@RepeatedTest(5)` |

## 5. Failures and diagnostics

최종 실행에서 실패는 없다. 작성 중 발견해 고친 항목은 다음과 같다.

- 기존 `ApiResponseConventionTest`가 `ResponseEntity<Void>` 반환을 거부했다. 저장소 규칙대로 `ResponseEntity<ApiResponse<Void>>`로 바꿨다(구현 수정).
- 기존 `FlywayMigrationContractTest`, `FlywayMigrationIntegrationTest`, `NotificationPreferenceMigrationIntegrationTest`가 migration 목록·개수를 고정값으로 갖고 있어 V30을 등록했다. 테스트 판정 기준은 바꾸지 않았고, migration 등록부만 갱신했다. 계획 9절의 "기존 테스트 미수정" 원칙의 예외이며 PR에 기록한다.
- 신규 MockMvc 테스트의 오류 JSON 경로를 `$.errorDetail.code`로 바로잡았다(테스트 수정).
- `javaConventionCheck`의 TX-001 ratchet이 변경한 `NotificationFanOutWorker`에 클래스 read-only를 요구했다. 사용자 결정에 따라 `docs/harness/JAVA_CONVENTIONS.md`에 규칙을 추가하고 클래스 `@Transactional(readOnly = true)` + `processBatch`의 `NOT_SUPPORTED`로 반영했다. resolver는 `@Transactional` 프록시를 위해 `final`을 제거했다.

## 6. Potential issues

### Application code

- 알림 끄기는 fan-out 시점의 값만 반영한다. 끄기 전에 이미 만들어진 `notification_delivery`는 회수하지 않는다. 판정 직후 worker가 먼저 돌면 사용자가 그 뒤에 꺼도 push가 나간다.
- `QuestionProposalRepository.findById`는 삭제 행을 거르지 않는다. fan-out(INT-006)과 운영자 판정이 이 동작에 의존한다. 이후 누군가 이 메서드에서 삭제 행을 제외하면 INT-006이 실패해야 정상이다.

### Infrastructure and resource limits

- 해당 없음.

### Database and migrations

- V30은 `ADD COLUMN ... DEFAULT FALSE`와 부분 인덱스 생성이다. PostgreSQL 11+에서 상수 기본값 추가는 테이블 재작성 없이 끝난다. `CREATE INDEX`는 `CONCURRENTLY`가 아니라 생성 중 `question_proposal` 쓰기를 막는다. 현재 행 수가 작아 영향은 작다고 판단하지만 운영 행 수는 확인하지 않았다.
- `docs/product/data-model/`의 schema manifest·DBML은 V1~V28 스냅샷이며 이번 변경을 반영하지 않았다(V29도 미반영 상태). 별도 데이터 모델 갱신 작업이 필요하다.

### Concurrency and idempotency

- 삭제·알림 설정·판정 모두 `findByIdForUpdate` 행 잠금으로 직렬화된다. INT-008은 삭제 시각이 승인 저장에 덮이지 않음을 확인했다.
- 알림 설정 변경과 fan-out worker 사이에는 잠금이 없다. 위 Application code 항목과 같은 경합이다.

### Transactions and event ordering

- `QuestionProposalApplicationService`에 클래스 read-only를 붙였으므로 쓰기 메서드(`submit`, `delete`, `changeNotificationMuted`)에 method-level `@Transactional`을 붙였다. 빠뜨리면 하위 `QuestionReviewService`의 쓰기가 읽기 전용 transaction에 합류한다. 전체 통합 테스트 통과로 현재 경로는 확인했다.
- `NotificationFanOutWorker.processBatch`의 `NOT_SUPPORTED`는 호출자 transaction을 중단한다. 현재 호출자는 scheduler adapter와 테스트뿐이라 동작 변화가 없다.

### External APIs

- 해당 없음. push 외부 발송은 검증 범위 밖이다.

### Failure recovery and reconciliation

- 삭제된 제안의 `QUESTION_PROPOSAL_REVIEWED` event는 dead로 끝나지 않고 처리 완료된다(INT-006).

## 7. Regression and residual risk

- 전체 단위 1143개, 통합 771개 통과. OpenAPI 산출물(`docs/api/openapi.json`)은 통합 테스트가 재생성했다.
- 잔여 위험: 알림 끄기 직전 생성된 delivery 미회수, 데이터 모델 문서 미갱신, INT-008의 실행 순서 미기록.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-310-TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE.md`
- CI run: 미실행 (PR 생성 후 확인)
- Related ADR: 없음
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
