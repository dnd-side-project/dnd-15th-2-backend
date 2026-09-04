# GitHub Issue #215 Task Contract

> Generated at: `2026-09-05T02:13:20+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `HTTP 요청 구조화 로그와 Request ID 추적 도입`
- GitHub Issue: `#215`
- Branch: `chore/gh-215-structured-request-logging`
- Base branch: `main`
- Task ID: `GH-215-STRUCTURED-REQUEST-LOGGING`
- Design ID: `APP-DESIGN-GH-215-001`
- Design path:
  `docs/superpowers/specs/2026-09-05-structured-request-logging-design.md`
- Design status: `APPROVED_FOR_IMPLEMENTATION_PLAN`
- Approved architecture choice: `DEC-215-001` Filter 단독 구조
- Architecture approval evidence: `2026-09-05T02:11:00+09:00` 사용자가
  `1번으로 설계확정`이라고 명시함
- Test plan: `TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING`
- Test plan path:
  `docs/test-plans/gh-215-TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING.md`
- Test plan status: `APPROVED_FOR_IMPLEMENTATION_PLAN`
- Written plan approval evidence: `2026-09-05T02:29:39+09:00` 사용자가
  설계 spec과 테스트 계획을 `승인할게`라고 명시함
- Implementation plan:
  `docs/superpowers/plans/2026-09-05-structured-request-logging.md`
- Implementation plan status: `DRAFT_FOR_HUMAN_APPROVAL`
- Implementation gate: `BLOCKED_PENDING_PLAN_APPROVAL`

## Objective

- 안전하게 검증한 `X-Request-ID`로 동기 HTTP 요청의 성공·오류 로그를 연결한다.
- 모든 HTTP 응답에 최종 Request ID를 반환하고 요청 종료 시 route template,
  method, status와 duration을 일관된 필드로 기록한다.
- `observability` 프로필에서 Spring Boot 3.5 내장 ECS JSON stdout을 사용하고
  기본 프로필의 출력 형식은 바꾸지 않는다.
- 성공, 예외, Security 조기 종료와 같은 thread 재사용 뒤에 요청 MDC가 남지
  않음을 검증한다.

## Scope

Included:

- 최외곽 `OncePerRequestFilter` 하나가 소유하는 Request ID 검증·생성·응답 헤더·MDC lifecycle
- 외부 Request ID 허용식 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`
- 누락, 공백, 65자 이상, 허용하지 않은 문자, 복수 헤더를 소문자 UUID로 교체
- 완료 event의 `requestId`, `route`, `method`, `status`, `durationMs`
- 기존 `APP_ERROR` event의 `errorCode`, `errorType`와 동일 Request ID 연계
- `observability` 프로필 전용 Spring Boot 내장 ECS console format
- 성공·예외·인증 실패·미매핑·같은 thread 재사용의 로그 및 MDC 검증
- 요청/응답 body, 인증정보, 위치와 사용자 식별정보 비기록 검증

## Approved decisions

- `DEC-215-001`: 최우선 순위 Servlet Filter 하나가 Security와 MVC를 포함한
  동기 HTTP 요청 전체를 감싼다. MVC Interceptor는 추가하지 않는다.
- `DEC-215-002`: 외부 `X-Request-ID`는 정확히 한 값이며 1~64자 ASCII
  allowlist와 일치할 때만 재사용한다. 그 외에는 UUID를 생성한다.
- `DEC-215-003`: `requestId`만 요청 전 구간의 MDC에 둔다. 완료 event의
  route, method, status, durationMs는 SLF4J key-value field로 기록한다.
- `DEC-215-004`: MVC route template을 확인할 수 없으면 실제 URI 대신
  고정값 `UNRESOLVED`를 기록한다.
- `DEC-215-005`: 완료 event 이름은 `http_request_completed`로 고정하고
  측정에는 `System.nanoTime()`을 사용한다. 시간은 품질 임계값으로 쓰지 않는다.
- `DEC-215-006`: 구조화 출력은 `application-observability.properties`에서
  Spring Boot 내장 `ecs`만 활성화한다. custom encoder와 `logback-spring.xml`은
  만들지 않는다.
- `DEC-215-007`: 비동기 correlation, Outbox/Worker 전파와 DB migration은
  별도 Issue로 남긴다.

## Explicit exclusions

- Outbox와 Worker의 correlation ID 저장·복원
- DB schema, Flyway migration, transaction과 repository 변경
- `@Async` 또는 executor MDC 복사
- Loki, CloudWatch, OpenSearch, tracing backend와 로그 전송기 구성
- Prometheus, Grafana, Actuator endpoint 노출과 보안 체인 변경
- request/response body logging 또는 사용자·위치·인증정보 logging
- custom JSON encoder, custom Logback 설정과 수집기 종속 필드
- API body와 오류 코드 계약 변경
- 배포, 프로덕션 설정 변경과 외부 서비스 호출

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 요구사항·설계·테스트 계획 통합 | Orchestrator | Human partner |
| Filter 및 profile 구현 | Execution agent | Independent verifier |
| unit/integration scenario 구현 | Test executor | Independent verifier |
| 전체 diff·보안·로그 비식별 검증 | Independent verifier | Human partner |

구현자는 승인된 구현 계획에 포함된 파일만 수정한다. 검증자는 테스트를 통과시키기
위해 production source나 테스트를 수정하지 않는다.

## Existing user-owned changes

- #215 worktree 생성 전 원래 checkout의 #214 변경은 별도 worktree에 보존했다.
- 이 worktree는 `origin/main`의 `51e054b`에서 분기했다.
- 계획 시작 시 worktree는 깨끗했고 기존 사용자 변경은 없었다.
- 현재 계획 변경은 `TASK.md`, #215 설계 spec, #215 테스트 계획과 #215 구현
  계획뿐이다.

## Validation

Planning checks:

```bash
rg -n "T[B]D|T[O]DO|PLACE[H]OLDER" TASK.md \
  docs/superpowers/specs/2026-09-05-structured-request-logging-design.md \
  docs/test-plans/gh-215-TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING.md \
  docs/superpowers/plans/2026-09-05-structured-request-logging.md
git diff --check
./harness status
```

Implementation checks after human approval:

```bash
./gradlew test --tests '*HttpRequestLoggingFilterTest'
./gradlew integrationTest --tests '*HttpRequestLoggingSecurityIntegrationTest'
./gradlew integrationTest --tests '*StructuredLoggingProfileIntegrationTest'
./gradlew check
./harness test-run --id TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] GitHub Issue #215와 일치하는 격리 worktree 및 branch가 존재한다.
- [x] Request ID 검증 규칙과 Filter 단독 구조를 사람이 확정했다.
- [x] 설계 spec과 테스트 계획을 사람이 승인했다.
- [ ] 승인된 구현 계획이 존재한다.
- [ ] 모든 HTTP 응답이 최종 `X-Request-ID`를 반환한다.
- [ ] mapped 요청은 route template을, 미확인 요청은 `UNRESOLVED`를 기록한다.
- [ ] 성공·예외·Security 종료 event가 method, status와 durationMs를 기록한다.
- [ ] 기존 error event가 동일 requestId와 errorCode/errorType을 가진다.
- [ ] 같은 thread와 동시 요청 사이에 MDC가 누출되지 않는다.
- [ ] `observability` 프로필만 ECS JSON console format을 사용한다.
- [ ] 로그에 body, token, 위치, 사용자 식별정보와 실제 URI가 포함되지 않는다.
- [ ] DB, migration, async correlation, metrics와 외부 수집기를 변경하지 않는다.
- [ ] 저장소 필수 검증을 통과하거나 실패·차단 범위를 정확히 기록한다.
