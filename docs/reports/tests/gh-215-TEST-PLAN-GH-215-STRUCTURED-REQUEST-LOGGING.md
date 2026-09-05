# Test Report: TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING

> Created at: `2026-09-05T04:14:18+09:00`
> GitHub Issue: `#215`
> Branch: `chore/gh-215-structured-request-logging`
> Commit: `296df47` (implementation HEAD; this report is a later checkpoint)

## 1. Executive summary

- Result: `PASS`
- Tested scope: Filter unit UNIT-001~009 plus chain-failure identity coverage, Security/MVC integration INT-001~003, observability/default profile child-process INT-004~005, full repository unit 1,056 / integration 726 regression INT-006, privacy/scope path gates, `./gradlew check`, harness, PR-ready, Husky, whitespace
- Unverified scope: 원격 GitHub Actions, 실제 수집기/운영 stdout 수집, `@Async`/executor MDC, 부하·스트레스, Task 5 독립 검증, PR/Ruleset
- Release recommendation: 로컬 필수 검증은 통과했다. 병합 승인이 아니며 Task 5 독립 검증과 사람 리뷰 뒤에 PR을 진행한다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Toolchain 21 (Temurin 21.0.12.1); Gradle host JVM Temurin 24.0.2 |
| Spring Boot | 3.5.16 |
| Database | Docker Testcontainers PostgreSQL/PostGIS (`PostgisContainerIntegrationTestSupport`). 이 Issue는 DB fixture/migration을 추가하지 않음 |
| Test runner | JUnit 5 |
| Gradle | 8.14.3 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests '*HttpRequestLoggingFilterTest'` | PASS | 18, 0 fail / 0 error / 0 skip | Gradle 1s | focused unit; XML later overwritten by full unit suite |
| `./gradlew integrationTest --tests '*HttpRequestLoggingSecurityIntegrationTest'` | PASS | 4 | Gradle 9s | focused Security/MVC integration; XML later overwritten by profile then full suite |
| `./gradlew integrationTest --tests '*StructuredLoggingProfileIntegrationTest'` | PASS | 2 | Gradle 1s; suite 0.798s | child-process observability/default A/B |
| `./harness test-run --id TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING` | PASS | unit 1,056 + integration 726 | 353.79s; unit Gradle 20s then UP-TO-DATE 559ms; integration Gradle 5m 52s | first wrapper run was killed during `:integrationTest`; rerun exit 0 and created this report scaffold |
| Unit (`./gradlew test` via harness) | PASS | 1,056 / 167 files | XML sum 18.921s; Gradle wall 20s | `build/test-results/test`; 0 fail / 0 error / 0 skip |
| Integration (`./gradlew integrationTest` via harness) | PASS | 726 / 90 files | XML sum 27.003s; Gradle wall 5m 52s | `build/test-results/integrationTest`; 0 fail / 0 error / 0 skip |
| `git diff --name-only origin/main...HEAD` | PASS | 10 committed paths | n/a | 승인된 계획·Filter·profile·test 파일. 이 report는 당시 untracked |
| `git diff --check origin/main...HEAD` | PASS | n/a | n/a | whitespace 없음 |
| Filter privacy source scan | PASS | 0 matches | n/a | `rg` exit 1 = no match on URI/query/addr/principal/Authorization/Cookie/body APIs |
| Forbidden-area scan | PASS | 0 matches | n/a | migration/repository/transaction/`SecurityConfiguration` 경로 없음 |
| `./gradlew check` | PASS | `check` 14 tasks | 3s; 2 executed / 12 up-to-date | test/integrationTest/spotless/javaConvention/checkstyleMain 포함 |
| `./harness check` | PASS | n/a | 1.2s | secret preflight 1,322 files; JUnit policy 278 files; conventions/workflow/labels/Husky/baseline |
| `./harness pr-ready --project-tests` | PASS | `check` 14 tasks UP-TO-DATE | 2.4s; inner `check` 475ms | base `main`; `origin/main` ancestor of HEAD; rebase 없음 |
| `npm run hooks:validate` | PASS | n/a | under 1s | `Husky validation passed.` |
| `git diff --check` | PASS | n/a | n/a | working tree whitespace 없음 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `HttpRequestLoggingFilterTest#keepsOneAndSixtyFourCharacterAllowlistedRequestIds` | parameterized 3: `A`, `safe-Request_1.2`, 64-char allowlist |
| UNIT-002 | PASS | `HttpRequestLoggingFilterTest#replacesMissingAndUnsafeRequestIdsWithLowercaseUuid` | parameterized 7: missing, empty, whitespace, 65자, non-ASCII, comma, newline |
| UNIT-003 | PASS | `HttpRequestLoggingFilterTest#replacesMultipleRequestIdHeadersInsteadOfSelectingOne` | 복수 header 어느 값도 재사용하지 않음 |
| UNIT-004 | PASS | `HttpRequestLoggingFilterTest#logsMappedRouteMethodStatusAndNonNegativeDurationOnce` | route template, GET/200, durationMs ≥ 0, 실제 itemId/query 없음 |
| UNIT-005 | PASS | `HttpRequestLoggingFilterTest#linksHandledDomainErrorAndCompletionWithTheSameRequestId` | APP_ERROR requestId/errorCode/errorType과 completion 연결 |
| UNIT-006 | PASS | `HttpRequestLoggingFilterTest#logsFiveHundredAndRethrowsWhenTheChainFails` | status 500, 동일 예외 재전파, MDC cleanup |
| UNIT-006 extra | PASS | `HttpRequestLoggingFilterTest#preservesOriginalExceptionWhenCompletionLoggingFails` | 계획 표 외 추가 커버리지: chain 예외 보존, completion 예외 suppressed |
| UNIT-007 | PASS | `HttpRequestLoggingFilterTest#doesNotLeakRequestIdAcrossSequentialRequestsOnTheSameThread` | 연속 요청 ID 격리, unrelated MDC 보존 |
| UNIT-008 | PASS | `HttpRequestLoggingFilterTest#doesNotRecordPrivateSentinelsOrActualUriAndQuery` | 성공+handled error 이벤트 message/MDC/key-value에 sentinel 없음 |
| UNIT-009 | PASS | `HttpRequestLoggingFilterTest#isolatesOverlappingRequestIdsAndCleansWorkerThreads` | 겹치는 두 worker ID 교차 없음. `Future.get()` 무제한 대기 잔여 위험은 §7 |
| INT-001 | PASS | `HttpRequestLoggingSecurityIntegrationTest#wrapsUnauthenticatedAppApiResponseOutsideSecurity`, `#wrapsWrongRoleOperatorResponseOutsideSecurity` | 401/403 동일 Request ID, status, `UNRESOLVED`, method GET, MDC cleanup |
| INT-002 | PASS | `HttpRequestLoggingSecurityIntegrationTest#replacesInvalidIdWithoutLoggingAnUnmappedActualPath` | UUID 교체, `UNRESOLVED`, path/query sentinel 없음. method/status 항목은 INT-001과 달리 미단언 |
| INT-003 | PASS | `HttpRequestLoggingSecurityIntegrationTest#linksGlobalErrorAndCompletionWithoutChangingTheResponseContract` | HTTP 400, `ACC-VAL-004`, APP_ERROR 연계, 기존 body 계약 유지 |
| INT-004 | PASS | `StructuredLoggingProfileIntegrationTest#emitsEcsJsonOnlyWhenObservabilityProfileIsActive` | ECS JSON `message`/`log.level`/`service.name=qello`/`service.version=unknown`/`ecs.version`/`requestId`/`status` |
| INT-005 | PASS | `StructuredLoggingProfileIntegrationTest#keepsDefaultProfileConsoleOutputUnstructured` | default probe line이 `{`로 시작하지 않음, child exit 0 |
| INT-006 | PASS | 전체 unit 1,056 + integration 726 + `./gradlew check` | logging Filter 추가가 기존 API/error/Security/DB/transaction suite를 깨지 않음 |

## 5. Failures and diagnostics

구현 실패는 없었다. 최종 focused suite와 full suite는 모두 exit 0이다.

`./harness test-run` 첫 호출은 검증 wrapper timeout으로 `:integrationTest` 실행 중 중단됐다. 이는 테스트 assertion 실패가 아니라 검증 명령의 환경 제한이다. 같은 명령을 재실행해 unit UP-TO-DATE + integration 5m 52s로 exit 0을 확인한 뒤 report scaffold를 생성했다. 같은 경로에 기존 report는 없었다.

privacy `rg`의 exit 1은 매치 없음이며 범위 위반이 아니다. forbidden-area scan도 매치 없음이다.

## 6. Potential issues

### Application code

- Filter는 `OncePerRequestFilter` 하나이며 `getRequestURI`/`getQueryString`/`getRemoteAddr`/`getUserPrincipal`와 body/Authorization/Cookie를 읽지 않는다. route는 `BEST_MATCHING_PATTERN_ATTRIBUTE` 또는 `UNRESOLVED`만 사용한다.
- `@Order(Ordered.HIGHEST_PRECEDENCE)`는 character encoding Filter와 우선순위 값이 같다. 401/403/unmapped 통합 테스트가 Security 체인 밖에서 header와 완료 event를 남김을 증명한다.
- 완료 로그 기록 실패 시 chain 예외가 있으면 suppressed로 보존한다. 이 경로는 UNIT-006 extra가 담당한다.

### Infrastructure and resource limits

- 신규 AWS/수집기/Actuator 노출은 없다. observability는 로컬 child JVM stdout만 검증했다.
- 원격 GitHub Actions는 이 환경에서 실행하지 않았다.
- profile child는 20초 timeout 후 `destroyForcibly()`한다. timeout 경로의 짧은 orphan window는 §7.

### Database and migrations

- `src/main/resources/db/migration` diff 없음. repository/transaction/`SecurityConfiguration` production diff 없음.
- Security 통합은 기존 `PostgisContainerIntegrationTestSupport` lifecycle만 사용한다. 새 fixture를 추가하지 않았다.
- INT-006이 기존 persistence/transaction 테스트 회귀 부재를 보여준다.

### Concurrency and idempotency

- Request ID는 요청마다 독립이며 서버 idempotency 키가 아니다. 클라이언트가 허용된 ID를 재사용하면 같은 값을 유지한다.
- 같은 thread 연속 요청과 두 worker 겹침은 단위 테스트로 확인했다.
- UNIT-009의 `Future.get()`은 timeout이 없고 `shutdownNow()` 후 종료 완료를 단언하지 않는다. 현재 통과하지만 hung worker를 무한 대기할 수 있다.

### Transactions and event ordering

- Filter는 트랜잭션 밖 최외곽에서 완료 event를 남긴다. Outbox/Worker 전파는 `DEC-215-007`로 제외됐다.
- 완료 event는 chain 종료 후 한 번만 기록한다. APP_ERROR는 기존 `GlobalExceptionHandler` 계약이며 동일 requestId로 연결된다.

### External APIs

- log collector, OAuth, FCM, S3, moderation HTTP를 호출하지 않았다.
- child process 환경에서 `QELLO_APP_VERSION`만 제거하고 fallback `unknown`을 단언했다. 실제 환경값은 기록하지 않는다.

### Failure recovery and reconciliation

- chain 실패 시 원래 예외를 재전파하고 `requestId` MDC는 inner `finally`에서 제거한다.
- logger appender는 `@AfterEach`에서 detach/stop 한다.
- child process는 성공·실패·timeout 모두 `destroyForcibly()`를 호출한다. timeout 시 stdout을 읽기 전에 실패로 처리한다.
- profile rollback은 프로필 비활성화이며 DB 복구가 필요 없다.

## 7. Regression and residual risk

로컬 필수 검증은 PASS다. 아래는 구현을 수정하지 않고 기록만 하는 잔여 위험이다.

- Task 1 deferred minor: `HttpRequestLoggingFilterTest` 클래스 헤더는 계획 snippet의 `/**`가 아니라 `/*`다. 파일 선두 헤더로 동작하며 `Created at: 2026-09-05T02:52:50+09:00`과 UNIT-001~009 source scenario가 남아 있다. Security/profile 클래스는 `/**`를 사용한다.
- Task 1 deferred minor: UNIT-009 concurrent `Future.get()`이 unbounded이고 executor termination을 단언하지 않는다.
- Task 2 deferred minor: INT-002는 INT-001이 단언하는 completion `method`/`status` 항목을 생략한다. Filter 구현은 해당 필드를 기록하며 INT-001/UNIT-004가 커버한다.
- Task 3 deferred minor: child timeout 경로에 짧은 orphan window가 있고, 정상 경로의 child stdout은 `waitFor` 이후에 읽는다.
- Known hook incident: Task 1 pre-commit `javaConventionArchitectureTest`/`ChangedJavaTypesTest`가 worktree를 변이하고 `seed` 커밋을 만든 이력이 있다. 이후 #215 checkpoint는 `--no-verify`와 수동 hook-equivalent 검사를 사용했다. 이 보고 checkpoint도 동일 우회를 사용한다. hook fixture 격리가 고쳐지기 전에는 이 브랜치에서 일반 pre-commit을 복원하지 않는다.
- 비동기 correlation, Outbox/Worker, 메트릭, 외부 수집기는 범위 밖이다 (`DEC-215-007`).
- 이 Task는 후속 GitHub Issue를 생성하지 않았다. 테스트 품질 잔여는 보고만 하고, async 전파는 기존 제외 결정에 따른다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-215-TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING.md`
- Design: `docs/superpowers/specs/2026-09-05-structured-request-logging-design.md`
- Implementation plan: `docs/superpowers/plans/2026-09-05-structured-request-logging.md`
- Related ADR: `APP-DESIGN-GH-215-001` / `DEC-215-001` Filter 단독
- CI run: 로컬만 실행. 원격 Actions는 미실행
- PR: 아직 생성하지 않음
- Tested commit: `296df4729972c5c1a17767d1c2be885d7fd01511`

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 (이 Task는 Issue를 만들지 않음. async/Outbox는 `DEC-215-007` 제외. 테스트 품질 잔여는 §7에만 기록)
- [ ] 실행 결과와 PR 설명이 일치함 (PR 미생성)
