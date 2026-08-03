# GitHub Issue #38 Task Contract

> Generated at: `2026-08-03T18:47:24+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Question persistence`
- GitHub Issue: `#38`
- Branch: `feat/gh-38-question-persistence`

## Objective

- 승인된 질문 schema 위에 QuestionProposal → Review → ApprovedQuestion →
  AssignmentCycle/Assignment 흐름을 domain model과 repository port/JPA adapter로
  구현하고, 검토되지 않은 질문의 노출과 중복 배정을 transaction·constraint로
  차단한다.

## Scope

- `question_proposal`, `question_proposal_review`, `approved_question`,
  `question_assignment_cycle`, `question_assignment` 5개 테이블을 JPA로 매핑한다.
- 제안, 승인 질문, 배정 주기를 분리된 aggregate로 두고 repository port와 JPA
  adapter를 제공한다.
- Account 및 aggregate 간 연결은 Entity 관계가 아닌 scalar ID로 보관한다.
- 제안은 `DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED` 최소 검수 흐름을
  domain에서 검증하고, 제출 후 문구 불변 trigger를 함께 검증한다.
- 승인·반려 시 review append, proposal 상태 변경, 승인 질문 생성을 한 transaction
  경계에서 처리한다.
- 사용자 제안에서 생성된 승인 질문은 최종 승인 transaction이 끝나기 전에는
  `ACTIVE` 질문 조회에 노출하지 않는다.
- 활성 질문 조회는 `status = ACTIVE`와 `[active_from, active_until)` 절대 시각을
  기준으로 한다.
- 배정 주기는 서버가 계산해 전달한 절대 `startsAt`/`endsAt`을 저장하고 사용자와
  `cycleKey` 중복을 차단한다.
- 같은 주기에서 질문과 표시 순서 중복, 잘못된 노출·사용 시각을 domain과 DB
  constraint로 검증한다.
- PostgreSQL/PostGIS Testcontainers에서 상태 전이, trigger, unique/check constraint,
  transaction rollback과 repository query를 검증한다.

## Explicit exclusions

- 적용된 V1 migration과 DBML/ERD/schema manifest를 수정하지 않는다.
- 질문 배정 주기 길이, 주기당 질문 개수, 검수 SLA와 만료 기본값을 만들지 않는다.
- 추천·랜덤 선택·최근 질문 제외·fallback 알고리즘을 구현하지 않는다.
- 비속어·중복 의미·콘텐츠 안전 검사는 후속 safety pipeline 범위로 남긴다.
- Question API, 운영 검수 UI, 인증·권한 검사를 구현하지 않는다.
- Direction/PostGIS, Answer, Safety, Notification persistence를 구현하지 않는다.
- Account JPA Entity/Repository를 직접 참조하지 않고 `Long` account ID만 사용한다.
- 다른 feature가 Question JPA Entity 또는 Spring Data Repository를 참조하게 하지
  않는다.
- 동시 상태 갱신용 version column이나 row lock을 임의 추가하지 않는다. 검수자
  경합은 별도 concurrency 정책이 승인될 때까지 잔여 위험으로 기록한다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Question domain models and transition rules | Issue #38 domain executor | Domain policy review |
| Five JPA mappings, mappers and repository adapters | Issue #38 persistence executor | Schema mapping review |
| Review approval and assignment transaction services | Issue #38 service executor | Transaction boundary review |
| Unit/integration tests and report | TEST-PLAN-GH-38-QUESTION-PERSISTENCE | Test-plan approval |

## Existing user-owned changes

- Issue #37 승인 commit `9e98dbc`을 origin에 push한 clean 상태에서 분기했다.
- Issue #35~#37의 ADR, Flyway V1, JPA auditing과 Account domain/adapter를 선행
  계약으로 보존한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- 질문 제안 저장과 최소 검수 상태 전이가 domain 및 실제 DB에서 검증된다.
- 제출 후 제안 문구와 승인 질문 문구를 변경하면 V1 trigger가 거절한다.
- 반려 review는 사유가 필수이고 승인/반려 이력이 append-only로 저장된다.
- 승인 transaction이 review, proposal, ApprovedQuestion을 모두 반영하거나 모두
  rollback한다.
- 승인되지 않았거나 활성 기간 밖인 질문은 assignable query에서 반환되지 않는다.
- `activeUntil`과 배정 주기 `endsAt`은 서버가 전달한 절대 `Instant` 그대로 보존되며
  임의 기본 기간을 계산하지 않는다.
- 같은 사용자/cycleKey, 같은 주기의 질문 및 displayOrder 중복이 거절된다.
- assignment의 firstViewedAt/usedAt은 assignedAt보다 빠를 수 없다.
- 모든 외부 aggregate 참조가 scalar ID이고 feature 간 JPA 구현 직접 참조가 없다.
- 모든 JUnit 5 테스트와 Harness, Gradle check, Hook 검증이 통과한다.
- 구현과 테스트 보고서를 로컬 commit까지만 만들고 origin에는 push하지 않은 채
  사용자 검토를 기다린다.
