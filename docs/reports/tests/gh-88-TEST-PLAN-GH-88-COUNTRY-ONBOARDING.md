# Test Report: TEST-PLAN-GH-88-COUNTRY-ONBOARDING

> Created at: `2026-08-08T18:21:17+09:00`
> GitHub Issue: `#88`
> Branch: `feat/gh-88-onboarding-country-required`
> Commit: `2a60fb8`

## 1. Executive summary

- Result: `PASS`
- Tested scope: 국가 정규화·마스터 검증·지역 계층 일치, 저장 전 실패, 계정/자격증명 발급,
  USER/OPERATOR DB 제약, V9 backfill·rollback, 기존 인증 회귀
- Unverified scope: 실제 운영 데이터 migration apply와 배포 후 관측성
- Release recommendation: 승인된 PR 검토와 CI 재실행 후 병합 가능

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine` |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` | PASS | 160 / 0 failures | 5s | Gradle test XML |
| `./gradlew integrationTest` | PASS | 147 / 0 failures | 2m 32s | Gradle integrationTest XML |
| `./harness test-run --id TEST-PLAN-GH-88-COUNTRY-ONBOARDING` | PASS | 위 두 suite 재확인 | 1s up-to-date | 이 보고서 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001~UNIT-004 | PASS | `DeviceRegistrationServiceTest`, `AccountTest`, `AccountJpaMapperTest` | 정규화·마스터·계층 일치와 USER country 저장을 검증 |
| UNIT-005~UNIT-006 | PASS | `DeviceRegistrationTransactionIntegrationTest`, `AccountTest` | 저장 실패 경계와 OPERATOR 국가 NULL 예외를 검증 |
| INT-001~INT-002 | PASS | `DeviceAuthIntegrationTest` | 정상 등록, 누락/불일치 400, account·credential 0건을 검증 |
| INT-003 | PASS | `AccountPersistenceIntegrationTest`, `FlywayMigrationIntegrationTest` | USER NULL·비국가 참조 DB 제약과 catalog를 검증 |
| INT-004 | PASS | `SchemaRevisionMigrationIntegrationTest` | 기존 USER root backfill과 미확정 계층 rollback을 검증 |
| INT-005 | PASS | `DeviceAuthIntegrationTest` | active installation 중복 409와 기존 토큰 흐름을 검증 |

## 5. Failures and diagnostics

초기 실행에서 V9 backfill 재귀 CTE의 PostgreSQL 배열 타입 불일치와 JDBC 계층 조회의
동일한 타입 추론 오류를 재현했다. 두 CTE의 `VARCHAR(100)[]` 타입을 명시한 뒤 핵심 및
전체 통합 테스트를 재실행해 통과했다. 민감한 로그 원문은 첨부하지 않았다.

최종 `harness test-run` 재호출은 기존 보고서를 안전하게 덮어쓰지 않도록 종료되었으며,
동일 시점의 `./gradlew test integrationTest`와 `./harness pr-ready --project-tests`는
통과했다. 보고서는 최종 실행 수치로 수동 갱신했다.

## 6. Potential issues

### Application code

- country validation은 account 저장 전 수행되며 실패 시 credential/token 발급 경로에 진입하지 않는다.

### Infrastructure and resource limits

- ARM 호스트에서 amd64 PostGIS 이미지 에뮬레이션 경고가 있었으나 테스트는 통과했다.

### Database and migrations

- V9는 기존 V1~V8을 수정하지 않고 추가되며, backfill 미확정 USER가 있으면 migration 전체가 실패한다.

### Concurrency and idempotency

- check-then-insert 경합의 최종 방어선은 기존 `uq_active_device_installation`이며, 기존 409 계약을 유지한다.

### Transactions and event ordering

- account 저장·credential 저장·token 발급 흐름은 기존 트랜잭션 경계를 유지한다. credential 저장 실패 전용 통합 장애 주입은 미실행이다.

### External APIs

- 국가 master는 동일 PostgreSQL의 `region_code`를 사용하며 외부 API 호출은 없다.

### Failure recovery and reconciliation

- migration 실패 시 Flyway transactional rollback으로 부분 country backfill을 남기지 않는 구조를 검증했다.

## 7. Regression and residual risk

- 이전 클라이언트의 `countryCode` 누락 요청은 400으로 전환된다. 기존 token 재발급은 regression suite에서 통과했다. 후속 추적은 #88에서 한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-88-TEST-PLAN-GH-88-COUNTRY-ONBOARDING.md`
- CI run: 로컬 Gradle 및 하네스 실행 완료; 원격 CI 미실행
- Related ADR: `docs/adr/0007-require-country-before-user-account-creation.md`
- PR: 아직 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨
- [x] 실행 결과와 PR 설명이 일치함
