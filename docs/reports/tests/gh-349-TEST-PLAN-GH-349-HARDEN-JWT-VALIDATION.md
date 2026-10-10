# Test Report: TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION

> Created at: `2026-10-10T15:09:45+09:00`
> GitHub Issue: `#349`
> Branch: `feat/gh-349-harden-jwt-validation`
> Commit: `68f530fa` (변경 미커밋 상태에서 실행)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 서명 키 길이 바인딩 검증, 디코더의 iss·aud·만료 검증, role 클레임 권한 변환, 앱 API 체인의 USER role 요구와
  401·403 구분, 짧은 키·키 없음으로 컨텍스트 기동 실패, 기존 단위·통합 테스트 전체 회귀
- Unverified scope: 실제 배포 환경의 서명 키 길이(값을 확인하지 않는다), CI 실행
- Release recommendation: 배포 전 각 환경의 서명 키가 UTF-8 기준 32바이트 이상인지 확인한 뒤 병합한다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 21 (Gradle 실행 JDK) |
| Spring Boot | 3.5.16 (Spring Security 6.5.11) |
| Database | Testcontainers PostGIS(통합 테스트 공유 컨테이너) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test integrationTest --continue` — Unit | PASS | 1399, 실패 0, 건너뜀 0 | 전체 6분 31초 중 일부 | `build/test-results/test` |
| `./gradlew test integrationTest --continue` — Integration | PASS | 856, 실패 0, 건너뜀 0 | 같음 | `build/test-results/integrationTest` |
| `./harness test-run --id TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION` | PASS | 위와 같음 | 1초 | 입력이 바뀌지 않아 Gradle이 두 task를 UP-TO-DATE로 처리했다. 새로 실행한 것이 아니라 바로 앞 실행 결과다. |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `AccessTokenPropertiesTest.rejectsMissingSecret` | |
| UNIT-002 | PASS | `AccessTokenPropertiesTest.rejectsSecretShorterThan32Bytes` | 메시지에 키 값 없음 확인 |
| UNIT-003 | PASS | `AccessTokenPropertiesTest.acceptsSecretOfExactly32Bytes`, `measuresSecretLengthInUtf8Bytes` | 한글 11자·33바이트 통과 |
| UNIT-004 | PASS | `AccessTokenPropertiesTest.redactsSecretInToString` | |
| UNIT-005 | PASS | `AccessTokenConfigurationTest.decodesTokenWithConfiguredIssuerAndAudience` | |
| UNIT-006 | PASS | `AccessTokenConfigurationTest.rejectsTokenWithDifferentIssuer` | |
| UNIT-007 | PASS | `AccessTokenConfigurationTest.rejectsTokenWithDifferentAudience`, `rejectsTokenWithoutAudience` | |
| UNIT-008 | PASS | `AccessTokenConfigurationTest.acceptsTokenWhoseAudienceContainsConfiguredValue` | |
| UNIT-009 | PASS | `AccessTokenConfigurationTest.rejectsExpiredToken` | 기본 만료 검증 유지 |
| UNIT-010 | PASS | `AccessTokenConfigurationTest.convertsUserRoleClaimToRoleAuthority` | |
| UNIT-011 | PASS | `AccessTokenConfigurationTest.convertsMissingRoleClaimToNoAuthorities`, `convertsBlankRoleClaimToNoAuthorities`, `convertsNonStringRoleClaimToNoAuthorities` | |
| UNIT-012 | PASS | `AccessTokenConfigurationTest.failsToStartWithShortSecret`, `startsWithLongEnoughSecret` | |
| UNIT-013 | PASS | `AccessTokenConfigurationTest.failsToStartWithoutSecret` | |
| INT-001 | PASS | `DeviceAuthIntegrationTest.rejectsOperatorRoleTokenOnAppApi` | 403 |
| INT-002 | PASS | `DeviceAuthIntegrationTest.rejectsTokenWithoutRoleOnAppApi` | 403 |
| INT-003 | PASS | `DeviceAuthIntegrationTest.rejectsTokenWithDifferentIssuerOnAppApi` | 401 |
| INT-004 | PASS | `DeviceAuthIntegrationTest.rejectsTokenWithDifferentAudienceOnAppApi` | 401 |
| INT-005 | PASS | `DeviceAuthIntegrationTest.issuedAccessTokenAuthenticatesProtectedApiPath` | 401·403 모두 아님 |
| 기존 통합 테스트 적응 | PASS | `jwt()`에 `ROLE_USER`를 준 5개 클래스 | 기대값 변경 없음. `PushDeviceRegistrationIntegrationTest`는 아래 5절 정리 후 그 클래스만 다시 실행해 7개 통과 |

## 5. Failures and diagnostics

- 첫 단위 실행에서 UNIT-005가 실패했다. 원인은 테스트 쪽으로, `Jwt.getIssuer()`가 `iss`를 URL로 변환하려다 `qello`에서
  `IllegalArgumentException`을 던졌다. `getClaimAsString("iss")`로 바꿔 해결했다. 운영 코드는 `iss`를 URL로 읽지 않는다
  (`JwtIssuerValidator`는 문자열로 비교한다).
- 커밋 단계에서 pre-commit의 staged checkstyle이 `PushDeviceRegistrationIntegrationTest`의 기존 메서드
  `validatesAuthenticationAndRedactsTokenAcrossBothEndpoints`를 메서드 길이 초과(QELLO-JAVA-SIZE-001)로 막았다. main에서 이미
  57줄이었고 권한 추가로 60줄이 됐다. 사용자 결정으로 그 파일 안에 `userJwt(long)` 헬퍼를 두어 `jwt()` 10곳을 바꾸고, 토큰 없는
  등록·해지 401 확인 두 요청을 private 메서드로 뺐다. 테스트 이름·요청·기대값·순서는 같다. 이 정리 뒤에는
  `./gradlew integrationTest --tests '*PushDeviceRegistrationIntegrationTest'`만 다시 실행했다(7개 통과).

## 6. Potential issues

### Application code

- role 변환은 `JwtGrantedAuthoritiesConverter`를 쓰므로 공백으로 구분된 role 문자열은 여러 권한이 된다. 토큰은 서버만
  서명하고 발급 코드는 `AccountRole` 이름 하나만 넣으므로 현재는 영향이 없다.
- 앱 API의 기존 테스트 일부는 `jwt()` post-processor로 디코더·변환기를 거치지 않는다. 이 테스트들은 role 변환을 검증하지
  않으며, 실제 경로 검증은 INT-001~005가 맡는다.

### Infrastructure and resource limits

- 배포 환경의 키가 32바이트 미만이면 새 버전은 기동하지 않는다. 기존 버전은 짧은 키로도 기동했지만 첫 발급에서 500이었다.

### Database and migrations

- 해당 없음.

### Concurrency and idempotency

- 해당 없음. 디코더·변환기는 상태가 없다.

### Transactions and event ordering

- 해당 없음.

### External APIs

- 해당 없음.

### Failure recovery and reconciliation

- 배포 직전 이미 발급된 토큰은 iss·aud·role이 같은 값으로 발급됐으므로 배포 후에도 그대로 통과한다.
- 기동 실패 시 원인 메시지는 설정 키 이름과 최소 바이트 수만 담고 키 값은 담지 않는다(UNIT-002).

## 7. Regression and residual risk

- 전체 단위 1399개, 통합 856개가 실패 0으로 통과했다.
- CI는 아직 실행하지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-349-TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION.md`
- CI run: 미실행
- Related ADR: `docs/adr/0006-split-operator-and-device-authentication.md`, `docs/product/AUTH_DESIGN.md` 4.5
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
