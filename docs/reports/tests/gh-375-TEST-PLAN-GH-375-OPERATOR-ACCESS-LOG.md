# Test Report: TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG

> Created at: `2026-11-22T22:29:17+09:00`
> GitHub Issue: `#375`
> Branch: `feat/gh-375-operator-access-log`
> Commit: base `32ab7b5`. 최종 `pr-ready`를 돌린 코드는 커밋 `c8676c7`~`80176ab`의 내용과 같다(실행 당시 미커밋)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 운영자 로그인 결과 5종(성공, `AUT-APP-001` 미존재 id·비밀번호 불일치, `AUT-APP-002`, `AUT-APP-003`, `AUT-APP-007`), 로그아웃, `/admin/**`·`/api/v1/operator/**` 운영자 API 호출의 `operator_access_log` 기록. 기록 행의 민감정보 부재, 로그인 기록 실패 시 롤백(fail-closed), API 호출·로그아웃 기록 실패 시 응답 유지(fail-open), V34 스키마·CHECK, append-only 계약, 기존 운영자·인증·migration 테스트 회귀.
- Unverified scope: 운영 DB·RDS에 V34 적용, 실제 ALB·프록시 뒤의 클라이언트 주소(C7), DB 권한 수준의 수정·삭제 차단(I4), 실제 브라우저 세션 흐름, DBML 파서(`@dbml/core`) 검증(저장소에 도구가 없다).
- Release recommendation: 코드 리뷰와 PR 승인 뒤 병합 가능. 운영 배포 전 위치기반서비스 점검표의 보존 기간(1년 기준 검토)과 I4·C7 작업이 남는다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain Java 21 |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine`(로컬 Docker Desktop), 클래스 시작 전 TRUNCATE(#350) |
| Test runner | JUnit 5, MockMvc, AssertJ, Mockito(`@MockitoSpyBean`) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./harness test-run --id TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG` 1회차 | FAIL | 단위 1,542 통과 / 통합 894 중 2 실패 | 약 4분 40초 | 5절. V34로 바뀐 기존 테스트의 고정 개수 2건 |
| `./harness test-run --id TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG` 2회차 | PASS | 통합 894, 실패·오류·skip 0. 단위는 입력이 같아 Gradle이 up-to-date로 건너뛰었다(1회차 1,542 통과 결과) | 통합 3분 50초 | 두 고정 개수를 갱신한 뒤 |
| 독립 검증 후 보강(5절) 뒤 영향 단위 테스트 | PASS | 146, 실패 0 | 약 30초 | `auth.*`, `ClientAddressTest`, `convention.*`, `architecture.*` |
| `./harness check` | PASS | — | — | JUnit 정책 첫 실행 실패 1건 수정 후(5절) |
| `./harness pr-ready --project-tests` (`./gradlew check`) | PASS | 단위 1,545 / 통합 894, 실패·오류·skip 0. spotless, checkstyle, `javaConventionCheck`, 아키텍처·소스 컨벤션 테스트 포함 | 5분 31초 | 최종 코드 기준. 이어서 `git diff --check` 통과 |
| `npm run hooks:validate` | PASS | — | — | Husky validation passed |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `OperatorLoginServiceTest.recordsSuccessfulLogin` | |
| UNIT-002 | PASS | `OperatorLoginServiceTest.recordsUnknownLoginIdWithoutOperator` | 더미 해시 검증 1회 확인 |
| UNIT-003 | PASS | `OperatorLoginServiceTest.recordsWrongPasswordWithOperator` | |
| UNIT-004 | PASS | `OperatorLoginServiceTest.recordsLockedCredentialWithoutPasswordCheck` | 비밀번호 검증 0회 |
| UNIT-005 | PASS | `OperatorLoginServiceTest.recordsInactiveAccount` | |
| UNIT-006 | PASS | `OperatorLoginServiceTest.propagatesRecordingFailure` | |
| UNIT-007 | PASS | `OperatorAccessLogFilterTest.recordsAuthenticatedOperatorCall` | |
| UNIT-008 | PASS | `OperatorAccessLogFilterTest.recordsStatusWrittenDownstream` | 403, 404 |
| UNIT-009 | PASS | `recordsServerErrorAndRethrowsChainFailure`, `keepsClientErrorStatusOnChainFailure` | |
| UNIT-010 | PASS | `skipsExcludedPaths`, `excludesOnlyExactMethodAndDecodedPath` | method+경로 쌍, 인코딩 경로 |
| UNIT-011 | PASS | `skipsRequestsWithoutAuthenticatedOperator` | 빈 context, 익명, ROLE_USER |
| UNIT-012 | PASS | `keepsResponseWhenRecorderThrows`, `keepsChainFailureWhenRecorderAlsoThrows`, `keepsResponseWhenRepositoryFails` | |
| UNIT-013 | PASS | `truncatesLongPath` | |
| UNIT-014 | PASS | `ClientAddressTest` 3개 메서드 | IPv6 전체 주소(D3 A) |
| UNIT-015 | PASS | `OperatorAccessLogRecorderTest` 3개 메서드 | error 로그에 예외 메시지·스택 없음 |
| UNIT-016 | PASS | `OperatorAccessLogEntryTest` 11개 메서드 | 요청 정보 길이 상한 포함 |
| UNIT-017 | PASS | `OperatorAccessLogAppendOnlyTest` 2개 메서드 | |
| INT-001 | PASS | `FlywayMigrationIntegrationTest.v34AddsOperatorAccessLogCatalog`, `v34RejectsInvalidAccessLogRows`, 카탈로그 총계, `FlywayMigrationContractTest.v34AddsOperatorAccessLogContract` | CHECK 134 → 142, 인덱스 71 → 72 |
| INT-002 | PASS | `OperatorAccessLogIntegrationTest.recordsSuccessfulLogin` | XFF 무시 |
| INT-003 | PASS | `recordsUnknownLoginIdWithoutOperator` | |
| INT-004 | PASS | `recordsWrongPasswordTogetherWithFailureCounter` | |
| INT-005 | PASS | `recordsLockedAttemptAfterFiveFailures` | |
| INT-006 | PASS | `recordsInactiveAccount` | |
| INT-007 | PASS | `AuthRateLimitIntegrationTest.recordsRateLimitedOperatorLoginInAccessLog` | |
| INT-008 | PASS | `recordsLogoutAndNotTheRejectedFollowUp` | |
| INT-009 | PASS | `recordsOperatorApiCallsOnBothChains` | |
| INT-010 | PASS | `recordsFailedOperatorApiCallWithActualStatus` | |
| INT-011 | PASS | `recordsAuthenticatedRequestRejectedByCsrfFilter` | |
| INT-012 | PASS | `excludedPathsLeaveOnlyOutcomeRows` | |
| INT-013 | PASS | `anonymousRequestsLeaveNoRows` | |
| INT-014 | PASS | `rowsContainNoSecrets` | 마스킹 token과 `XSRF-TOKEN` 원문 모두 검사 |
| INT-015 | PASS | `withinTransactionRecordingRequiresCallerTransaction` | |
| INT-016 | PASS | `OperatorAccessLogFailureIntegrationTest` 2개 메서드 | |
| INT-017 | PASS | `independentRecordingFailureKeepsResponses` | |
| INT-018 | PASS | `concurrentCallsAreAllRecorded` | P2 |
| INT-019 | PASS | 기존 8개 클래스 무수정 실행(`pr-ready` 전체 통합 894개에 포함) | `docs/api/openapi.json` 변경 없음, `OpenApiSpecificationIntegrationTest` 통과 |

통합 시나리오는 구현 뒤에 작성해 처음 실행부터 통과했다. 구현 전 실패를 먼저 확인한 것은 단위 시나리오(UNIT-001~006, 016, 017)와 INT-001의 컴파일·계약 실패까지다.

## 5. Failures and diagnostics

- 1회차 `test-run`의 통합 실패 2건(구현 문제)
  - `AccountPersistenceIntegrationTest`: public 테이블 수 `expected: 54 but was: 55`.
  - `NotificationPreferenceMigrationIntegrationTest`: V24 이후 적용 migration 수 `expected: 9 but was: 10`.
  - 원인: 두 테스트가 테이블 수와 migration 수를 고정해 두었는데 V34가 각각 1을 더했다. #337도 같은 두 값을 갱신했다. 값과 근거 주석을 고친 뒤 재실행해 통과했다.
- 첫 Flyway 통합 실행(환경 문제): Docker 데몬이 꺼져 있어 Testcontainers가 `DockerClientProviderStrategy` 단계에서 실패했다. Docker Desktop을 켠 뒤 같은 명령으로 통과했다.
- 첫 `./harness check`(정책 위반): JUnit 정책 검사가 `ClientAddressTest`의 `@ParameterizedTest` 뒤에 여러 줄 `@CsvSource`가 먼저 오는 메서드에서 `@DisplayName`을 찾지 못했다. 새 parameterized 테스트 7개 모두 `@DisplayName`을 `@ParameterizedTest` 바로 다음으로 옮겼다.
- 독립 검증자(소스 수정 권한 없음)가 찾은 항목과 처리
  - blocker: `config/java-conventions/baseline.json`의 `JAVA-CONV-0040`이 origin/main 기준 `OperatorLoginService` 해시를 담고 있어, 머지 뒤 main에서 `QELLO-JAVA-BASELINE-006`이 난다. 항목을 지웠다. 생성자가 더미 해시를 계산하므로 CTOR-001 대상이 아니며 `JavaSourceConventionTest`가 통과한다.
  - should-fix: 필터의 fail-open이 recorder 내부 catch 하나에만 기대고 있었다. 필터가 기록 호출을 한 번 더 감싸고, chain 예외가 있으면 suppressed로 붙이게 했다. 요청 제한 거절 경로도 기록 실패가 429를 바꾸지 않게 감쌌다.
  - nit: 제외 기준을 경로에서 method+디코딩 경로 쌍으로 바꿨다. INT-014에 `XSRF-TOKEN` 쿠키 원문 검사를 더했다. `SecurityConfiguration`을 `@RequiredArgsConstructor`로 바꿨다.

## 6. Potential issues

### Application code

- CSRF 토큰 없이 들어온 `/admin/login`, 그리고 인증된 세션의 CSRF 없는 `POST /admin/logout`은 `CsrfFilter`가 403으로 끝내 어떤 행도 남지 않는다. 필터 제외 대상이고 컨트롤러에도 닿지 않기 때문이다. 로그인 시도 일부가 접근 기록에서 빠진다.
- `loginId` 형식 검증(400)으로 거절된 로그인 요청은 5가지 결과에 속하지 않아 기록하지 않는다.
- 로그아웃 기록은 recorder의 예외 흡수에만 기대고 컨트롤러에서 한 번 더 감싸지 않는다. 항목 값이 모두 검증된 상수·인증 id라 생성 단계 실패 가능성은 낮다.
- `OperatorAccessLogFilter`는 `SecurityContextHolder`의 principal 이름을 운영자 id로 해석한다. 로그인 컨트롤러가 principal을 계정 id 문자열로 만드는 계약(`OperatorLoginController`)이 바뀌면 기록이 빠진다(그 경우 null로 보고 기록하지 않는다).
- `ClientAddress`는 `ClientAddressKey`와 IPv6 파싱 로직이 겹친다. 프록시 도입(C7) 때 두 곳을 함께 바꿔야 한다.

### Infrastructure and resource limits

- 요청 제한을 넘긴 로그인 거절도 매번 한 행을 남긴다. 한도를 넘긴 대입이 계속되면 insert가 요청 수만큼 생긴다. 각 insert는 bcrypt 검증보다 가볍지만 행은 계속 쌓인다.
- 보존 기간이 지난 행의 파기가 없다. 행이 계속 늘어나며, 보존 기간 결정과 파기 작업이 따로 필요하다(이슈 #375 제외 범위).

### Database and migrations

- 수정·삭제 차단은 애플리케이션 계약(저장소 인터페이스와 소스 검사)뿐이다. DB 권한·트리거 차단은 I4에서 다룬다.
- 스키마 문서 3개는 V1~V28 스냅샷 기준 집계라 V34를 "스냅샷 이후 추가"로 따로 표시했다. V29~V34를 반영한 전체 카탈로그 재추출은 아직 하지 않았다.
- 운영자별 조회 인덱스가 없다. 조회 API가 생기면 `(operator_user_id, occurred_at)` 인덱스를 검토한다.

### Concurrency and idempotency

- 행마다 독립 insert이고 유일 제약이 없어 동시 요청끼리 충돌하지 않는다(INT-018).
- 기존 실패 카운터의 read-modify-write는 동시 실패 로그인에서 lost update가 날 수 있다(기존 동작, 이번 범위 밖). 이 경우에도 접근 기록 행 수는 실제 시도 수를 보여 준다.

### Transactions and event ordering

- 로그인 결과 기록은 `MANDATORY`로 로그인 트랜잭션에 참여한다. 기록 insert가 실패하면 `DataAccessException`이 `noRollbackFor` 대상이 아니므로 실패 카운터 증가까지 롤백된다(INT-016). 접근 기록 저장소가 계속 실패하면 실패 카운터도 오르지 않지만, 이때는 로그인 자체가 500으로 막힌다.
- API 호출 기록은 컨트롤러 트랜잭션이 커밋된 뒤 별도 auto-commit으로 남는다. 기록이 실패하면 작업은 반영됐는데 기록만 빠진다. error 로그(`operator_access_log_write_failed`)와 `HTTP_REQUEST` 로그로 사후 대조한다.

### External APIs

- 외부 공급자 연동은 없다.

### Failure recovery and reconciliation

- 코드를 revert하면 기록만 멈추고 테이블과 행은 남는다(Flyway forward-only). 데이터 정리는 필요 없고 원장 행은 지우지 않는다.
- fail-open 경로의 기록 누락은 error 로그로만 드러난다. 운영 알림 연결은 운영 인프라 작업에서 정한다.

## 7. Regression and residual risk

- 기존 운영자 로그인·세션·요청 제한·운영자 API·OpenAPI 통합 테스트는 수정 없이 통과했다(`AuthRateLimitIntegrationTest`는 INT-007 메서드만 추가). 계정 열거 방지 테스트(`failedLoginsAreIndistinguishable`)도 그대로 통과한다.
- 수정한 기존 테스트: `FlywayMigrationIntegrationTest`, `FlywayMigrationContractTest`, `AccountPersistenceIntegrationTest`, `NotificationPreferenceMigrationIntegrationTest`의 고정 개수·최신 버전, `OperatorLoginServiceTest`의 시그니처. 기대값을 완화한 곳은 없다.
- 남은 위험: 6절의 CSRF 거절 로그인 미기록, 요청 제한 거절의 쓰기 증폭, 보존 기간 미정, DB 수준 위·변조 방지 부재.

## 8. Artifacts

- Test plan: [`docs/test-plans/gh-375-TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG.md`](../../test-plans/gh-375-TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG.md) (9절에 실행 중 바뀐 소유 파일 기록)
- CI run: 아직 없음(미push)
- Related ADR: `docs/adr/0002-jpa-jdbc-boundary.md`(append-only JDBC), `docs/adr/0006-split-operator-and-device-authentication.md`(세션 체인 분리)
- PR: 아직 없음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 (보존 기간·파기, CSRF 거절 로그인 기록 여부는 아직 Issue가 없다. I4·C7은 점검표 작업으로 따로 있다)
- [ ] 실행 결과와 PR 설명이 일치함 (PR 작성 시 확인)
