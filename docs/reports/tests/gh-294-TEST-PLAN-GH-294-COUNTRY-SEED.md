# Test Report: TEST-PLAN-GH-294-COUNTRY-SEED

> Created at: `2026-10-01T10:08:00+09:00`
> GitHub Issue: `#294`
> Branch: `feat/gh-294-country-code-seed`
> Commit: `uncommitted`

## 1. Executive summary

- Result: `PARTIAL`
- Tested scope: V29 시드 SQL의 정적 계약(UNIT-001~004, 4건 통과), 마이그레이션 파일 목록 일치(UNIT-005, 별도 비교로 확인).
- Unverified scope: INT-001~007 전부. 통합 테스트 코드는 작성하고 컴파일(`compileIntegrationTestJava`)만 확인했으며 실행하지 않았다. 이 환경에 Docker가 없어 Testcontainers를 기동할 수 없다. V29가 실제 PostgreSQL에 적용되는지, `existsCountry` 판정, FK, 멱등성, V28 이후 업그레이드, 기존 통합 테스트 회귀는 검증되지 않았다.
- Release recommendation: Docker가 있는 환경(CI 포함)에서 `./gradlew integrationTest` 전체 통과를 확인하기 전에는 병합하지 않는다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | JDK 21 (Gradle 실행용). 기본 `java`는 1.8이라 Gradle이 실패한다 |
| Spring Boot | 3.5.16 |
| Database | 통합 테스트용 PostGIS 16 test-container. 이 환경에서는 기동하지 못했다 |
| Test runner | JUnit 5 |
| OS | Windows 11. `core.autocrlf=true`로 작업 트리가 CRLF다 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests com.dnd.qello.CountrySeedMigrationContractTest` | PASS | 4 | 4s | `BUILD SUCCESSFUL`, failures=0 |
| `./gradlew test`(전체 단위, 변경 포함) | FAIL(환경 요인으로 판단) | 1080, 실패 14 | 1m16s | 아래 5절 |
| `./gradlew test`(전체 단위, 변경을 stash한 기준선) | FAIL | 1076, 실패 29 | 45s | 같은 환경 |
| `./gradlew compileIntegrationTestJava` | PASS | 해당 없음 | 10s | 신규·수정 통합 테스트가 컴파일된다 |
| `./gradlew integrationTest` | 미실행 | 0 | 해당 없음 | Docker 없음 |
| `./harness test-run --id TEST-PLAN-GH-294-COUNTRY-SEED` | FAIL | 해당 없음 | 해당 없음 | 하네스가 전체 단위 테스트를 먼저 실행하며 위 환경 요인 실패로 중단되어 보고서를 생성하지 않았다. 이 보고서는 템플릿에 따라 직접 작성했다 |
| `./harness pr-ready --project-tests`, `./harness check`, `npm run hooks:validate` | 미실행 | 0 | 해당 없음 | 전체 테스트가 환경 요인으로 실패해 통과를 보장할 수 없어 실행하지 않았다 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-294-COUNTRY-SEED-UNIT-001 | PASS | `CountrySeedMigrationContractTest.seedHasUniqueWellFormedCountries` | 249행, 코드 형식, 중복 없음, 이름 채워짐, 파싱 누락 행 없음 |
| TEST-PLAN-GH-294-COUNTRY-SEED-UNIT-002 | PASS | `CountrySeedMigrationContractTest.seedRowsAreRootCountries` | level COUNTRY, parent NULL |
| TEST-PLAN-GH-294-COUNTRY-SEED-UNIT-003 | PASS | `CountrySeedMigrationContractTest.seedIsIdempotentAndNonDestructive` | `ON CONFLICT (code) DO NOTHING` 포함, DELETE·UPDATE·TRUNCATE·DROP 없음 |
| TEST-PLAN-GH-294-COUNTRY-SEED-UNIT-004 | PASS | `CountrySeedMigrationContractTest.seedContainsRepresentativeCodes` | KR, US, JP 포함, ZZ 제외 |
| TEST-PLAN-GH-294-COUNTRY-SEED-UNIT-005 | 부분 확인 | `FlywayMigrationContractTest.migrationsMatchAcceptedContent` | 같은 메서드의 V1 SHA 단언이 CRLF 때문에 먼저 실패해 목록 단언에 도달하지 못했다. 디렉터리 정렬 결과와 테스트 목록을 `diff`로 비교해 일치를 확인했다 |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-001 | 미실행 | `CountrySeedMigrationIntegrationTest.seedsAllCountriesAsRootRows` | Docker 없음 |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-002 | 미실행 | `CountrySeedMigrationIntegrationTest.existsCountryReflectsSeed` | Docker 없음 |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-003 | 미실행 | `CountrySeedMigrationIntegrationTest.reapplyingSeedKeepsExistingRows` | Docker 없음 |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-004 | 미실행 | `CountrySeedMigrationIntegrationTest.userAccountReferencesSeededCountryOnly` | Docker 없음 |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-005 | 미실행 | `CountrySeedMigrationIntegrationTest.upgradeKeepsPreExistingRows` | Docker 없음. 기존 `SchemaRevisionMigrationIntegrationTest`의 스키마 분리 방식을 따라 구현했다 |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-006 | 미실행 | `FlywayMigrationIntegrationTest`의 전체 migrate 테스트 | 적용 건수 단언을 28에서 29로 바꾸고 V29 성공 단언을 추가했다. 컴파일만 확인했다 |
| TEST-PLAN-GH-294-COUNTRY-SEED-INT-007 | 미실행 | 기존 통합 테스트 전체 | Docker 없음 |

## 5. Failures and diagnostics

전체 단위 테스트(변경 포함)의 실패 14건은 모두 Windows 환경 요인으로 분류된다. 이번 변경 파일과 관련된 실패 메시지는 없다.

| Suite | 실패 유형 |
| --- | --- |
| `FlywayMigrationContractTest` 2건 | V1·V2·V8의 SHA-256 기대값 불일치. 작업 트리가 CRLF로 변환되어 해시가 달라진다 |
| `*PersistenceBoundaryTest`, `AnswerJdbcBoundaryTest`, `DirectionPersistenceBoundaryTest` | 소스 파일 내용 단언이 CRLF(`\r\n`)를 포함한 텍스트와 맞지 않는다 |
| `JavaConventionBaselineTest`, `JavaStaticAnalysisRuleTest` | `./gradlew` 프로세스를 직접 실행하지 못한다(`CreateProcess error=193`) |
| `RepoMapToolTest` | 임시 디렉터리 심볼릭 링크 생성 권한 부족, 하위 프로세스 종료 코드 불일치 |

기준선(변경을 stash한 상태)에서도 같은 종류의 실패가 29건 발생했다. 기준선의 실패 수가 변경 포함 실행보다 많은 이유는 확인하지 못했다. 실행 순서나 환경 요인에 의한 변동으로 추정하지만 검증하지 않았다. 따라서 "변경이 실패를 늘리지 않았다"는 결론은 두 실행의 실패 유형이 모두 위 환경 요인이라는 범위에서만 말할 수 있다. 실패한 테스트 이름 단위의 정밀 비교는 하지 않았다.

## 6. Potential issues

### Application code

- `JdbcCountryCatalogRepository.existsCountry`는 `region_code`만 조회하므로 시드 변경이 곧 지원 국가 변경이다. 국가를 제한해야 하는 정책이 생기면 시드가 아니라 별도 설정이 필요하다.
- `DeviceAuthIntegrationTest`의 `US` 불일치 케이스는 `US`가 시드된 뒤에도 같은 `AUT-VAL-004`를 반환한다는 것을 코드로 확인했다. 실행으로는 확인하지 못했다.

### Infrastructure and resource limits

- 단일 INSERT 249행이라 부하 영향은 무시할 수 있다고 판단했으나 측정하지 않았다.

### Database and migrations

- `ON CONFLICT (code) DO NOTHING`은 같은 code가 COUNTRY가 아닌 level로 이미 있는 DB에서 그 행을 건너뛴다. 그러면 해당 국가가 COUNTRY로 존재하지 않아 `existsCountry`가 false가 된다. 배포 전에 대상 DB에서 `SELECT code, level FROM region_code WHERE code ~ '^[A-Z]{2}$' AND level <> 'COUNTRY'`가 0건인지 확인해야 한다.
- 기존 `KR`의 display_name(`Korea`)은 유지되고 신규 환경은 `대한민국`이어서 환경 간 표시명이 다를 수 있다.
- 일부 기존 통합 테스트가 `KR`을 평문 INSERT한다(`AccountPersistenceIntegrationTest`, `NicknameDuplicateModerationIntegrationTest`, `DeviceAuthIntegrationTest`). 모두 선행 DELETE가 있어 충돌하지 않는 것으로 코드에서 확인했으나 실행으로는 확인하지 못했다.
- 국가명 249개는 직접 작성했으며 외부 번역 자료와 대조하지 않았다.

### Concurrency and idempotency

- 여러 인스턴스가 동시에 기동해도 Flyway 잠금으로 V29는 한 번만 실행된다고 보며 별도로 검증하지 않았다.

### Transactions and event ordering

- V29는 단일 INSERT라 Flyway 기본 트랜잭션에서 전체 적용 또는 전체 롤백된다.

### External APIs

- 해당 없음.

### Failure recovery and reconciliation

- 롤백은 V29 수정이 아니라 후속 마이그레이션(예: V30)에서 시드 행을 삭제하는 방식이어야 한다. `user_account`가 참조하는 행은 FK `ON DELETE RESTRICT`로 삭제되지 않는다.

## 7. Regression and residual risk

- 통합 테스트가 한 건도 실행되지 않았다. V29의 실제 적용, 제약 만족, FK, 멱등성, 업그레이드 경로, 기존 통합 테스트 회귀가 모두 남은 위험이다.
- 이 환경의 CRLF와 프로세스 실행 제약으로 단위 테스트 전체 통과를 확인하지 못했다.
- 후속 검증 방법: Docker가 있는 환경 또는 CI에서 `./gradlew integrationTest`와 `./gradlew check`를 실행하고, 이 보고서의 INT 결과를 갱신한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-294-TEST-PLAN-GH-294-COUNTRY-SEED.md`
- CI run: 없음
- Related ADR: 없음
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
