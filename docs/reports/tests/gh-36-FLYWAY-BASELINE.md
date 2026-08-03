# Test Report: FLYWAY-BASELINE

> Created at: `2026-08-03T17:46:17+09:00`
> GitHub Issue: `#36`
> Branch: `chore/gh-36-flyway-baseline`
> Commit: pre-implementation base `007ed4b`; this report is included in the local implementation commit

## 1. Executive summary

- Result: `PASS`
- Tested scope: V1 checksum와 안전 설정, startup migration, 재실행, manifest catalog,
  8방향 seed, 실패 migration rollback 및 이력
- Unverified scope: production RDS 권한, 다중 애플리케이션 동시 startup,
  기존 DB baseline/repair
- Release recommendation: Issue #36 범위의 로컬 검토 가능. 운영 적용은 금지하며
  후속 인프라 preflight와 사람 승인이 필요함.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 21.0.12 |
| Spring Boot | 3.5.16 |
| Flyway | 11.7.2 |
| Database | disposable PostgreSQL 16 / PostGIS 3.5 Testcontainer |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --rerun-tasks` | PASS | 2 | suite 0.030s | JUnit XML: failures 0, errors 0 |
| `./gradlew integrationTest --rerun-tasks` | PASS | 7 | Gradle 12s; test methods 0.905s | JUnit XML: failures 0, errors 0 |
| `./harness pr-ready --project-tests` | PASS | repository gates + project tests | Gradle 945ms | local PR readiness checks passed |
| `./harness check` / `npm run hooks:validate` / `git diff --check` | PASS | policy and diff gates | local | secret, JUnit, convention, workflow, label, Husky, whitespace checks passed |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-36-FLYWAY-BASELINE-UNIT-001 | PASS | `FlywayMigrationContractTest.v1MatchesAcceptedDdlAndExcludesLegacyMigrations` | accepted DDL SHA-256 일치, V1 단일 SQL, script transaction 설정 검증 |
| TEST-PLAN-GH-36-FLYWAY-BASELINE-UNIT-002 | PASS | `FlywayMigrationContractTest.flywaySafetySettingsAreEnabled` | clean/baseline 금지, naming validation 활성화 |
| TEST-PLAN-GH-36-FLYWAY-BASELINE-INT-001 | PASS | `FlywayMigrationIntegrationTest.appliesV1OnApplicationStartup` | V1 성공 이력 1건과 PostGIS 함수 확인 |
| TEST-PLAN-GH-36-FLYWAY-BASELINE-INT-002 | PASS | `FlywayMigrationIntegrationTest.secondMigrateIsIdempotent` | 두 번째 migrate 적용 0건 |
| TEST-PLAN-GH-36-FLYWAY-BASELINE-INT-003 | PASS | `FlywayMigrationIntegrationTest.catalogMatchesApprovedManifest` | 26 tables, 45 FK, 18 unique, 95 checks, 47 explicit indexes, 10 functions, 9 triggers, 8 segments |
| TEST-PLAN-GH-36-FLYWAY-BASELINE-INT-004 | PASS | `FlywayMigrationIntegrationTest.failedMigrationIsNotRecordedAsSuccessful` | V2 변경 rollback, success 이력 0건 |

## 5. Failures and diagnostics

- 최초 통합 실행에서 PostgreSQL 예약어 `constraint`를 catalog query alias로
  사용해 SQL 문법 오류가 발생했다. alias를 `pc`로 교정했다.
- 같은 test profile의 Spring context cache가 class-scoped container의 이전 포트를
  재사용해 연결 실패가 발생했다. Flyway 검증 class에 전용 profile key를 추가해
  context와 container lifecycle을 격리했다.
- 두 문제를 수정한 뒤 통합 7개를 모두 재실행해 통과했다. 민감정보가 포함된
  원문 로그는 보고서에 기록하지 않았다.

## 6. Potential issues

### Application code

- 이 Issue는 persistence bootstrap만 다루므로 JPA Entity/Repository와 API 동작은
  검증하지 않았다. 후속 Issue #37 이후 별도 검증이 필요하다.

### Infrastructure and resource limits

- Testcontainers 환경만 검증했다. RDS의 PostGIS extension 권한과 migration role의
  DDL 권한은 운영 전 인프라 preflight가 필요하다.

### Database and migrations

- V1은 승인된 독립 DDL의 `BEGIN/COMMIT`을 그대로 보존한다. 따라서 인접한
  `.sql.conf`의 `executeInTransaction=false`가 삭제되면 중복 transaction 관리 위험이
  생기며, unit contract가 이를 차단한다.
- 적용된 V1은 checksum 보호 대상이며 수정하지 않고 새 versioned migration으로
  forward-fix해야 한다.

### Concurrency and idempotency

- 동일 DB에서 순차 재실행은 0건 적용을 확인했다. 여러 애플리케이션 인스턴스의
  동시 startup lock 경합은 P1 잔여 위험이다.

### Transactions and event ordering

- test-only V2의 첫 DDL과 오류가 같은 PostgreSQL transaction에서 rollback되는 것을
  확인했다. 애플리케이션 이벤트·Outbox 순서는 이 Issue 범위가 아니다.

### External APIs

- 외부 API 호출은 없다. Docker image 실행만 사용했으며 운영 자격 증명은 사용하지
  않았다.

### Failure recovery and reconciliation

- 실패 migration에 `repair`나 `clean`을 호출하지 않았다. production 실패 시 환경을
  복구하거나 새 migration으로 forward-fix하는 운영 절차가 필요하다.

## 7. Regression and residual risk

- 기존 test/local profile 통합 테스트 3개도 함께 통과했다.
- production 권한과 동시 startup은 미검증이며 이번 로컬 PASS가 운영 적용 승인을
  의미하지 않는다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-36-FLYWAY-BASELINE.md`
- Migration: `src/main/resources/db/migration/V1__create_direction_communication_schema.sql`
- Related ADR: `docs/adr/ADR-0001-flyway-schema-ownership.md`, `docs/adr/ADR-0002-jpa-mapping-strategy.md`
- CI run: 없음 — 로컬 검토 단계
- PR: 없음 — origin push 금지 상태

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 후속 Entity/JPA 검증 범위를 GitHub Issue #37에 연결함
- [x] PR이 없음을 명시하고 로컬 실행 결과와 일치시킴
