# Test Report: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY

> Created at: `2026-11-25T00:14:29+09:00`
> GitHub Issue: `#376`
> Branch: `feat/gh-376-operator-password-policy`
> Commit: base `30efa9ca`. `pr-ready`를 뺀 실행은 모두 미커밋 변경에서 했다. 최종 `pr-ready`는 이 브랜치 커밋을 `e6d67465`(main, #391 병합) 위로 rebase한 뒤 실행했다

## 1. Executive summary

- Result: `PASS`
- Tested scope:
  - 운영자 비밀번호 작성 규칙: 길이, 문자 종류, 허용 문자, 72자 한도, login_id와 같은 값.
  - V35 `password_change_required`: 컬럼 정의, 기존 행 TRUE 채움.
  - 로그인 시 변경 필요 판정: 초기 비밀번호, 90일 주기 경계.
  - 변경이 필요한 세션의 운영자 API 거부(403 `AUT-APP-008`). CSRF 거부는 공통 403 유지.
  - `POST /admin/password`의 검사 순서와 오류 코드 `AUT-VAL-005`·`AUT-APP-009`·`AUT-APP-010`·`AUT-APP-002`.
  - 현재 비밀번호 불일치의 실패 카운터와 잠금.
  - 성공 시 세션 ID 재발급과 권한 교체. 세션 생성 시각은 유지.
  - 접근 기록 `API_CALL`·`LOGOUT`에 제한 세션 포함, 기록에 비밀번호 없음.
  - 시드는 새로 만들 때만 규칙 검사.
  - OpenAPI 재생성.
  - 기존 운영자·인증·migration 통합 테스트 회귀.
- Unverified scope:
  - test-server·운영 DB에 V35 적용, 기존 운영자의 첫 로그인 흐름.
  - 실제 브라우저에서 세션 쿠키 교체.
  - DBML 파서(`@dbml/core`) 검증. 저장소에 도구가 없다.
  - 같은 세션의 동시 변경 요청. 계획 7절대로 테스트하지 않았다.
- Release recommendation: 코드 리뷰와 PR 승인 뒤 병합 가능하다. 다만 병합하면 test-server의 기존 운영자는 다음 로그인부터 비밀번호 변경을 요구받는다. 프론트에 변경 화면이 있어야 운영자 API를 다시 쓸 수 있다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain Java 21 |
| Spring Boot | 3.5.16 (Spring Security 6.5.11, spring-security-crypto 6.5.11) |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine`(로컬 Docker Desktop), 클래스 시작 전 TRUNCATE(#350) |
| Test runner | JUnit 5, MockMvc, AssertJ |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| 신규·후보 통합 테스트(helper 적용 전) | FAIL(예상) | 121 중 23 실패 | 약 3분 | 5절. 기존 8개 클래스가 시드 직후 운영자 API에서 403 |
| 같은 8개 클래스(helper 적용 뒤) | PASS | 73, 실패 0 | 약 2분 | 시드 helper 한 줄과 헤더 한 줄만 추가 |
| `./harness test-run --id TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY` | PASS | 단위 1,661 / 통합 918, 실패·오류·skip 0 | 단위 1분 4초, 통합 6분 22초 | 독립 검증 전 코드 |
| 독립 검증 반영(5절) 뒤 영향 테스트 | PASS | 단위 194(`auth.*` 등) / 통합 25(`OperatorPasswordChangeIntegrationTest`, `OpenApiSpecificationIntegrationTest`), 실패 0 | 약 2분 | 문서 문구·상수 정리·테스트 단언 보강 |
| `./harness check` | PASS | — | — | 첫 실행의 JUnit 정책 실패 1건 수정 후(5절) |
| `./gradlew spotlessCheck javaConventionCheck` | PASS | — | — | `spotlessApply` 뒤 |
| `./harness sync` | PASS | — | — | main에 들어온 #386·#387 위로 rebase했다. 충돌은 `TASK.md` 하나였고 #376 계약을 남겼다. 그 밖의 파일은 rebase 전후 내용이 같다 |
| `./harness pr-ready --project-tests` (첫 실행) | FAIL(환경) | 통합 131 중 129 실패 | 약 1분 | Docker Desktop이 꺼져 있어 Testcontainers가 초기화되지 않았다. `PushSecretDeploymentIntegrationTest`는 PATH의 `python3`(3.9·3.11.6·3.15)에 PyYAML이 없어 실패했다. 코드 결함이 아닌 로컬 환경 문제다 |
| `./harness pr-ready --project-tests` (재실행) | PASS | 단위 1,663 / 통합 918 / 아키텍처 23 / 소스 컨벤션 3, 실패·오류·skip 0 | Gradle 3분 48초 | Docker Desktop을 켜고, PyYAML 6.0.3이 있는 Homebrew Python 3.11과 Docker CLI를 PATH 앞에 두었다. 패키지 설치나 설정 변경은 하지 않았다. 단위 테스트는 같은 커밋에서 돈 첫 실행 결과를 Gradle이 재사용했다. 단위가 1,661에서 늘어난 2개는 main이 추가한 `RepoMapToolTest`다 |
| `./harness sync` (#391 병합 뒤) | PASS | — | — | main에 #390·#391(좌표 key SSM 주입)이 들어와 다시 rebase했다. 충돌은 `TASK.md` 하나였고 #376 계약을 남겼다. 그 밖의 파일은 main의 변경만 더해졌다 |
| `./harness pr-ready --project-tests` (최종) | PASS | 단위 1,667 / 통합 918 / 아키텍처 23 / 소스 컨벤션 3, 실패·오류·skip 0 | Gradle 5분 52초 | 위 재실행과 같은 환경이다. 단위·통합 모두 새로 실행했다. 단위가 늘어난 4개는 #391이 추가한 `PushSecretEncodingTest`다 |
| `git diff --check`, `npm run hooks:validate` | PASS | — | — | rebase 뒤 HEAD 기준 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `OperatorPasswordPolicyTest.acceptsCompliantPasswords`, `rejectsShortOrSingleTypePasswords` | 3종 8자·2종 10자·4종 8자 통과. 2종 9자·3종 7자·4종 7자는 길이, 1종 30자는 종류 사유 |
| UNIT-002 | PASS | `countsSpaceAsSpecialCharacter`, `rejectsNonPrintableAscii` | 공백은 특수문자로 센다. 한글·이모지·탭·제어문자·DEL·`é` 거부 |
| UNIT-003 | PASS | `enforcesBcryptInputLimit` | 72자 통과, 73자 거부 |
| UNIT-004 | PASS | `rejectsLoginIdIgnoringCase`, `acceptsPasswordContainingLoginId` | 대소문자만 다른 값 거부. 포함만 하면 통과 |
| UNIT-005 | PASS | `violationCarriesNoInput` | field·reason·message·toString에 입력값 없음 |
| UNIT-006 | PASS | `OperatorCredentialTest` 신규 4개 | 발급 true, 변경 false·실패 0·잠금 해제·변경 시각, 실패·성공 기록은 상태 유지, 복원 보존 |
| UNIT-007 | PASS | `requiresChangeFromExactMaxAge`, `initialPasswordAlwaysRequiresChange` | T+90d−1s false, T+90d·+1s true. 초기 비밀번호는 항상 true |
| UNIT-008 | PASS | `OperatorPasswordPropertiesTest` | null·0·음수 실패, `application.yml` 기본 `P90D` |
| UNIT-009 | PASS | `OperatorLoginServiceTest.reports*` 3개 | 초기·최근·만료가 true·false·true. 세 경우 모두 실패 횟수 0, `LOGIN_SUCCEEDED` 1행 |
| UNIT-010 | PASS | `OperatorPasswordChangeServiceTest.rejectsWrongCurrentPassword`, `locksAfterFiveWrongCurrentPasswords` | 규칙 위반 새 값을 붙여도 `AUT-APP-009`. 해시 호출 0, 실패 1회. 5회면 잠금 |
| UNIT-011 | PASS | `rejectsPolicyViolationWithoutCountingFailure` | 실패 카운터·저장 변화 없음 |
| UNIT-012 | PASS | `rejectsUnchangedPassword` | `AUT-APP-010`, 저장 없음 |
| UNIT-013 | PASS | `rejectsLockedCredentialWithoutComparing` | `matches` 호출 0 |
| UNIT-014 | PASS | `changesPassword` | 새 해시, 변경 시각 = Clock, 변경 필요 false, 실패 0, 잠금 없음 |
| UNIT-015 | PASS | `OperatorSeedServiceTest` 3개 | 기존 login_id는 검사·저장 없음, 새 규칙 위반은 `AUT-VAL-005`·저장 없음, 새 규칙 통과는 변경 필요 true로 저장 |
| UNIT-016 | PASS | `AuthEntryPointsTest` 4개 | 제한 세션 `AUT-APP-008`. 제한 세션의 `InvalidCsrfTokenException`·`MissingCsrfTokenException`과 정상 세션은 `CMN-DOM-001`. 본문에 예외 메시지 없음 |
| UNIT-017 | PASS | `OperatorAccessContextTest` 4개, `OperatorPasswordChangeIntegrationTest.initialPasswordSessionIsRestricted`(LOGOUT 행) | 제한 권한에서도 운영자 id 42. 필터가 제한 세션의 403 호출을 `API_CALL`로 남김. 제한 세션 로그아웃이 `LOGOUT` 행으로 남음 |
| UNIT-018 | PASS | `OperatorPasswordChangeRequestTest` | 두 비밀번호 모두 `REDACTED` |
| INT-001 | PASS | `FlywayMigrationIntegrationTest.v35AddsPasswordChangeRequiredColumn`, `v35BackfillsExistingCredentialsAsChangeRequired`, `FlywayMigrationContractTest.v35AddsPasswordChangeRequiredContract`, `NotificationPreferenceMigrationIntegrationTest` | `boolean`·`NOT NULL`·기본 `true`. V34 상태에서 넣은 행이 V35 뒤 TRUE. 최신 버전 35, V25 이후 적용 수 11. CHECK·인덱스 총계는 그대로 |
| INT-002 | PASS | `OperatorPasswordChangeIntegrationTest.initialPasswordSessionIsRestricted` | 로그인 200·`passwordChangeRequired` true. `/api/v1/operator/report-cases`와 `/admin/filtering/manual-review-cases`가 403 `AUT-APP-008`. 로그아웃 200, 이후 401 |
| INT-003 | PASS | `changingPasswordRotatesSessionAndUnlocksOperatorApi` | `SESSION` 값 변경. 이전 쿠키 401, 새 쿠키 200. 해시·변경 시각 갱신, 변경 필요 false. `SPRING_SESSION.CREATION_TIME` 동일. 이전 비밀번호 401 `AUT-APP-001`, 새 비밀번호 200·false |
| INT-004 | PASS | `rejectsPolicyViolations`(5건), `rejectsUnchangedPassword` | 길이 미달·종류 부족·login_id 동일·ASCII 밖·73자가 400 `AUT-VAL-005`, 같은 값은 400 `AUT-APP-010`. 500 없음, 자격증명 행 그대로 |
| INT-005 | PASS | `locksAfterFiveWrongCurrentPasswords` | 1~5회 400 `AUT-APP-009`, `failed_attempt_count` 5·`locked_until` 설정. 6번째 변경과 로그인 423 `AUT-APP-002` |
| INT-006 | PASS | `csrfAndSessionFailuresDoNotUseChangeRequiredCode` | CSRF 없음 403 `CMN-DOM-001`, 세션 없음 401, 자격증명 그대로 |
| INT-007 | PASS | `recordsPasswordChangeCallsWithoutPasswords` | `GET report-cases 403`, `POST /admin/password 400`, `POST /admin/password 200`이 운영자 id와 함께 남음. 전체 행 JSON에 사용한 비밀번호 3개 없음 |
| INT-008 | PASS | `OperatorPasswordExpiryIntegrationTest` 2개 | 고정 Clock −90일: true·403·변경 200(변경 시각 = Clock). −90일+1초: false·200 |
| INT-009 | PASS | `fullSessionCanChangePasswordVoluntarily` | 정상 세션도 변경 200, 세션 ID 변경, 운영자 API 계속 200 |
| INT-010 | PASS | 전체 `./gradlew integrationTest` | helper를 적용한 8개 클래스는 기대값 변경 없음. `OperatorLoginIntegrationTest`·`AuthRateLimitIntegrationTest`는 수정 없이 통과 |
| INT-011 | PASS | `OpenApiSpecificationIntegrationTest` | `docs/api/openapi.json`에 `POST /admin/password`(200·400·401·403·423), `OperatorPasswordChangeRequest`, 로그인 응답 `passwordChangeRequired`가 추가됨. 예시 비밀번호 없음 |

단위 시나리오는 구현과 함께 작성했다. 먼저 실패를 확인한 것은 다음 두 가지다.

- 기존 `OperatorLoginServiceTest`의 컴파일 실패: 시그니처와 반환 타입이 바뀌었다.
- 기존 통합 테스트 8개 클래스의 403 실패: helper 적용 전이다.

## 5. Failures and diagnostics

- helper 적용 전 통합 실행(예상한 실패)
  - 23건 모두 시드 직후 운영자 API 호출에서 `Status expected:<200> but was:<403>`였다(동시 호출 테스트는 `[403, ...]`).
  - 해당 클래스: `OperatorReportCaseIntegrationTest` 10건, `FilterReleaseRegistryIntegrationTest` 4건, `OperatorAccessLogIntegrationTest` 3건, `OperatorSessionLifetimeIntegrationTest` 2건, `OperatorAccessLogFailureIntegrationTest`·`ManualReviewPriorityIntegrationTest`·`AppealCaseIntegrationTest`·`SnapshotHealthMigrationIntegrationTest` 각 1건.
  - 원인: V35 이후 시드 운영자는 변경 필요 상태로 시작한다. 계획 8절대로 시드 호출 바로 뒤에 `OperatorTestAccounts.completePasswordChange`를 넣었고, 그 밖의 기대값은 바꾸지 않았다.
- 테스트 데이터 오류(테스트 수정)
  - `OperatorPasswordPolicyTest`의 "3종 7자" 예시 `Abcde1!`가 실제로는 4종이었다.
  - 예시를 `abcde1!`로 고치고 `Abcde1!`는 4종 7자 사례로 남겼다.
  - 이때 사유 문구도 "3종류 이상을 섞은 비밀번호는 8자 이상"으로 바꿨다. 종류 수를 그대로 찍지 않고 규칙을 그대로 알린다.
- 첫 `./harness check`(정책 위반): 여러 줄 `@CsvSource` 뒤의 `@DisplayName`을 JUnit 정책 검사가 찾지 못했다. 새 테스트 8곳에서 `@DisplayName`을 `@Test`/`@ParameterizedTest` 바로 다음으로 옮겼다.
- OpenAPI 태그 중복
  - 처음 재생성에서 `백오피스 인증` 태그가 설명만 다른 두 개로 갈라졌다. `CsrfTokenApiSpec`이 같은 이름에 이전 설명을 쓰기 때문이다.
  - 범위 밖 파일을 고치지 않고 `OperatorLoginApiSpec`의 태그 설명을 원래대로 두었다.
- 포맷: `spotlessApply`가 수정한 기존 파일 전체를 포맷했다(origin/main ratchet). `git diff -w`로 줄바꿈·들여쓰기·import 순서 외의 변경이 없음을 확인했다.
- 독립 검증자(소스 수정 권한 없음): 차단·중요 0건, 사소 7건. 처리는 다음과 같다.
  - schema manifest: 새 절 제목을 V35 한정으로 좁히고 V30·V31·V33 컬럼이 빠져 있음을 적었다. #375의 체크섬 두 값을 이력 표로 옮겼다.
  - 엔티티 주석: 포매터가 잘못 접은 줄을 다시 썼다.
  - 문서 문구: 제한 세션이 호출할 수 있는 경로를 API 문서·응답 스키마·`AUTH_DESIGN.md`에서 맞췄다. CSRF 발급·로그인은 인증 없이 열려 있다. `AUTH_DESIGN.md` 6절 체인 예시에도 새 matcher를 넣었다.
  - 테스트 보강: INT-002에 제한 세션 로그아웃의 `LOGOUT` 행 단언을 더했다. `OperatorCredentialTest`의 잠금 해제 단언은 실제로 잠근 상태에서 시작하게 했다.
  - 권한 이름: `OperatorAccessContext` 한곳에 두고 `SecurityConfiguration`이 참조하게 했다.
  - 보고서: 이 문서로 채웠다.

## 6. Potential issues

### Application code

- 세션 도중의 주기 만료는 다음 로그인까지 반영되지 않는다(D3 A). 상한은 세션 절대 만료 12시간이다.
- 비밀번호를 바꿔도 같은 운영자의 다른 세션은 유지된다. 이 역시 절대 만료 12시간이 상한이다. 탈취된 세션이 변경 뒤에도 남을 수 있어 후속 이슈 후보다(범위 밖).
- 직전 비밀번호 재사용은 현재 값과 같은 경우만 막는다. 이전 N개 이력은 범위 밖이다.
- 로그인 입력과 현재 비밀번호 입력이 72바이트를 넘으면 앞 72바이트만 비교한다(4절 P2 위험 확인).
  - 500이 나지 않는 이유: spring-security-crypto 6.5.11의 `BCrypt.checkpw`는 비교 경로에서 72바이트 검사를 건너뛴다.
  - 확인 방법: 독립 실행으로 72자 해시에 73자 입력 `matches`가 true임을 보았다. 해시 생성(`encode`)만 예외를 던진다.
  - 새 비밀번호는 정책이 ASCII 72자로 막으므로 저장되는 해시의 입력은 항상 72바이트 이하다.
- 변경 API에는 IP 요청 한도가 없다(가정). 세션이 있어야 호출할 수 있고, 계정 잠금 5회가 대입을 막는다.
- 변경 API의 현재 비밀번호 불일치로 잠겨도 지금 세션은 끊지 않는다(가정). 잠금은 로그인과 변경 API만 막는다.

### Infrastructure and resource limits

- 비밀번호 변경마다 BCrypt 비교 1회와 해시 1회가 든다. 로그인과 같은 비용 수준이다.

### Database and migrations

- 병합 뒤 test-server에 V35가 적용되면 기존 운영자 행이 모두 `password_change_required = TRUE`가 된다. 다음 로그인부터 변경을 요구받는 것은 의도한 동작이다(계획 4절 P1).
- Byuntil의 Draft PR #384(`V35__add_location_consent_history`)와 #389(`V35__encrypt_exact_coordinates`)도 V35를 쓴다. 먼저 병합되는 쪽이 V35를 쓰고, 나중 쪽이 번호와 Flyway 테스트의 고정 개수를 다시 맞춘다.
- schema manifest의 전체 카탈로그(V1~V28)는 다시 추출하지 않았다. V35 컬럼은 별도 절에만 적었다.

### Concurrency and idempotency

- 같은 세션의 동시 변경 두 건은 둘 다 현재 비밀번호 확인을 통과할 수 있고, 나중 커밋이 이긴다. 어느 쪽이 이기든 결과는 규칙을 지킨 새 비밀번호다. 테스트하지 않았다.
- 동시 불일치 요청은 `@Version`이 없어 실패 증가 하나를 잃을 수 있다(V5의 기존 결정). INT-005는 순차 요청으로 검증했다.

### Transactions and event ordering

- 현재 비밀번호 불일치의 실패 카운터는 `noRollbackFor = AuthException.class`로 예외와 함께 커밋된다(INT-005).
- 세션 ID 재발급과 권한 교체는 서비스 트랜잭션이 커밋된 뒤 컨트롤러에서 한다. 변경이 실패하면 세션은 그대로다. INT-007에서 400 뒤 같은 쿠키로 변경 200이 됐다.
- 접근 기록은 기존 필터의 독립 기록(fail-open)이다. 기록이 실패해도 변경 결과는 바뀌지 않는다.

### External APIs

- 외부 연동은 없다.

### Failure recovery and reconciliation

- 변경 트랜잭션이 실패하면 비밀번호와 세션이 모두 이전 상태로 남고, 운영자는 다시 시도하면 된다.
- 시드 비밀번호가 새로 만들 때 규칙을 어기면 `ApplicationRunner` 예외로 기동이 멈춘다. 이미 있는 운영자는 검사하지 않는다(UNIT-015).
- 비밀번호를 잊으면 복구 경로가 없다(범위 밖). 지금처럼 DB 운영 절차로 처리한다.

## 7. Regression and residual risk

- 기존 운영자 로그인·요청 제한 통합 테스트는 수정 없이 통과했다. 계정 열거 방지 검사도 그대로다.
- 시드 직후 운영자 API를 호출하는 기존 통합 테스트 8개 클래스의 변경은 두 줄뿐이다.
  - 시드 helper 호출 한 줄.
  - 헤더의 Source scenario 한 줄.
  - 나머지 diff는 spotless 포맷 결과다(`git diff -w` 기준 helper와 헤더 외 변경 없음).
- 고정값을 갱신한 기존 테스트도 기대를 완화하지 않았다.
  - `FlywayMigrationIntegrationTest`: 최신 버전 35.
  - `FlywayMigrationContractTest`: migration 목록, 최신 버전 단언을 V35 테스트로 이동.
  - `NotificationPreferenceMigrationIntegrationTest`: 적용 수 11.
  - `OperatorLoginServiceTest`: 시그니처.
- 남은 위험: 6절의 다른 세션 유지, 세션 도중 만료 지연, test-server 운영자의 변경 강제, #384·#389와의 migration 번호 충돌.

## 8. Artifacts

- Test plan: [`docs/test-plans/gh-376-TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY.md`](../../test-plans/gh-376-TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY.md). 9절에 구현 중 추가한 소유 문서(`docs/product/AUTH_DESIGN.md`)를 기록했다.
- CI run: 아직 없음(미push)
- Related ADR: `docs/adr/0006-split-operator-and-device-authentication.md`(세션 체인, 자체 가입 없음)
- PR: 아직 없음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음(테스트 fixture 비밀번호는 저장소에 이미 있는 가짜 값이다)
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨: 다른 세션 무효화와 비밀번호 이력은 아직 Issue가 없다. OTP는 #377이다
- [ ] 실행 결과와 PR 설명이 일치함(PR 작성 시 확인)
