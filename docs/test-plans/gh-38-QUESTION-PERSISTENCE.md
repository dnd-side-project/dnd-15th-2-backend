# Test Plan: TEST-PLAN-GH-38-QUESTION-PERSISTENCE

> Created at: `2026-08-03T18:50:29+09:00`
> GitHub Issue: `#38`
> Status: Draft — human approval required before implementation

## 1. Objective

승인된 PostgreSQL V1 schema를 변경하지 않고 질문 제안, 검수, 승인 질문, 사용자별
배정 주기를 domain model과 repository port/JPA adapter로 안전하게 영속화하는지
검증한다. 검수되지 않은 질문 노출, 부분 승인, 중복 배정, 시간 경계 손실이 발생하면
제품 정책과 데이터 이력이 함께 훼손되므로 실제 PostgreSQL의 trigger·constraint와
application transaction을 모두 증거로 남긴다.

## 2. Scope

### Included

- `question_proposal`, `question_proposal_review`, `approved_question`,
  `question_assignment_cycle`, `question_assignment`의 domain/JPA mapping과 CRUD/query
- `DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED` 최소 상태 전이와 불법 전이 거절
- review 이력 추가, proposal 상태 변경, 승인 시 `ApprovedQuestion` 생성의 단일 transaction
- 최종 승인 전 질문 비노출과 `ACTIVE` + `[activeFrom, activeUntil)` 활성 질문 조회
- 서버가 전달한 절대 `Instant`인 `activeUntil`, cycle `startsAt`/`endsAt`의 무변형 저장
- 사용자/cycleKey, cycle/question, cycle/displayOrder 중복 및 시간 check constraint
- 외부 aggregate scalar ID와 feature 간 JPA 구현 의존 금지 검증
- PostgreSQL/PostGIS Testcontainers 통합 테스트와 테스트 보고서

### Excluded

- Flyway V1, DBML/ERD, schema manifest 변경 또는 신규 migration
- 배정 주기 길이, 질문 개수, 검수 SLA, 만료 기본값 결정
- 질문 추천·랜덤·fallback, 콘텐츠 안전 검사, API/UI/인증·권한
- Direction/PostGIS, Answer, Safety, Notification persistence
- version column, row lock 또는 검수자 경합 정책의 임의 도입
- 인프라 적용, 배포, 프로덕션 변경, origin push와 PR 생성

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #38 | 제안 저장/검수 전이, 미승인 질문 비노출, 배정 중복·유효성, 서버 기준 만료, 독립 repository 경계를 검증한다. |
| `TASK.md` | 5개 테이블, scalar ID, 최소 상태 전이, 승인 transaction, 절대 시간, 로컬 검토 게이트를 구현 계약으로 사용한다. |
| ADR-0001 | Flyway V1이 schema source of truth이며 Hibernate는 `validate`만 수행한다. |
| ADR-0002 | 단순 aggregate CRUD는 JPA, domain/application은 port 의존, 외부 aggregate는 scalar ID로 참조한다. |
| Flyway V1 / schema manifest | enum/check/unique/FK/index, 제출 후 제안 문구와 승인 질문 문구 불변 trigger를 그대로 따른다. |
| Issue #37 Account contract | Account JPA Entity/Repository를 참조하지 않고 `Long` proposer/reviewer/approver/user ID만 저장한다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 불법 proposal 상태 전이가 저장된다. | 검수 이력과 노출 상태 불일치 | Medium | P0 | domain unit + repository integration |
| review만 남거나 proposal/승인 질문만 반영되는 부분 승인이 발생한다. | 승인 이력 복구 곤란 | Medium | P0 | 강제 실패 transaction rollback integration |
| 미승인·비활성·기간 밖 질문이 활성 조회에 포함된다. | 사용자에게 잘못된 질문 노출 | High | P0 | 상태/시각 경계 query integration |
| 제출/승인 뒤 문구가 변경된다. | 감사 이력과 응답 의미 훼손 | Medium | P0 | 실제 trigger integration |
| 승인 source proposal 또는 배정 key가 중복된다. | 중복 질문·중복 배정 | Medium | P0 | 실제 unique constraint integration |
| 절대 시간이 변환되거나 임의 기간이 계산된다. | 서버 기준 만료 정책 위반 | Medium | P0 | fixed `Instant` round-trip |
| 배정 질문 활성 여부가 검사 후 저장 사이에 바뀐다. | 비활성 질문 배정 race | Low | P1 | 잔여 위험 기록; 후속 lock/version 정책 필요 |
| 두 검수자가 같은 proposal을 동시에 종결한다. | 중복 review 또는 최종 상태 경합 | Medium | P1 | 잔여 위험 기록; 이번 이슈는 lock/version 제외 |
| 다른 feature의 JPA 구현을 직접 참조한다. | aggregate 결합과 변경 전파 | Medium | P0 | architecture/source dependency test |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| QUESTION-PERSISTENCE-UNIT-001 | 새 DRAFT proposal | 제출, 검수 시작, 승인 또는 반려 | 허용된 순서만 상태와 시각/사유를 변경하고 건너뛰기·종결 후 전이를 거절한다. | P0 | Domain executor |
| QUESTION-PERSISTENCE-UNIT-002 | USER_PROPOSAL source의 승인 질문 | PENDING_REVIEW/ACTIVE 생성과 활성 시각 판정 | source ID·승인 정보·활성 범위 invariant와 `[from, until)` 경계를 지킨다. | P0 | Domain executor |
| QUESTION-PERSISTENCE-UNIT-003 | 서버가 계산한 cycle 시작/종료와 assignment | 주기와 표시 순서/조회·사용 시각을 구성 | 절대 시각을 그대로 보존하고 역전 범위, 0 이하 순서, assignedAt 이전 조회·사용을 거절한다. | P0 | Domain executor |
| QUESTION-PERSISTENCE-UNIT-004 | 5개 persistence model의 모든 enum/scalar ID/Instant | domain↔JPA mapping round-trip | 의미·정밀도·nullable 값 손실 없이 복원하고 Account 관계를 만들지 않는다. | P0 | Persistence executor |
| QUESTION-PERSISTENCE-UNIT-005 | question domain/port source set | architecture dependency 검증 | JPA/Spring Data 및 다른 feature JPA Entity/Repository 직접 참조가 없다. | P0 | Test executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| QUESTION-PERSISTENCE-INT-001 | Flyway, Hibernate, 5개 JPA mapping | 빈 PostgreSQL/PostGIS container | application context 시작 및 metadata validation | 신규 migration 없이 5개 mapping이 V1 schema와 일치한다. | container 종료 |
| QUESTION-PERSISTENCE-INT-002 | Proposal repository, immutable trigger | account fixture와 DRAFT proposal | DRAFT 문구 수정 후 제출하고 다시 문구 수정 | DRAFT 수정은 저장되고 제출 후 수정은 trigger가 거절하며 기존 문구가 유지된다. | row 정리 |
| QUESTION-PERSISTENCE-INT-003 | Review service, review/proposal repositories | UNDER_REVIEW proposal | 사유 없는 반려와 유효한 반려 실행 | 사유 없는 반려는 전부 rollback하고 유효 반려는 REJECTED review와 proposal 상태만 남긴다. | row 정리 |
| QUESTION-PERSISTENCE-INT-004 | Review service, proposal/review/approved repositories | UNDER_REVIEW proposal | 승인 후, 별도 fixture에서 승인 질문 저장 실패를 강제 | 정상 승인은 review+APPROVED proposal+ACTIVE question을 함께 남기고 실패는 세 변경을 모두 rollback한다. | row 정리 |
| QUESTION-PERSISTENCE-INT-005 | ApprovedQuestion repository query | PENDING_REVIEW/ACTIVE/INACTIVE와 시각 경계 fixtures | 고정 Clock 시점의 assignable 질문 조회 | ACTIVE이면서 `activeFrom <= now`이고 `activeUntil`이 null 또는 `now < activeUntil`인 질문만 반환한다. | row 정리 |
| QUESTION-PERSISTENCE-INT-006 | ApprovedQuestion repository, immutable trigger | 저장된 승인 질문 | `questionText` 수정 및 같은 source proposal 재저장 | 문구 수정과 source 중복을 DB가 거절하고 기존 row를 유지한다. | row 정리 |
| QUESTION-PERSISTENCE-INT-007 | Cycle/Assignment repositories, constraints | account와 ACTIVE 질문 fixture | 동일 user/cycleKey, cycle/question, cycle/displayOrder를 각각 중복 저장 | 각 unique constraint가 중복을 거절하고 정상 cycle은 절대 시작/종료 시각 그대로 조회된다. | row 정리 |
| QUESTION-PERSISTENCE-INT-008 | Assignment service, active query, time checks | 비활성/기간 밖/활성 질문과 cycle | 각 질문 배정 및 잘못된 viewedAt/usedAt 저장 | service는 비활성·기간 밖 질문을 거절하고 DB는 assignedAt 이전 시각을 거절한다. | row 정리 |
| QUESTION-PERSISTENCE-INT-009 | Assignment transaction, cycle/assignment repositories | 정상 cycle 입력과 중복 assignment 묶음 | cycle과 children 일괄 저장 중 unique 실패 유발 | cycle과 모든 child가 함께 rollback되어 부분 배정이 없다. | row 정리 |

## 7. Cross-cutting scenarios

### Database and transactions

- H2 대체 없이 실제 PostgreSQL 16/PostGIS container에서 Flyway V1을 적용한다.
- 승인 transaction은 review append, proposal 종결, 승인 질문 생성을 모두 반영하거나
  모두 rollback해야 한다.
- 배정 transaction은 cycle과 assignment 묶음을 모두 반영하거나 모두 rollback해야 한다.
- DB가 보장하는 trigger/check/unique/FK와 domain/service 보완 규칙을 테스트 이름과
  보고서에서 구분한다.

### Concurrency and idempotency

- V1 unique constraint를 최종 중복 방어선으로 사용하고 conflict를 domain/application
  오류로 변환하는지 확인한다.
- version/row lock이 없으므로 동시 검수 종결과 활성 검사 후 상태 변경 race는 P1 잔여
  위험으로 남긴다. 순차 테스트 통과를 동시성 보장으로 표현하지 않는다.
- 동일 승인 명령의 재시도는 `source_proposal_id` unique로 중복 승인 질문을 만들지
  못해야 한다.

### External APIs

- 외부 API 호출은 없다. Account 등 외부 aggregate는 사전에 저장한 scalar fixture ID로
  경계만 검증하며 JPA 연관을 만들지 않는다.

### Failure recovery and reconciliation

- constraint/trigger 실패 후 transaction이 rollback되고 동일한 유효 명령을 새
  transaction에서 재시도할 수 있어야 한다.
- 실패 뒤 review/proposal/approved question 및 cycle/assignment 개수를 조회해 부분 row가
  없는지 확인한다.
- raw DB에서 review row를 수정하는 행위는 V1 trigger로 막히지 않는다. adapter는 review
  추가만 노출하고 이 한계를 테스트 보고서의 잔여 위험으로 기록한다.

## 8. Test data and isolation

- Fixtures: 테스트 전용 account, proposal/reviewer, 승인 질문, cycle/assignment builder를
  사용하고 실제 계정 식별자를 기록하지 않는다.
- Database isolation: integration class 또는 scenario마다 transaction rollback을
  우선하고 trigger/commit 검증은 명시적 cleanup으로 격리한다.
- Clock/randomness: UTC fixed `Clock`과 명시적 `Instant`를 주입한다. 기간 기본값이나
  난수를 사용하지 않는다.
- External API doubles: 외부 API가 없어 사용하지 않는다.
- Cleanup: FK 순서를 따라 assignment → cycle → approved question → review → proposal →
  account fixture 순으로 제거하거나 container를 폐기한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Domain executor | `src/main/java/com/dnd/qello/question/domain/**`, `src/test/java/com/dnd/qello/question/domain/**` | UNIT-001~003 | 해당 unit test |
| 2 | Persistence executor | `src/main/java/com/dnd/qello/question/repository/**`, `src/test/java/com/dnd/qello/question/repository/**` | UNIT-004, INT-001~002, INT-005~007 | mapping/repository test |
| 3 | Service executor | `src/main/java/com/dnd/qello/question/service/**`, `src/test/java/com/dnd/qello/question/service/**` | INT-003~004, INT-008~009 | service transaction test |
| 4 | Test orchestrator | `src/test/java/com/dnd/qello/question/architecture/**`, `docs/reports/tests/gh-38-QUESTION-PERSISTENCE.md` | UNIT-005, 전체 P0 및 잔여 위험 | Gradle check + report + harness |

각 executor는 소유 경로 밖의 파일을 수정하지 않는다. 공유 fixture가 필요하면 test
orchestrator가 `src/testFixtures` 도입 여부를 먼저 검토하고, 새 dependency는 추가하지
않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 모든 테스트 클래스 헤더에 정확한 ISO 8601 timestamp와 원본 scenario ID 기록
- [ ] 단위 테스트와 PostgreSQL/PostGIS 통합 테스트 통과
- [ ] Hibernate `validate` 및 Flyway V1 schema 불변 확인
- [ ] 승인·배정 transaction rollback과 미승인 질문 비노출 증거 확보
- [ ] DB 보장, application 보완, P1 동시성 잔여 위험을 분리한 잠재 문제 분석
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성
- [ ] `./harness check`, `./harness pr-ready --project-tests`,
  `npm run hooks:validate`, `git diff --check` 통과
- [ ] 구현 결과를 origin에 push하지 않고 사용자 검토 대기

## 11. Human approval

- Reviewer: Pending
- Decision: Pending
- Approved at: Pending
