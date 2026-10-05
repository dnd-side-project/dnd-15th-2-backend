# Test Plan: TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE

> Created at: `2026-10-05T17:53:43+09:00`
> GitHub Issue: `#310`
> Status: Approved

## 1. Objective

사용자가 "제안한 질문" 화면에서 자기 제안을 삭제하고, 제안 1건의 검토 결과 push를 끌 수
있는지 검증한다. 실패하면 다른 사용자의 제안이 지워지거나, 철회한 제안이 운영자 판정으로
승인 질문이 되거나, 알림을 끈 제안에 push가 나간다. 소프트 삭제로 인해 fan-out이 제안을
찾지 못하면 outbox event가 dead로 끝나 다른 정상 알림 처리도 가려질 수 있다.

## 2. Scope

### Included

- `QuestionProposal` 도메인의 삭제·알림 끄기/켜기 전이 규칙
- `QuestionProposalApplicationService`의 소유자 확인과 삭제·알림 끄기 위임
- `QuestionReviewService`의 삭제된 제안 검수 시작·승인·반려 거부
- `DELETE /api/v1/questions/proposals/{proposalId}`와 제안별 알림 끄기·켜기 endpoint의 HTTP 계약
- `GET /api/v1/questions/proposals/me`의 삭제 제안 제외와 알림 끄기 여부 응답
- 신규 Flyway 마이그레이션의 컬럼 기본값과 기존 행 호환
- `QUESTION_PROPOSAL_REVIEWED` fan-out의 제안별 push delivery 억제
- 삭제와 운영자 판정의 동시 실행 직렬화

### Excluded

- 알림 종류 단위 설정(`PUT /notifications/preferences`) 동작 자체. 기존 테스트가 담당하고 회귀만 확인한다.
- `GET /proposals/me`의 상태 필터·페이지네이션, 승인 질문 ID 응답, 삭제 복구 API (Issue #310 제외 범위)
- 실제 FCM/APNs 발송. delivery 행 생성 여부까지만 검증한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #310 | 본인 제안 삭제 후 `GET /proposals/me`에 나오지 않는다 |
| GitHub Issue #310 | 다른 사용자의 제안 삭제·알림 끄기 요청은 404, 대상 제안 불변 |
| GitHub Issue #310 | 같은 제안을 두 번 삭제해도 두 번째 요청이 성공 응답 |
| GitHub Issue #310 | 삭제된 `UNDER_REVIEW` 제안의 승인·반려는 409, `question_proposal_review` 행 미생성 |
| GitHub Issue #310 | 알림 끈 제안이 판정되면 `notification_delivery` 미생성, 다른 제안은 기존대로 생성 |
| GitHub Issue #310 | 삭제·알림 끄기 상태 전이 규칙의 `QuestionProposal` 단위 테스트 |
| TASK.md Decisions | 삭제는 모든 상태에서 허용, 물리 삭제 대신 소프트 삭제 |
| Schema (V1) | `question_proposal_review.proposal_id`, `approved_question.source_proposal_id`가 `ON DELETE RESTRICT`로 참조 |
| 기존 동작 | `NotificationFanOutWorker.persistPendingDeliveries`는 종류별 설정이 꺼져도 notification 행은 남기고 delivery만 생략한다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 소유자 확인 누락으로 타인 제안 삭제·알림 변경 | 높음 | 중간 | P0 | 타인 제안 요청 시 404와 DB 행 불변 (UNIT-008, INT-002) |
| 삭제된 `UNDER_REVIEW` 제안이 운영자 승인으로 승인 질문이 됨 | 높음 | 중간 | P0 | 승인·반려 409, review·approved_question·outbox 행 0 (UNIT-010, INT-004) |
| 삭제와 판정이 동시에 실행되어 둘 다 성공 | 높음 | 낮음 | P0 | 두 transaction 중 하나만 성공하고 최종 상태가 일관 (INT-008) |
| `findById`가 소프트 삭제 행을 거르면서 fan-out resolver가 제안을 못 찾아 event가 dead | 높음 | 중간 | P0 | 판정 후 삭제된 제안의 event가 완료 상태로 끝남 (INT-006) |
| 알림 끈 제안에 push delivery 생성 | 중간 | 중간 | P0 | delivery 0, 다른 제안 delivery 정상 (UNIT-012, INT-005) |
| 마이그레이션이 기존 행에 NULL 알림 값을 남겨 도메인 restore 실패 | 중간 | 낮음 | P1 | 기존 행의 기본값 확인 (INT-007) |
| 중복 삭제가 오류나 `deleted_at` 덮어쓰기를 유발 | 낮음 | 중간 | P1 | 두 번째 삭제 성공, 최초 삭제 시각 유지 (UNIT-003, INT-003) |
| 운영자 판정·조회 경로가 삭제 행을 정상 제안처럼 노출 | 중간 | 낮음 | P1 | 목록 제외 (INT-001) |

## 5. Unit scenarios

경로: `src/test`. 리포지토리는 mock 또는 기존 fake를 사용한다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-001 | `SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED` 제안 각각 | 삭제한다 | 모두 삭제 시각이 기록되고 상태 값은 바뀌지 않는다 | P0 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-002 | 삭제 시각이 null인 시각 인자 | 삭제한다 | `QuestionException`(필수 값 누락)을 던진다 | P1 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-003 | 이미 삭제된 제안 | 다시 삭제한다 | 예외 없이 최초 삭제 시각을 유지한다 | P1 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-004 | 삭제된 `UNDER_REVIEW` 제안 | `startReview`, `approve`, `reject`를 호출한다 | 각각 `INVALID_PROPOSAL_STATUS`(409)를 던진다 | P0 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-005 | 알림이 켜진 제안 | 알림을 끈 뒤 다시 켠다 | 끄기 후 muted=true, 켜기 후 muted=false이고 다른 필드는 그대로다 | P0 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-006 | 이미 알림이 꺼진 제안 | 다시 끈다 | 예외 없이 muted=true를 유지한다 | P2 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-007 | 삭제된 제안 | 알림을 끄거나 켠다 | `PROPOSAL_NOT_FOUND`를 던진다(D2) | P1 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-008 | 제안자 A의 제안, 요청자 B | application service에서 삭제·알림 끄기를 호출한다 | `PROPOSAL_NOT_FOUND`를 던지고 `save`를 호출하지 않는다 | P0 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-009 | `ACTIVE`가 아니거나 `USER`가 아닌 계정 | 삭제·알림 끄기를 호출한다 | `PROPOSER_ACCOUNT_NOT_ELIGIBLE`(403)을 던진다 | P1 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-010 | `findByIdForUpdate`가 삭제된 `UNDER_REVIEW` 제안을 반환 | `QuestionReviewService.approve`/`reject`를 호출한다 | 409를 던지고 review·approved question·outbox `save`를 호출하지 않는다 | P0 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-011 | 인증된 사용자 | `DELETE /proposals/{id}`, 알림 끄기·켜기 요청을 MockMvc로 보낸다 | 성공 상태 코드와 응답 envelope, 401(토큰 없음), 404(소유자 아님) 매핑이 `QuestionProposalApiSpec`과 일치한다 | P0 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-012 | muted=true 제안의 `QUESTION_PROPOSAL_REVIEWED` event, push 종류 설정은 켜짐 | fan-out worker가 처리한다 | notification은 저장하고 delivery는 저장하지 않는다 | P0 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-013 | muted=false 제안의 같은 event | fan-out worker가 처리한다 | 기존처럼 활성 기기 수만큼 delivery를 저장한다 | P0 | Unit executor |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-014 | `QuestionProposalResponse.from` | muted 제안을 변환한다 | 응답에 알림 끄기 여부가 들어간다 | P2 | Unit executor |

## 6. Integration scenarios

경로: `src/integrationTest`. `PostgisContainerIntegrationTestSupport`와 실제 Flyway 마이그레이션을 사용한다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-INT-001 | application service, JPA repository, PostgreSQL | ACTIVE USER와 제안 3건 | 1건 삭제 후 `findMine` | 2건만 최신순으로 반환하고, 삭제 행은 DB에 남아 삭제 시각이 있다 | `@BeforeEach` 테이블 정리 |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-INT-002 | application service, PostgreSQL | 사용자 A의 제안, 사용자 B | B가 삭제·알림 끄기 요청 | `PROPOSAL_NOT_FOUND`, A 제안의 삭제 시각·muted 값 불변 | 동일 |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-INT-003 | application service, PostgreSQL | 제안 1건 | 같은 제안을 두 번 삭제 | 두 번째도 성공하고 삭제 시각은 첫 번째 값이다 | 동일 |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-INT-004 | `QuestionReviewService`, PostgreSQL | `UNDER_REVIEW` 제안을 사용자가 삭제 | 운영자 승인과 반려를 각각 시도 | 409, `question_proposal_review`·`approved_question`·`outbox_event` 행 0 | 동일 |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-INT-005 | review service, outbox, `NotificationFanOutWorker`, PostgreSQL | 같은 사용자의 제안 2건, 1건만 muted, 활성 push 기기 1대 | 두 제안을 승인한 뒤 fan-out worker 실행 | 두 제안 모두 notification 행 생성, muted 제안의 delivery 0, 다른 제안 delivery 1 | 동일 |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-INT-006 | review service, fan-out worker, PostgreSQL | 승인 판정 직후(outbox 미처리) 사용자가 제안 삭제 | fan-out worker 실행 | outbox event가 dead가 아닌 완료 상태로 끝나고, notification·delivery 결과가 D1 결정과 일치한다 | 동일 |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-INT-007 | Flyway, PostgreSQL | 신규 마이그레이션 직전 버전에 제안 행 삽입 | 신규 마이그레이션 적용 후 repository로 조회 | 삭제 시각 NULL, muted=false로 restore된다 | 컨테이너 단위 격리 |
| TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-INT-008 | review service, application service, PostgreSQL 행 잠금 | `UNDER_REVIEW` 제안 1건 | 두 스레드에서 삭제와 승인을 동시에 실행 | 둘 중 하나만 성공한다. 삭제가 먼저면 승인 409와 review 0, 승인이 먼저면 삭제 성공과 승인 질문 1건이 남는다 | 동일 |

## 7. Cross-cutting scenarios

### Database and transactions

- 삭제와 알림 끄기는 `findByIdForUpdate`로 행을 잠근 transaction 안에서 상태를 읽고 쓴다. INT-008이 이를 검증한다.
- 물리 삭제는 FK `ON DELETE RESTRICT` 때문에 실패하므로, 판정 이력이 있는 제안을 삭제해도 review·approved_question 행이 남아야 한다(INT-001에서 승인된 제안 1건 포함).
- 마이그레이션은 기존 행 호환만 확인한다(INT-007). 롤백 스크립트는 저장소 관행상 작성하지 않는다.

### Concurrency and idempotency

- 삭제와 운영자 판정의 경합: INT-008.
- 중복 삭제: UNIT-003, INT-003.
- 알림 끄기 직후 이미 생성된 delivery는 회수하지 않는다. fan-out 시점의 muted 값만 반영한다는 점을 보고서에 잠재 문제로 기록한다.

### External APIs

- 없음. push 발송은 delivery 행 생성까지만 확인하고 외부 push 클라이언트는 호출하지 않는다.

### Failure recovery and reconciliation

- 소프트 삭제된 제안의 fan-out이 `invalidPayload`로 dead 처리되지 않아야 한다(INT-006). `QuestionProposalRepository.findById`가 삭제 행을 거르도록 바꾸면 이 시나리오가 실패한다.
- fan-out lease 재처리 시 muted 제안도 notification 1건으로 수렴하는지는 기존 dedupKey 테스트가 담당하므로 새로 만들지 않는다.

## 8. Test data and isolation

- Fixtures: 기존 `QuestionProposalApiIntegrationTest`의 계정·지역 생성 방식을 따른다. 지역 코드는 `TEST-QUESTION-310`처럼 이 계획 전용 값을 쓴다.
- Database isolation: Testcontainers PostgreSQL(PostGIS) 공유 컨테이너, 각 테스트 `@BeforeEach`에서 관련 테이블을 정리한다.
- Clock/randomness: `MutableClock`(기존 test configuration 패턴)으로 삭제·판정 시각을 고정한다.
- External API doubles: 없음.
- Cleanup: 테스트가 만든 계정·제안·outbox·notification 행을 테이블 정리로 제거한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Unit executor | `src/test/java/com/dnd/qello/question/domain/QuestionProposalDeleteMuteTest.java` (신규) | UNIT-001 ~ UNIT-007 | `./gradlew test --tests '*QuestionProposalDeleteMuteTest'` |
| 2 | Unit executor | `src/test/java/com/dnd/qello/question/service/QuestionProposalDeleteMuteServiceTest.java` (신규) | UNIT-008 ~ UNIT-010 | `./gradlew test --tests '*QuestionProposalDeleteMuteServiceTest'` |
| 3 | Unit executor | `src/test/java/com/dnd/qello/question/web/QuestionProposalDeleteMuteApiMockMvcTest.java` (신규) | UNIT-011, UNIT-014 | `./gradlew test --tests '*QuestionProposalDeleteMuteApiMockMvcTest'` |
| 4 | Unit executor | `src/test/java/com/dnd/qello/notification/fanout/QuestionProposalMuteFanOutTest.java` (신규) | UNIT-012, UNIT-013 | `./gradlew test --tests '*QuestionProposalMuteFanOutTest'` |
| 5 | Integration executor | `src/integrationTest/java/com/dnd/qello/QuestionProposalDeleteMuteIntegrationTest.java` (신규) | INT-001 ~ INT-006 | `./gradlew integrationTest --tests '*QuestionProposalDeleteMuteIntegrationTest'` |
| 6 | Integration executor | `src/integrationTest/java/com/dnd/qello/QuestionProposalDeleteMuteMigrationIntegrationTest.java`, `src/integrationTest/java/com/dnd/qello/QuestionProposalDeleteReviewConcurrencyIntegrationTest.java` (신규) | INT-007, INT-008 | `./gradlew integrationTest --tests '*QuestionProposalDeleteMute*' --tests '*QuestionProposalDeleteReviewConcurrency*'` |

기존 테스트 파일은 수정하지 않는다. 구현 변경으로 기존 테스트(`QuestionDomainTest`, `QuestionReviewServiceTest`, `NotificationFanOutWorkerTest`, `OpenApiSpecificationIntegrationTest` 등)가 깨지면 실행 에이전트가 고치지 않고 보고서에 기록해 구현 쪽에서 판단한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과 (`./gradlew test`)
- [ ] 통합 테스트 통과 (`./gradlew integrationTest`)
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성 (`templates/test-report.md`)

실패 판단 기준: P0 시나리오 중 하나라도 실패하거나 실행하지 못하면 FAIL 또는 BLOCKED로 보고한다. 기존 테스트 회귀가 있으면 FAIL이다.

## 11. Open decisions

2026-10-05 사용자가 D1~D3 권장안을 승인했다.

| ID | 질문 | 권장안 |
| --- | --- | --- |
| D1 | 판정 직후 fan-out 전에 삭제된 제안의 알림을 어떻게 처리하는가 | (확정) 삭제를 알림 끄기로 간주한다. notification 행은 남기고 push delivery는 만들지 않는다 |
| D2 | 삭제된 제안에 알림 끄기·켜기를 요청하면 어떻게 응답하는가 | (확정) 404 `PROPOSAL_NOT_FOUND` |
| D3 | 삭제 성공 응답 코드 | (확정) 첫 삭제와 중복 삭제 모두 204 No Content |

## 12. Human approval

- Reviewer: Byuntil (저장소 사용자, 대화 세션에서 승인)
- Decision: 권장안 D1~D3 포함 승인
- Approved at: 2026-10-05T17:56:32+09:00
