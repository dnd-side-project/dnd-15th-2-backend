# Test Report: TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME

> Created at: `2026-10-09T21:02:18+09:00`
> GitHub Issue: `#342`
> Branch: `feat/gh-342-operator-session-lifetime`
> Commit: `63855a2b` (기준 commit, 이번 변경은 커밋 전 작업 트리에서 실행)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 승인된 계획의 UNIT-001~006, INT-001~009 전부. 미사용 만료 8시간 저장과 만료, 최대 수명 12시간의 경계·두 세션 체인 적용·재로그인, 앱 API 체인 비적용, 기본·dev·local 프로필의 쿠키와 프록시 헤더 설정, 기존 운영자·IP 한도 통합 테스트 회귀.
- Unverified scope: 실제 브라우저가 HTTP 응답의 `Secure` 쿠키를 버리는 동작(MockMvc는 속성만 확인한다), 같은 만료 세션으로 동시에 들어온 요청(계획에서 분석만 하기로 했다), TLS 프록시 뒤의 실제 배포 환경.
- Release recommendation: 병합 가능. 배포 전에 만든 세션은 30분 미사용 만료가 유지되고, 새로 로그인한 세션부터 8시간이 적용된다(7절).

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 21.0.11 (Temurin) |
| Spring Boot | 3.5.16 (Spring Session JDBC) |
| Database | Testcontainers PostGIS 16-3.5 이미지 |
| Test runner | JUnit 5, Gradle `test`·`integrationTest` |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| 구현 전 `integrationTest --tests OperatorSessionLifetimeIntegrationTest` | FAIL (기대한 실패) | 8건 중 5건 실패 | 18초 | INT-001 1800≠28800, INT-002 `Secure` false, INT-003·004 200≠401, INT-007 7시간 59분 미사용에서 401 |
| 구현 후 새 단위 테스트 3개 클래스 | PASS | 11 | 1분 이내 | JUnit XML |
| 구현 후 `OperatorSessionLifetimeIntegrationTest` | PASS | 8 | 15초 | JUnit XML |
| INT-009 기존 통합 5개 클래스 | PASS | 43 | 약 1분 | `OperatorLoginIntegrationTest` 10, `OperatorReportCaseIntegrationTest` 12, `ManualReviewPriorityIntegrationTest` 8, `SnapshotHealthMigrationIntegrationTest` 8, `AuthRateLimitIntegrationTest` 5 |
| `./harness test-run` — Unit | PASS | 1,324 (196 클래스, 실패·건너뜀 0) | 1분 2초 | Gradle `BUILD SUCCESSFUL` |
| `./harness test-run` — Integration | PASS | 831 (108 클래스, 실패·건너뜀 0) | 11분 45초 | Gradle `BUILD SUCCESSFUL` |

구현 전 실행에서 INT-005·006·008은 통과했다. 세 시나리오는 필터가 없을 때도 성립해야 하는 회귀 방지 검사다(12시간 전 세션 통과, 재로그인, 앱 API 체인의 세션 행 보존).

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `OperatorSessionAbsoluteTimeoutFilterTest.keepsSessionBeforeAbsoluteTimeout` | 12시간 - 1ms 유지 |
| UNIT-002 | PASS | `OperatorSessionAbsoluteTimeoutFilterTest.invalidatesSessionFromAbsoluteTimeout` | +0ms·+1ms 두 경우, 응답 미작성·다음 필터 호출 |
| UNIT-003 | PASS | `OperatorSessionAbsoluteTimeoutFilterTest.passesThroughWithoutCreatingSession` | 세션 생성 없음 |
| UNIT-004 | PASS | `OperatorSessionPropertiesTest` 3개 메서드 | null·0·음수 거부, 양수 보관 |
| UNIT-005 | PASS | `OperatorSessionDefaultsTest.defaultProfile` | `application.yml`만 바인딩 |
| UNIT-006 | PASS | `OperatorSessionDefaultsTest.httpProfiles` | dev·local 두 경우 |
| INT-001 | PASS | `OperatorSessionLifetimeIntegrationTest.loginStoresEightHourIdleTimeout` | `MAX_INACTIVE_INTERVAL` 28800 |
| INT-002 | PASS | `…sessionCookieIsSecureHttpOnlyLax` | `Secure`·`HttpOnly`·`SameSite=Lax` |
| INT-003 | PASS | `…backofficeRejectsSessionPastAbsoluteTimeout` | 401 `CMN-VAL-003`, 행 삭제 |
| INT-004 | PASS | `…operatorApiRejectsSessionPastAbsoluteTimeout` | 401 `CMN-VAL-003`, 행 삭제 |
| INT-005 | PASS | `…sessionBeforeAbsoluteTimeoutPasses` | 11시간 59분, 두 경로 200 |
| INT-006 | PASS | `…canLogInAgainWithExpiredSessionCookie` | 만료 쿠키로 로그인 200, 새 세션 |
| INT-007 | PASS | `…idleTimeoutIsEightHours` | 7시간 59분 200, 8시간 1분 401과 행 삭제 |
| INT-008 | PASS | `…appApiChainDoesNotTouchOperatorSession` | `/api/**` 401, 운영자 세션 행 유지 |
| INT-009 | PASS | 기존 5개 클래스, 수정 없음 | 43건 |

## 5. Failures and diagnostics

- 구현 후 첫 실행에서 INT-007이 실패했다(`updated` 기대 1, 실제 2). 테스트 문제였다. 같은 운영자가 두 번 로그인해 `SPRING_SESSION`에 행이 두 개 생겼는데, `PRINCIPAL_NAME`으로 찾는 UPDATE가 두 행을 모두 바꿨다. 행을 찾는 기준을 SESSION 쿠키를 Base64 디코딩한 `SESSION_ID`로 바꿨고, 이후 8건 모두 통과했다. production 코드는 바꾸지 않았다.

## 6. Potential issues

### Application code

- 필터의 현재 시각은 `Clock` 빈이고, 세션 생성 시각은 Spring Session이 시스템 시각으로 기록한다. 운영자 로그인과 시험용 `Clock`을 함께 쓰는 통합 테스트는 4개다. 그중 `OperatorReportCaseIntegrationTest`, `ManualReviewPriorityIntegrationTest`, `SnapshotHealthMigrationIntegrationTest`는 과거(2026-08)로 고정되어 있다. 경과 시간이 음수가 되므로 이 테스트들에서는 최대 수명 판정이 꺼진 채로 돈다. `AuthRateLimitIntegrationTest`는 현재 시각에서 시작해 최대 1시간만 앞당기는 `MutableClock`을 써서 12시간에 닿지 않는다. 계획 3절의 "고정 시각은 모두 2026-08로 과거다"는 이 클래스에 대해서는 틀린 조사였다. 결론(기존 테스트 영향 없음)은 같고 INT-009로 확인했다. 앞으로 `Clock`을 12시간 넘게 앞당긴 테스트가 운영자 로그인을 하면 모든 세션이 바로 만료된다. 운영에서는 둘 다 시스템 시각이라 차이가 없다.
- 최대 수명은 세션 생성 시각 기준이다. 로그인 경로가 기존 세션을 무효화하고 새로 만드는 동작(`OperatorLoginController`)에 기대고 있다. 이 동작이 바뀌어 로그인 전 세션을 재사용하면, 로그인 전에 만든 시각부터 12시간이 계산된다. 이 전제는 기존 `OperatorLoginIntegrationTest.loginRotatesSessionId`가 지킨다.

### Infrastructure and resource limits

- 배포 기본값(프로필 없음)은 `Secure` 쿠키다. TLS 없이 HTTP로 운영 환경을 띄우면 브라우저가 세션 쿠키를 저장하지 않아 운영자 로그인이 안 된다. 지금 HTTP로 노출하는 dev 테스트 서버(#229)는 `dev` 프로필에서 껐다.
- `server.forward-headers-strategy`는 설정하지 않았다. Spring Boot는 Kubernetes·Cloud Foundry·Heroku를 감지하면 이 값을 `native`로 켠다. 그 환경으로 옮기면 신뢰할 프록시 범위를 정하지 않은 채로 `X-Forwarded-For`를 받아들이게 된다. 지금 배포 대상(EC2)은 감지 대상이 아니다. 프록시나 LB를 둘 때의 설정은 `AUTH_DESIGN.md` 8.2절에 적었다.

### Database and migrations

- 스키마 변경은 없다.
- 최대 수명이 지난 세션 행은 요청이 들어와야 지워진다. 요청이 없으면 Spring Session의 만료 정리 작업(`spring.session.jdbc.cleanup-cron`, 기본 매분)이 마지막 사용 후 8시간이 지난 뒤 지운다. 그때까지 행은 남지만, 요청이 오면 필터가 무효화하므로 다시 쓸 수 없다.

### Concurrency and idempotency

- 같은 만료 세션으로 요청 두 개가 동시에 오면 둘 다 행 삭제를 시도한다. Spring Session JDBC의 삭제는 없는 행에 대해 0건으로 끝나고 응답은 둘 다 401일 것으로 본다. 이번에 테스트하지 않았다(계획 7절).

### Transactions and event ordering

- 세션 행의 읽기·쓰기·삭제는 Spring Session JDBC가 자체 transaction으로 처리한다. 필터의 `invalidate()`는 요청 안에서 바로 행을 지우고(INT-003·004), 애플리케이션 transaction과 섞이지 않는다.

### External APIs

- 외부 연동 변경이 없다.

### Failure recovery and reconciliation

- 배포 전에 만든 세션 행은 `MAX_INACTIVE_INTERVAL` 1800을 그대로 가지고 있어, 배포 뒤에도 30분 미사용 기준으로 끝난다. 최대 수명은 배포 즉시 모든 세션에 적용된다. 생성 후 12시간이 지난 기존 세션이 있으면 다음 요청에서 끝난다.
- 기능 commit을 revert하면 최대 수명 필터와 쿠키 설정은 바로 빠진다. 이미 저장된 세션 행의 28800은 남아 해당 세션은 8시간 미사용 기준으로 끝난다. DB 정리 작업은 필요 없다.

## 7. Regression and residual risk

- 운영자 세션이 30분 미사용에서 8시간 미사용·최대 12시간으로 늘어난다. 세션 쿠키가 유출됐을 때 쓸 수 있는 시간이 늘어나는 만큼, `HttpOnly`·`Secure`·`SameSite=Lax`와 즉시 해지(세션 행 삭제)에 기댄다.
- 남은 위험은 6절의 `Clock` 차이(테스트 전용)와 Kubernetes 계열 배포 시 프록시 헤더 자동 활성화다. 두 번째는 프록시·LB 도입 이슈에서 다룬다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-342-TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME.md`
- CI run: PR 생성 후 확인
- Related ADR: `docs/adr/0006-split-operator-and-device-authentication.md`, `docs/product/AUTH_DESIGN.md` 2절·5.2절·8.2절
- PR: 생성 전

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
