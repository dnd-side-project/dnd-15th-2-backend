# Test Report: TEST-PLAN-GH-38-QUESTION-PERSISTENCE

> Created at: `2026-08-03T20:10:00+09:00`
> GitHub Issue: `#38`
> Branch: `feat/gh-38-question-persistence`
> Commit: pre-implementation base `01b4b54`; this report is included in the local implementation commit

## 1. Executive summary

- Result: `PASS`
- Tested scope: 질문 제안·검수·승인 질문·배정 주기의 domain/JPA mapping, scalar ID
  경계, 승인·반려 transaction, active half-open time query, trigger·FK·CHECK·unique
  constraint, cycle/assignment rollback
- Unverified scope: API/UI/authentication, safety pipeline, recommendation/fallback,
  production RDS, concurrent reviewer lost update, row-lock/version 정책
- Release recommendation: Issue #38 범위의 로컬 검토 가능. V1 trigger 결함과 DRAFT
  재삽입 우회는 아래 잔여 위험을 확인한 뒤 승인해야 한다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 21.0.12 |
| Spring Boot | 3.5.16 |
| Hibernate ORM | 6.6.53.Final |
| Database | disposable PostgreSQL 16 / PostGIS 3.5 Testcontainer |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --no-daemon` | PASS | 19 | 4s after dependency setup | JUnit XML: failures 0, errors 0 |
| `./gradlew integrationTest --no-daemon` | PASS | 21 | 23s | PostgreSQL/PostGIS Testcontainer; failures 0, errors 0 |
| `./harness check` | PASS | repository gates | local | secret, JUnit, convention, workflow, label, Husky checks passed |
| `./harness pr-ready --project-tests` | PASS | repository gates + 40 tests | 24s | local PR readiness checks passed |
| `npm run hooks:validate` / `git diff --check` | PASS | policy and diff gates | local | Husky and whitespace checks passed |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| QUESTION-PERSISTENCE-UNIT-001 | PASS | `QuestionDomainTest.enforcesProposalStateTransitions` | DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED와 불법 전이 |
| QUESTION-PERSISTENCE-UNIT-002 | PASS | `QuestionDomainTest.appliesAssignableHalfOpenTimeRange` | ACTIVE `[activeFrom, activeUntil)`와 source/approval invariant |
| QUESTION-PERSISTENCE-UNIT-003 | PASS | `QuestionDomainTest.validatesCycleAndAssignmentBoundaries` | 절대 시각, cycle range, display order, viewed/used boundary |
| QUESTION-PERSISTENCE-UNIT-004 | PASS | `QuestionJpaMapperTest.mapsAllQuestionModelsWithoutValueLoss` | 5개 model의 enum/scalar ID/nullable Instant 왕복 |
| QUESTION-PERSISTENCE-UNIT-005 | PASS | `QuestionPersistenceBoundaryTest` 3 methods | domain/port 무의존, relation·Version 부재, feature 직접 참조 금지 |
| QUESTION-PERSISTENCE-INT-001 | PASS | `startsWithValidatedQuestionMappings` | V1 schema와 Hibernate validate에서 5개 table mapping 시작 |
| QUESTION-PERSISTENCE-INT-002 | PASS | `enforcesProposalTextImmutabilityAfterSubmit` | DRAFT 변경, 제출 후 trigger 거절 및 문구 유지 |
| QUESTION-PERSISTENCE-INT-003 | PASS | `rejectsWithoutReasonAndAppendsValidReview` | 사유 없는 반려 rollback, 유효 반려 review append |
| QUESTION-PERSISTENCE-INT-004 | PASS | `approvesProposalAtomically`, `rollsBackApprovalWhenApprovedQuestionInsertFails` | 정상 승인과 source unique 충돌 전체 rollback |
| QUESTION-PERSISTENCE-INT-005 | PASS | `findsOnlyQuestionsInsideActiveRange` | ACTIVE 및 시간 경계 query |
| QUESTION-PERSISTENCE-INT-006 | PASS | `enforcesApprovedQuestionTextImmutability` | 승인 질문 문구 immutable trigger |
| QUESTION-PERSISTENCE-INT-007 | PASS | `persistsCycleAndRejectsDuplicates` | user/cycleKey, cycle/question/order 중복 |
| QUESTION-PERSISTENCE-INT-008 | PASS | `enforcesAssignmentActivityAndTimestampConstraints` | 비활성 질문 거절 및 viewed/used DB check |
| QUESTION-PERSISTENCE-INT-009 | PASS | `persistsCycleAndRejectsDuplicates` | child unique 충돌 시 cycle까지 rollback |

## 5. Failures and diagnostics

- 초기 통합 실행에서 V1 함수 `enforce_question_text_immutability()`가
  `question_proposal` UPDATE 중 존재하지 않는 `NEW.question_text`를 평가해
  `SQLGrammarException`을 냈다.
- V1 migration은 Issue #38 계약상 수정하지 않았다. 상태 변경에는 Hibernate
  `@DynamicUpdate`를 적용하고, 제출되지 않은 DRAFT의 문구 변경만 delete/insert로
  처리해 같은 trigger가 상태 변경을 가로막지 않도록 했다. 제출 이후 문구 변경은
  여전히 DB trigger가 거절한다.
- 제출 후 proposal trigger의 예외 타입은 V1 함수 결함 때문에
  `DataIntegrityViolationException`이 아닌 일반 `DataAccessException`으로
  번역된다. 문구가 저장되지 않는 결과는 검증했지만, 오류 코드 정규화는 V1 수정
  또는 후속 migration 범위다.
- 민감정보가 포함될 수 있는 원문 로그는 보고서에 기록하지 않았다.

## 6. Potential issues

### Application code

- Question API, authentication/authorization, safety moderation과 error-code mapping은
  아직 구현하지 않았다.
- DRAFT 문구 변경은 V1 trigger 결함을 피하려고 같은 transaction에서 row를 교체하므로
  draft ID가 새 identity로 바뀐다. 제출 이후 proposal ID는 변경되지 않는다.

### Infrastructure and resource limits

- disposable Testcontainer에서만 검증했다. production RDS connection limit, migration
  role, Hibernate startup latency는 미검증이다.

### Database and migrations

- Flyway V1, DBML/ERD/schema manifest는 변경하지 않았다.
- `enforce_question_text_immutability()`는 두 table 공용 함수에서
  `approved_question.question_text`를 무조건 참조한다. 현재는 proposal 문구 변경 시
  올바른 `23514` 불변식 오류가 아닌 SQL grammar 오류로 거절된다.
- 이 결함을 정식으로 고치려면 V1 수정 또는 별도 migration 승인이 필요하다. 이번
  Issue의 migration 변경 금지 계약 때문에 후속 결정으로 남겼다.

### Concurrency and idempotency

- schema에 version column이 없어 `@Version`과 row lock을 추가하지 않았다. 두 검수자가
  같은 proposal을 동시에 종결하거나 두 writer가 DRAFT를 동시에 바꾸는 경합은
  미검증이다.
- unique constraint는 재시도 중복을 막지만 application 예외를 제품 오류로 변환하는
  API 경계는 후속 범위다.

### Transactions and event ordering

- 승인 transaction은 review append, proposal 상태, approved question을 함께 flush하고
  source unique 충돌 시 모두 rollback한다.
- 배정 transaction은 cycle과 assignments를 함께 저장하고 child unique 충돌 시
  cycle도 rollback한다. Outbox/event 발행은 구현하지 않았다.

### External APIs

- 외부 API 호출은 없다. Account는 fixture의 scalar ID만 사용했다.

### Failure recovery and reconciliation

- constraint/trigger 실패 뒤 부분 row가 남지 않고 새 transaction에서 재시도 가능한지
  순차 통합 테스트로 확인했다.
- V1 trigger의 오류 타입을 정규화하거나 운영 데이터의 기존 잘못된 문구를 대사하는
  절차는 아직 없다.

## 7. Regression and residual risk

- 기존 테스트와 신규 테스트를 합쳐 unit 19개, integration 21개가 통과했다.
- V1 trigger 결함을 바로잡지 않은 상태라 proposal 문구 변경 실패 원인이 제품 오류
  코드로 안정적으로 분류되지 않는다. migration 변경 승인 없이는 이 잔여 위험을
  제거할 수 없다.
- 동시 검수 경합, production 환경, API·인증·안전 파이프라인은 미검증이다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-38-QUESTION-PERSISTENCE.md`
- Question domain/port/adapter: `src/main/java/com/dnd/qello/question/**`
- Integration tests: `src/integrationTest/java/com/dnd/qello/QuestionPersistenceIntegrationTest.java`
- Related ADR: `docs/adr/0001-database-schema-ownership.md`, `docs/adr/0002-jpa-jdbc-boundary.md`
- CI run: 없음 — local PR readiness만 실행
- PR: branch push 후 생성 예정

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트와 잔여 위험이 명시됨
- [x] V1 migration/ERD/schema manifest를 변경하지 않음
- [x] 실행 결과와 보고서가 일치함
