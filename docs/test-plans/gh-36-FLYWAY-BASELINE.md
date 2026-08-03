# Test Plan: TEST-PLAN-GH-36-FLYWAY-BASELINE

> Created at: `2026-08-03T17:40:39+09:00`
> GitHub Issue: `#36`
> Status: Draft — human approval required before implementation

## 1. Objective

승인된 26-table schema가 Flyway의 단일 V1 migration으로 빈
PostgreSQL/PostGIS에 재현되고, 재실행·검증·실패 복구 시에도 잘못된 성공
상태를 남기지 않는다는 증거를 만든다.

## 2. Scope

### Included

- Flyway migration 파일의 승인 DDL checksum과 폐기 계보 미포함 검증
- Spring Boot startup에 의한 빈 DB migration
- 같은 DB에서 두 번째 migrate의 idempotency와 schema history 검증
- PostGIS extension 및 manifest 오브젝트 수 검증
- PostgreSQL의 transactional DDL 실패 시 성공 이력 미기록 검증
- migration 테스트 결과와 잠재 문제 보고서

### Excluded

- JPA Entity/Repository 및 Hibernate schema validation
- 기존 또는 운영 DB baseline, repair, clean
- AWS/RDS extension 활성화와 migration-role 권한 적용
- `region_code` 운영 seed와 미승인 P04/P07 정책 값
- 성능 부하 시험과 여러 애플리케이션 인스턴스의 동시 startup 시험

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #36 | startup migration, 빈 DB 재현, 두 번째 실행, manifest 일치, 실패 이력 안전성 |
| ADR-0001 | Flyway만 schema를 변경하고 V1은 빈 DB 전용이며 적용 migration은 수정하지 않음 |
| ADR-0002 | 이 Issue에서는 JPA를 도입하지 않고 DB 특화 schema를 SQL로 검증 |
| schema manifest | 26 tables, 45 FK, 18 unique constraints, 7 unique indexes, 95 checks, 40 non-unique indexes, 10 functions, 9 triggers, PostGIS 1 |
| accepted standalone DDL | SHA-256 `cc93ba87aa5999bdd48589b63fa4da4e383270626fb36ecb7adac482ed3d95a7` |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| V1이 승인 DDL과 달라짐 | 잘못된 production schema의 시작점 고정 | Medium | P0 | migration resource SHA-256 일치 |
| startup 이전/이후 순서 오류 | 애플리케이션이 없는 table에 접근 | Medium | P0 | Spring context와 Flyway history 동시 검증 |
| PostGIS 또는 DB 전용 DDL 실패 | 위치 기반 기능 전체 차단 | Medium | P0 | 실제 PostGIS 컨테이너 migrate 성공 |
| constraint/index/trigger 누락 | 무결성·동시성·성능 계약 훼손 | Medium | P0 | catalog object count와 이름 비교 |
| 재실행 시 중복 DDL | 재배포 실패 | Medium | P0 | 두 번째 `migrate()` 결과 0 migrations |
| 실패 migration을 성공으로 오인 | 부분 schema를 정상으로 운영 | Low | P0 | 의도적 V2 실패와 history 성공값 검사 |
| 여러 인스턴스 동시 migrate | startup lock 대기 또는 경합 | Low | P1 | 후속 병렬 startup 시험 또는 운영 관측 |
| production extension 권한 부족 | 배포 시 migration 실패 | Medium | P1 | 인프라 preflight 별도 Issue |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-36-FLYWAY-BASELINE-UNIT-001 | V1 resource와 accepted DDL checksum이 주어짐 | SHA-256을 계산함 | checksum이 manifest 값과 같고 `001~004` migration resource가 없음 | P0 | Test executor |
| TEST-PLAN-GH-36-FLYWAY-BASELINE-UNIT-002 | application Flyway 설정이 주어짐 | 설정을 로드함 | clean/baseline은 비활성화되고 migration naming validation이 활성화됨 | P0 | Test executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-36-FLYWAY-BASELINE-INT-001 | Spring Boot, Flyway, PostGIS | 빈 Testcontainers DB와 test profile | context startup | V1 한 건이 성공하고 PostGIS 함수를 호출할 수 있음 | container 종료 |
| TEST-PLAN-GH-36-FLYWAY-BASELINE-INT-002 | Flyway API, schema history | INT-001과 같은 DB | `migrate()`를 다시 호출 | executed migration 0, V1 success row 1개 | container 종료 |
| TEST-PLAN-GH-36-FLYWAY-BASELINE-INT-003 | PostgreSQL catalogs | V1 적용 DB | table/constraint/index/function/trigger 조회 | manifest count와 필수 이름이 모두 일치 | read-only query |
| TEST-PLAN-GH-36-FLYWAY-BASELINE-INT-004 | Flyway isolated schema, invalid V2 fixture | 별도 failure schema와 test-only migration location | V1 성공 후 V2 문법 오류 migrate | migrate 실패, V2가 success=true로 기록되지 않음 | test container 폐기 |

## 7. Cross-cutting scenarios

### Database and transactions

- PostgreSQL 16/PostGIS 3.5 컨테이너에서 실제 DDL을 실행한다.
- V1의 transactional DDL과 constraint trigger 생성 결과를 catalog에서 확인한다.
- test가 production schema를 clean/repair하지 않도록 별도 disposable DB를 사용한다.

### Concurrency and idempotency

- P0는 순차 두 번째 migrate가 0건임을 검증한다.
- 다중 인스턴스 동시 migrate는 P1 잔여 위험으로 보고하고 이번 Issue에서
  flaky 병렬 test를 만들지 않는다.

### External APIs

- 외부 API 호출은 없다. Docker/Testcontainers image 실행만 필요하다.
- 실제 자격 증명, 운영 주소, `.env` 값은 사용하지 않는다.

### Failure recovery and reconciliation

- test-only V2 오류로 Flyway 예외와 history 상태를 확인한다.
- `repair`로 실패를 숨기지 않고 disposable schema 폐기로 정리한다.
- production 실패 시 forward-fix 또는 환경 복구가 필요하며 자동 repair는 금지한다.

## 8. Test data and isolation

- Fixtures: V1 production migration, test-only valid V1 + invalid V2
- Database isolation: class-scoped disposable PostGIS container, failure test 전용 schema
- Clock/randomness: catalog count와 checksum만 검증하므로 고정 clock 불필요
- External API doubles: 없음
- Cleanup: Testcontainers가 DB 전체를 폐기하며 Flyway clean은 호출하지 않음

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Migration executor | `build.gradle`, `src/main/resources/application.properties`, `src/main/resources/db/migration/V1__*.sql` | implementation prerequisite | `compileJava`, startup smoke |
| 2 | Test executor | `src/test/**`, `src/integrationTest/**`, `docs/reports/tests/gh-36-*.local.md` | UNIT-001~002, INT-001~004 | `test`, `integrationTest`, report |
| 3 | Orchestrator | `TASK.md`, plan/report review only | all | Harness, Hook, diff checks |

각 executor는 다른 executor의 소유 파일을 수정하지 않으며 순서대로 작업한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성
- [ ] JDK 21로 `./harness pr-ready --project-tests` 통과

## 11. Human approval

- Reviewer: pending
- Decision: pending
- Approved at: pending
