# Test Plan: TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION

> Created at: `2026-10-10T14:52:42+09:00`
> GitHub Issue: `#349`
> Status: Approved

## 1. Objective

앱 API(`/api/**`, 운영자 세션 경로 제외)는 `iss`·`aud`가 설정값과 맞고 `role`이 USER인 액세스 토큰만 받는다.
서명 키가 없거나 32바이트 미만이면 앱이 기동하지 않는다. 검증이 빠지면 같은 키로 서명된 다른 용도의 토큰이나
USER가 아닌 토큰이 앱 API를 통과하고, 짧은 키는 운영 중 첫 발급 요청에서야 500으로 드러난다.

## 2. Scope

### Included

- `AccessTokenProperties` 생성자 검증: secret 없음·31바이트·32바이트·멀티바이트 경계, 오류 메시지에 키 값이 없는지
- `AccessTokenConfiguration.jwtDecoder`의 `iss`·`aud`·만료 검증
- `role` 클레임 → `ROLE_<role>` 권한 변환
- `appApiSecurityFilterChain`의 USER role 요구와 401·403 구분
- 짧은 키로 설정 바인딩 시 컨텍스트 기동 실패
- 기존 통합 테스트 5개 파일의 `jwt()` post-processor에 USER 권한 부여(동작 변경에 따른 기존 테스트 적응)

### Excluded

- 차단 사용자 즉시 차단 캐시, 키 회전, 비대칭 키
- 발급 코드(`AccessTokenIssuer`)와 클레임 구성
- 운영자 세션 체인(`/admin/**`, `/api/v1/operator/**`)

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #349 | role이 USER가 아니거나 없는 토큰은 403, iss가 다르거나 aud에 audience가 없는 토큰은 401 |
| GitHub Issue #349 | secret이 없거나 31바이트인 설정으로 컨텍스트가 기동하지 않는다 |
| GitHub Issue #349 | 정상 USER 토큰의 기존 통합 테스트가 그대로 통과한다 |
| `docs/product/AUTH_DESIGN.md` 4.5 | 토큰 클레임은 `iss=qello`, `aud=qello-app`, `role=USER`, `did`, `jti`, `iat`, `exp` |
| `AccessTokenProperties` 주석 | HS256은 최소 32바이트(256bit) 키를 요구한다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 기본 만료 검증이 새 validator 조합에서 빠진다 | 만료 토큰 통과 | 중 | P0 | 만료 토큰 거절 단위 테스트 |
| role 변환이 빠져 정상 USER 토큰까지 403 | 앱 API 전체 차단 | 중 | P0 | 실제 발급 토큰으로 보호 경로 통과 통합 테스트 |
| 등록·재발급 경로가 role 요구에 걸린다 | 로그인 불가 | 낮음 | P0 | 기존 `DeviceAuthIntegrationTest` 통과 |
| `/api/v1/operator/**`가 앱 체인 규칙에 영향받는다 | 운영자 API 오동작 | 낮음 | P1 | 기존 `OperatorReportCaseIntegrationTest` 통과 |
| 키 길이를 문자 수로 세서 멀티바이트 키 판정이 틀린다 | 짧은 키 통과 또는 정상 키 거절 | 중 | P1 | UTF-8 바이트 경계 단위 테스트 |
| 기동 실패 메시지·로그에 키 값이 찍힌다 | 비밀 노출 | 낮음 | P0 | 오류 메시지에 secret 값이 없는지 단위 테스트 |
| role 클레임이 문자열이 아니거나 비어 있을 때 500 | 인증 오류 대신 서버 오류 | 낮음 | P1 | 비문자열·빈 role 변환 단위 테스트 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-001 | secret이 null | `AccessTokenProperties` 생성 | 예외, 메시지에 `qello.auth.access-token.secret` 포함 | P0 | 실행 에이전트 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-002 | secret이 31바이트 ASCII | 생성 | `IllegalArgumentException`, 메시지에 키 값 없음 | P0 | 실행 에이전트 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-003 | secret이 정확히 32바이트, 또는 11자·33바이트 한글 | 생성 | 성공 | P1 | 실행 에이전트 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-004 | `toString()` 호출 | 문자열 생성 | secret 값이 들어가지 않는다(기존 동작 회귀 방지) | P1 | 실행 에이전트 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-005 | 설정의 디코더, 설정과 같은 iss·aud·유효 기간 토큰 | decode | 성공 | P0 | 실행 에이전트 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-006 | iss만 다른 토큰 | decode | `JwtValidationException` | P0 | 실행 에이전트 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-007 | aud가 다른 값만 있거나 aud 클레임이 없는 토큰 | decode | `JwtValidationException` | P0 | 실행 에이전트 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-008 | aud에 설정 audience와 다른 값이 함께 있는 토큰 | decode | 성공(포함 검증) | P1 | 실행 에이전트 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-009 | 만료 시각이 허용 오차를 넘긴 토큰 | decode | `JwtValidationException`(기본 만료 검증 유지) | P0 | 실행 에이전트 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-010 | `role=USER` 클레임 | 인증 변환 | 권한이 `ROLE_USER` 하나 | P0 | 실행 에이전트 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-011 | role 없음, 빈 문자열, 숫자 role | 인증 변환 | 권한이 비어 있고 예외가 나지 않는다 | P1 | 실행 에이전트 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-012 | `ApplicationContextRunner`에 31바이트 secret 설정 | 컨텍스트 기동 | 기동 실패, 원인에 바인딩 오류 | P0 | 실행 에이전트 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-013 | `ApplicationContextRunner`에 secret 설정 없음 | 컨텍스트 기동 | 기동 실패 | P1 | 실행 에이전트 |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-INT-001 | 전체 보안 체인, `JwtEncoder` 빈 | 같은 키로 `role=OPERATOR` 토큰 서명 | 인증 필요 `/api/**` GET | 403 | 없음(DB 미사용) |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-INT-002 | 같음 | role 클레임 없는 토큰 | 같음 | 403 | 없음 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-INT-003 | 같음 | iss가 다른 USER 토큰 | 같음 | 401 | 없음 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-INT-004 | 같음 | aud가 다른 USER 토큰 | 같음 | 401 | 없음 |
| TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-INT-005 | 기기 등록 → 발급 토큰 | `POST /api/v1/auth/devices` | 응답 토큰으로 인증 필요 `/api/**` GET | 401·403이 아니다 | 클래스 기존 `@BeforeEach` 정리 |

INT-001~005는 컨텍스트 기동 비용을 늘리지 않도록 기존 `DeviceAuthIntegrationTest`에 추가한다. INT-005는 같은 클래스의
기존 테스트(`issuedAccessTokenAuthenticatesProtectedApiPath`)가 401만 확인하므로 403도 아님을 확인하도록 고친다.

기존 통합 테스트 적응: `FeedMediaViewUrlIntegrationTest`, `NotificationPreferenceApiIntegrationTest`,
`PushDeviceRegistrationIntegrationTest`, `ActiveUserPresenceApiIntegrationTest`, `HttpRequestLoggingSecurityIntegrationTest`의
`jwt()`에 `ROLE_USER` 권한을 준다. 기대값과 시나리오는 바꾸지 않는다.

## 7. Cross-cutting scenarios

### Database and transactions

- 해당 없음. 토큰 검증은 DB를 읽지 않는다.

### Concurrency and idempotency

- 해당 없음. 디코더·변환기는 상태가 없다.

### External APIs

- 해당 없음.

### Failure recovery and reconciliation

- 짧은 키 배포는 기동 실패로 드러나므로 이전 버전이 계속 서비스한다. 배포 전 환경변수 키 길이 확인이 필요하다는 점을 PR
  위험에 기록한다. 실제 환경의 키 값은 확인하거나 기록하지 않는다.

## 8. Test data and isolation

- Fixtures: 테스트 전용 32바이트 이상 문자열 키, `qello`/`qello-app` 기본 iss·aud
- Database isolation: 통합 시나리오는 DB를 쓰지 않는다. INT-005는 기존 클래스 정리를 따른다.
- Clock/randomness: 단위 디코더 테스트는 `iat`를 현재 시각 기준으로 두고 만료 시나리오는 허용 오차(60초)보다 충분히 과거로 둔다.
- External API doubles: 없음
- Cleanup: 없음

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 실행 에이전트 | `src/test/java/com/dnd/qello/auth/token/AccessTokenPropertiesTest.java` | UNIT-001~004 | `./gradlew test --tests '*AccessTokenPropertiesTest'` |
| 2 | 실행 에이전트 | `src/test/java/com/dnd/qello/auth/config/AccessTokenConfigurationTest.java` | UNIT-005~013 | `./gradlew test --tests '*AccessTokenConfigurationTest'` |
| 3 | 실행 에이전트 | `src/integrationTest/java/com/dnd/qello/DeviceAuthIntegrationTest.java` | INT-001~005 | `./gradlew integrationTest --tests '*DeviceAuthIntegrationTest'` |
| 4 | 실행 에이전트 | 6절의 기존 통합 테스트 5개 파일 | 기존 시나리오 적응 | `./gradlew integrationTest` |

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: 사용자(tkv00)
- Decision: 승인
- Approved at: 2026-10-10
