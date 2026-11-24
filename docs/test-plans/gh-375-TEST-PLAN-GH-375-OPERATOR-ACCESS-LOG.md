# Test Plan: TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG

> Created at: `2026-11-22T21:48:29+09:00`
> GitHub Issue: `#375`
> Status: Approved — 사람 승인 완료

## 1. Objective

운영자 로그인 결과 5종(성공, 자격증명 불일치, 잠금, 비활성 계정, 요청 제한), 로그아웃, 운영자 API 호출이 각각 `operator_access_log`에 한 행으로 남는지 검증한다. 각 행에는 운영자 id, 클라이언트 IP, method, 경로, HTTP 상태, 결과 코드, 발생 시각이 들어가야 하고, 비밀번호·세션 ID·CSRF 토큰·요청 본문·query string은 들어가지 않아야 한다.
이 검증이 실패하면 위치기반서비스 운영 배포 전 점검표 첨부 2 3항의 운영자 접근 기록(C3)을 채우지 못한다. 기록이 빠지면 누가 언제 어떤 운영자 API를 호출했는지 답할 수 없다. 기록에 비밀값이 들어가면 접근 기록 자체가 유출 경로가 된다. 기록을 추가하면서 로그인 판정이 바뀌면 계정 열거 방지(#72)와 실패 잠금이 깨진다.

## 2. Scope

### Included

- V34 migration: append-only `operator_access_log` 테이블, CHECK, 발생 시각 인덱스. FK는 두지 않는다(7절).
- `OperatorLoginService.login`의 네 결과 기록. 기록은 로그인 트랜잭션에 묶인다. 실패 기록은 실패 카운터와 함께 커밋되고, 기록 insert가 실패하면 판정 전체가 롤백된다.
- `/admin/login`에서 요청 제한으로 거절된 요청(`AUT-APP-007`) 기록.
- `/admin/logout` 기록.
- `/admin/**`, `/api/v1/operator/**` 세션 체인의 운영자 API 호출 기록 필터. `/admin/login`, `/admin/logout`, `/admin/csrf`는 제외한다.
- 클라이언트 주소 추출. `remoteAddr`만 쓰고 `X-Forwarded-For`는 무시한다.
- 기록 행에 민감정보가 없음을 검사한다.
- append-only 계약을 검사한다. 저장소 인터페이스와 production 소스에 UPDATE·DELETE 경로가 없어야 한다.
- `FlywayMigrationIntegrationTest`의 최신 버전과 카탈로그 총계 갱신, ERD·DBML·schema manifest 반영.
- 기존 운영자 로그인·세션·요청 제한·운영자 API 통합 테스트의 회귀.

### Excluded

- 보존 기간이 지난 행의 파기와 보존 기간 결정.
- DB 권한·트리거로 UPDATE·DELETE를 막는 위·변조 방지, RDS 감사 로그(운영 인프라 I4).
- 프록시 헤더 신뢰 설정(`server.forward-headers-strategy`, C7).
- 앱 사용자 API(`/api/**` JWT 체인)의 접근 기록.
- 접근 기록 조회 API·화면.
- 비밀번호 변경(#376)과 OTP(#377) 이벤트. 이 두 이슈가 같은 테이블에 이벤트 유형을 더한다.
- CSRF 토큰 없이 들어온 `/admin/login` 요청. `CsrfFilter`가 컨트롤러에 닿기 전에 403으로 끝낸다. 4절 위험에 기록한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #375 범위 | 컬럼(이벤트 유형, 운영자 user id, 클라이언트 IP, method, 경로, HTTP 상태·결과 코드, 발생 시각). 로그인 4결과는 서비스에서 기록하고 실패 기록은 실패 카운터와 같은 트랜잭션에 둔다. 요청 제한 거절·로그아웃 기록. 세션 체인 필터와 3개 경로 제외. IP는 `ClientAddressKey`와 같은 값. ERD·DBML 반영 |
| GitHub Issue #375 완료 조건 | 5결과가 각각 한 행이다. 미존재 login_id 행은 운영자 id가 NULL이고 기존 열거 방지 테스트가 통과한다. 로그아웃·API 호출이 기록된다. 민감정보가 없다(행 전체 검사). 수정·삭제 경로가 없다. `pr-ready`가 통과한다 |
| `OperatorLoginService` | `@Transactional(noRollbackFor = AuthException.class)`. 미존재 id는 더미 해시 검증 후 `AUT-APP-001`, 잠금은 비밀번호 검증 전에 `AUT-APP-002`, 비밀번호가 맞은 뒤 계정이 비활성이면 `AUT-APP-003` |
| `AuthErrorCode` | `AUT-APP-001` 401, `AUT-APP-002` 423, `AUT-APP-003` 403, `AUT-APP-007` 429 |
| `OperatorLoginController`, `AuthRequestRateLimiter` | 요청 제한 검사가 서비스 호출보다 먼저다. 거절된 요청은 계정을 조회하지 않는다. 로그아웃은 세션을 무효화한다 |
| `SecurityConfiguration` | 세션 체인 두 개(Order 1·2). `OperatorSessionAbsoluteTimeoutFilter`는 빈으로 등록하지 않고 체인에 직접 추가한다. permitAll은 `/admin/csrf` GET과 `/admin/login` POST뿐이다 |
| `ClientAddressKey` | `remoteAddr`만 쓰고 IPv6는 /64 접두사로 묶는다 |
| `HttpRequestLoggingFilter` | chain이 예외를 던졌는데 상태가 400 미만이면 500으로 기록한다 |
| V20, `OperatorActionAuditRecorder`, `JdbcOperatorActionAuditRepository` | append-only 원장의 선례다. FK 없음, CHECK, 저장소는 save와 조회만 둔다. 기록은 `Propagation.MANDATORY`, 구현은 JDBC다 |
| `docs/adr/0002-jpa-jdbc-boundary.md` | append-only 삽입은 JPA entity 없이 JDBC로 한다 |
| test-policy, `src/test/AGENTS.md`, `src/integrationTest/AGENTS.md` | JUnit 5, `@DisplayName`, 클래스 헤더의 생성 시각·원본 시나리오, 단위·통합 분리, 보고서 템플릿 |
| #350 테스트 계획 | 설정이 같은 통합 테스트 클래스끼리 컨텍스트를 공유하고, 클래스 시작 전에 public 테이블을 TRUNCATE한다 |

조사 결과:

- `PostgisContainerIntegrationTestSupport`는 `pg_tables`의 public 테이블을 동적으로 TRUNCATE한다. 새 테이블도 따로 등록하지 않아도 클래스 사이에서 비워진다.
- `FlywayMigrationIntegrationTest`에는 `LATEST_MIGRATION_VERSION = 33`과 CHECK 134개·인덱스 71개 총계가 고정돼 있다. V34를 추가하면 이 테스트도 함께 고쳐야 한다.
- `OperatorLoginServiceTest`는 fake 저장소로 서비스를 직접 생성한다. 생성자나 `login` 시그니처가 바뀌면 이 테스트도 고쳐야 한다.
- `OperatorLoginIntegrationTest`의 `@BeforeEach`는 `DELETE FROM user_account`를 실행한다. 접근 기록에 `user_account` FK를 두면 이 정리가 실패한다.
- 운영자 로그인을 하는 기존 통합 테스트 클래스는 9개다. 변경 후에는 이 클래스들도 접근 기록 행을 남긴다.
  - `OperatorLoginIntegrationTest`
  - `OperatorReportCaseIntegrationTest`
  - `ManualReviewPriorityIntegrationTest`
  - `SnapshotHealthMigrationIntegrationTest`
  - `AuthRateLimitIntegrationTest`
  - `OperatorSessionLifetimeIntegrationTest`
  - `AppealCaseIntegrationTest`
  - `FilterReleaseRegistryIntegrationTest`
  - `OpenApiSpecificationIntegrationTest`
- `@WebMvcTest` 슬라이스 테스트는 없다. 새 필터의 의존성 때문에 깨질 슬라이스 컨텍스트가 없다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 로그인 실패 기록이 예외와 함께 롤백된다 | 실패한 접근이 남지 않는다 | 중간 | P0 | 비밀번호 불일치 후 행 1건과 `failed_attempt_count` 1이 함께 남는다 |
| 미존재 login_id 분기의 응답 본문이 달라진다 | 계정을 열거할 수 있다 | 낮음 | P0 | 기존 열거 방지 테스트가 수정 없이 통과한다. 미존재 id 행의 운영자 id가 NULL이다 |
| 필터가 `/admin/login`까지 기록한다 | 로그인 1회에 행이 2건 생겨 이벤트 해석이 꼬인다 | 중간 | P0 | 로그인 1회 후 행이 정확히 1건이다 |
| 필터가 한쪽 체인에만 연결된다 | `/api/v1/operator/**` 또는 `/admin/**` 호출이 빠진다 | 중간 | P0 | 두 경로 각각 기록된다 |
| query string·요청 본문·쿠키·CSRF 헤더가 행에 들어간다 | 접근 기록이 비밀값 유출 경로가 된다 | 중간 | P0 | 행 전체를 JSON 문자열로 검사한다 |
| 필터가 chain 예외를 삼키거나 상태를 200으로 기록한다 | 오류 응답이 성공으로 남는다 | 중간 | P0 | chain 예외 시 500으로 기록하고 예외를 다시 던진다 |
| 로그인 기록 실패를 무시해 기록 없는 세션이 생긴다 | 기록 없는 운영자 접속이 생긴다 | 낮음 | P0 | 기록 실패 시 500이 나고, 세션 쿠키가 없고, 카운터·`last_login_at`이 롤백된다(D4) |
| API 호출 기록이 실패하면 이미 커밋된 작업의 응답이 500으로 바뀐다 | 운영자가 성공한 판정을 실패로 알고 다시 실행한다 | 중간 | P1 | 기록 실패 시 원래 상태와 본문이 유지되고 error 로그가 1줄 남는다(D4) |
| `X-Forwarded-For` 값을 IP로 저장한다 | 위조된 IP가 접근 기록에 남는다 | 낮음 | P1 | XFF를 넣어도 `remoteAddr`가 저장된다 |
| IPv6를 /64 키로 저장한다(`ClientAddressKey` 그대로 사용) | 접속지 IP가 회선 단위로 뭉개진다 | D3 B를 고르면 확실 | P1 | D3 결정에 맞는 단위 테스트 |
| 익명 요청까지 기록한다 | 인증 없는 스캐너가 행을 무제한 늘린다 | D1 B를 고르면 높음 | P1 | 세션 없이 보낸 요청이 401을 받은 뒤 행이 0건이다(D1) |
| 요청 제한 거절마다 행이 1건 생긴다 | 한도를 넘긴 대입 시도가 insert를 계속 만든다 | 중간 | P2 | 이슈 범위대로 거절마다 남긴다. 보고서의 잠재 문제에 기록한다 |
| `user_account` FK를 둔다 | 기존 테스트 정리와 계정 삭제가 막히거나, cascade로 기록이 지워진다 | 중간 | P1 | V34 스키마 검사에서 FK가 없다 |
| CSRF 토큰 없는 `/admin/login` 시도가 기록되지 않는다 | 일부 로그인 시도가 남지 않는다 | 확실 | P2 | 이번 범위 밖이다. 보고서에 기록한다 |
| 저장소에 update·delete 메서드나 SQL이 생긴다 | append-only 계약을 어긴다 | 낮음 | P1 | 반사·소스 검사 단위 테스트 |
| V34 추가로 Flyway 카탈로그 테스트가 실패한다 | CI가 실패한다 | 확실 | P0 | 최신 버전 34와 총계를 갱신하고 V34 스키마 테스트를 추가한다 |
| 독립 기록 메서드가 트랜잭션 안쪽에서 예외를 잡는다 | rollback-only 표시 때문에 커밋 시점에 `UnexpectedRollbackException`이 나서 fail-open이 깨진다 | 중간 | P1 | 저장소 예외를 주입해도 응답이 유지된다(INT-017) |
| 기존 실패 카운터의 동시 갱신 lost update(기존 동작) | 잠금 전 실패 횟수가 5를 넘을 수 있다 | 낮음 | P2 | 이번에는 테스트하지 않는다. 접근 기록 행 수가 실제 시도 수를 보여 준다는 점과 함께 보고서에 기록한다 |

## 5. Unit scenarios

담당자는 9절의 실행자다. A는 persistence-executor, B는 auth-flow-executor다. 클래스 이름은 9절을 따른다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-001 | 시드 자격증명, `Clock.fixed`, fake recorder | 올바른 비밀번호로 `login` | `LOGIN_SUCCEEDED` 1건. 운영자 id = 시드 user id, 상태 200, 결과 코드 NULL, 발생 시각 = 고정 시각, 클라이언트 주소·method·경로 = 컨트롤러가 전달한 값 | P0 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-002 | 자격증명 없음 | 미존재 login_id로 `login` | `AUT-APP-001` 예외. `LOGIN_FAILED` 1건, 운영자 id NULL, 상태 401, 결과 코드 `AUT-APP-001`. 더미 해시 검증 1회는 그대로 수행 | P0 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-003 | 시드 자격증명 | 틀린 비밀번호로 `login` | `LOGIN_FAILED` 1건, 운영자 id 있음, 401, `AUT-APP-001`. 실패 카운터 1 증가. 예외를 던지기 전에 기록 호출 | P0 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-004 | 잠긴 자격증명 | 올바른 비밀번호로 `login` | `LOGIN_FAILED` 1건, 운영자 id 있음, 423, `AUT-APP-002`. 비밀번호 검증 호출 없음(기존 순서 유지) | P0 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-005 | 비밀번호는 맞고 계정 상태가 `BLOCKED` | `login` | `LOGIN_FAILED` 1건, 운영자 id 있음, 403, `AUT-APP-003`. `recordSuccess` 미호출 | P0 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-006 | recorder가 `DataAccessException`을 던진다 | 성공 분기와 비밀번호 불일치 분기 각각 `login` | 같은 예외가 그대로 전파되고 `AuthException`으로 바뀌지 않는다 | P0 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-007 | `ROLE_OPERATOR`로 인증된 `SecurityContext`(principal `"42"`), `GET /api/v1/operator/report-cases?queue=URGENT`, 다음 필터가 200을 쓴다 | 필터 실행 | `API_CALL` 1건. 운영자 id 42, `GET`, 경로 `/api/v1/operator/report-cases`(query 없음), 200, 발생 시각 = `Clock` | P0 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-008 | 인증된 context, 다음 필터가 404 또는 403을 쓰고 정상 반환한다 | 필터 실행 | 쓰인 상태 그대로 기록 | P0 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-009 | 인증된 context, 다음 필터가 `RuntimeException`·`ServletException`·`IOException`을 던진다 | 필터 실행 | 상태가 400 미만이면 500으로, 이미 4xx면 그 값으로 기록한다. 같은 예외를 다시 던진다(`HttpRequestLoggingFilter`와 같은 규칙) | P0 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-010 | 인증된 context | `POST /admin/login`, `POST /admin/logout`, `GET /admin/csrf` 각각 필터 실행 | 기록 0건, 다음 필터는 1회 호출 | P0 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-011 | 빈 context 또는 `AnonymousAuthenticationToken` | `GET /api/v1/operator/report-cases` 필터 실행 | 기록 0건(D1 A) | P1 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-012 | 인증된 context, recorder가 예외를 던진다 | 필터 실행 | 필터는 예외를 던지지 않고 응답 상태를 그대로 둔다. error 로그 1줄(예외 클래스명과 경로만 담고 세션 ID는 담지 않는다)(D4) | P1 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-013 | 인증된 context, 600자 경로 | 필터 실행 | 경로를 512자로 잘라 기록(D2 A) | P2 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-014 | `remoteAddr` `192.0.2.10`과 `X-Forwarded-For` `198.51.100.7` / `2001:db8::1` / null·공백 | 클라이언트 주소 추출 | `192.0.2.10`. IPv6는 D3 A면 `InetAddress.getHostAddress()`로 정규화한 전체 주소, D3 B면 `20010db800000000/64`. null·공백은 `unknown` | P1 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-015 | fake 저장소(정상 / 예외) | recorder의 독립 기록 메서드 호출 | 정상이면 append 1회. 예외면 예외를 전파하지 않고 error 로그 1줄(D4) | P1 | B |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-016 | 잘못된 값: 이벤트 유형 null, 상태 99·600, 빈 method·경로, `LOGIN_SUCCEEDED`·`LOGOUT`·`API_CALL`인데 운영자 id null, 운영자 id 0 이하 | `OperatorAccessLogEntry` 생성 | null은 `NullPointerException`, 나머지는 `IllegalArgumentException`. 정상값은 그대로 보관 | P1 | A |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-017 | production 클래스와 `src/main` 소스 | `OperatorAccessLogRepository` 메서드를 반사로 조회하고, `src/main`의 `.java`·`.sql`을 검사 | 저장소 메서드 이름에 update·delete·remove·modify가 없다. 소스에 `UPDATE operator_access_log`, `DELETE FROM operator_access_log`, `TRUNCATE operator_access_log`가 없다(대소문자 무시, 공백 정규화) | P1 | A |

## 6. Integration scenarios

테스트 클래스:

- X: 신규 `OperatorAccessLogIntegrationTest`. `@ActiveProfiles({"test", "account-persistence"})`이고 `@Import`를 두지 않아 `OperatorLoginIntegrationTest`와 컨텍스트를 공유한다.
- F: 신규 `OperatorAccessLogFailureIntegrationTest`. `OperatorAccessLogRecorder`에 `@MockitoSpyBean`을 두므로 컨텍스트를 따로 쓴다.
- R: 기존 `AuthRateLimitIntegrationTest`. 테스트 하나를 추가한다.
- M: 기존 `FlywayMigrationIntegrationTest`. 기존 검사를 갱신하고 테스트 하나를 추가한다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-001 | M: Flyway + PostgreSQL | 빈 DB에 전체 migration 적용 | V34 카탈로그 조회와 잘못된 행 insert | 최신 버전 34. 컬럼 9개와 CHECK·인덱스 이름이 있다. `operator_user_id`에 FK가 없다. 정의되지 않은 `event_type`, `LOGIN_SUCCEEDED`인데 운영자 id NULL, `http_status` 600은 `DataIntegrityViolationException`. CHECK·인덱스 총계는 V34가 더한 수만큼 올리고 주석에 근거를 남긴다 | 기존 lifecycle |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-002 | X: MockMvc + Security + 서비스 + JDBC | 시드 운영자, CSRF 발급 | `remoteAddr` `192.0.2.10`, `X-Forwarded-For` `198.51.100.7`로 로그인 성공 | 행 1건: `LOGIN_SUCCEEDED`, 운영자 id = 응답 `userId`, 주소 `192.0.2.10`, `POST`, `/admin/login`, 200, 결과 코드 NULL. `occurred_at`이 요청 전후 시각 사이에 있다 | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-003 | X | 시드 운영자 | 미존재 login_id로 로그인 | 401. 행 1건: `LOGIN_FAILED`, 운영자 id NULL, 401, `AUT-APP-001` | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-004 | X | 시드 운영자 | 틀린 비밀번호로 로그인 | 401. 행 1건: 운영자 id = 시드 id, 401, `AUT-APP-001`. 같은 시점에 `failed_attempt_count` = 1(같은 커밋) | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-005 | X | 시드 운영자 | 틀린 비밀번호 5회 후 올바른 비밀번호 | 423. 행 6건. 마지막 행은 423·`AUT-APP-002`. 6건 모두 운영자 id 있음 | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-006 | X | 시드 운영자, `user_account.status` = `BLOCKED` | 올바른 비밀번호로 로그인 | 403 `AUT-APP-003`, `SESSION` 쿠키 없음. 행 1건: 운영자 id 있음, 403, `AUT-APP-003` | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-007 | R: 낮춘 한도(2회) + `MutableClock` | 시드 운영자, 이 테스트 전용 문서용 IP | 같은 IP로 로그인 3회 | 3회째 429 `AUT-APP-007`. 거절된 요청은 행 1건: `LOGIN_FAILED`, 운영자 id NULL, 그 IP, 429, `AUT-APP-007`. 한도 안 요청 2건도 각각 행 1건. 기존 INT-004의 카운터 검사는 그대로 통과 | 기존 lifecycle |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-008 | X | 로그인 세션 | `POST /admin/logout` 후 같은 쿠키로 `GET /api/v1/operator/report-cases` | 로그아웃은 200. `LOGOUT` 행 1건: 운영자 id, `/admin/logout`, 200. 뒤 요청은 401이고 행이 더 생기지 않는다(D1 A) | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-009 | X: 두 세션 체인 + 필터 | 로그인 세션 | `GET /api/v1/operator/report-cases`, `GET /admin/filtering/manual-review-cases?agingThresholdSeconds=60` | 둘 다 200. `API_CALL` 행 2건, 운영자 id 있음, 경로는 query 없이 저장(D2 A) | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-010 | X | 로그인 세션, CSRF | 존재하지 않는 사건 id로 `POST /api/v1/operator/report-cases/{caseId}/review`, 본문에 표식 문자열 | 4xx(실제 응답 상태). `API_CALL` 행 1건, 상태 = 응답 상태, 경로에 사건 id 포함 | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-011 | X | 로그인 세션, CSRF 헤더 없음 | INT-010과 같은 POST | 403. `API_CALL` 행 1건, 운영자 id 있음, 403(D1 A: 인증된 세션이 보안 필터에서 거절된 경우도 남긴다) | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-012 | X | 시드 운영자 | `GET /admin/csrf`, 로그인 1회, 로그아웃 1회 | 행은 `LOGIN_SUCCEEDED` 1건과 `LOGOUT` 1건뿐이다. `API_CALL` 행이 없다 | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-013 | X: 세션 체인과 앱 API 체인 | 세션 없음 | `GET /api/v1/operator/report-cases`, `GET /admin/filtering/manual-review-cases?agingThresholdSeconds=60`, `GET /api/v1/anything` | 셋 다 401, 행 0건(D1 A) | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-014 | X | INT-002·009·010 흐름을 한 테스트에서 실행 | `SELECT row_to_json(l)::text FROM operator_access_log l` | 어떤 행에도 비밀번호, `SESSION` 쿠키 값, Base64를 푼 세션 ID, CSRF 토큰, 본문 표식, `agingThresholdSeconds`, `?`가 없다 | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-015 | X: 트랜잭션 프록시 | 트랜잭션 없음 | recorder의 로그인 기록 메서드를 직접 호출 | `IllegalTransactionStateException`, 행 0건(`MANDATORY`, `OperatorActionAuditIntegrationTest` INT-005와 같은 계약) | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-016 | F: spy recorder | 로그인 기록 메서드가 `DataAccessResourceFailureException`을 던지게 한다 | 틀린 비밀번호 로그인과 올바른 비밀번호 로그인 | 둘 다 500. 틀린 비밀번호 쪽은 `failed_attempt_count` 0 그대로, 행 0건. 올바른 비밀번호 쪽은 `SESSION` 쿠키 없음, `last_login_at` 그대로(D4) | spy reset, `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-017 | F | 정상 로그인 뒤, 독립 기록 메서드가 예외를 던지게 한다 | `GET /api/v1/operator/report-cases`, 이어서 `POST /admin/logout`, 같은 쿠키로 다시 GET | GET은 200, 본문 그대로. 로그아웃은 200. 다시 보낸 GET은 401(세션 무효화는 그대로). `UnexpectedRollbackException` 없음(D4) | spy reset, `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-018 | X | 로그인 세션 | 같은 세션으로 `GET /api/v1/operator/report-cases` 10건 동시 실행 | 200 10건, `API_CALL` 행 10건 | `@BeforeEach` 정리 |
| TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-019 | 기존 운영자·인증 통합 테스트 | 변경 없음 | 3절의 9개 클래스 중 R을 뺀 8개를 실행 | 수정 없이 전부 통과. 열거 방지(`failedLoginsAreIndistinguishable`) 포함. `docs/api/openapi.json` diff 없음 | 기존 lifecycle |

INT-018은 P2다. INT-011·013·015·017은 P1이다. 나머지는 P0이다.

## 7. Cross-cutting scenarios

### Database and transactions

- 로그인 결과 기록은 `Propagation.MANDATORY`로 `login` 트랜잭션에 참여한다. `AuthException`은 `noRollbackFor` 대상이라 실패 기록과 실패 카운터가 함께 커밋된다(INT-004). 반면 기록 insert의 `DataAccessException`은 롤백 대상이다. 이때 카운터 증가와 성공 갱신이 함께 사라지고 500이 난다(INT-016, D4).
- 요청 제한 거절, 로그아웃, API 호출은 호출자 트랜잭션 밖에서 기록한다. 필터는 컨트롤러 트랜잭션이 끝난 뒤에 기록한다. recorder의 독립 기록 메서드가 자체 트랜잭션으로 insert하고, 예외는 트랜잭션 경계 바깥에서 잡아 로그로 남긴다. 안쪽에서 잡으면 rollback-only 표시 때문에 커밋 시점에 `UnexpectedRollbackException`이 날 수 있다(INT-017).
- FK를 두지 않는다. 운영자 계정이 삭제되거나 탈퇴해도 기록이 남는다. 기존 테스트의 `DELETE FROM user_account`도 막히지 않는다. V20과 같은 선택이다.
- `occurred_at`은 DB 기본값(`clock_timestamp()`)이 아니라 `Clock` 빈 값으로 넣는다. 로그인 판정 시각(`now`)과 같은 값이 되고, 단위 테스트에서 고정 시각으로 검증할 수 있다.
- 인덱스는 `occurred_at` 하나를 둔다. 보존 기간 파기와 기간 조회에 쓴다. 운영자별 조회 인덱스는 조회 경로가 생길 때 추가한다.

### Concurrency and idempotency

- 행마다 독립된 insert이고 유일 제약이 없어 충돌이 없다. INT-018(P2)로 누락이 없는지 확인한다.
- 같은 요청을 다시 보내면 행이 다시 남는다. 접근 기록은 시도마다 남는 것이 맞으므로 멱등성 키를 두지 않는다.
- 기존 실패 카운터의 read-modify-write lost update는 범위 밖이다. 보고서에 기록한다.

### External APIs

- 외부 공급자 연동은 없다.

### Failure recovery and reconciliation

- 로그인은 fail-closed다. 기록이 없는 세션은 만들지 않는다. 요청 제한 거절, 로그아웃, API 호출은 fail-open이다. 이미 정해진 결과를 되돌릴 수 없기 때문이다. 기록이 누락되면 error 로그와 `HTTP_REQUEST` 로그(route·status)로 사후 대조한다.
- 배포하면 V34가 먼저 적용되고 같은 이미지가 기록을 시작한다. revert하면 Flyway가 forward-only라 테이블과 행은 남고 코드만 기록을 멈춘다. 데이터 정리는 필요 없다. 원장 행은 지우지 않는다(AGENTS.md 6절).
- 배포 전에 발급된 세션도 배포 후 API 호출부터 기록된다. 기록이 세션 속성에 의존하지 않기 때문이다.

## 8. Test data and isolation

- Fixtures: `OperatorLoginIntegrationTest`와 같은 시드를 쓴다(`region_code` `TEST-COUNTRY`, `OperatorSeedService.seedIfAbsent`, 예시 비밀번호). CSRF는 `/admin/csrf` 실제 발급 흐름으로 받고 `csrf()` post-processor는 쓰지 않는다.
- Database isolation: 클래스 시작 전 TRUNCATE(#350)에 더해, 클래스 안에서는 `@BeforeEach`에서 `operator_access_log`, `operator_credential`, `user_account`의 해당 행을 지운다. 검사는 이 클래스의 시드 운영자 id와 IP로 한정한다.
- IP: `remoteAddr`는 문서용 대역 `192.0.2.0/24`, 위조 `X-Forwarded-For`는 `198.51.100.0/24`를 쓴다. 실제 IP는 기록하지 않는다.
- Clock/randomness: 단위 테스트는 `Clock.fixed`를 쓴다. X는 컨텍스트 공유를 위해 고정 `Clock`을 `@Import`하지 않고, 요청 전후 시각 범위로 검사한다. R은 기존 `MutableClock`을 쓴다.
- External API doubles: 없다. F만 recorder를 `@MockitoSpyBean`으로 바꾼다.
- Cleanup: 기존 `PostgisContainerIntegrationTestSupport` lifecycle을 따른다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다. 운영자 비밀번호는 기존 테스트처럼 예시 문자열을 쓴다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 부모 오케스트레이터 | `TASK.md`, 이 계획 | 요구사항·파일·시나리오 계약 | Issue·branch·TASK 일치, `git diff --check` |
| 2 | persistence-executor (A, 승인 후 호출) | 아래 A 파일 | UNIT-016~017, INT-001 | 실패 테스트 확인 → 구현 → `./gradlew test --tests '*OperatorAccessLog*'`, `./gradlew integrationTest --tests '*FlywayMigrationIntegrationTest'` |
| 3 | auth-flow-executor (B, A 완료 후 호출) | 아래 B 파일, 테스트 보고서 | UNIT-001~015, INT-002~019 | 실패 테스트 확인 → 구현 → `./gradlew test`, `./gradlew integrationTest`, `./harness check` |
| 4 | auth-verifier (구현 후 독립 호출) | 소스 수정 권한 없음 | 전체 | 실제 diff, JUnit 결과, 범위 밖 파일 불변, `./harness pr-ready --project-tests`, `git diff --check` |

두 실행자는 작업 전에 `src/main/java/AGENTS.md`와 `docs/harness/JAVA_CONVENTIONS.md`를 읽는다. 아래 이름은 제안이며, 구현 중 이름이 바뀌면 이 표를 먼저 고친다.

A 소유 파일:

- 신규 production: `src/main/resources/db/migration/V34__add_operator_access_log.sql`
- 신규 production: `src/main/java/com/dnd/qello/auth/domain/OperatorAccessEvent.java`
- 신규 production: `src/main/java/com/dnd/qello/auth/domain/OperatorAccessLogEntry.java`
- 신규 production: `src/main/java/com/dnd/qello/auth/repository/OperatorAccessLogRepository.java`
- 신규 production: `src/main/java/com/dnd/qello/auth/repository/jdbc/JdbcOperatorAccessLogRepository.java`
- 신규 production: `src/main/java/com/dnd/qello/auth/repository/jdbc/package-info.java`
- 문서: `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md`
- 문서: `docs/product/data-model/direction_communication.dbml`
- 문서: `docs/product/data-model/schema-manifest.md`
- 신규 테스트: `src/test/java/com/dnd/qello/auth/domain/OperatorAccessLogEntryTest.java` (UNIT-016)
- 신규 테스트: `src/test/java/com/dnd/qello/auth/repository/OperatorAccessLogAppendOnlyTest.java` (UNIT-017)
- 수정 테스트: `src/integrationTest/java/com/dnd/qello/FlywayMigrationIntegrationTest.java` (INT-001)

B 소유 파일:

- 신규 production: `src/main/java/com/dnd/qello/auth/service/OperatorAccessLogRecorder.java`
- 신규 production: `src/main/java/com/dnd/qello/auth/web/OperatorAccessLogFilter.java` (빈으로 등록하지 않고 `SecurityConfiguration`에서 생성한다)
- 신규 production: `src/main/java/com/dnd/qello/common/web/ClientAddress.java` (D3 A일 때만)
- 수정 production: `OperatorLoginService.java`, `OperatorLoginController.java`, `SecurityConfiguration.java`
- 수정 production: 거절 기록을 리미터에 둘 경우 `AuthRequestRateLimiter.java`
- 수정 테스트: `src/test/java/com/dnd/qello/auth/service/OperatorLoginServiceTest.java` (UNIT-001~006)
- 신규 테스트: `src/test/java/com/dnd/qello/auth/web/OperatorAccessLogFilterTest.java` (UNIT-007~013)
- 신규 테스트: `src/test/java/com/dnd/qello/common/web/ClientAddressTest.java` (UNIT-014)
- 신규 테스트: `src/test/java/com/dnd/qello/auth/service/OperatorAccessLogRecorderTest.java` (UNIT-015)
- 신규 테스트: `src/integrationTest/java/com/dnd/qello/OperatorAccessLogIntegrationTest.java` (INT-002~006, 008~015, 018)
- 신규 테스트: `src/integrationTest/java/com/dnd/qello/OperatorAccessLogFailureIntegrationTest.java` (INT-016~017)
- 수정 테스트: `src/integrationTest/java/com/dnd/qello/AuthRateLimitIntegrationTest.java` (INT-007)
- 보고서: `docs/reports/tests/gh-375-TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG.md` (`templates/test-report.md` 기반)

실행 중 바뀐 소유 파일(2026-11-22, 실행자 기록):

- A 추가 production: `src/main/java/com/dnd/qello/auth/domain/OperatorAccessRequest.java`. 주소·method·경로를 담고 컬럼 길이로 자른다. 로그인 서비스와 필터가 같은 값을 쓰도록 분리했다.
- A 추가 수정 테스트: `src/test/java/com/dnd/qello/FlywayMigrationContractTest.java`. migration 파일 목록과 "마지막 migration" 검사가 V33으로 고정돼 있어 V34를 추가하면 실패한다. V33 테스트에서 "마지막" 검사를 빼고 V34 계약 테스트로 옮겼다.
- A 추가 수정 테스트: `src/integrationTest/java/com/dnd/qello/AccountPersistenceIntegrationTest.java`(public 테이블 수 54 → 55), `src/integrationTest/java/com/dnd/qello/NotificationPreferenceMigrationIntegrationTest.java`(V24 이후 적용 migration 수 9 → 10). 첫 전체 실행에서 V34 때문에 실패했다. #337도 같은 두 값을 갱신했다.
- B 추가 production: `src/main/java/com/dnd/qello/auth/web/OperatorAccessContext.java`. 컨트롤러와 필터가 함께 쓰는 요청 정보·현재 운영자 id 추출이다.
- B 미사용: `AuthRequestRateLimiter.java`는 고치지 않았다. 거절 기록은 컨트롤러에 두었다.
- B 추가 수정: `config/java-conventions/baseline.json`에서 `JAVA-CONV-0040`(LEGACY CTOR-001, `OperatorLoginService`)을 지웠다. 이 항목은 origin/main 기준 해시로 검사되므로, 지우지 않으면 머지 뒤 main에서 `QELLO-JAVA-BASELINE-006`이 난다(#341 → #344와 같은 경우). 생성자가 더미 해시를 계산하므로 CTOR-001 대상이 아니다. 독립 검증자가 찾았다.
- 필터 제외 기준: 경로만이 아니라 method와 디코딩한 경로의 쌍(`POST /admin/login`, `POST /admin/logout`, `GET /admin/csrf`)이다. UNIT-010에 다른 method·인코딩 경로 사례를 더했다.
- UNIT-012: 계획대로 recorder 자체가 예외를 던지는 경우를 검증한다. 필터가 recorder 호출을 한 번 더 감싸고, chain 예외가 있으면 suppressed로 붙인다. 저장소 실패 사례는 따로 남겼다.
- INT-014: 응답의 마스킹된 CSRF token에 더해 `XSRF-TOKEN` 쿠키 원문도 검사한다.
- F의 spy 대상: recorder가 아니라 `OperatorAccessLogRepository`다. recorder의 실제 전파(`MANDATORY`·`NOT_SUPPORTED`)와 예외 처리를 거쳐야 INT-016·017이 의도한 동작을 검증한다.
- `OperatorAccessLogRecorder`는 `OperatorActionAuditRecorder`와 같이 `@Component`다.
- 문서: 세 스키마 문서는 V1~V28 스냅샷 기준 집계라 집계 수치를 바꾸지 않고 "스냅샷 이후 추가"로 표시했다. manifest의 DBML·ERD 현재 체크섬을 갱신하고 이전 값을 이력 표에 옮겼다.

INT-019의 기존 클래스는 수정하지 않고 실행만 한다. 실패하면 기대값을 고치지 않고 원인을 보고한다. 오류 코드와 API 계약은 바뀌지 않으므로 `docs/error-codes.md`와 `docs/api/openapi.json`은 수정하지 않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

가정(ASSUMED, 승인 시 함께 확정):

- 이벤트 유형 이름은 `LOGIN_SUCCEEDED`, `LOGIN_FAILED`, `LOGOUT`, `API_CALL`이다. 실패의 결과 코드는 오류 코드(`AUT-APP-00x`)이고, 성공·로그아웃·API 호출의 결과 코드는 NULL이다.
- `occurred_at`은 `Clock` 빈 값이다.

승인할 결정:

1. D1 API 호출 기록 대상. 권장 A: 인증된 운영자 요청만 남긴다. 인증된 세션이 보안 필터에서 거절된 경우(CSRF 403 등)는 포함하고, 익명 401은 남기지 않는다. 인증 없는 스캐너가 행을 무제한 늘리지 못하게 하고, 로그인 시도는 로그인 기록으로 따로 남는다. 필터는 `SecurityContextHolderFilter` 뒤, `CsrfFilter` 앞에 둔다. 대안 B: 익명 요청도 운영자 id NULL로 남긴다.
2. D2 경로 형식. 권장 A: query를 뺀 실제 요청 URI를 저장한다(예: `/api/v1/operator/report-cases/17/review`). 512자를 넘으면 잘라 저장한다. route 패턴만 남기면 어떤 사건을 다뤘는지 알 수 없다. 대안 B: route 패턴(`/api/v1/operator/report-cases/{caseId}/review`).
3. D3 IP 형식. 권장 A: `ClientAddressKey`와 같은 원천(`remoteAddr`, XFF 무시)을 쓰되 전체 주소를 저장한다. `ClientAddressKey`는 IPv6를 /64로 묶는 요청 한도용 키라서 접속지 기록에는 맞지 않는다. 이 안을 고르면 이슈 #375 본문의 "`ClientAddressKey`와 같은 값"을 "같은 원천"으로 고친다. 대안 B: 이슈 문구대로 `ClientAddressKey.of` 값을 저장한다.
4. D4 기록 실패 시 동작. 권장: 로그인 결과 기록은 fail-closed로 한다(500, 세션 없음, 카운터 롤백). 요청 제한 거절·로그아웃·API 호출 기록은 fail-open으로 한다(응답 유지, error 로그).

- Reviewer: `tkv00`
- Decision: 승인. D1~D4를 권장안(D1 A, D2 A, D3 A, D4 권장안)으로 확정하고 위 가정도 함께 확정한다. D3 A에 따라 이슈 #375 본문의 IP 문구를 "`ClientAddressKey`와 같은 원천"으로 고친다.
- Approved at: `2026-11-22T21:56:28+09:00`
