# Test Report: ACCOUNT-PERSISTENCE

> Created at: `2026-08-03T18:10:34+09:00`
> GitHub Issue: `#37`
> Branch: `feat/gh-37-account-persistence`
> Commit: pre-implementation base `d4671f4`; this report is included in the local implementation commit

## 1. Executive summary

- Result: `PASS`
- Tested scope: JPA 안전 설정, Account domain/mapper/adapter, 저장·조회·순차 갱신,
  enum·`Instant` mapping, FK/CHECK 제약과 transaction rollback
- Unverified scope: Account API·인증, 닉네임 고유성, production RDS, 동시 detached
  writer의 lost update
- Release recommendation: Issue #37 범위의 로컬 검토 가능. `@Version` 또는 조건부
  update 정책 없이 동시 수정 안전성을 주장해서는 안 됨.

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
| `./gradlew test --rerun-tasks` | PASS | 11 | Gradle 1s; test methods 0.054s | JUnit XML: failures 0, errors 0 |
| `./gradlew integrationTest --rerun-tasks` | PASS | 12 | Gradle 18s; test methods 0.848s | JUnit XML: failures 0, errors 0 |
| `./harness pr-ready --project-tests` | PASS | repository gates + 23 tests | Gradle 17s | local PR readiness checks passed |
| `./harness check` / `npm run hooks:validate` / `git diff --check` | PASS | policy and diff gates | local | secret, JUnit, convention, workflow, label, Husky, whitespace checks passed |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-UNIT-001 | PASS | `AccountTest` 4 methods | 생성, 입력 길이·공백·timezone, 삭제 상태, 순차 변경 규칙 |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-UNIT-002 | PASS | `AccountJpaMapperTest.mapsAccountRoundTripWithoutValueLoss` | ID, scalar region, nullable nickname, enum, Instant 왕복 |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-UNIT-003 | PASS | `AccountPersistenceBoundaryTest` 3 boundary methods | domain/port 무의존, relation·Version 부재, feature 직접 참조 금지 |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-UNIT-004 | PASS | `AccountPersistenceBoundaryTest.jpaSafetySettingsAreFixed` | validate/generate-ddl/open-in-view/UTC 설정 |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-INT-001 | PASS | `startsWithFlywaySchemaValidationOnly` | V1 성공 1건, application table 26개 유지 |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-INT-002 | PASS | `savesAndFindsAccountThroughDomainPort` | identity ID와 고정 auditing 시각 포함 round-trip |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-INT-003 | PASS | `mapsEnumsAndUpdatesAuditTimestamp` | OPERATOR/ACTIVE/BLOCKED/DELETED 문자열과 created/updated/deleted 시각 |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-INT-004 | PASS | `rejectsForeignKeyAndCheckConstraintViolations` | 없는 region, 공백 nickname, 삭제 시각 불일치 거절 |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-INT-005 | PASS | `rollsBackWholeTransactionAfterConstraintFailure` | valid insert까지 전체 rollback |

## 5. Failures and diagnostics

- 구현 후 첫 단위·통합 실행에서 실패한 시나리오는 없었다.
- 첫 `pr-ready`에서 integration test 파일의 metadata가 validator의 첫 30줄 밖에
  있어 정책 검사가 실패했다. 파일 상단에도 동일한 정확한 timestamp/source ID를
  배치한 뒤 재실행해 JUnit policy와 전체 gate가 통과했다.
- 통합 테스트가 의도적으로 발생시킨 FK/CHECK 예외는 assertion 대상이며 테스트
  종료 후 disposable transaction/container로 정리됐다.
- 민감정보가 포함될 수 있는 원문 로그는 보고서에 기록하지 않았다.

## 6. Potential issues

### Application code

- Account service/API는 아직 없으며 JPA adapter가 반환하는 persistence 예외를 제품
  오류로 번역하는 경계는 후속 application layer에서 결정해야 한다.

### Infrastructure and resource limits

- Testcontainers에서만 검증했다. production RDS의 connection limit, migration role,
  Hibernate startup latency는 미검증이다.

### Database and migrations

- V1과 schema manifest는 변경하지 않았다. Hibernate `validate`는 매핑된
  `user_account` 일치를 검증하고 전체 26-table inventory는 기존 Flyway 회귀 테스트가
  계속 검증한다.
- nickname은 schema에 unique constraint가 없으므로 이번 adapter도 고유성을
  제공하지 않는다.

### Concurrency and idempotency

- schema에 version column이 없어 `@Version`을 넣지 않았다. 두 detached writer가
  같은 Account를 갱신하면 마지막 commit이 앞선 값을 덮을 수 있다.
- identity insert와 순차 update만 검증했으며 중복 요청 멱등성은 API 범위가 아니다.

### Transactions and event ordering

- `saveAndFlush`로 DB 제약을 adapter transaction 안에서 확인하고, 외부 transaction에
  참여한 두 insert 중 하나가 실패하면 전체 rollback됨을 검증했다.
- Account 변경에서는 아직 domain event나 Outbox를 발행하지 않는다.

### External APIs

- 외부 API 호출은 없다. Docker image 실행만 사용했고 운영 자격 증명은 사용하지
  않았다.

### Failure recovery and reconciliation

- FK/CHECK 실패 뒤 부분 Account가 남지 않는 것을 새 transaction에서 확인했다.
- constraint 오류 자동 재시도나 Flyway repair/clean은 수행하지 않는다.

## 7. Regression and residual risk

- 기존 Flyway 및 local/test profile 통합 테스트 7개와 기존 단위 테스트 2개를 포함해
  unit 11개, integration 12개가 모두 통과했다.
- 동시 수정 lost update와 production 환경은 미검증이며 상위 Issue #34의 후속
  persistence 결정에서 다뤄야 한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-37-ACCOUNT-PERSISTENCE.md`
- Account port/adapter: `src/main/java/com/dnd/qello/account/**`
- Related ADR: `docs/adr/0001-database-schema-ownership.md`, `docs/adr/0002-jpa-jdbc-boundary.md`
- CI run: 없음 — 로컬 검토 단계
- PR: 없음 — origin push 금지 상태

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잔여 persistence 결정을 상위 GitHub Issue #34에 연결함
- [x] PR이 없음을 명시하고 로컬 실행 결과와 일치시킴
