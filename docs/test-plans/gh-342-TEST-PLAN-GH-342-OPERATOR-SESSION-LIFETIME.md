# Test Plan: TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME

> Created at: `2026-10-09T20:35:57+09:00`
> GitHub Issue: `#342`
> Status: Approved — 사람 승인 완료

## 1. Objective

운영자 세션이 설계대로 미사용 8시간, 생성 후 12시간에 끝나는지 검증한다. 세션 쿠키가 기본 설정에서 `Secure`·`HttpOnly`·`SameSite=Lax`로 나가는지도 확인한다.
실패하면 운영자 세션이 30분 만에 끊기거나, 차단·삭제 권한을 가진 세션이 상한 없이 이어진다. 또 TLS 구간에서 쿠키가 `Secure` 없이 나가거나, HTTP로 직접 노출하는 dev 서버에서 로그인이 불가능해진다.

## 2. Scope

### Included

- `spring.session.timeout=PT8H` 설정과 Spring Session JDBC 세션 행의 `MAX_INACTIVE_INTERVAL`.
- 미사용 8시간이 지난 세션의 만료(Spring Session JDBC 기본 동작과 새 설정의 결합).
- 최대 수명 필터: `/admin/**`·`/api/v1/operator/**` 체인에서 생성 후 12시간이 지난 세션 무효화, 경계 시각, 세션이 없는 요청.
- 만료된 세션 쿠키를 가진 채로 다시 로그인하는 흐름.
- `qello.auth.operator-session.absolute-timeout` 바인딩과 잘못된 값 거부.
- `application.yml`, `application-dev.yml`, `application-local.yml`의 세션·쿠키 기본값. `server.forward-headers-strategy` 미설정 유지.
- 필터가 앱 API 체인(`/api/**`)과 그 밖의 경로에 적용되지 않는지.
- 기존 운영자 통합 테스트와 IP 한도(`X-Forwarded-For` 무시) 회귀.

### Excluded

- ALB·TLS 도입과 `forward-headers-strategy` 활성화, 신뢰할 프록시 범위 검증.
- 동시 로그인 제한, 강제 로그아웃 API, 세션 만료 알림.
- 앱 API 액세스 토큰 수명.
- 실제 브라우저의 `Secure` 쿠키 거부 동작(MockMvc는 쿠키 속성만 확인한다).
- 스키마·마이그레이션 변경.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #342 | `MAX_INACTIVE_INTERVAL` 28800, 생성 후 12시간 세션 401과 행 삭제, 12시간 전 통과, 기본 쿠키 `Secure`·`HttpOnly`·`SameSite=Lax` |
| `docs/product/AUTH_DESIGN.md` 2절 | 운영자 세션 수명 idle 8h / absolute 12h, 세션 행 삭제로 즉시 해지 |
| `docs/product/AUTH_DESIGN.md` 5.1절 | `Set-Cookie: SESSION=...; HttpOnly; Secure; SameSite=Lax; Path=/` |
| `docs/product/AUTH_DESIGN.md` 8.2절, `ClientAddressKey` | 프록시가 없는 동안 `remoteAddr`만 사용, `X-Forwarded-For` 불신 |
| `V6__add_spring_session_tables.sql` | `SPRING_SESSION`의 `CREATION_TIME`·`LAST_ACCESS_TIME`·`MAX_INACTIVE_INTERVAL`·`EXPIRY_TIME`·`PRINCIPAL_NAME` |
| `SecurityConfiguration` | `backofficeSecurityFilterChain`(Order 1)과 `operatorReportCaseSecurityFilterChain`(Order 2)이 세션 기반, 미인증은 `AuthEntryPoints.unauthorized()` |
| `OperatorLoginController` | 로그인 때 기존 세션을 무효화하고 새 세션을 만든다. 따라서 세션 생성 시각이 로그인 시각이다 |
| `ClockConfiguration` | 애플리케이션 시각 원천은 `Clock` 빈 하나다 |
| `src/test/AGENTS.md`, `src/integrationTest/AGENTS.md`, test-policy | JUnit 5, `@DisplayName`, 클래스 헤더 생성 시각·원본 시나리오, 보고서 템플릿 |

조사 결과:
- `application*.yml`에 세션 timeout 설정이 없다. Spring Boot는 `spring.session.timeout`이 없으면 `server.servlet.session.timeout`을 쓰고, 그 기본값은 30분이다.
- Spring Session JDBC는 세션을 읽을 때 `LAST_ACCESS_TIME + MAX_INACTIVE_INTERVAL`을 시스템 시각과 비교한다. 만료된 세션 행은 지우고 없는 세션으로 처리한다. 최대 수명 개념은 없다.
- `DefaultCookieSerializer`는 `useSecureCookie`가 지정되지 않으면 `request.isSecure()`를 따른다. `SameSite` 기본값은 `Lax`, `HttpOnly` 기본값은 `true`다.
- 운영자 로그인과 고정 `Clock`을 함께 쓰는 기존 통합 테스트가 4개 있다(`OperatorReportCaseIntegrationTest`, `ManualReviewPriorityIntegrationTest`, `SnapshotHealthMigrationIntegrationTest`, `AuthRateLimitIntegrationTest`). 고정 시각은 모두 2026-08로 과거다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| `spring.session.timeout`이 JDBC 저장소에 적용되지 않음 | 운영자 세션이 계속 30분에 끊긴다 | 낮음 | P0 | 로그인 후 `SPRING_SESSION.MAX_INACTIVE_INTERVAL` = 28800 |
| 최대 수명 필터가 한쪽 체인에만 연결됨 | `/api/v1/operator/**`로 12시간 이후에도 판정 API 접근 | 중간 | P0 | 두 경로 각각 401과 행 삭제 |
| 만료 세션에서 필터가 바로 401을 응답함 | 오래된 쿠키를 가진 브라우저가 `/admin/login`·`/admin/csrf`도 못 써 다시 로그인할 수 없다 | 중간 | P0 | 만료 세션 쿠키로 로그인 200, 새 세션 발급 |
| 경계 비교 오류(`>` 대 `>=`) | 정확히 12시간 시점의 판정이 계약과 다르다 | 중간 | P1 | 12시간 - 1ms 유지, 12시간 정각 만료 |
| 세션이 없는 요청에서 필터가 `getSession(true)`로 세션을 만듦 | 익명 요청마다 세션 행이 생긴다 | 낮음 | P0 | 세션 없는 요청 후에도 세션 없음 |
| 필터를 `@Component`·`@Bean`으로 등록해 서블릿 필터로 전역 적용됨 | 앱 API 요청이 운영자 세션 행을 지우거나 조회한다 | 중간 | P1 | 만료 세션 쿠키로 `/api/**` 호출 후 행 유지 |
| 기본 쿠키가 `Secure`가 아님 | TLS 프록시 뒤에서 쿠키가 평문 구간으로 샌다 | 낮음 | P0 | 로그인 응답 쿠키 속성 |
| dev·local 프로필에 `secure=false`가 빠짐 | HTTP로 노출된 dev 서버에서 브라우저가 쿠키를 버려 로그인 불가 | 중간 | P1 | 프로필 YAML 바인딩 검사 |
| `forward-headers-strategy`가 실수로 켜짐 | `X-Forwarded-For` 위조로 IP 한도 우회(#315) | 낮음 | P1 | YAML에 키 없음, 기존 `AuthRateLimitIntegrationTest` INT-002 통과 |
| 필터의 `Clock` 빈과 Spring Session의 시스템 시각이 다름 | 테스트에서 미래로 고정한 `Clock`이 모든 세션을 만료시킨다. 과거 고정이면 최대 수명이 꺼진다 | 낮음 | P2 | 기존 고정 `Clock` 테스트 4개 회귀 통과. 위험은 보고서에 기록 |
| `absolute-timeout` 누락·0·음수로 기동 | 모든 세션 즉시 만료 또는 NPE | 낮음 | P1 | properties 생성 검사 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-UNIT-001 | `MockHttpSession`, `Clock` = 세션 생성 시각 + 12시간 - 1ms, 최대 수명 12시간 | 필터 실행 | 세션 유효, 다음 필터 1회 호출 | P0 | auth-executor |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-UNIT-002 | `Clock` = 생성 시각 + 12시간 정각 / + 12시간 + 1ms, 인증된 `SecurityContextHolder` | 필터 실행 | 두 경우 모두 세션 무효화, `SecurityContextHolder` 비움, 다음 필터는 계속 호출(응답을 직접 쓰지 않음) | P0 | auth-executor |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-UNIT-003 | 세션 없는 요청 | 필터 실행 | 다음 필터 호출, 실행 후에도 `getSession(false)`가 null | P0 | auth-executor |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-UNIT-004 | `absolute-timeout` null / `PT0S` / 음수 / `PT12H` | `OperatorSessionProperties` 생성 | null은 `NullPointerException`, 0과 음수는 `IllegalArgumentException`, `PT12H`는 그대로 보관 | P1 | auth-executor |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-UNIT-005 | `application.yml`만 property source로 둔 `Binder` | 바인딩 | `spring.session.timeout` = 8시간, `server.servlet.session.cookie.secure` = true, `qello.auth.operator-session.absolute-timeout` = 12시간, `server.forward-headers-strategy` 없음 | P1 | auth-executor |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-UNIT-006 | `application-dev.yml`, `application-local.yml` 각각을 property source로 둔 `Binder` | 바인딩 | 두 프로필 모두 `server.servlet.session.cookie.secure` = false, `server.forward-headers-strategy` 없음 | P1 | auth-executor |

UNIT-005·006은 `RateLimitDefaultsTest`와 같은 방식(`YamlPropertySourceLoader` + `Binder`)으로 환경 변수 영향을 받지 않게 한다.

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-INT-001 | MockMvc + Security + Spring Session JDBC + PostgreSQL | 시드 운영자, CSRF 발급 | `POST /admin/login` | `PRINCIPAL_NAME` = 운영자 ID인 `SPRING_SESSION` 행의 `MAX_INACTIVE_INTERVAL` = 28800 | 클래스 `@BeforeEach` reset |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-INT-002 | MockMvc + Spring Session 쿠키 직렬화 | INT-001과 같음 | `POST /admin/login` | `SESSION` 쿠키 `Secure` = true, `HttpOnly` = true, `SameSite` = `Lax` | 클래스 reset |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-INT-003 | 백오피스 체인 + 최대 수명 필터 + JDBC | 로그인 후 해당 행의 `CREATION_TIME`을 현재 - 12시간 - 1분으로 갱신 | `GET /admin/filtering/manual-review-cases?agingThresholdSeconds=60` | 401과 기존 미인증 오류 본문, 해당 `PRINCIPAL_NAME` 행 0건 | 클래스 reset |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-INT-004 | 운영자 API 체인 + 최대 수명 필터 + JDBC | INT-003과 같은 세션 조작 | `GET /api/v1/operator/report-cases` | 401과 기존 미인증 오류 본문, 행 0건 | 클래스 reset |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-INT-005 | 두 체인 + 필터 | `CREATION_TIME`을 현재 - 11시간 59분으로 갱신 | INT-003·004의 두 GET | 둘 다 200, 행 유지 | 클래스 reset |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-INT-006 | 백오피스 체인 + 로그인 컨트롤러 | INT-003처럼 만료시킨 세션 쿠키, 새 CSRF 토큰 | 만료 세션 쿠키를 실은 채 `POST /admin/login` | 200, 새 `SESSION` 쿠키 값이 이전과 다름, 새 행의 `CREATION_TIME`이 현재 시각 근처 | 클래스 reset |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-INT-007 | Spring Session JDBC 미사용 만료 | 로그인 후 `LAST_ACCESS_TIME`을 현재 - 8시간 - 1분 / 현재 - 7시간 59분으로 갱신(`EXPIRY_TIME`도 맞춤) | `GET /api/v1/operator/report-cases` | 8시간 초과는 401과 행 0건, 7시간 59분은 200 | 클래스 reset |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-INT-008 | 앱 API 체인 + 서블릿 필터 등록 | INT-003처럼 만료시킨 세션 쿠키 | 세션 쿠키만 실어 `GET /api/v1/anything` | 앱 체인의 401, 운영자 세션 행은 그대로 남음(필터가 앱 체인에 없음) | 클래스 reset |
| TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-INT-009 | 기존 운영자·인증 통합 테스트 | 변경 없음 | `OperatorLoginIntegrationTest`, `OperatorReportCaseIntegrationTest`, `ManualReviewPriorityIntegrationTest`, `SnapshotHealthMigrationIntegrationTest`, `AuthRateLimitIntegrationTest` 실행 | 수정 없이 전부 통과 | 각 클래스 기존 reset |

INT-001~008은 신규 클래스 하나에 둔다. 이 클래스는 고정 `Clock`을 `@Import`하지 않아 실제 시스템 시각을 쓴다. 세션 시각 조작은 `JdbcTemplate` UPDATE로 하고 대상 행은 `PRINCIPAL_NAME`으로 찾는다. 쿠키 값은 Base64로 인코딩된 세션 ID라서 `SESSION_ID` 검색에 그대로 쓸 수 없다.

## 7. Cross-cutting scenarios

### Database and transactions

- 실제 PostgreSQL Testcontainers를 쓴다. 스키마 변경은 없고 V6 테이블을 그대로 쓴다.
- Spring Session JDBC는 자체 transaction으로 세션을 읽고 쓴다. 필터의 `invalidate()`는 요청 안에서 행을 바로 지운다. INT-003·004에서 응답 직후 행 0건으로 확인한다.
- 세션을 저장할 때 `CREATION_TIME`은 갱신되지 않는다. 그래서 테스트에서 직접 바꾼 값이 다음 요청까지 유지된다.

### Concurrency and idempotency

- 같은 만료 세션으로 요청 두 개가 동시에 오면 둘 다 행을 지우려 한다. Spring Session JDBC의 삭제는 없는 행에 대해 0건으로 끝나고, 먼저 지워진 쪽 이후의 요청은 세션 없음으로 처리된다. 응답은 둘 다 401이다. 이번 계획에서는 테스트하지 않고 보고서의 잠재 문제 분석에 기록한다.
- 로그인 재시도(INT-006)는 기존 세션 무효화 후 새 세션을 만든다. 같은 운영자의 이전 세션 행이 남는지는 #72 동작 그대로이고 이번 범위가 아니다.

### External APIs

- 외부 공급자 연동 변경이 없다.

### Failure recovery and reconciliation

- 새 복구 흐름은 없다. 세션 저장소 DB 오류는 기존 동작(요청 실패)에 맡긴다.
- 배포 전에 만든 세션은 행에 저장된 `MAX_INACTIVE_INTERVAL` 1800을 유지하므로 배포 후에도 30분 미사용 시 끝난다. 새 로그인부터 8시간이 적용된다.
- 기능 commit을 revert하면 최대 수명 필터와 쿠키 설정이 즉시 빠진다. 이미 저장된 세션 행의 `MAX_INACTIVE_INTERVAL` 28800은 남으므로, revert 후에도 그 세션은 8시간 미사용 기준으로 끝난다. DB 정리가 필요 없다.

## 8. Test data and isolation

- Fixtures: `OperatorLoginIntegrationTest`와 같은 방식으로 `OperatorSeedService.seedIfAbsent`와 `region_code` 시드, `/admin/csrf` 실제 발급 흐름을 쓴다. `csrf()` post-processor는 쓰지 않는다(기존 테스트 주석의 이유).
- Database isolation: `@BeforeEach`에서 `SPRING_SESSION`(첨부 테이블은 CASCADE), `operator_credential`, `user_account`의 해당 행을 지운다. 다른 클래스가 남긴 세션 행에 영향을 받지 않게 검사는 `PRINCIPAL_NAME`으로 한정한다.
- Clock/randomness: 단위 테스트는 `MockHttpSession.getCreationTime()` 기준의 `Clock.fixed`를 쓴다. 통합 테스트는 시스템 시각과 DB 시각 조작을 쓰고, 경계는 1분 여유를 둔다.
- External API doubles: 없다.
- Cleanup: 기존 `PostgisContainerIntegrationTestSupport` lifecycle을 따른다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다. 운영자 비밀번호는 기존 테스트처럼 예시 문자열을 쓴다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 부모 오케스트레이터 | `TASK.md`, 이 계획 | 요구사항·파일·시나리오 계약 | Issue·branch·TASK 일치, `git diff --check` |
| 2 | auth-executor (승인 후 호출) | 아래 production·테스트 파일, `docs/product/AUTH_DESIGN.md`, 테스트 보고서 | UNIT-001~006, INT-001~009 | 실패 테스트 확인 → 구현 → `./gradlew test`, `./gradlew integrationTest`, `./harness check` |
| 3 | auth-verifier (구현 후 독립 호출) | 소스 수정 권한 없음 | 전체 시나리오·범위 | 실제 diff, JUnit 결과, 범위 밖 파일 불변, `./harness pr-ready --project-tests` |

Production 파일(auth-executor 소유, 작업 전 `src/main/java/AGENTS.md`를 읽는다):
- `src/main/java/com/dnd/qello/auth/config/OperatorSessionProperties.java` (신규)
- `src/main/java/com/dnd/qello/auth/web/OperatorSessionAbsoluteTimeoutFilter.java` (신규, 빈으로 등록하지 않고 `SecurityConfiguration`에서 생성)
- `src/main/java/com/dnd/qello/auth/config/SecurityConfiguration.java` (두 세션 체인에 필터 추가)
- `src/main/resources/application.yml`, `application-dev.yml`, `application-local.yml`

신규 테스트 파일:
- `src/test/java/com/dnd/qello/auth/web/OperatorSessionAbsoluteTimeoutFilterTest.java` (UNIT-001~003)
- `src/test/java/com/dnd/qello/auth/config/OperatorSessionPropertiesTest.java` (UNIT-004)
- `src/test/java/com/dnd/qello/config/OperatorSessionDefaultsTest.java` (UNIT-005~006)
- `src/integrationTest/java/com/dnd/qello/OperatorSessionLifetimeIntegrationTest.java` (INT-001~008)

수정 테스트 파일: 없다. INT-009의 기존 클래스는 수정하지 않고 실행만 한다. 실패하면 기대를 고치지 않고 원인을 보고한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

승인할 결정:
1. 경계: 생성 후 정확히 12시간이 되는 순간부터 만료로 본다(UNIT-002).
2. 만료 처리: 필터는 세션을 무효화하고 요청을 익명으로 계속 진행시킨다. 보호 경로는 기존 진입점이 401을 내고, `/admin/login`·`/admin/csrf`는 그대로 통과한다(INT-006). 이슈 본문의 "401로 응답한다"를 이 방식으로 구현한다.
3. 시각 원천: 필터는 `Clock` 빈을 쓴다. Spring Session의 생성 시각은 시스템 시각이므로 테스트에서 미래로 고정한 `Clock`과 섞으면 세션이 즉시 만료된다는 위험을 보고서에 남긴다.

- Reviewer: `tkv00`
- Decision: 승인. 위 세 결정을 그대로 확정한다.
- Approved at: `2026-10-09T20:41:07+09:00`
