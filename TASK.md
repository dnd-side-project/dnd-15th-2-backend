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
- [ ] `observability` 프로필에서 management port `/actuator/health`가 200을
      반환한다.
- [ ] management port `/actuator/prometheus`가 200과 Prometheus content type을
      반환한다.
- [ ] management port `/actuator/env`와 `/api/**`의 응답 코드를 실측해 계약으로
      고정했다.
- [ ] app port `/actuator/health`와 `/actuator/prometheus`의 응답 코드를 실측해
      계약으로 고정했다. 둘 중 하나라도 200이면 `DEC-B1-002` 전제가 깨진
      것으로 보고 child context 전용 보안 구성을 재검토한다.
- [ ] app port 기존 API의 인증 계약이 바뀌지 않는다.
- [ ] 기본 프로필에서 관리 endpoint가 닫혀 있다.
- [ ] scrape 본문에 `http.server.requests`,
      `qello.filtering.pipeline.latency`, `hikaricp.connections.acquire`,
      `hikaricp.connections.usage`의 `_bucket`, `_count`, `_sum`이 존재한다.
- [ ] 노출된 지표의 tag key에 사용자 식별자, request ID, correlation ID,
      event ID와 예외 메시지가 없다.
- [ ] `qello.worker.batch.duration`과 `qello.provider.request.duration`을
      이 작업의 완료 증거에 포함하지 않는다.
- [ ] production API, domain, DB schema, migration과 기존 보안 체인의 matcher를
      변경하지 않는다.
- [ ] 저장소 필수 검증이 통과하거나 최종 상태를 정확히 `FAIL`/`BLOCKED`로
      기록한다.
