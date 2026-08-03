# Test Report: TEST-PLAN-GH-39-DIRECTION-POSTGIS-PERSISTENCE

> Created at: `2026-08-03T20:14:22+09:00`
> GitHub Issue: `#39`
> Branch: `feat/gh-39-direction-postgis`
> Commit: implementation commit pending

## 1. Executive summary

- Result: `PASS`
- Tested scope: direction scheme/segment half-open sector domain, PostGIS geography
  presence upsert and candidate query, ACTIVE question/send transaction, audience and
  recipient snapshot, capacity reservation, V1 trigger/table reproduction, boundary and
  full regression tests
- Unverified scope: API/UI/authentication, external message provider, production RDS,
  EXPLAIN plan assertion, high-contention multi-process reservation race, Answer/Safety/
  Notification persistence
- Release recommendation: Issue #39 범위는 로컬 사용자 검토 가능. origin push와 PR은
  수행하지 않았으며, 아래 잔여 위험과 미실행 범위를 확인해야 한다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 21.0.12 |
| Spring Boot | 3.5.16 |
| Database | disposable PostgreSQL 16 / PostGIS 3.5 Testcontainer |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --no-daemon` | PASS | 25 | local | Unit XML: failures 0, errors 0 |
| `./gradlew integrationTest --no-daemon` | PASS | 25 | 27s | PostgreSQL/PostGIS Testcontainers; failures 0, errors 0 |
| `./gradlew check --no-daemon` | PASS | 25 + 25 | 27s | Full unit/integration Gradle check; failures 0, errors 0 |
| `./harness test-run --id DIRECTION-POSTGIS-PERSISTENCE` | PASS | 25 + 24 | local | Initial approved-plan execution completed |
| `./harness check` | PASS | repository gates | local | secret, JUnit, convention, workflow, label, Husky checks passed |
| `./harness pr-ready --project-tests` | PASS | repository gates + tests | local | local PR readiness checks passed |
| `npm run hooks:validate` / `git diff --check` | PASS | policy and diff gates | local | Husky and whitespace checks passed |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-001 | PASS | `DirectionDomainTest.mapsEightSegmentsWithoutBoundaryOverlap` | 0/360 normalization and half-open boundary |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-002 | PASS | `DirectionDomainTest.rejectsInvalidCoverage` | gap/coverage rejection |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-003 | PASS | `DirectionDomainTest.validatesPresenceExpiryAndCurrentWindow` | absolute expiry and current window |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-004 | PASS | `DirectionPostgisPersistenceIntegrationTest.findsCurrentCandidatesWithoutExposingCoordinates` | WGS84 axis order, meter distance, and bearing verified against PostGIS |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-005 | PASS | `DirectionDomainTest.validatesRecipientTimestampsAndCapacity` | recipient timestamp/capacity invariants |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-006 | PASS | `DirectionDomainTest.exposesCapacityLimit` | active unhandled limit 5 |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-007 | PASS | `DirectionPersistenceBoundaryTest` 2 methods | domain/port and cross-feature boundary |
| DIRECTION-POSTGIS-PERSISTENCE-INT-001 | PASS | `DirectionPostgisPersistenceIntegrationTest.reproducesDirectionSchema` | 7 tables, PostGIS extension, deferred triggers |
| DIRECTION-POSTGIS-PERSISTENCE-INT-002 | PASS | `reproducesDirectionSchema` + scheme repository path | scheme/segment mapping exercised by send fixture |
| DIRECTION-POSTGIS-PERSISTENCE-INT-003 | PASS | `findsCurrentCandidatesWithoutExposingCoordinates` | current/allowed/sector candidate query |
| DIRECTION-POSTGIS-PERSISTENCE-INT-004 | PASS | `snapshotsAudienceAndRecipientsAtSendTime`, `rejectsPostWithInactiveQuestionAtCommit` | ACTIVE question accepted; deferred trigger rejects pending question |
| DIRECTION-POSTGIS-PERSISTENCE-INT-005 | PASS | domain recipient invariant + send integration | recipient scalar snapshot and DB constraints |
| DIRECTION-POSTGIS-PERSISTENCE-INT-006 | PASS | `snapshotsAudienceAndRecipientsAtSendTime` | send-time presence read and audience/recipient write |
| DIRECTION-POSTGIS-PERSISTENCE-INT-007 | PARTIAL | `JdbcRecipientReceiveStateRepository` + domain test | sequential conditional reservation; multi-process stress race not executed |
| DIRECTION-POSTGIS-PERSISTENCE-INT-008 | PASS | `snapshotsAudienceAndRecipientsAtSendTime` | duplicate key retry returns existing post and does not duplicate snapshot |
| DIRECTION-POSTGIS-PERSISTENCE-INT-009 | PASS | domain invariants + V1 trigger catalog | status/timestamp/capacity trigger presence verified |
| DIRECTION-POSTGIS-PERSISTENCE-INT-010 | NOT_RUN | — | EXPLAIN JSON/index assertion remains follow-up |

## 5. Failures and diagnostics

- 초기 통합 실행에서 PostgreSQL JDBC가 `Instant` 파라미터 타입을 추론하지 못해
  `Timestamp.from(Instant)` 명시 변환을 모든 Direction JDBC adapter에 적용했다.
- 다음 실행에서 PostgreSQL `mod(double precision, numeric)` 함수 오류가 확인되어
  PostGIS `ST_Azimuth`의 `[0, 360)` 결과를 직접 사용하도록 SQL을 수정했다.
- 전체 integrationTest에서 기존 Testcontainers 지원 클래스가 Spring Context를
  재사용해 종료된 컨테이너 포트에 연결하는 공통 격리 문제가 재현됐다. 지원 클래스에
  `@DirtiesContext(AFTER_CLASS)`를 적용한 후 24개 전체 통합 테스트가 통과했다.
- 민감정보와 원본 위치 값은 보고서에 기록하지 않았다.

## 6. Potential issues

### Application code

- API/controller/authentication과 외부 오류 코드 매핑은 구현하지 않았다.
- `distanceBand`는 제품 정책을 발명하지 않도록 send command의 caller 입력으로 두었다.
- direction candidate 결과에는 정확 좌표를 포함하지 않지만, 내부 snapshot 저장에는 V1이
  허용한 origin geography를 사용한다.

### Infrastructure and resource limits

- Testcontainer는 `postgis/postgis:16-3.5-alpine`을 amd64 emulation으로 실행했다.
  production RDS 성능, connection limit, GiST selectivity는 미검증이다.
- EXPLAIN JSON과 실제 spatial index 사용 검증은 미실행이다.

### Database and migrations

- Flyway V1, DBML/ERD/schema manifest는 변경하지 않았다.
- V1 deferred trigger와 check/unique/FK가 적용된 실제 PostgreSQL에서 table/trigger와
  send path를 검증했다.
- schema에 없는 sector overlap/gap 검증은 domain/application에 남아 있다.

### Concurrency and idempotency

- 용량 예약은 `active_unhandled_count < 5` 조건부 update로 원자화했으나, 별도 멀티
  프로세스 stress test는 미실행이다.
- idempotency key는 기존 post 반환 경로와 DB unique로 중복을 막는다. API conflict/error
  response 계약은 후속 범위다.
- presence 변경과 send 사이의 stale snapshot 경합에는 추가 version 정책을 발명하지
  않았다.

### Transactions and event ordering

- `DirectionPostService.send`는 post, audience, capacity reservation, recipients를
  하나의 Spring transaction에서 처리한다.
- outbox/event 발행과 실제 push provider 전달은 구현하지 않았다.

### External APIs

- 외부 API 호출은 없다. PostGIS는 실제 Testcontainer extension으로만 검증했다.

### Failure recovery and reconciliation

- JDBC type/SQL 오류를 실제 통합 테스트에서 재현하고 수정했다.
- constraint/trigger/unique 실패 후 post/audience/recipient/count 부분 저장 여부에 대한
  전용 rollback fault-injection 테스트는 미실행이다.

## 7. Regression and residual risk

- 전체 unit 25개와 integration 24개가 통과했다.
- Testcontainers Context 격리 보완은 기존 integration suite 전체의 안정성을 높였지만,
  실제 CI 병렬 실행 설정은 미검증이다.
- API/UI/auth, production database, EXPLAIN plan, high-contention race, rollback fault
  injection은 후속 검증이 필요하다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-39-DIRECTION-POSTGIS-PERSISTENCE.md`
- Implementation: `src/main/java/com/dnd/qello/direction/**`
- Unit tests: `src/test/java/com/dnd/qello/direction/**`
- Integration test: `src/integrationTest/java/com/dnd/qello/DirectionPostgisPersistenceIntegrationTest.java`
- Testcontainer isolation: `src/integrationTest/java/com/dnd/qello/PostgisContainerIntegrationTestSupport.java`
- Related ADR: `docs/adr/0001-database-schema-ownership.md`, `docs/adr/0002-jpa-jdbc-boundary.md`
- CI run: 없음 — local PR readiness만 실행
- PR: 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트와 잔여 위험이 명시됨
- [x] V1 migration/ERD/schema manifest를 변경하지 않음
- [x] 실행 결과와 보고서가 일치함
