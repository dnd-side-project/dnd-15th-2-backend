# GitHub Issue #218 Task Contract

> Generated at: `2026-09-05T17:57:49+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Prometheus 지표 노출 경계와 management port 분리`
- GitHub Issue: `#218`
- Branch: `chore/gh-218-observability-metrics-exposure`
- Base branch: `chore/gh-215-structured-request-logging`
- Task ID: `GH-218-OBSERVABILITY-METRICS-EXPOSURE`
- Design ID: `APP-DESIGN-GH-218-001`
- Design path:
  `docs/superpowers/specs/2026-09-05-observability-metrics-exposure-design.md`
- Design status: `APPROVED_FOR_IMPLEMENTATION`
- Design approval evidence: `2026-09-05T17:46:09+09:00` 사용자가 설계 섹션
  §1~§6에 `승인할게`라고 명시함
- Implementation plan path:
  `docs/superpowers/plans/2026-09-05-observability-metrics-exposure.md`
- Implementation plan status: `APPROVED_FOR_IMPLEMENTATION`
- Implementation approval evidence: 사용자가 `Subagent-Driven 으로 구현 시작해줘`라고
  명시함

## Objective

- Micrometer가 프로세스 안에만 보관하는 지표를 Prometheus가 읽을 수 있는
  출구로 연결하되, 노출 대상을 `health`와 `prometheus` 두 endpoint로 한정한다.
- 비즈니스 API listener와 관리 listener를 별도 포트로 분리해, 노출 차단이
  애플리케이션 설정 한 줄이 아니라 network 경계로도 보장되게 한다.
- 실행 중인 두 포트를 실제로 띄우는 통합 테스트로 노출 경계를 계약으로
  고정하고, 기본 프로필에서는 관리 endpoint가 계속 닫혀 있음을 검증한다.
- 이 작업은 노출 경계까지만 다룬다. Prometheus 서버, Grafana와 부하 실험은
  후속 Issue로 분리한다.

## Scope

Included:

- `build.gradle`에 `micrometer-registry-prometheus`를 `runtimeOnly`로 추가
- `application-observability.yml`의 `management.server.port`, endpoint
  활성화·노출과 `show-details: never`
- 실존 Timer 4종의 histogram 활성화
- `ObservabilitySecurityConfiguration`의 Actuator 전용 `SecurityFilterChain`
- app port와 management port를 각각 띄우는 노출 경계 통합 테스트
- 기본 프로필 차단 회귀 테스트
- 노출된 지표의 tag cardinality 검증

## Approved decisions

- `DEC-B1-001`: management port를 8081로 분리한다. host 비공개는 bind address가
  아니라 Compose의 port 경계가 보장한다.
- `DEC-B1-002`: management child context는 부모의 `springSecurityFilterChain`을
  재사용한다. 따라서 child context 전용 보안 구성이 아니라 부모 context의
  Actuator 전용 체인 하나를 추가한다.
- `DEC-B1-003`: matcher를 `EndpointRequest.to("health", "prometheus")`로
  한정하고 `toAnyEndpoint()`를 쓰지 않는다.
- `DEC-B1-004`: registry는 `runtimeOnly`로 넣는다. matcher를 endpoint ID
  문자열로 써서 production source가 Prometheus 클래스를 참조하지 않는다.
- `DEC-B1-005`: 실존 Timer만 histogram을 켠다. 없는 Meter 이름을 설정에 미리
  적지 않는다.
- `DEC-B1-006`: 운영 bucket 경계와 `service-level-objectives`를 정하지 않는다.
- `DEC-B1-007`: 기본 프로필의 차단은 endpoint 비활성과 fallback `denyAll`로
  이중 유지한다.
- `DEC-B1-008`: #215 위에 stacked로 작업하고 PR은 #217 머지 후에 올린다.

## Explicit exclusions

- Compose overlay, Prometheus 서버와 Grafana provisioning
- k6 부하 scenario와 Hikari before/after 실험
- `qello.worker.batch.duration`과 `qello.provider.request.duration` 신규 Timer
- 운영 SLO, alert threshold와 histogram bucket 경계
- dev·stage·prod 주소와 인증 방식
- 기존 API, domain, DB schema, migration과 기존 보안 체인의 matcher 변경
- `HttpRequestLoggingFilter`와 #215가 만든 파일의 동작 변경
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 요구사항·설계·구현 계획 통합 | Orchestrator | Human partner |
| 의존성·설정·보안 체인 구현 | Execution agent | Independent verifier |
| 노출 경계·Meter 계약 시나리오 구현 | Test executor | Independent verifier |
| 전체 diff·보안 경계·tag 비식별 검증 | Independent verifier | Human partner |

구현자는 승인된 구현 계획에 포함된 파일만 수정한다. 검증자는 테스트를
통과시키기 위해 production source나 테스트를 수정하지 않는다.

## Existing user-owned changes

- 이 브랜치는 `origin/chore/gh-215-structured-request-logging`에서 분기했다.
  로컬 `chore/gh-215-structured-request-logging`이 별도 worktree
  (`.worktrees/chore-gh-215-structured-request-logging`)에 checkout돼 있어
  `./harness start`의 로컬 fast-forward는 건너뛰었고, 원격 ref를 base로 썼다.
- `./harness start` 실행 시점의 `git status --short`에는 이 세션에서 생성한
  untracked 설계 문서
  (`docs/superpowers/specs/2026-09-05-observability-metrics-exposure-design.md`)
  하나만 있었고 다른 사용자 변경은 없었다. harness가 clean worktree를
  요구해 파일을 임시로 옮겼다가 브랜치 생성 후 그대로 복원했다.
- Task 1까지 생성·수정한 변경은 이 `TASK.md`, 위 설계 문서와 구현 계획 문서
  뿐이다.
- predecessor #215의 승인·검증 이력은 immutable reference
  `e8383cbfe64e9aa7fcc24fb988d811cb87ce523b:TASK.md`에 보존되어 있다.

## Validation

Focused checks (Task 2 이후):

```bash
./gradlew integrationTest --tests '*ObservabilityEndpointExposureIntegrationTest'
./gradlew integrationTest --tests '*ObservabilityDisabledByDefaultIntegrationTest'
./gradlew integrationTest --tests '*ObservabilityMeterContractIntegrationTest'
```

Final checks:

```bash
./gradlew integrationTest --tests '*Observability*'
./gradlew check
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] 사람이 설계 문서와 이 구현 계획을 승인했다.
- [x] `observability` 프로필에서 management port `/actuator/health`가 200을
      반환한다.
- [x] management port `/actuator/prometheus`가 200과 Prometheus content type을
      반환한다.
- [x] management port `/actuator/env`와 `/api/**`의 응답 코드를 실측해 계약으로
      고정했다.
- [x] app port `/actuator/health`와 `/actuator/prometheus`의 응답 코드를 실측해
      계약으로 고정했다. 둘 중 하나라도 200이면 `DEC-B1-002` 전제가 깨진
      것으로 보고 child context 전용 보안 구성을 재검토한다.
- [x] app port 기존 API의 인증 계약이 바뀌지 않는다.
- [x] 기본 프로필에서 관리 endpoint가 닫혀 있다.
- [x] scrape 본문에 `http.server.requests`,
      `qello.filtering.pipeline.latency`, `hikaricp.connections.acquire`,
      `hikaricp.connections.usage`의 `_bucket`, `_count`, `_sum`이 존재한다.
- [x] 노출된 지표의 tag key에 사용자 식별자, request ID, correlation ID,
      event ID와 예외 메시지가 없다.
- [x] `qello.worker.batch.duration`과 `qello.provider.request.duration`을
      이 작업의 완료 증거에 포함하지 않는다.
- [x] production API, domain, DB schema, migration과 기존 보안 체인의 matcher를
      변경하지 않는다.
- [x] 저장소 필수 검증이 통과하거나 최종 상태를 정확히 `FAIL`/`BLOCKED`로
      기록한다.

## Final verification contract

status: PASS
issue_number: 218
task_id: GH-218-OBSERVABILITY-METRICS-EXPOSURE
design_id: APP-DESIGN-GH-218-001
changed_files: TASK.md; build.gradle; docs/superpowers/plans/2026-09-05-observability-metrics-exposure.md; docs/superpowers/specs/2026-09-05-observability-metrics-exposure-design.md; src/integrationTest/java/com/dnd/qello/ObservabilityDisabledByDefaultIntegrationTest.java; src/integrationTest/java/com/dnd/qello/ObservabilityEndpointExposureIntegrationTest.java; src/integrationTest/java/com/dnd/qello/ObservabilityMeterContractIntegrationTest.java; src/main/java/com/dnd/qello/auth/config/ObservabilitySecurityConfiguration.java; src/main/resources/application-observability.yml
executed_checks: ./gradlew integrationTest --tests '*Observability*'; ./gradlew check; ./harness check; ./harness pr-ready --project-tests; npm run hooks:validate; git diff --check; git diff --name-only origin/chore/gh-215-structured-request-logging...HEAD; rg identifier/secret assignment patterns on observability source and test files
passed_checks: ./gradlew integrationTest --tests '*Observability*'; ./gradlew check; ./harness check; ./harness pr-ready --project-tests; npm run hooks:validate; git diff --check; git diff --name-only origin/chore/gh-215-structured-request-logging...HEAD; rg identifier/secret assignment patterns (0 matches)
failed_checks: none
blocked_checks: none
assumptions: 로컬 Testcontainers PostgreSQL이 persistence를 대표한다; management port는 테스트에서 0으로 덮어 임의 포트를 쓴다
risks: management child context의 보안 체인 재사용 동작은 Spring Boot 3.5.16 기준 실측값이며 버전 상향 시 재확인이 필요하다
required_human_decisions: PR은 #217 머지 후에 올린다

# Test Report: TEST-PLAN-GH-218-OBSERVABILITY-METRICS-EXPOSURE

> Created at: `2026-09-05T19:01:24+09:00`
> GitHub Issue: `#218`
> Branch: `chore/gh-218-observability-metrics-exposure`
> Commit: `3ea9486` (implementation HEAD verified by this run; this report is recorded in the following TASK.md commit)

## 1. Executive summary

- Result: `PASS`
- Tested scope: observability 프로필의 management-port 노출 경계, 기본 프로필 차단, 실존 Timer 4종의 histogram series와 금지 tag 키, 저장소 필수 검증(Gradle check, harness, hooks, whitespace, 변경 파일 범위, 민감정보 패턴)
- Unverified scope: Compose overlay, Prometheus 서버, Grafana, k6 부하, 운영 bind/인증, Worker/provider Timer 신규 계측, `performanceTest` 소스셋, PR 생성과 `origin/main` rebase
- Release recommendation: #218 노출 경계의 로컬 검증은 완료됐다. PR은 #217 머지 후에 올린다. 인프라 apply와 배포는 이 작업 범위가 아니다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | toolchain 21 (Eclipse Temurin 21.0.12.1+1-LTS); host JVM 24.0.2 was not the test toolchain |
| Spring Boot | 3.5.16 |
| Database | Testcontainers PostGIS 16-3.5 local/test-container |
| Test runner | JUnit 5 via Gradle 8.14.3 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew integrationTest --tests '*Observability*'` | PASS | 9 tests, 0 failed, 0 error, 0 skipped (노출 5 + 기본 2 + Meter 2) | 17s | BUILD SUCCESSFUL, exit 0 |
| `./gradlew check` | PASS | unit 1056; integration 735; architecture 23; source convention 3; all 0 failed/error/skipped | successful run 6m 11s | BUILD SUCCESSFUL, exit 0; Spotless/Checkstyle main passed; checkstyleTest and checkstyleIntegrationTest skipped by project config |
| `./harness check` | PASS | n/a | ~1s | Secret preflight 1337 text files; JUnit policy 285 files; convention, commit-msg formatter, workflow (5 files), label, Husky, Java convention self-test passed |
| `./harness pr-ready --project-tests` | PASS | Gradle `check` UP-TO-DATE (14 tasks) | ~3s | Local PR readiness checks passed, exit 0 |
| `npm run hooks:validate` | PASS | n/a | <1s | Husky validation passed, exit 0 |
| `git diff --check` | PASS | n/a | <1s | empty output, exit 0 |
| Unit | PASS | 1056 / 0 / 0 / 0 | included in `check` | JUnit XML |
| Integration | PASS | 735 / 0 / 0 / 0 | included in `check` | JUnit XML |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| INT-001 | PASS | ObservabilityEndpointExposureIntegrationTest#exposesHealthOnManagementPort | management `/actuator/health` = 200 |
| INT-002 | PASS | ObservabilityEndpointExposureIntegrationTest#exposesPrometheusOnManagementPort | management `/actuator/prometheus` = 200 and `text/plain` |
| INT-003 | PASS | ObservabilityEndpointExposureIntegrationTest#blocksNonExposedPathsOnManagementPort | management `/actuator/env` and `/api/**` contracted to 401; not 200 |
| INT-004 | PASS | ObservabilityEndpointExposureIntegrationTest#keepsManagementEndpointsClosedOnAppPort | app `/actuator/health` and `/actuator/prometheus` contracted to 401; not 200, so DEC-B1-002 stop condition did not fire |
| INT-005 | PASS | ObservabilityDisabledByDefaultIntegrationTest#keepsManagementEndpointsClosedWithoutObservabilityProfile | default profile management endpoints are not 200 |
| INT-006 | PASS | ObservabilityDisabledByDefaultIntegrationTest#doesNotRegisterObservabilitySecurityChainWithoutProfile | observability security chain bean is absent without the profile |
| INT-007 | PASS | ObservabilityMeterContractIntegrationTest#exposesHistogramSeriesForConfiguredTimers | four existing Timers expose `_bucket`, `_count`, `_sum` |
| INT-008 | PASS | ObservabilityMeterContractIntegrationTest#keepsExposedTagsBounded | forbidden identifier tag keys are absent |
| INT-009 | PASS | ObservabilityEndpointExposureIntegrationTest#keepsAppApiAuthenticationContractUnchanged | unauthenticated domain API remains 401 |

## 5. Failures and diagnostics

필수 검증 실패는 없다.

첫 `./gradlew check` 호출은 `:test` 완료 후 `:integrationTest` 진행 중 도구
wrapper 제한으로 중단됐다. 같은 세션에서 재실행해 BUILD SUCCESSFUL을 얻었고,
재실행은 이미 통과한 `:test`를 UP-TO-DATE로 재사용했다.

`./harness pr-ready --project-tests`는 종료 코드 0으로 통과했다. 로컬 stacked base
브랜치 fast-forward는 해당 브랜치가 다른 worktree에 checkout되어 있어 건너뛰었고,
`origin/chore/gh-215-structured-request-logging` ancestor 검사는 통과했다. 이
경고는 코드 결함이 아니며 `./harness sync`는 실행하지 않았다.

## 6. Potential issues

### Application code

- Actuator 전용 체인은 `EndpointRequest.to("health", "prometheus")`와
  `@Order(-1)`에 의존한다. 이후 endpoint를 추가하면 matcher를 같이 바꾸지 않으면
  fallback `denyAll`에 막히거나, 반대로 matcher를 넓히면 노출 범위가 커진다.

### Infrastructure and resource limits

- host 비공개는 bind address가 아니라 Compose port 경계가 보장한다(DEC-B1-001).
  Compose overlay는 이 Issue 범위가 아니다.

### Database and migrations

- DB schema와 migration 파일은 이 브랜치에서 변경되지 않았다. persistence 검증은
  Testcontainers PostGIS에 한정된다.

### Concurrency and idempotency

- scrape와 노출 경계 테스트는 단일 요청이다. 동시 scrape 부하와 cardinality 폭증은
  검증하지 않았다.

### Transactions and event ordering

- 트랜잭션, outbox, 도메인 이벤트 경로는 변경하지 않았다.

### External APIs

- Prometheus 서버 scrape, Grafana, 외부 인증은 이 작업에서 실행하지 않았다.

### Failure recovery and reconciliation

- management child context가 부모 `springSecurityFilterChain`을 재사용하는 동작은
  Spring Boot 3.5.16 실측값이다. 버전 상향 시 app port에서 Actuator가 200을
  반환하면 DEC-B1-002 전제를 재검토해야 한다.

## 7. Regression and residual risk

- 변경 파일은 stacked base 대비 허용 목록 9개뿐이다. 기존
  `SecurityConfiguration`, `HttpRequestLoggingFilter`, domain API, DB schema는
  diff에 없다.
- 이 완료 증거는 네 실존 Timer의 histogram series와 노출 경계만 포함한다.
  Worker/provider Timer 이름은 완료 증거로 사용하지 않는다.
- PR, `origin/main` rebase, 경로 B2/B3, 운영 인증은 후속 작업이다.

## 8. Artifacts

- Test plan: `docs/superpowers/plans/2026-09-05-observability-metrics-exposure.md`
- CI run: 로컬 실행만. GitHub Actions run은 이 작업에서 만들지 않았다.
- Related ADR: `docs/superpowers/specs/2026-09-05-observability-metrics-exposure-design.md` (`APP-DESIGN-GH-218-001`)
- PR: not created; required after #217 merge
- Predecessor #215 audit: immutable git object `e8383cbfe64e9aa7fcc24fb988d811cb87ce523b:TASK.md`

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 (B2/B3와 Worker Timer는 별도 Issue로 예정이며 이 작업에서 번호를 만들지 않음; PR 게이트는 #217)
- [ ] 실행 결과와 PR 설명이 일치함 (PR not created)
