# Test Plan: TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING

> Created at: `2026-09-05T02:13:20+09:00`
> GitHub Issue: `#215`
> Design: `APP-DESIGN-GH-215-001`
> Status: `DRAFT_FOR_HUMAN_APPROVAL`

## 1. Objective

외부 Request ID를 안전한 allowlist로 제한하면서 성공, 애플리케이션 예외, Security
조기 종료와 미매핑 요청의 응답·로그를 연결하고, 같은 thread 또는 동시 요청 사이에
MDC가 누출되지 않음을 검증한다. `observability` 프로필의 실제 stdout만 ECS JSON으로
바뀌며 신규 event가 요청 body, 인증정보, 위치, 사용자 식별정보와 실제 URI를 기록하지
않는다는 증거를 만든다.

## 2. Scope

### Included

- `X-Request-ID` 1~64자 ASCII allowlist의 양·음성 경계
- 누락, 빈 값, 초과 길이, non-ASCII, 제어문자, delimiter와 복수 header 교체
- 성공, MVC 예외, chain failure, 401/403과 미매핑 응답의 response header
- 완료 event의 requestId, route, method, status, durationMs와 단일 기록
- mapped route template과 `UNRESOLVED` fallback
- 기존 APP_ERROR event의 requestId, errorCode, errorType 연계
- 같은 thread 연속 요청, 겹치는 요청과 모든 경로의 MDC cleanup
- observability profile ECS JSON과 default profile format isolation
- 민감정보 및 실제 URI 비기록 negative assertion

### Excluded

- Outbox/Worker correlation, async/executor MDC 전파
- DB, transaction, migration과 repository behavior
- request/response body logger, tracing와 log collector
- Actuator/Prometheus/Grafana와 Security 정책 자체의 변경
- custom encoder와 Logback configuration
- 지연시간 성능 threshold

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #215 | observability profile에서 ECS JSON stdout, default profile 유지 |
| GitHub Issue #215 | 안전한 외부 Request ID 재사용, invalid/missing 값 교체, 모든 응답 header |
| GitHub Issue #215 | route template, method, status, durationMs를 성공·예외 경로에 기록 |
| GitHub Issue #215 | errorCode/errorType 연계와 성공·예외·same-thread MDC cleanup |
| GitHub Issue #215 | body, token, 위치와 사용자 식별정보 비기록 |
| `APP-DESIGN-GH-215-001` | 최외곽 Filter 단독, `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`, 복수 header 거절 |
| `APP-DESIGN-GH-215-001` | mapped route만 기록하고 나머지는 `UNRESOLVED`, 완료 message 고정 |
| Spring Boot 3.5 reference | 내장 `ecs` console format과 MDC/SLF4J key-value JSON member |
| `AGENTS.md` §3 | JUnit 5, unit/integration 분리, 모든 method `@DisplayName`, 정확한 class header |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| CR/LF·공백·non-ASCII Request ID가 그대로 로그/응답에 반영됨 | log forging, parser 혼란 | 중간 | P0 | invalid table test와 generated UUID assertion |
| 복수 Request ID 중 첫 값만 신뢰함 | proxy/app 간 추적 불일치 | 중간 | P0 | multiple-header replacement test |
| Security가 Filter보다 먼저 종료함 | 401/403 header와 완료 event 누락 | 중간 | P0 | 실제 SecurityFilterChain integration evidence |
| actual URI/query를 route fallback으로 기록함 | 사용자·resource 식별정보 노출 | 중간 | P0 | dynamic URI/query sentinel absence |
| 예외 시 완료 event 또는 MDC cleanup이 누락됨 | 요청 추적 단절·thread 오염 | 높음 | P0 | handled/unhandled failure와 post-request MDC assertion |
| 같은 thread 다음 요청이 이전 requestId를 봄 | 잘못된 상관관계 | 중간 | P0 | sequential same-thread request pair |
| 동시 요청의 MDC가 교차함 | 상관관계 오염 | 낮음~중간 | P1 | overlapping request IDs와 captured event pair |
| 한 동기 요청에서 완료 event가 중복됨 | 요청 수 과대계상 | 중간 | P0 | event count exactly one per synchronous request |
| exception event가 requestId와 연결되지 않음 | 원인 로그와 완료 로그 분리 | 중간 | P0 | APP_ERROR/completion requestId equality |
| status가 chain failure에서 200으로 기록됨 | 장애 성공 오분류 | 중간 | P0 | propagated failure fallback status 500 |
| wall clock 변화로 duration이 음수임 | 잘못된 측정 | 낮음 | P1 | monotonic non-negative duration, no upper threshold |
| observability 설정이 default profile에 적용됨 | 기존 로그 consumer 파손 | 중간 | P0 | child-process profile A/B output |
| ECS 설정 이름·service field가 잘못됨 | JSON contract 불충족 | 낮음~중간 | P0 | actual console JSON parse and field assertions |
| body/token/location/user sentinel이 신규 event에 포함됨 | 민감정보 유출 | 중간 | P0 | logger event message/field negative scan |
| Filter가 기존 error mapping을 바꿈 | API error regression | 낮음 | P0 | existing handler tests와 full check |
| logging 변경이 DB/transaction/external API를 건드림 | 범위 확대·회귀 | 낮음 | P0 | branch path diff gate |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-UNIT-001 | 1자, 64자와 영문·숫자·`.`·`_`·`-` 조합의 단일 header | Filter가 요청을 처리 | 원래 값이 response header와 completion requestId에 동일하게 유지 | P0 | Request Filter Test Executor |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-UNIT-002 | missing, empty, whitespace, 65자, non-ASCII, CR/LF-equivalent unsafe 값과 comma 값 | Filter가 요청을 처리 | 입력을 재사용하지 않고 lowercase UUID contract 값으로 교체 | P0 | Request Filter Test Executor |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-UNIT-003 | 같은 이름의 header가 둘 이상 | Filter가 요청을 처리 | 어느 값도 선택하지 않고 새 UUID를 응답·로그에 사용 | P0 | Request Filter Test Executor |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-UNIT-004 | `/probe/items/{itemId}` mapped controller와 query sentinel | 성공 요청 완료 | route는 template, method/status는 `GET`/200, durationMs는 0 이상, 실제 itemId/query는 event에 없음 | P0 | Request Filter Test Executor |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-UNIT-005 | controller가 DomainException을 던지고 GlobalExceptionHandler가 응답 | 예외 요청 완료 | APP_ERROR와 completion이 같은 requestId, errorCode/errorType 유지, completion status는 mapped 4xx | P0 | Request Filter Test Executor |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-UNIT-006 | chain이 처리되지 않은 예외를 밖으로 던지고 response error status가 없음 | Filter finally 실행 | completion status 500, 원래 예외 재전파, requestId MDC 제거 | P0 | Request Filter Test Executor |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-UNIT-007 | 같은 test thread에서 서로 다른 두 요청을 순서대로 실행 | 각 요청 후 MDC와 event 검사 | 각 event는 자기 ID만 가지며 두 요청 뒤 MDC requestId 없음 | P0 | Request Filter Test Executor |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-UNIT-008 | body, token-like header, location, user label, path와 query sentinel | 성공 및 validation-error 요청 완료 | completion/error event의 message, MDC와 key-value field에 sentinel이 없음 | P0 | Request Filter Test Executor |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-UNIT-009 | 서로 다른 valid ID 두 개와 barrier controller | 두 request를 겹쳐 실행 | response/event ID가 교차하지 않고 각 thread 종료 후 owned MDC가 없음 | P1 | Request Filter Test Executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-INT-001 | Filter, 실제 app SecurityFilterChain, MockMvc | 인증 없는 app API 요청과 wrong-role로 인증한 operator API 요청 | 401과 403 모두 같은 `X-Request-ID`, 실제 status와 `UNRESOLVED`, MDC cleanup | ListAppender detach, MDC owned key 제거 |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-INT-002 | Filter, fallback SecurityFilterChain | 존재하지 않는 식별자 포함 path와 invalid Request ID | 실제 path를 기록하지 않고 `UNRESOLVED`, 새 UUID response header, 실제 4xx status | ListAppender detach, MDC owned key 제거 |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-INT-003 | Filter, GlobalExceptionHandler, mapped test endpoint | deterministic DomainException | APP_ERROR의 requestId/errorCode/errorType과 completion requestId가 연결되고 기존 response contract 유지 | test context 종료 |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-INT-004 | minimal child Spring process, observability profile, Boot logging system | service name `qello`, version fallback `unknown`, MDC probe value | probe stdout 한 줄이 ECS JSON이며 service, ecs, level, message와 MDC member를 파싱 가능 | process 종료, output memory 폐기 |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-INT-005 | 같은 minimal child process, default profile | structured property 없이 probe 실행 | probe line이 ECS JSON이 아니며 process exit 0 | process 종료, output memory 폐기 |
| TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-INT-006 | 전체 Qello unit/integration suite | DB·migration·external client 변경 없음 | `./gradlew check` | 기존 API, error, Security, DB와 transaction tests 통과 | suite lifecycle |

## 7. Cross-cutting scenarios

### Database and transactions

- 새 DB fixture, migration, repository query와 transaction annotation을 만들지 않는다.
- `src/main/resources/db/migration` 또는 repository diff가 존재하면 범위 위반으로 FAIL한다.
- 전체 integration suite로 logging Filter 추가가 기존 context와 transaction test를 깨뜨리지
  않았다는 회귀 증거만 수집한다.

### Concurrency and idempotency

- Request ID 처리는 요청마다 독립적이며 공유 mutable state를 두지 않는다.
- 같은 thread의 연속 요청은 이전 requestId를 상속하지 않는다.
- 두 요청을 겹쳐 실행해 ThreadLocal MDC가 서로 섞이지 않음을 확인한다.
- client가 valid ID를 재시도에 재사용하면 같은 값을 유지하지만 서버는 idempotency 의미를
  부여하지 않는다.

### External APIs

- 실제 log collector, OAuth, FCM, S3, moderation과 외부 HTTP API를 호출하지 않는다.
- structured logging은 local child JVM stdout만 검증한다.
- `QELLO_APP_VERSION`의 실제 환경값을 기록하지 않고 test process에는 고정 fallback만 쓴다.

### Failure recovery and reconciliation

- Filter chain failure 뒤에도 원래 예외가 전파되고 MDC는 정리되어야 한다.
- logger appender는 test failure에도 `finally`/`@AfterEach`에서 detach한다.
- child process timeout/non-zero exit는 application failure와 test-environment failure를 구분해
  기록하고 process를 종료한다.
- profile rollback에는 DB/data 복구가 필요하지 않으며 profile 비활성화로 기존 format을
  복원할 수 있다.

## 8. Test data and isolation

- Fixtures: `safe-Request_1.2`, 64자 boundary, 65자 boundary, non-ASCII/공백/comma와
  복수 header, path/body/query/token/location/user 모양의 가짜 sentinel을 쓴다.
- Database isolation: 새 데이터가 필요 없다. 전체 integration suite만 기존 Testcontainers
  lifecycle을 사용한다.
- Clock/randomness: duration은 `System.nanoTime()`의 0 이상만 검증하고 상한을 두지 않는다.
  generated ID는 정확한 UUID string이 아니라 lowercase UUID format과 unsafe input 비재사용을
  검증한다.
- External API doubles: 없다. child JVM과 local MockMvc만 사용한다.
- Log capture: 전용 logger에 Logback ListAppender를 attach하고 각 test에서 event count,
  MDC map과 key-value pairs만 검사한다. 전체 console log를 보고서에 복사하지 않는다.
- Cleanup: appender detach, MDC owned key 제거, executor/process 종료를 실패 경로에서도 실행한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Request Filter Test Executor | `src/test/java/com/dnd/qello/common/web/HttpRequestLoggingFilterTest.java` | UNIT-001~009 | focused unit test RED/GREEN evidence |
| 2 | Security Integration Test Executor | `src/integrationTest/java/com/dnd/qello/HttpRequestLoggingSecurityIntegrationTest.java` | INT-001~003 | actual filter/security/error integration evidence |
| 3 | Structured Profile Test Executor | `src/integrationTest/java/com/dnd/qello/StructuredLoggingProfileIntegrationTest.java`, `src/integrationTest/java/com/dnd/qello/StructuredLoggingProcessProbeApplication.java` | INT-004~005 | child-process observability/default profile A/B |
| 4 | Repository Regression Verifier | 기존 source와 test file은 수정하지 않음 | INT-006 | full check와 scope/path diff |
| 5 | Report Owner | `docs/reports/tests/gh-215-TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING.md` | 전체 | 실행 결과와 잠재 문제 분석 |

각 Test Executor는 표의 test file만 수정한다. Production file 소유권과 구현 순서는 승인된
implementation plan에서 별도로 지정한다. 같은 file 변경이 필요하면 작업을 직렬화하고
계획을 먼저 갱신한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] P1 미구현 시 이유, 영향과 후속 검증 기록
- [ ] 모든 테스트 method에 `@DisplayName`
- [ ] 모든 신규 test class header에 정확한 ISO 8601 timestamp와 source scenario 기록
- [ ] allowlist 경계와 invalid/multiple header 교체 test 통과
- [ ] success, handled exception, propagated failure, Security 401/403와 unmapped test 통과
- [ ] same-thread와 concurrent MDC isolation test 통과
- [ ] APP_ERROR/completion requestId 연결과 errorCode/errorType 회귀 test 통과
- [ ] observability ECS/default profile isolation child-process test 통과
- [ ] 민감 sentinel과 actual URI/query 비기록 scan 통과
- [ ] `./gradlew test --tests '*HttpRequestLoggingFilterTest'` 통과
- [ ] `./gradlew integrationTest --tests '*HttpRequestLoggingSecurityIntegrationTest'` 통과
- [ ] `./gradlew integrationTest --tests '*StructuredLoggingProfileIntegrationTest'` 통과
- [ ] `./gradlew check` 통과
- [ ] `./harness test-run --id TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING` 실행 및 report 생성
- [ ] `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`,
  `git diff --check` 통과
- [ ] application, DB, concurrency, transaction, external API와 failure recovery 잠재 문제 분석
- [ ] production diff가 Filter와 observability profile에 한정되고 migration/workflow가 없음

## 11. Human approval

- Reviewer: human partner
- Decision: `PENDING`
- Approved at: not recorded
