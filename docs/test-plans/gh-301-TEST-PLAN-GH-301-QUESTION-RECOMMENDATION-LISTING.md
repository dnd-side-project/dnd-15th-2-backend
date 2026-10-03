# Test Plan: TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING

> Created at: `2026-10-02T16:35:38+09:00`
> GitHub Issue: `#301`
> Status: Approved

## 1. Objective

프론트엔드가 `GET /api/v1/questions/recommendations`로 받은 `approvedQuestionId`를 그대로 질문 전송에 쓸 수 있는지 검증한다. 목록에 비활성·기간 밖 질문이 섞이면 전송이 `QUESTION_NOT_ACTIVE`로 거부되고, 활성 질문이 빠지면 사용자가 고를 질문이 사라진다.

## 2. Scope

### Included

- 추천 질문 조회 서비스의 필터·정렬·응답 매핑(단위)
- HTTP 경계: 인증, 응답 envelope, 필드 이름(MockMvc)
- ApiSpec·Controller 분리와 경로(웹 계약)
- PostgreSQL에서 `findAssignableAt(now)` 기준 필터와 전송 검증 일치(통합)

### Excluded

- 사용자별 cycle·assignment 생성과 조회(Issue 제외 범위)
- `first_viewed_at`·`used_at` 갱신
- 질문 시드와 운영자 질문 생성 API
- 성능 측정(활성 질문 풀이 운영자 승인 수로 제한된다)

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #301 | 활성 기간 안의 `ACTIVE` 질문만 반환한다 |
| GitHub Issue #301 | 반환한 `approvedQuestionId`로 질문 전송이 `QUESTION_NOT_ACTIVE`로 거부되지 않는다 |
| GitHub Issue #301 | 비로그인 요청은 401이다 |
| GitHub Issue #301 | 응답 항목은 `approvedQuestionId`, `questionText`, `answerFormat`이다 |
| `SpringDataApprovedQuestionRepository.findAssignableAt` | `status = ACTIVE`, `activeFrom <= at`, `activeUntil is null or activeUntil > at`, `order by id` |
| `DirectionPostService` 전송 검증 | 같은 `findAssignableAt(submittedAt)`로 활성 여부를 판단한다 |
| `QuestionProposalApplicationService.findMine` | 사용자 API는 ACTIVE USER 계정만 허용한다(아래 D-1 결정 필요) |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| `PENDING_REVIEW`·`INACTIVE`·`ARCHIVED` 질문이 목록에 포함된다 | 전송 실패 | Medium | P0 | INT-001 |
| `active_from` 미래, `active_until` 경계(같은 시각) 처리 오류 | 전송 실패 또는 질문 누락 | Medium | P0 | INT-001 |
| 서비스가 시스템 시각 대신 다른 시각을 써서 전송 검증과 어긋난다 | 목록과 전송 결과 불일치 | Low | P0 | UNIT-001, INT-002 |
| 비로그인 요청이 통과한다 | 비인증 노출 | Low | P0 | UNIT-004 |
| 응답 필드 이름이 프론트 계약과 다르다 | 프론트 파싱 실패 | Medium | P1 | UNIT-003 |
| OPERATOR·비활성 계정이 사용자 API를 호출한다 | 정책 불일치 | Low | P1 | UNIT-002(D-1 확정 시) |
| 활성 질문이 없을 때 오류를 반환한다 | 프론트 빈 화면 처리 불가 | Low | P1 | UNIT-001 빈 목록 케이스 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING-UNIT-001 | 고정 Clock, repository stub이 `findAssignableAt(clock.instant())`에 질문 2건을 돌려준다 | 서비스 조회 | stub이 Clock 시각으로 호출되고 반환 순서를 유지한다. stub이 빈 목록이면 빈 목록을 반환한다 | P0 | Executor A |
| TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING-UNIT-002 | OPERATOR 계정 또는 ACTIVE가 아닌 USER 계정 | 서비스 조회 | 기존 `findMine`과 같은 오류로 거부하고 repository를 호출하지 않는다(D-1이 "검사함"일 때만) | P1 | Executor A |
| TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING-UNIT-003 | 인증 사용자, 서비스 mock이 질문 1건을 돌려준다 | `GET /api/v1/questions/recommendations` | 200, `data[0].approvedQuestionId`·`questionText`·`answerFormat` 존재, 다른 내부 필드(`status`, `approvedBy` 등) 없음 | P0 | Executor A |
| TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING-UNIT-004 | 인증 정보 없음 | `GET /api/v1/questions/recommendations` | 401, 서비스 호출 없음 | P0 | Executor A |
| TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING-UNIT-005 | 새 ApiSpec·Controller·Response 클래스 | 리플렉션 검사 | Controller가 ApiSpec을 구현하고, Response가 `question.web.response` 패키지에 있으며, 경로가 `/api/v1/questions` 아래다 | P1 | Executor A |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING-INT-001 | 추천 조회 서비스, JPA repository, PostgreSQL | 고정 시각 T. ACTIVE(기간 안, `active_until` null), ACTIVE(`active_from` = T), ACTIVE(`active_from` > T), ACTIVE(`active_until` = T), ACTIVE(`active_until` < T), `PENDING_REVIEW`, `INACTIVE` 질문을 넣는다 | 서비스 조회 | 기간 안 ACTIVE와 `active_from` = T인 질문만 `id` 오름차순으로 반환한다 | 테스트별 truncate(기존 `resetDatabaseAndClock` 방식) |
| TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING-INT-002 | 추천 조회 서비스, `DirectionPostApplicationService`, PostgreSQL/PostGIS | INT-001과 같은 질문 구성, 발신자·방향 체계·presence는 `DirectionMatchingVerticalFlowIntegrationTest`의 fixture 구성을 따른다 | 추천 목록 첫 질문 id로 전송하고, 목록에 없는 `INACTIVE` 질문 id로도 전송한다 | 첫 질문은 전송되고, `INACTIVE` 질문은 `QUESTION_NOT_ACTIVE`로 거부된다 | 테스트별 truncate |

## 7. Cross-cutting scenarios

### Database and transactions

- 조회 전용이다. 서비스는 `@Transactional(readOnly = true)` 또는 트랜잭션 없이 동작하며 쓰기가 없다. INT-001 이후 `approved_question` 행 수가 변하지 않는지 확인한다.
- `ck_approved_question_approval` 때문에 ACTIVE 행에는 `approved_by`(user_account FK)가 필요하다. fixture에서 승인자 계정을 먼저 만든다.

### Concurrency and idempotency

- 해당 없음. 상태를 바꾸지 않는 GET이다.

### External APIs

- 해당 없음.

### Failure recovery and reconciliation

- 목록 조회 시각과 전송 시각 사이에 질문이 만료되면 전송이 `QUESTION_NOT_ACTIVE`로 거부된다. 이는 기존 전송 검증의 동작이며 이번 범위에서 바꾸지 않는다. operation description에 적는다.

## 8. Test data and isolation

- Fixtures: SQL INSERT로 `user_account`(승인자·요청자), `approved_question`(`source_type = OPERATOR`)을 넣는다. INT-002는 방향 체계·presence fixture를 추가한다.
- Database isolation: `PostgisContainerIntegrationTestSupport` 상속, 테스트마다 관련 테이블 truncate
- Clock/randomness: 단위는 `Clock.fixed`, 통합은 기존 `MutableClock` 패턴
- External API doubles: 없음
- Cleanup: `@BeforeEach` truncate

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor A | `src/test/java/com/dnd/qello/question/service/QuestionRecommendationServiceTest.java`, `src/test/java/com/dnd/qello/question/web/QuestionRecommendationApiMockMvcTest.java`, `src/test/java/com/dnd/qello/question/web/QuestionProposalWebContractTest.java`(UNIT-005 메서드 추가) | UNIT-001~005 | `./gradlew test --tests 'com.dnd.qello.question.*'` |
| 2 | Executor B | `src/integrationTest/java/com/dnd/qello/QuestionRecommendationIntegrationTest.java` | INT-001, INT-002 | `./gradlew integrationTest --tests 'com.dnd.qello.QuestionRecommendationIntegrationTest'` |

두 실행자는 파일이 겹치지 않는다. production 클래스 이름(`QuestionRecommendationService` 등)은 구현 단계에서 확정되며, 바뀌면 테스트 파일 이름도 같이 바꾼다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: @Byuntil
- Decision: 승인. D-1은 "허용 계정을 검사한다"로 확정하고 UNIT-002를 포함한다.
- Approved at: 2026-10-02

### 승인 시 결정할 항목

- D-1: 추천 조회도 `findMine`처럼 ACTIVE USER 계정만 허용할지 정한다. 권장안은 "허용 계정을 검사한다"이다. 사용자 API 간 정책을 맞추고, OPERATOR 계정이 질문을 받지 않게 하기 위해서다. 검사하지 않기로 하면 UNIT-002를 뺀다.
