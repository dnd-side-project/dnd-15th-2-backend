# HTTP 요청 구조화 로그와 Request ID 추적 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 검증된 `X-Request-ID`로 모든 동기 Servlet 요청의 응답, 기존 오류 event와 단일 완료 event를 연결하고, `observability` 프로필에서만 Spring Boot 내장 ECS JSON stdout을 활성화한다.

**Architecture:** `Ordered.HIGHEST_PRECEDENCE`의 `OncePerRequestFilter` 하나가 Spring Security와 MVC 바깥에서 Request ID 생성·응답 헤더·MDC lifecycle·완료 event를 소유한다. MVC가 남긴 route template만 기록하고 확인할 수 없는 경로는 `UNRESOLVED`로 수렴한다. 구조화 출력은 별도 Logback 구성 없이 profile property로만 켠다.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring MVC, Spring Security, SLF4J 2, Logback, JUnit 5, MockMvc, AssertJ, Jackson, Gradle

**Spec:** `docs/superpowers/specs/2026-09-05-structured-request-logging-design.md`

## Global Constraints

- GitHub Issue, branch와 커밋 식별자는 모두 `#215`와 일치해야 한다.
- 승인된 구조는 Servlet Filter 단독이다. `HandlerInterceptor`, 두 번째 request lifecycle
  component 또는 custom logging encoder를 추가하지 않는다.
- 외부 Request ID는 header 값이 정확히 하나이고
  `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`와 일치할 때만 재사용한다. 나머지는
  `UUID.randomUUID().toString()`의 소문자 표현으로 교체한다.
- Filter는 chain 진입 전에 최종 `X-Request-ID` 응답 header와 MDC `requestId`를 설정한다.
  자신이 소유한 `requestId`만 제거하고 `MDC.clear()`를 호출하지 않는다.
- 전용 logger 이름은 `HTTP_REQUEST`, 완료 message는 `http_request_completed`다.
  `requestId`는 MDC, `route`, `method`, `status`, `durationMs`는 SLF4J typed key-value로
  기록한다.
- route는 `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`의 non-blank template 또는
  `UNRESOLVED`뿐이다. request URI와 query를 fallback으로 쓰지 않는다.
- chain failure가 밖으로 전파될 때 response status가 400 미만이면 event에는 500을 쓴다.
  이미 설정된 4xx/5xx는 유지하며 원래 throwable을 그대로 다시 던진다.
- duration은 `System.nanoTime()`의 차이를 millisecond로 내림한 0 이상 `long`이다. 상한을
  테스트하지 않는다.
- ECS 설정은 `application-observability.properties`에만 두고 Spring Boot 내장 `ecs`를
  사용한다. 기본 profile, `logback-spring.xml`과 기존 logger level은 바꾸지 않는다.
- request/response body, Authorization, Cookie, principal, account/session ID, 위치,
  remote address, 실제 URI와 query string을 읽거나 신규 event에 기록하지 않는다.
- sync Servlet request만 다룬다. async/executor MDC 전파, Outbox/Worker correlation,
  tracing, metrics와 log collector는 추가하지 않는다.
- DB schema, Flyway migration, repository, transaction, domain behavior, Security 정책과
  workflow를 변경하지 않는다.
- 새 JUnit 5 test class 상단에는 파일을 실제 생성하는 즉시 `date -Iseconds`로 얻은 정확한
  ISO 8601 시각과 원본 scenario ID를 기록한다. 모든 test method에는 `@DisplayName`을 쓴다.
- 각 task의 RED 명령과 예상 실패를 보존한 다음 최소 구현으로 GREEN을 만든다. 예상과 다른
  실패는 `superpowers:systematic-debugging`으로 원인을 확인한 뒤 진행한다.
- 실행 에이전트는 지정된 owned file만 수정하고 다른 에이전트나 사용자의 변경을 되돌리지
  않는다. 각 task 뒤 fresh spec reviewer와 code-quality reviewer가 실제 diff와 test output을
  독립 검토한다.

---

### Task 1: Standalone test로 request lifecycle core를 TDD한다

**Owner:** Request Filter execution agent

**Files:**

- Create: `src/test/java/com/dnd/qello/common/web/HttpRequestLoggingFilterTest.java`
- Create: `src/main/java/com/dnd/qello/common/web/HttpRequestLoggingFilter.java`

**Scenarios:** `UNIT-001` through `UNIT-009`

- [ ] **Step 1: 시작 상태와 test class 생성 시각을 기록한다.**

Run:

```bash
git status --short
date -Iseconds
```

Expected: 계획 문서 변경 외에 예상하지 못한 변경이 없고, 출력된 시각을 새 test class의
`Created at`에 그대로 사용한다.

- [ ] **Step 2: standalone test fixture와 log assertion helper를 작성한다.**

`HttpRequestLoggingFilterTest`는 다음 header로 시작한다.

```java
/**
 * Created at: date -Iseconds에서 방금 얻은 실제 값
 * Source scenario: TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-UNIT-001 through UNIT-009
 */
```

Test fixture는 다음 계약을 사용한다.

```java
private static final String REQUEST_ID_HEADER = "X-Request-ID";
private static final String REQUEST_ID_MDC_KEY = "requestId";
private static final Pattern GENERATED_ID = Pattern.compile(
    "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

private final Logger requestLogger = (Logger)LoggerFactory.getLogger("HTTP_REQUEST");
private final Logger errorLogger = (Logger)LoggerFactory.getLogger("APP_ERROR");
private final CapturingAppender requestLogs = new CapturingAppender();
private final CapturingAppender errorLogs = new CapturingAppender();
```

`@BeforeEach`는 appender의 event list를 비우고 appender를 시작·attach한 뒤 다음 standalone
MockMvc를 만든다.

```java
mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
    .setControllerAdvice(new GlobalExceptionHandler(
        new ApiErrorResponseFactory(Clock.fixed(FIXED_NOW, ZoneOffset.UTC)),
        new ConstraintExceptionMapper()))
    .addFilters(new HttpRequestLoggingFilter())
    .build();
```

기본 Logback `ListAppender`는 쓰지 않는다. 다음 test-only appender가 emitting thread에서 MDC를
고정하고 concurrent append를 안전하게 저장하도록 한다.

```java
private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {

    private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent event) {
        event.prepareForDeferredProcessing();
        events.add(event);
    }
}
```

`@AfterEach`는 두 appender를 detach/stop하고 test가 사용한 `requestId`와 unrelated MDC key를
제거한다.
`ILoggingEvent#getMDCPropertyMap()`과 `getKeyValuePairs()`를 Map으로 변환하는 helper,
`http_request_completed` event만 고르는 helper를 만든다. helper도 actual URI, body 또는
header 전체를 stringify하지 않는다.

- [ ] **Step 3: allowlist와 생성 규칙의 실패 test를 작성한다.**

다음 test method를 작성한다. parameterized test에도 모두 `@DisplayName`을 붙인다.

```text
keepsOneAndSixtyFourCharacterAllowlistedRequestIds
replacesMissingAndUnsafeRequestIdsWithLowercaseUuid
replacesMultipleRequestIdHeadersInsteadOfSelectingOne
```

입력 표는 최소한 다음을 포함한다.

```text
valid: A, safe-Request_1.2, 64 ASCII allowlisted characters
invalid: missing, empty, one space, 65 characters, 요청-식별자, unsafe,value,
         line-break가 포함된 값
multiple: X-Request-ID: first 와 X-Request-ID: second
```

각 요청은 response header와 completion event MDC가 같은 값을 갖는지 검사한다. invalid와
multiple은 입력 어느 것도 재사용하지 않고 `GENERATED_ID`와 일치해야 한다.

- [ ] **Step 4: 완료 event, 예외, privacy와 MDC 격리 실패 test를 작성한다.**

`ProbeController`는 다음 endpoint만 제공한다.

```java
@GetMapping("/probe/items/{itemId}")
ResponseEntity<Void> item(@PathVariable String itemId) {
    return ResponseEntity.ok().build();
}

@GetMapping("/probe/domain")
void domain() {
    throw new AccountException(
        AccountErrorCode.INVALID_TIMEZONE,
        "timezone",
        "timezone은 유효한 IANA ID여야 합니다");
}

@PostMapping("/probe/private/{pathSentinel}")
ResponseEntity<Void> privateProbe(@PathVariable String pathSentinel, @RequestBody String body) {
    return ResponseEntity.ok().build();
}
```

다음 test method를 작성한다.

```text
logsMappedRouteMethodStatusAndNonNegativeDurationOnce
linksHandledDomainErrorAndCompletionWithTheSameRequestId
logsFiveHundredAndRethrowsWhenTheChainFails
doesNotLeakRequestIdAcrossSequentialRequestsOnTheSameThread
doesNotRecordPrivateSentinelsOrActualUriAndQuery
isolatesOverlappingRequestIdsAndCleansWorkerThreads
```

구체 assertion은 다음과 같다.

- mapped 성공은 event가 정확히 하나이며 `route=/probe/items/{itemId}`, `method=GET`,
  `status=200`, `durationMs >= 0`이다. 실제 item ID와 query sentinel은 event에 없다.
- handled `AccountException`은 400 응답 contract를 유지하고 `APP_ERROR`의 `requestId`,
  `errorCode=ACC-VAL-004`, `errorType=AccountException`과 completion의 requestId가 같다.
- chain failure는 `MockHttpServletRequest`, `MockHttpServletResponse`와 직접 호출한 FilterChain을
  사용한다. 같은 exception instance가 재전파되고 completion status는 500이며, 호출 뒤
  `MDC.get("requestId")`가 null이다.
- sequential test는 서로 다른 valid ID 두 개를 같은 test thread에서 처리해 event가 각각
  자기 ID만 가지는지와 각 호출 뒤 MDC cleanup을 검사한다. 사전에 넣은 unrelated MDC key는
  두 호출 뒤에도 보존되는지 함께 확인한다.
- privacy test는 가짜 body/token/location/user/path/query sentinel을 넣고 request completion과
  APP_ERROR event의 formatted message, MDC map, key-value field 어디에도 나타나지 않는지
  검사한다.
- concurrency test는 고정 크기 2 executor와 `CountDownLatch`로 두 chain을 동시에 열어 둔다.
  chain 안과 completion event가 자기 ID만 관찰하며 worker 종료 직전 MDC requestId가 null임을
  각각 반환한다. `finally`에서 latch를 풀고 executor를 `shutdownNow()`한다.

- [ ] **Step 5: focused test를 실행해 RED를 확인한다.**

Run:

```bash
./gradlew test --tests '*HttpRequestLoggingFilterTest'
```

Expected: `HttpRequestLoggingFilter`가 아직 없어서 test compilation이 실패한다. 다른 기존
source의 compilation failure라면 구현하지 말고 원인을 보고한다.

- [ ] **Step 6: Spring bean 등록 없이 Filter core를 최소 구현한다.**

먼저 다음 형태로 class를 만든다. 이 task에서는 `@Component`와 `@Order`를 붙이지 않아
Task 2의 실제 context test가 RED를 증명할 수 있게 한다.

```java
public final class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger HTTP_REQUEST_LOG = LoggerFactory.getLogger("HTTP_REQUEST");
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_MDC_KEY = "requestId";
    private static final String UNRESOLVED_ROUTE = "UNRESOLVED";
    private static final Pattern TRUSTED_REQUEST_ID = Pattern.compile(
        "[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
```

`doFilterInternal`의 순서는 고정한다.

```text
1. getHeaders("X-Request-ID") 전체를 List로 복사한다.
2. 정확히 한 값이고 matcher.matches()면 유지하고, 아니면 lowercase UUID를 생성한다.
3. response.setHeader와 MDC.put을 chain 전에 실행한다.
4. System.nanoTime() 시작값과 failed=false를 기록한다.
5. chain을 호출한다.
6. IOException, ServletException, RuntimeException, Error를 잡아 failed=true로 바꾸고
   같은 instance를 다시 던진다.
7. finally 안의 try에서 completion event를 기록하고, 안쪽 finally에서 requestId만 제거한다.
```

완료 event는 자유 문자열 보간 없이 다음 형태를 사용한다.

```java
HTTP_REQUEST_LOG.atInfo()
    .addKeyValue("route", route(request))
    .addKeyValue("method", request.getMethod().toUpperCase(Locale.ROOT))
    .addKeyValue("status", completionStatus(response, failed))
    .addKeyValue("durationMs", elapsedMillis(startedAtNanos))
    .log("http_request_completed");
```

`route`는 `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`가 non-blank String일 때만 그 값을
반환한다. `completionStatus`는 `failed && response.getStatus() < 400`일 때만 500을 반환한다.
`elapsedMillis`는 `TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - start))`를
반환한다. UUID에는 사용자 입력이나 내부 식별자를 섞지 않는다.

- [ ] **Step 7: format과 focused test를 GREEN으로 만든다.**

Run:

```bash
./gradlew spotlessApply
./gradlew test --tests '*HttpRequestLoggingFilterTest'
git diff --check
```

Expected: `HttpRequestLoggingFilterTest` 전체 통과, formatting 오류 없음.

- [ ] **Step 8: task diff를 검토하고 checkpoint commit을 만든다.**

Run:

```bash
git diff -- src/main/java/com/dnd/qello/common/web/HttpRequestLoggingFilter.java \
  src/test/java/com/dnd/qello/common/web/HttpRequestLoggingFilterTest.java
git add src/main/java/com/dnd/qello/common/web/HttpRequestLoggingFilter.java \
  src/test/java/com/dnd/qello/common/web/HttpRequestLoggingFilterTest.java
git commit -m "chore(observability): add HTTP request logging core"
```

Expected commit message: `chore(observability): add HTTP request logging core (#215)`.

Task 1 종료 후 fresh spec reviewer는 `DEC-215-002`부터 `DEC-215-006`과 UNIT-001~009의
누락·범위 초과를 검사한다. 승인된 뒤 fresh code-quality reviewer가 concurrency cleanup,
exception identity, log injection과 test determinism을 검사한다. 발견 사항은 같은 execution
agent가 수정하고 두 reviewer의 재검토를 통과해야 Task 2로 이동한다.

---

### Task 2: 실제 Spring Security보다 앞에 Filter를 등록한다

**Owner:** Security integration execution agent

**Files:**

- Create: `src/integrationTest/java/com/dnd/qello/HttpRequestLoggingSecurityIntegrationTest.java`
- Modify: `src/main/java/com/dnd/qello/common/web/HttpRequestLoggingFilter.java`

**Scenarios:** `INT-001`, `INT-002`, `INT-003`

- [ ] **Step 1: integration test class 생성 시각과 clean handoff를 확인한다.**

Run:

```bash
git status --short
date -Iseconds
```

Expected: Task 1 commit 이후 계획 tracking 외 unexpected diff가 없다. 출력된 시각을 test class
header에 그대로 사용한다.

- [ ] **Step 2: 실제 application context와 logger fixture를 작성한다.**

Class 선언은 다음 구성을 사용한다.

```java
/**
 * Created at: date -Iseconds에서 방금 얻은 실제 값
 * Source scenario: TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-INT-001 through INT-003
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(HttpRequestLoggingSecurityIntegrationTest.ProbeController.class)
class HttpRequestLoggingSecurityIntegrationTest extends PostgisContainerIntegrationTestSupport {
```

`MockMvc`를 주입한다. `HTTP_REQUEST`와 `APP_ERROR` Logback logger에 Task 1과 같은
`CapturingAppender`를 각각 attach하고 `@AfterEach`에서 detach/stop 및
`MDC.remove("requestId")`를 실행한다. completion event는 message와 requestId로 필터링해
다른 context log와 섞이지 않게 한다.

Test 전용 controller는 production component scan에 들어가지 않도록 이 test의 static nested
class로 두고 `@Import`만 사용한다.

```java
@RestController
static class ProbeController {

    @GetMapping("/api/v1/request-logging-probe/domain")
    void domain() {
        throw new AccountException(
            AccountErrorCode.INVALID_TIMEZONE,
            "timezone",
            "timezone은 유효한 IANA ID여야 합니다");
    }
}
```

- [ ] **Step 3: Security 조기 종료와 미매핑 요청의 실패 test를 작성한다.**

다음 test method를 작성한다.

```text
wrapsUnauthenticatedAppApiResponseOutsideSecurity
wrapsWrongRoleOperatorResponseOutsideSecurity
replacesInvalidIdWithoutLoggingAnUnmappedActualPath
```

요청과 기대값은 다음과 같다.

```java
mockMvc.perform(get("/api/v1/direction/inbox")
        .header("X-Request-ID", "security-401"))
    .andExpect(status().isUnauthorized())
    .andExpect(header().string("X-Request-ID", "security-401"));

mockMvc.perform(get("/api/v1/operator/report-cases")
        .with(user("viewer").roles("VIEWER"))
        .header("X-Request-ID", "security-403"))
    .andExpect(status().isForbidden())
    .andExpect(header().string("X-Request-ID", "security-403"));

mockMvc.perform(get("/private-path-int002")
        .queryParam("private-query-int002", "private-value-int002")
        .header("X-Request-ID", "unsafe request id"))
    .andExpect(status().isUnauthorized());
```

401/403 completion event의 `route=UNRESOLVED`, actual status, request method와 supplied
requestId를 검사한다. 미매핑 요청은 response/completion ID가 generated UUID이고 event
전체에 path/query sentinel이 없으며 route가 `UNRESOLVED`인지 검사한다.

- [ ] **Step 4: 기존 APP_ERROR linkage의 실패 test를 작성한다.**

`linksGlobalErrorAndCompletionWithoutChangingTheResponseContract`를 작성한다.
`/api/v1/request-logging-probe/domain`에 `.with(jwt())`와 valid ID `domain-error-int003`을
보내고 다음을 검사한다.

```text
HTTP 400
response header X-Request-ID=domain-error-int003
body errorDetail.code=ACC-VAL-004
APP_ERROR requestId=domain-error-int003
APP_ERROR errorCode=ACC-VAL-004
APP_ERROR errorType=com.dnd.qello.account.error.AccountException
HTTP_REQUEST requestId=domain-error-int003
HTTP_REQUEST route=/api/v1/request-logging-probe/domain
HTTP_REQUEST status=400
각 logger의 대상 event 수=1
요청 뒤 test thread MDC requestId 없음
```

- [ ] **Step 5: 실제 context test를 실행해 registration RED를 확인한다.**

Run:

```bash
./gradlew integrationTest --tests '*HttpRequestLoggingSecurityIntegrationTest'
```

Expected: Filter가 아직 Spring bean이 아니므로 response `X-Request-ID` 또는 completion event
assertion이 실패한다. Security 자체가 기동하지 않거나 Testcontainers 환경이 실패하면 그
오류를 registration RED로 간주하지 않는다.

- [ ] **Step 6: Filter를 최외곽 Spring bean으로 등록한다.**

`HttpRequestLoggingFilter`에 다음 두 annotation만 추가한다.

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class HttpRequestLoggingFilter extends OncePerRequestFilter {
```

해당 import 외에 `SecurityConfiguration`, FilterChain bean, application property를 바꾸지
않는다. Spring Security 내부 chain에 `addFilterBefore`로 이 Filter를 다시 넣지 않는다.

- [ ] **Step 7: unit과 실제 Security integration test를 GREEN으로 만든다.**

Run:

```bash
./gradlew spotlessApply
./gradlew test --tests '*HttpRequestLoggingFilterTest'
./gradlew integrationTest --tests '*HttpRequestLoggingSecurityIntegrationTest'
git diff --check
```

Expected: 기존 standalone contract와 401/403/unmapped/error linkage가 모두 통과한다.

- [ ] **Step 8: registration checkpoint commit을 만든다.**

Run:

```bash
git add src/main/java/com/dnd/qello/common/web/HttpRequestLoggingFilter.java \
  src/integrationTest/java/com/dnd/qello/HttpRequestLoggingSecurityIntegrationTest.java
git commit -m "chore(observability): cover Security request logging"
```

Expected commit message: `chore(observability): cover Security request logging (#215)`.

Task 2 종료 후 fresh spec reviewer는 실제 application context 사용, Filter ordering,
401/403/fallback Security와 APP_ERROR 연결을 독립 검사한다. fresh code-quality reviewer는 test-only
controller 격리, appender cleanup, Security 정책 무변경과 event 선택의 결정성을 검사한다.
두 검토의 발견 사항이 해결되기 전에는 Task 3으로 이동하지 않는다.

---

### Task 3: observability profile의 실제 ECS stdout을 TDD한다

**Owner:** Structured profile execution agent

**Files:**

- Create: `src/integrationTest/java/com/dnd/qello/StructuredLoggingProfileIntegrationTest.java`
- Create: `src/integrationTest/java/com/dnd/qello/StructuredLoggingProcessProbeApplication.java`
- Create: `src/main/resources/application-observability.properties`

**Scenarios:** `INT-004`, `INT-005`

- [ ] **Step 1: 두 test class의 실제 생성 시각을 기록한다.**

Run:

```bash
git status --short
date -Iseconds
```

Expected: Task 2까지의 checkpoint와 계획 tracking 외 unexpected diff가 없다. 같은 명령의
출력 시각을 두 새 class header에 사용한다.

- [ ] **Step 2: auto-configuration 없는 최소 child application을 작성한다.**

`StructuredLoggingProcessProbeApplication`은 Qello application component scan과 DB를
시작하지 않는 독립 test main이다.

```java
/**
 * Created at: date -Iseconds에서 방금 얻은 실제 값
 * Source scenario: TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-INT-004 through INT-005
 */
public final class StructuredLoggingProcessProbeApplication {

    private static final Logger LOG = LoggerFactory.getLogger("STRUCTURED_LOGGING_PROBE");

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(
            StructuredLoggingProcessProbeApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext ignored = application.run(args)) {
            MDC.put("requestId", "profile-probe");
            try {
                LOG.atInfo()
                    .addKeyValue("status", 200)
                    .log("structured_logging_probe");
            } finally {
                MDC.remove("requestId");
            }
        }
    }
}
```

이 class에는 Spring configuration/component annotation이나 Qello main application 참조를
추가하지 않는다. `SpringApplication`의 명시적 primary source인 일반 class로만 등록해
같은 `com.dnd.qello` package의 기존 `QelloApplication`과 두 번째 Boot configuration 후보가
생기거나 production component scan에 test configuration이 섞이지 않게 한다.

- [ ] **Step 3: child JVM runner와 profile A/B 실패 test를 작성한다.**

`StructuredLoggingProfileIntegrationTest` header의 source scenario도 `INT-004 through
INT-005`로 기록한다. 다음 두 method를 작성한다.

```text
emitsEcsJsonOnlyWhenObservabilityProfileIsActive
keepsDefaultProfileConsoleOutputUnstructured
```

Child command contract는 다음과 같다.

```text
<java.home>/bin/java
-Dfile.encoding=UTF-8
-cp <current integration-test context classloader URLs + java.class.path>
com.dnd.qello.StructuredLoggingProcessProbeApplication
--spring.main.banner-mode=off
--spring.output.ansi.enabled=never
--spring.profiles.active=<observability 또는 default>
```

Classpath helper는 `Thread.currentThread().getContextClassLoader()`에서 parent 방향으로
순회하며 `URLClassLoader`의 `file:` URL을 `LinkedHashSet`에 넣고, 마지막에
`System.getProperty("java.class.path")` 항목을 합친다. 이 방식은 Gradle test worker가
별도 classloader에 둔 integration/main/dependency 경로를 child JVM에 전달하기 위한 test
전용 장치다. 결과 classpath가 빈 값이면 명시적으로 실패한다.

`ProcessBuilder`는 stderr를 stdout에 합치고, environment에서 `QELLO_APP_VERSION`만
제거한다. 20초 안에 종료하지 않으면 `destroyForcibly()` 후 environment failure로 실패한다.
non-zero exit이면 secret이나 전체 environment를 출력하지 말고 child output만 진단에
붙인다. output에서 `structured_logging_probe`를 포함한 정확히 한 줄을 고른다.

observability test는 Jackson으로 그 줄을 JSON parse하고 다음을 검사한다.

```text
message = structured_logging_probe
log.level = INFO
service.name = qello
service.version = unknown
ecs.version = non-blank
requestId = profile-probe
status = 200
```

default test는 probe line이 `{`로 시작하지 않으며 process exit code가 0인지 검사한다.

- [ ] **Step 4: profile integration test를 실행해 RED를 확인한다.**

Run:

```bash
./gradlew integrationTest --tests '*StructuredLoggingProfileIntegrationTest'
```

Expected: default profile test는 통과하고 observability test는 아직 profile file이 없어
probe line JSON parsing 또는 JSON assertion에서 실패한다. child main class를 찾지 못하는
경우는 classpath helper 문제이므로 profile RED로 간주하지 않고 먼저 runner를 수정한다.

- [ ] **Step 5: Spring Boot 내장 ECS profile property만 추가한다.**

`src/main/resources/application-observability.properties`의 전체 내용은 다음 세 줄이다.

```properties
logging.structured.format.console=ecs
logging.structured.ecs.service.name=${spring.application.name}
logging.structured.ecs.service.version=${QELLO_APP_VERSION:unknown}
```

`application.properties`, Logback XML, dependency와 production Java는 변경하지 않는다.

- [ ] **Step 6: profile A/B test를 GREEN으로 만든다.**

Run:

```bash
./gradlew spotlessApply
./gradlew integrationTest --tests '*StructuredLoggingProfileIntegrationTest'
git diff --check
```

Expected: observability process의 probe line은 ECS JSON이고 default process의 같은 line은 기존
plain console format이다.

- [ ] **Step 7: profile checkpoint commit을 만든다.**

Run:

```bash
git add src/main/resources/application-observability.properties \
  src/integrationTest/java/com/dnd/qello/StructuredLoggingProfileIntegrationTest.java \
  src/integrationTest/java/com/dnd/qello/StructuredLoggingProcessProbeApplication.java
git commit -m "chore(observability): enable ECS logging profile"
```

Expected commit message: `chore(observability): enable ECS logging profile (#215)`.

Task 3 종료 후 fresh spec reviewer는 실제 child process, profile isolation과 정확한 세 property를
검사한다. fresh code-quality reviewer는 timeout/forced cleanup, classpath portability,
environment 비노출과 JSON assertion의 구체성을 검사한다. 두 검토를 통과한 뒤에만 최종
검증으로 이동한다.

---

### Task 4: 전체 회귀 검증과 test report를 남긴다

**Owner:** Test report execution agent

**Files:**

- Create: `docs/reports/tests/gh-215-TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING.md`
- Modify: `TASK.md`

**Scenarios:** 승인된 test plan 전체와 `INT-006`

- [ ] **Step 1: focused suite를 각각 다시 실행한다.**

Run:

```bash
./gradlew test --tests '*HttpRequestLoggingFilterTest'
./gradlew integrationTest --tests '*HttpRequestLoggingSecurityIntegrationTest'
./gradlew integrationTest --tests '*StructuredLoggingProfileIntegrationTest'
```

Expected: 세 명령 모두 exit 0. 실패한 명령은 구현 문제와 test environment 문제를 구분해
기록하며 다음 검증을 성공으로 보고하지 않는다.

- [ ] **Step 2: 전체 JUnit suite와 report scaffold를 생성한다.**

Run:

```bash
./harness test-run --id TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING
```

이 명령은 repository unit/integration suite를 실행한 다음 test report를 생성한다. 생성 전에
같은 경로의 report가 없어야 한다. 실패하면 report를 임의 생성해 PASS로 기록하지 않는다.

- [ ] **Step 3: 생성된 report를 실제 증거로 완성한다.**

`templates/test-report.md`의 모든 section을 채운다.

```text
Result
실행한 각 명령과 exit 결과
UNIT-001~009, INT-001~006의 test method mapping
실패/환경 차단과 재현 조건
application, infrastructure/resource limit, DB/migration, concurrency/idempotency,
transaction/event ordering, external API, failure recovery/reconciliation 잠재 문제
미검증 범위와 residual risk
plan/spec 경로와 현재 commit
```

전체 console log, `.env`, URL, token, account/server 식별자와 실제 runtime environment 값은
report에 복사하지 않는다.

- [ ] **Step 4: scope와 privacy source gate를 실행한다.**

Run:

```bash
git diff --name-only origin/main...HEAD
git diff --check origin/main...HEAD
rg -n "getRequestURI|getQueryString|getRemoteAddr|getUserPrincipal|Authorization|Cookie|requestBody|responseBody" \
  src/main/java/com/dnd/qello/common/web/HttpRequestLoggingFilter.java
git diff --name-only origin/main...HEAD -- src/main/resources/db/migration \
  src/main/java/com/dnd/qello | rg "repository|transaction|SecurityConfiguration" || true
```

Expected: 첫 목록은 승인된 계획·Filter·profile·test·report 파일뿐이다. privacy scan과 금지
영역 scan은 match가 없다. match가 있으면 직접 검토해 범위 위반 여부를 report에 기록하고,
위반이면 FAIL로 종료한다.

- [ ] **Step 5: 저장소 필수 gate를 실행한다.**

Run:

```bash
./gradlew check
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

Expected: 모두 exit 0. `pr-ready`가 base sync 문제로 실패하면 자동으로 rebase하거나 사용자
변경을 정리하지 말고 `./harness base`, `git status --short`와 실패 요약을 report에 남긴다.

- [ ] **Step 6: TASK contract를 검증 결과와 일치시킨다.**

`TASK.md`의 implementation plan status가 실행 전 기록한 `APPROVED_FOR_EXECUTION`인지
확인하고 implementation gate를 `IMPLEMENTED_PENDING_INDEPENDENT_VERIFICATION`으로 바꾼다.
실제로 통과한 completion criteria만 `[x]`로 표시하고 다음 final contract를 채운다.

```text
status
issue_number
task_id
design_id
changed_files
executed_checks
passed_checks
failed_checks
blocked_checks
assumptions
risks
required_human_decisions
```

실패나 차단 항목이 있으면 `PASS`를 쓰지 않는다.

- [ ] **Step 7: report checkpoint commit을 만든다.**

Run:

```bash
git add TASK.md \
  docs/reports/tests/gh-215-TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING.md
git commit -m "chore(observability): record request logging verification"
```

Expected commit message: `chore(observability): record request logging verification (#215)`.

---

### Task 5: 독립 검증 에이전트가 구현을 판정한다

**Owner:** Independent verification agent

**Files:** Read-only. Source, test, plan과 report를 수정하지 않는다.

- [ ] **Step 1: Issue 계약과 실제 diff의 일치 여부를 검토한다.**

검증자는 구현 에이전트의 설명에 의존하지 않고 다음을 직접 읽는다.

```text
TASK.md
docs/superpowers/specs/2026-09-05-structured-request-logging-design.md
docs/test-plans/gh-215-TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING.md
docs/superpowers/plans/2026-09-05-structured-request-logging.md
origin/main...HEAD의 전체 diff
test report
```

필수 판정 항목은 Filter ordering, one-header allowlist, response-before-chain, MDC owned-key
cleanup, route privacy, exception status/rethrow, Security early exit, APP_ERROR linkage, ECS profile
isolation과 explicit exclusions다.

- [ ] **Step 2: 독립 verification command를 실행한다.**

Run:

```bash
git status --short
git diff --check origin/main...HEAD
./gradlew test --tests '*HttpRequestLoggingFilterTest'
./gradlew integrationTest --tests '*HttpRequestLoggingSecurityIntegrationTest'
./gradlew integrationTest --tests '*StructuredLoggingProfileIntegrationTest'
./gradlew check
./harness check
npm run hooks:validate
```

검증자는 test를 통과시키기 위해 source나 test를 수정하지 않는다. 환경 차단도 명령, 안전한
오류 요약, 재현 조건, 미검증 범위와 남은 위험을 포함해 `BLOCKED`로 판정한다.

- [ ] **Step 3: repository contract 형식으로 최종 verdict를 반환한다.**

결과는 `PASS`, `FAIL`, `BLOCKED` 중 하나이며 다음 field를 모두 포함한다.

```text
status
issue_number: 215
task_id: GH-215-STRUCTURED-REQUEST-LOGGING
design_id: APP-DESIGN-GH-215-001
changed_files
executed_checks
passed_checks
failed_checks
blocked_checks
assumptions
risks
required_human_decisions
```

PASS는 승인 범위를 구현했고 모든 필수 검증이 실행되어 실패·차단이 없을 때만 허용한다.
독립 검증 결과와 human review가 끝나기 전에는 PR 생성이나 branch 통합을 진행하지 않는다.

## Execution Handoff

사람이 이 implementation plan을 승인하면 현재 세션에서
`superpowers:subagent-driven-development`를 사용한다. Orchestrator는 직접 production code를
수정하지 않고 task마다 fresh execution agent를 배정하며, 각 task 뒤 별도의 spec reviewer와
code-quality reviewer를 거친다. 모든 agent에게 owned file, 선행 checkpoint, 다른 변경을
되돌리지 말라는 제약과 RED/GREEN 증거 보고 의무를 전달한다. 첫 execution agent를 호출하기
전에 승인 시각과 증거를 `TASK.md`에 기록하고 implementation plan status를
`APPROVED_FOR_EXECUTION`으로 전환한다.
