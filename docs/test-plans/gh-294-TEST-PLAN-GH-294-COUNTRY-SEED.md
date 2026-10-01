# Test Plan: TEST-PLAN-GH-294-COUNTRY-SEED

> Created at: `2026-10-01T09:49:59+09:00`
> GitHub Issue: `#294`
> Status: Approved

## 1. Objective

`V29__seed_country_region_code.sql`이 `region_code`에 ISO 3166-1 alpha-2 국가 249개를 COUNTRY 행으로 적재하는지
검증한다. 이 시드가 틀리면 `JdbcCountryCatalogRepository.existsCountry`가 유효한 국가를 거부하거나 존재하지 않는
코드를 허용하고, 기기 등록(`DeviceRegistrationService`)과 `user_account.country_code` FK 검증이 잘못된다.
기존 데이터가 있는 DB에 적용될 때 마이그레이션이 실패하거나 기존 행을 덮어쓰면 배포가 중단되거나 데이터가 바뀐다.

## 2. Scope

### Included

- V29 적재 결과(행 수, 코드 형식, level, parent_code, 대표 display_name)
- V29 재적용·기존 행 존재 시의 멱등성
- `existsCountry`가 시드 데이터에 대해 내리는 판정
- 시드된 국가를 `user_account.country_code`가 참조하는 FK 경로
- V29 추가로 깨지는 기존 테스트 갱신: `FlywayMigrationContractTest` 파일 목록(완료), `FlywayMigrationIntegrationTest`의 적용 건수 단언
- Docker 없이 실행 가능한 시드 파일 정적 검증

### Excluded

- REGION, CITY, DISTRICT 시드와 국가 목록 조회 API
- ISO 3166-1 국가명 번역의 정확성 검수(운영·기획 판단 영역)
- 기존 통합 테스트 전체의 개별 재설계. 회귀는 전체 `integrationTest` 실행으로만 확인한다.
- 성능 측정(행 249개 단일 INSERT)

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #294 | V29가 오류 없이 적용된다 |
| GitHub Issue #294 | 모든 행이 `ck_region_code_root`, `ck_region_code_level`, `code ~ '^[A-Z]{2}$'`를 만족한다 |
| GitHub Issue #294 | `existsCountry("KR")` true, `ZZ` false 통합 테스트 통과 |
| GitHub Issue #294 | V29를 두 번 적용해도 행 수가 변하지 않는다 |
| GitHub Issue #294 | `./harness check`와 `./gradlew check` 통과 |
| schema | `region_code(code PK, parent_code, display_name, level)`, `ck_region_code_root`는 COUNTRY일 때만 `parent_code IS NULL`, `uq_region_code_code_level`(code, level) 유니크 |
| schema | `user_account.country_code`는 `(country_code, country_level)` 복합 FK로 COUNTRY 행만 참조하고 `^[A-Z]{2}$` CHECK가 있다(V9) |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| `FlywayMigrationIntegrationTest`의 `hasSize(28)`이 V29 적용 후 실패한다 | 통합 테스트 스위트 실패 | 확정(코드 확인) | P0 | 단언을 29로 갱신하고 V29 성공 이력 단언 추가 |
| 시드 값이 CHECK 제약을 위반해 마이그레이션이 부팅 시 실패한다 | 애플리케이션 기동 불가 | 낮음 | P0 | 새 DB 전체 migrate 성공 + 249행 단언 |
| 코드 중복·오타·비ISO 코드가 섞인다 | 잘못된 국가 허용·거부 | 중간 | P0 | 정적 검증(UNIT)과 DB 단언 모두 |
| 기존 환경에 `KR` 등이 이미 있을 때 덮어쓰거나 실패한다 | 운영 데이터 변경·배포 실패 | 중간 | P0 | 기존 행 선존재 상태에서 시드 재실행 후 display_name 보존 단언 |
| 기존 통합 테스트가 `KR`을 평문 INSERT하다 중복 키로 실패한다 | 기존 스위트 회귀 | 중간 | P0 | 전체 `integrationTest` 실행. 코드상 `KR` 평문 INSERT 3곳은 선행 `DELETE ... code = 'KR'`가 있어 안전해 보이나 실행으로 확인한다 |
| 기존 통합 테스트가 "시드에 없는 국가"로 `US` 등을 사용한다 | 오류 코드 변경 | 낮음 | P1 | `DeviceAuthIntegrationTest`의 `US` 케이스는 시드 전후 모두 `AUT-VAL-004`를 반환함을 코드로 확인했고 실행으로 재확인 |
| 소문자·공백 입력이 시드에 매칭된다 | 검증 우회 | 낮음 | P2 | `existsCountry("kr")` false |
| 시드가 실수로 REGION 행을 포함한다 | 범위 위반 | 낮음 | P1 | COUNTRY 외 level 시드 0건 단언 |

## 5. Unit scenarios

Docker 없이 `./gradlew test`로 실행한다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-294-COUNTRY-SEED-UNIT-001 | classpath의 V29 SQL | 파일을 읽어 `('CODE', NULL, '이름', 'COUNTRY')` 행을 파싱한다 | 행이 249개이고 code가 모두 `^[A-Z]{2}$`이며 중복이 없고 display_name이 모두 공백이 아니다 | P0 | unit-executor |
| TEST-PLAN-GH-294-COUNTRY-SEED-UNIT-002 | 같은 파싱 결과 | level과 parent_code를 검사한다 | 모든 행이 level `COUNTRY`, parent `NULL`이다 | P0 | unit-executor |
| TEST-PLAN-GH-294-COUNTRY-SEED-UNIT-003 | 같은 파일 본문 | 문장 끝을 검사한다 | `ON CONFLICT (code) DO NOTHING`을 포함하고 `DELETE`, `UPDATE`, `TRUNCATE`가 없다 | P0 | unit-executor |
| TEST-PLAN-GH-294-COUNTRY-SEED-UNIT-004 | 같은 파싱 결과 | 대표 코드를 조회한다 | `KR`, `US`, `JP`가 존재하고 `ZZ`는 존재하지 않는다 | P1 | unit-executor |
| TEST-PLAN-GH-294-COUNTRY-SEED-UNIT-005 | 마이그레이션 디렉터리 | 목록을 `FlywayMigrationContractTest`와 비교한다 | `V29__seed_country_region_code.sql`이 목록에 있다. 구현 단계에서 이미 반영했고 이 환경의 CRLF 때문에 같은 메서드의 SHA 단언이 선행 실패하므로 목록 일치는 별도 비교로 확인했다 | P0 | 구현 에이전트(완료) |

## 6. Integration scenarios

Docker와 Testcontainers(PostGIS)가 필요하다. 클래스당 새 컨테이너에서 전체 마이그레이션이 적용된다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-001 | Flyway, `region_code` | 빈 컨테이너에서 애플리케이션 컨텍스트가 V1~V29를 적용한다 | `region_code`를 level별로 센다 | COUNTRY 249건, 다른 level 0건, 모든 COUNTRY의 parent_code NULL, code가 `^[A-Z]{2}$`, `KR`의 display_name이 `대한민국` | 없음(읽기 전용) |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-002 | `JdbcCountryCatalogRepository` | INT-001과 같은 컨텍스트 | `existsCountry`를 `KR`, `US`, `ZZ`, `kr`, 빈 문자열로 호출한다 | `KR`, `US` true. `ZZ`, `kr`, 빈 문자열 false | 없음 |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-003 | Flyway 스크립트, `region_code` | `KR`의 display_name을 `Korea`로 바꾸고 행 수를 기록한다 | V29 SQL 본문을 두 번 다시 실행한다 | 행 수가 249로 유지되고 `KR`의 display_name은 `Korea`로 남는다(DO NOTHING) | `KR`의 display_name을 원래 값으로 복구한다 |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-004 | `region_code`, `user_account` FK | 테스트 전용 REGION 코드를 `JP` 아래에 삽입한다 | USER를 `country_code='JP'`로 삽입하고, `country_code='ZZ'` 삽입을 시도한다 | `JP`는 성공하고 `ZZ`는 `DataIntegrityViolationException`으로 거부된다 | 테스트 USER와 테스트 REGION 삭제 |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-005 | Flyway(target 28), V29 | 기존 데이터가 있는 상태를 재현한다. 새 스키마에서 V28까지 적용하고 `KR`을 `Korea`로, 테스트 REGION을 삽입한다 | V29를 적용한다 | 성공하고 `KR`은 `Korea`로 남으며 COUNTRY가 249건이고 테스트 REGION이 유지된다 | 전용 스키마 또는 컨테이너 폐기. `FlywayMigrationIntegrationTest`의 별도 Flyway 구성 방식을 따르며 구현이 어려우면 사유와 함께 BLOCKED로 보고하고 INT-003으로 갈음하는 결정을 사람에게 올린다 |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-006 | `FlywayMigrationIntegrationTest`(기존) | 전체 migrate 후 | `flyway.info().applied()` 개수와 `flyway_schema_history`에서 V29 성공을 단언한다 | 적용 건수 29, V29 성공 1건. 기존 `hasSize(28)`을 갱신한다 | 없음 |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-007 | 기존 통합 테스트 전체 | V29가 포함된 상태 | `./gradlew integrationTest` 전체를 실행한다 | 이번 변경으로 새로 실패하는 테스트가 없다. 실패가 있으면 이번 변경 이전부터인지 구분해서 보고한다 | 없음 |

## 7. Cross-cutting scenarios

### Database and transactions

- V29는 단일 INSERT 문이라 Flyway 기본 트랜잭션 안에서 전부 적용되거나 전부 롤백된다. 부분 적재 상태는 없다.
- `ON CONFLICT (code) DO NOTHING`은 PK 충돌만 흡수한다. 같은 code가 다른 level로 이미 있으면 PK로 충돌해 건너뛰므로 COUNTRY가 아닌 행이 남을 수 있다. 현재 데이터에서 발생하는지는 INT-005 범위 밖이며 운영 DB 사전 점검 항목으로 PR 위험에 기록한다.

### Concurrency and idempotency

- 인스턴스 여러 개가 동시에 기동해도 Flyway 잠금으로 V29는 한 번만 실행된다. 별도 동시성 테스트는 두지 않는다(Flyway 책임).
- 멱등성은 INT-003, INT-005로 검증한다.

### External APIs

- 외부 API 호출 없음.

### Failure recovery and reconciliation

- 롤백은 신규 마이그레이션 `V30`에서 시드 행을 삭제하는 방식이며 기존 이력은 수정하지 않는다. 이번 이슈의 범위 밖이므로 계획만 PR에 기록한다.
- 시드 이후 `user_account`가 참조하는 COUNTRY 행은 FK `ON DELETE RESTRICT`로 보호된다.

## 8. Test data and isolation

- Fixtures: 시드 자체가 대상이다. 보조 데이터는 INT-003~005의 테스트 전용 REGION 코드와 USER 한 건이다. 코드는 기존 테스트와 겹치지 않는 접두어를 쓴다.
- Database isolation: `PostgisContainerIntegrationTestSupport`가 클래스마다 컨테이너를 새로 만든다. 같은 클래스 안에서는 변경한 행을 `finally` 또는 `@AfterEach`로 복구한다.
- Clock/randomness: 사용하지 않는다.
- External API doubles: 없음.
- Cleanup: 위 시나리오별 Cleanup 열을 따른다. 기존 테스트가 `KR`을 삭제하고 다시 넣는 패턴은 건드리지 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | unit-executor | `src/test/java/com/dnd/qello/CountrySeedMigrationContractTest.java`(신규) | UNIT-001 ~ UNIT-004 | `JAVA_HOME`을 JDK 21로 지정하고 `./gradlew test --tests com.dnd.qello.CountrySeedMigrationContractTest` |
| 2 | integration-executor | `src/integrationTest/java/com/dnd/qello/CountrySeedMigrationIntegrationTest.java`(신규) | INT-001 ~ INT-005 | `./gradlew integrationTest --tests com.dnd.qello.CountrySeedMigrationIntegrationTest` (Docker 필요) |
| 3 | integration-executor | `src/integrationTest/java/com/dnd/qello/FlywayMigrationIntegrationTest.java`(기존, 적용 건수 단언과 V29 성공 단언만 수정) | INT-006 | `./gradlew integrationTest --tests com.dnd.qello.FlywayMigrationIntegrationTest` |
| 4 | integration-executor | 수정 없음(실행만) | INT-007 | `./gradlew integrationTest` 전체와 `./gradlew check` |
| 5 | 구현 에이전트 | `src/test/java/com/dnd/qello/FlywayMigrationContractTest.java`(완료) | UNIT-005 | 파일 목록 비교 |

시나리오와 소유 파일은 겹치지 않는다. 이 환경에는 Docker가 없어 순서 2~4는 실행할 수 없으며, Docker가 있는 환경에서 실행하기 전까지 `BLOCKED`로 보고한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

이 환경의 실행 한계로 통합 테스트와 `./gradlew check` 결과가 없으면 보고서에 `BLOCKED`로 기록하고 PR에 미실행 검증으로 남긴다.
`FlywayMigrationContractTest`의 V1·V2·V8 SHA 단언은 Windows CRLF 변환 때문에 이 환경에서 변경 전부터 실패하며 이번 변경과 무관하다.

## 11. Human approval

- Reviewer: 사용자(대화에서 직접 승인)
- Decision: 승인. 승인 시점에 제시한 열린 결정 3건(INT-005 구현 난이도, Docker 부재 시 통합 테스트 처리, ON CONFLICT 범위)에 별도 지시는 없었다.
- Approved at: 2026-10-01T09:55:25+09:00
