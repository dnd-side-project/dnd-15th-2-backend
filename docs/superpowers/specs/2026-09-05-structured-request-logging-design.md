# HTTP 요청 구조화 로그와 Request ID 추적 설계

> Design ID: `APP-DESIGN-GH-215-001`
> GitHub Issue: `#215`
> Task ID: `GH-215-STRUCTURED-REQUEST-LOGGING`
> Status: `APPROVED_FOR_IMPLEMENTATION_PLAN`
> Architecture decision approved by: human partner at `2026-09-05T02:11:00+09:00`
> Approval evidence: Servlet Filter 단독 구조를 `1번으로 설계확정`이라고 명시함
> Written spec approved by: human partner at `2026-09-05T02:29:39+09:00`
> Written spec approval evidence: 설계 spec과 테스트 계획을 `승인할게`라고 명시함

## 1. 목적

한 동기 HTTP 요청의 성공, 애플리케이션 예외와 Security 조기 종료를 하나의 안전한
Request ID로 연결한다. 완료 event에는 실제 요청 URI 대신 route template, HTTP method,
최종 status와 단조 시계로 측정한 durationMs를 남긴다. 모든 응답은 최종 Request ID를
`X-Request-ID`로 반환한다.

`observability` 프로필은 Spring Boot 3.5.16 내장 Elastic Common Schema(ECS) console
format을 사용한다. 별도 encoder나 수집기를 만들지 않으며 기본 프로필의 console
format은 유지한다.

## 2. 현재 상태와 문제

- `GlobalExceptionHandler`는 `APP_ERROR` logger에 `errorCode`와 `errorType`을 MDC로
  넣고 각 error event 직후 제거한다.
- 정상 요청에는 공통 완료 event와 Request ID가 없다.
- Security FilterChain이 MVC보다 먼저 종료한 401/403 응답을 연결할 공통 경계가 없다.
- 실제 URI를 그대로 기록하면 path variable, query와 사용자 입력이 로그에 노출될 수 있다.
- MDC는 ThreadLocal이므로 명시적 정리가 없으면 container thread 재사용 시 다음 요청으로
  값이 누출될 수 있다.
- Spring Boot 3.5 공식 문서는 `logging.structured.format.console=ecs`와 ECS service
  property를 제공하며 MDC와 SLF4J key-value pair를 JSON member로 포함한다.

Reference:
`https://docs.spring.io/spring-boot/3.5/reference/features/logging.html#features.logging.structured`

## 3. 범위와 비범위

### 포함

- 모든 Servlet dispatch의 최초 동기 요청을 감싸는 최외곽 Filter
- 외부 Request ID의 ASCII allowlist 검증과 안전한 UUID 대체
- 응답 `X-Request-ID`와 요청 구간 MDC `requestId`
- 완료 event의 route, method, status, durationMs
- 기존 `APP_ERROR` event와 requestId 연결
- `observability` 프로필 전용 ECS stdout
- 성공, MVC 예외, Security 거부, 미매핑, chain failure와 thread 재사용 테스트
- 새 로그가 민감한 요청 값을 포함하지 않는다는 negative assertion

### 제외

- correlationId와 Outbox/Worker 전파
- executor, scheduler와 `@Async` MDC 전파
- request/response body, header 전체, query string 또는 실제 URI logging
- tracing, log shipping, 보존 정책과 수집 backend
- Actuator/Prometheus/Grafana 설정
- DB, migration, transaction, repository와 domain behavior 변경
- custom encoder와 custom `logback-spring.xml`

## 4. 검토한 접근

### 선택: 최외곽 Servlet Filter 단독

Filter가 Spring Security보다 앞에서 Request ID를 확정하고 응답 헤더와 MDC를 설정한다.
`filterChain.doFilter()`가 끝난 뒤 MVC가 남긴 best-matching-pattern을 읽어 완료 event를
한 번 기록하고 `finally`에서 소유 MDC를 제거한다.

장점은 모든 HTTP 경로를 하나의 lifecycle로 감싸고, error handler가 실행되는 동안
requestId가 유지되며, cleanup 소유권이 한 클래스에 있다는 점이다. MVC route가 없는
Security 조기 종료와 미매핑 요청은 `UNRESOLVED`로 기록한다.

### 제외: MVC HandlerInterceptor 단독

HandlerInterceptor는 mapped route를 직접 알 수 있지만 Spring Security에서 먼저 끝난
요청에 실행되지 않는다. 모든 응답의 Request ID와 인증 실패 완료 event를 보장할 수 없다.

### 제외: Filter와 HandlerInterceptor 이중 경계

Filter가 Request ID, Interceptor가 route와 완료 event를 담당할 수 있으나 요청 attribute,
예외와 cleanup 책임을 두 구성요소가 공유한다. Filter 종료 시점에 같은 route attribute를
읽을 수 있으므로 현재 동기 Spring MVC 구조에서 복잡도에 상응하는 이점이 없다.

## 5. 설계 결정

### DEC-215-001: Servlet Filter 하나가 요청 lifecycle을 소유한다

`OncePerRequestFilter` 구현을 Spring Security보다 앞에 등록한다. 현재 애플리케이션의
동기 MVC 요청에서 최초 `REQUEST` dispatch만 이 Issue의 추적 범위다. async와 error
redispatch의 별도 추적은 추가하지 않는다.

### DEC-215-002: Request ID는 정확히 한 allowlisted 값만 신뢰한다

외부 `X-Request-ID`는 값이 정확히 하나이고 다음 정규식과 일치할 때만 재사용한다.

```text
[A-Za-z0-9][A-Za-z0-9._-]{0,63}
```

누락, 빈 값, 공백, 65자 이상, non-ASCII, 제어문자, 허용하지 않은 구두점, 복수 header와
comma 결합 값은 모두 거절한다. 대체 값은 `UUID.randomUUID().toString()`의 소문자
36자 표현이다. 내부 account, session, token과 Request ID를 서로 파생하지 않는다.

### DEC-215-003: response header와 requestId MDC를 chain 진입 전에 설정한다

Security 또는 MVC가 응답을 commit하기 전에 `X-Request-ID`를 설정한다. 같은 값을
MDC `requestId`에 넣어 chain 안의 기존 `APP_ERROR` event가 자동으로 포함하게 한다.
Filter는 MDC 전체를 지우지 않고 자신이 소유한 `requestId`만 반드시 제거한다.

### DEC-215-004: 완료 event는 고정 message와 typed key-value field를 쓴다

전용 logger가 `http_request_completed` message를 한 번 기록한다. field 계약은 다음과 같다.

| Field | Source | Contract |
| --- | --- | --- |
| `requestId` | validated/generated value | MDC string |
| `route` | MVC best matching pattern | template 또는 `UNRESOLVED` |
| `method` | Servlet request method | 대문자 HTTP method |
| `status` | Servlet response | 정수 status |
| `durationMs` | `System.nanoTime()` delta | 0 이상 정수 millisecond |

route, method, status와 durationMs는 SLF4J fluent key-value field로 event에 붙인다. 자유
형식 message에 값을 보간하지 않는다. timing은 관측값이며 pass/fail 성능 기준이 아니다.

### DEC-215-005: 실제 URI를 fallback으로 사용하지 않는다

`HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`가 존재할 때만 route template을 쓴다.
Security 조기 종료, handler 미확정과 chain failure는 `UNRESOLVED`다. request URI,
query string, path variable 값과 controller argument는 읽거나 기록하지 않는다.

### DEC-215-006: chain failure는 안전한 status로 기록하고 원래 예외를 보존한다

chain이 처리되지 않은 예외를 밖으로 던지고 response가 아직 error status가 아니면 완료
event는 500으로 기록한다. 이미 4xx/5xx status가 있으면 그대로 사용한다. Filter는 예외
message나 stack trace를 추가로 기록하지 않고 원래 예외를 다시 던진다.

### DEC-215-007: ECS는 observability profile에서만 활성화한다

`application-observability.yml`는 다음 contract만 소유한다.

```yaml
logging:
  structured:
    format:
      console: ecs
    ecs:
      service:
        name: ${spring.application.name}
        version: ${QELLO_APP_VERSION:unknown}
```

기본 profile에는 structured format property를 추가하지 않는다. ECS JSON은 Boot 내장
timestamp, level, logger, process, service와 ecs version에 MDC/key-value field를 합친다.

### DEC-215-008: 기존 error contract를 확장하지 않고 연결한다

`GlobalExceptionHandler`의 errorCode, errorType, level과 응답 mapping은 유지한다. Filter가
requestId를 error handler보다 바깥 scope에 유지하므로 error event와 완료 event가 같은
requestId로 연결된다. #215는 exception message sanitization 정책이나 stack-trace 보존
정책을 재설계하지 않는다.

## 6. 요청 흐름

```text
Servlet request
  -> HttpRequestLoggingFilter
       validate one X-Request-ID or generate UUID
       set response X-Request-ID
       MDC.put(requestId)
       -> Spring Security
            -> DispatcherServlet / controller / GlobalExceptionHandler
       <- response or propagated chain failure
       read route template or UNRESOLVED
       log http_request_completed with method/status/durationMs
       MDC.remove(requestId) in finally
  -> Servlet response
```

## 7. 구성 요소와 파일

```text
src/main/java/com/dnd/qello/common/web/HttpRequestLoggingFilter.java
src/main/resources/application-observability.yml
src/test/java/com/dnd/qello/common/web/HttpRequestLoggingFilterTest.java
src/integrationTest/java/com/dnd/qello/HttpRequestLoggingSecurityIntegrationTest.java
src/integrationTest/java/com/dnd/qello/StructuredLoggingProfileIntegrationTest.java
src/integrationTest/java/com/dnd/qello/StructuredLoggingProcessProbeApplication.java
```

`HttpRequestLoggingFilter`가 Request ID 정책, response header, MDC와 완료 event의 단일
production owner다. 별도 public API나 설정 binding class는 만들지 않는다.

unit test는 standalone MockMvc와 emitting thread에서 MDC snapshot을 고정하는 thread-safe
test appender로 filter behavior를 검증한다.
Security integration test는 실제 FilterChain 앞뒤의 401/403 경계를 확인한다. structured
profile integration test는 별도 최소 Spring process를 시작해 observability profile의 실제
console line을 JSON으로 파싱하고 default profile 출력이 JSON으로 바뀌지 않았음을 확인한다.

## 8. 보안과 개인정보

- Request ID는 CR/LF, 공백, non-ASCII와 delimiter를 거절해 log/header injection을 막는다.
- 복수 header는 첫 값을 임의 선택하지 않고 전체를 불신한다.
- 완료 logger에는 body, Authorization, Cookie, query, URI, remote address와 principal을
  전달하지 않는다.
- route는 framework가 정한 template 또는 고정 enum-like label만 허용한다.
- 테스트 fixture는 token, 위치, 사용자 label 모양의 sentinel을 요청에 넣고 모든 신규
  event의 message와 structured field에 나타나지 않음을 검증한다.
- 로그 테스트 실패 출력에도 실제 credential, URL, account ID와 `.env` 값을 쓰지 않는다.

## 9. 오류와 정리

- 정상 응답, MVC가 처리한 예외, Security entry point, access denied와 미매핑 응답은 실제
  response status로 완료 event를 기록한다.
- chain이 밖으로 던진 예외는 완료 event만 남기고 다시 던져 기존 container behavior를
  바꾸지 않는다.
- 완료 event 기록 자체의 성공 여부와 관계없이 `requestId` 제거가 실행되어야 한다.
- 다음 요청 시작 시 새로운 ID로 덮어쓰고 이전 요청 값은 복원하지 않는다.
- `errorCode`와 `errorType`은 기존 `GlobalExceptionHandler`가 계속 소유하고 제거한다.

## 10. 테스트 전략

- unit: 1/64자 경계, invalid/multiple header 교체, 성공/예외 status, route template,
  한 번의 완료 event, response header와 MDC cleanup
- integration: 실제 Security 401/403 조기 종료에서도 header와 완료 event 보장
- error linkage: APP_ERROR event와 completion event의 requestId 동일성 및
  errorCode/errorType 보존
- thread safety: 같은 thread 연속 요청과 겹치는 요청의 ID 격리
- profile: observability child process의 ECS JSON/service/MDC field와 default process의
  non-structured output
- privacy: body, token, 위치, 사용자 식별자, 실제 URI와 query sentinel 부재
- regression: 전체 unit/integration suite와 source/migration diff gate

Post-approval planning clarification (`2026-09-05T02:45:35+09:00`): 기본 Logback
`ListAppender`는 concurrent append와 지연 MDC 조회에 적합하지 않으므로 scenario 변경 없이
동기 snapshot·thread-safe test appender를 사용한다.

## 11. 배포와 롤백

application behavior 변경은 응답 `X-Request-ID` 추가와 완료 event 추가뿐이다. ECS 출력은
profile opt-in이므로 `observability`를 활성화하지 않은 실행은 기존 format을 유지한다.

문제가 생기면 profile 활성화를 제거해 JSON format만 즉시 되돌릴 수 있다. 코드 롤백은
Filter와 전용 테스트를 한 변경 단위로 되돌리며 DB 복구나 데이터 migration은 없다.

## 12. 승인 상태

- 이 written spec과 risk-based test plan의 사람 승인 완료
- implementation plan의 사람 승인 대기
- 구현 이후 독립 verifier의 spec·quality 검토
