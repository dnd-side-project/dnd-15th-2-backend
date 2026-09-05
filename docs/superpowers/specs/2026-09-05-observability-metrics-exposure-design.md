# 관측 지표 노출 경계 설계 (경로 B1)

> Design ID: `APP-DESIGN-GH-218-001`
> GitHub Issue: `#218`
> Task ID: `GH-218-OBSERVABILITY-METRICS-EXPOSURE`
> Base branch: `chore/gh-215-structured-request-logging` (PR #217)
> Status: `APPROVED_FOR_IMPLEMENTATION`
> Design approval evidence: `2026-09-05T17:46:09+09:00` 사용자가 설계 섹션
> §1~§6에 `승인할게`라고 명시함
> Implementation approval evidence: 사용자가 `Subagent-Driven 으로 구현 시작해줘`라고
> 명시함
> Baseline commit: `1ad4c76`

## 1. 목적

Qello 프로세스 안에만 머무는 Micrometer 지표를 Prometheus가 읽을 수 있는
출구를 만든다. 출구는 API listener와 분리된 management port에 두고, 노출
대상은 `health`와 `prometheus` 두 endpoint로 한정한다.

이 작업의 성과물은 dashboard나 부하 실험 결과가 아니라 **노출 경계의 계약**이다.
어떤 지표를 어떤 이름으로 내보내고, 어느 listener에서만 접근할 수 있으며,
기본 프로필에서는 계속 닫혀 있다는 사실을 실행 가능한 테스트로 고정한다.

Prometheus 서버, Grafana, k6와 운영 임계값은 이 설계에 포함하지 않는다.

## 2. 현재 상태와 문제

`1ad4c76` 기준 현재 상태는 다음과 같다.

- `spring-boot-starter-actuator`는 이미 의존성에 있다 (`build.gradle:83`).
- Micrometer `MeterRegistry`를 쓰는 custom 계측이 두 곳 있다.
  - `FilteringMetrics` — Timer 2종(`qello.filtering.pipeline.latency`,
    `qello.filtering.queue.dwell`)과 Counter 8종
  - `WorkerMetrics` — Counter 3종. Timer는 없다
- exporter registry는 없다. `build.gradle:76-83` 주석이 그 이유를
  "관측·경보 도구가 아직 미결정"(#113)으로 기록하고 있다.
- 관리 endpoint는 전부 닫혀 있다 (`application.properties:111-112`,
  PR #217 이후에는 `application.yml`).
- `SecurityConfiguration`은 `@Order(0)` apiDocs, `@Order(1)` backoffice,
  `@Order(2)` operator API, `@Order(3)` app API와
  `@Order(LOWEST_PRECEDENCE)` fallback `denyAll` 다섯 체인으로 구성된다.

따라서 현재는 MeterRegistry 안에 값이 쌓이지만 프로세스 밖에서 읽을 방법이
없고, Actuator 설정만 켜도 fallback `denyAll` 때문에 scrape가 성공한다고
가정할 수 없다.

## 3. 범위와 비범위

### 포함

- Prometheus registry 런타임 의존성
- `observability` 프로필의 management port 분리
- `health`, `prometheus` 두 endpoint만 활성화·노출
- Actuator 전용 `SecurityFilterChain`
- 실제로 존재하는 Timer 4종의 histogram 활성화
- 두 포트를 실제로 띄우는 노출 경계 통합 테스트
- 기본 프로필의 차단 상태 회귀 테스트
- tag cardinality 검증

### 제외

- Prometheus 서버, Grafana, Compose overlay (경로 B2)
- k6 부하 scenario와 Hikari before/after 실험 (경로 B3)
- `qello.worker.batch.duration` 신규 Timer (Worker Observability)
- `qello.provider.request.duration` 신규 Timer와 provider tag 계약
  (Worker Observability)
- 운영 SLO, alert threshold와 운영 bucket 경계
- dev·stage·prod 주소, 인증 방식과 배포 구성
- correlation ID, tracing backend와 로그 수집기
- 기존 API, domain, DB schema와 migration 변경

## 4. 설계 결정

### DEC-B1-001: management port를 8081로 분리한다

`management.server.port=8081`을 `observability` 프로필에만 지정한다.

노출 차단을 애플리케이션 설정 하나에 의존하지 않기 위해서다. 포트가 분리되면
Compose가 8081을 host에 publish하지 않는 것만으로 외부 접근이 막히고, 설정
실수 한 줄이 곧 유출로 이어지지 않는다.

`management.server.address=127.0.0.1`로 묶지 않는다. Prometheus 컨테이너가
다른 컨테이너에서 접근해야 하므로, host 비공개는 bind address가 아니라 Compose의
port와 network 경계가 보장한다.

### DEC-B1-002: 부모 context에 Actuator 전용 체인을 명시한다

Spring Boot 3.5.16의 management child context는 보안 체인을 독립적으로 만들지
않고 부모의 `springSecurityFilterChain`과 filter registration을 재사용한다.
따라서 현재 `@Order(0~3)`과 fallback `denyAll` 체인이 8081 요청 평가에도
참여한다. 동시에 custom `SecurityFilterChain` bean이 하나라도 있으면 Actuator
기본 보안 자동 설정은 완전히 back off한다.

즉 체인을 추가하지 않으면 8081이 무방비로 열리는 것이 아니라 fallback
`denyAll`에 걸려 `health`와 `prometheus`까지 닫힌다. 그러므로 필요한 것은
child context 전용 보안 구성이 아니라 **부모 context의 Actuator 전용 체인**
하나다.

`@ManagementContextConfiguration`을 이용한 child context 전용 보안 구성은
§8의 통합 테스트가 이 전제를 반증할 때만 검토한다.

### DEC-B1-003: matcher를 두 endpoint로 한정한다

`EndpointRequest.toAnyEndpoint()`를 쓰지 않는다. `toAnyEndpoint()`는 나중에
누군가 endpoint를 추가로 활성화하면 그 endpoint까지 자동으로 이 체인의
`permitAll` 아래로 들어온다. 노출 목록이 보안 체인과 따로 자라지 않도록
matcher에 `health`와 `prometheus`를 직접 적는다.

이 matcher에 걸리지 않는 나머지 `/actuator/**`는 새 차단 규칙을 만들지 않고
기존 fallback `denyAll`이 처리한다.

### DEC-B1-004: Prometheus registry는 `runtimeOnly`로 넣는다

matcher를 `EndpointRequest.to(HealthEndpoint.class, PrometheusScrapeEndpoint.class)`가
아니라 endpoint ID 문자열 `EndpointRequest.to("health", "prometheus")`로
작성하면 production source가 Prometheus 클래스를 직접 참조하지 않는다.
그러면 registry를 `runtimeOnly`로 둘 수 있고, 컴파일 단위에서 exporter 선택이
드러나지 않는다.

classpath에 있으면 auto-configuration이 `PrometheusMeterRegistry`를 항상
생성하지만, 그 값은 프로세스 안에만 머문다. 노출 여부는 endpoint 설정과 보안
체인이 단독으로 결정한다. 의존성은 게이트가 아니다.

### DEC-B1-005: 실제로 존재하는 Meter만 histogram을 켠다

histogram 대상은 다음 네 개다.

| Meter | 출처 | 알고 싶은 것 |
| --- | --- | --- |
| `http.server.requests` | Spring Boot 자동 계측 | route별 API p95·p99 |
| `qello.filtering.pipeline.latency` | `FilteringMetrics` Timer | 판정 경로 지연 분포 |
| `hikaricp.connections.acquire` | `DataSourcePoolMetricsAutoConfiguration` | connection 획득 대기시간 |
| `hikaricp.connections.usage` | `DataSourcePoolMetricsAutoConfiguration` | connection 점유시간 |

Hikari Timer 두 개는 경로 B3의 connection 경합 분석에 직접 필요하며 새 계측
없이 이미 등록된다.

존재하지 않는 Meter 이름을 설정에 미리 적지 않는다. Spring Boot는 알 수 없는
Meter 이름을 오류 없이 무시하므로, 설정 파일만 보고 관측이 되고 있다고 오해할
수 있다. `_bucket` 시계열은 실제 Meter가 등록되고 기록된 뒤에만 검증할 수 있다.

따라서 `qello.worker.batch.duration`과 `qello.provider.request.duration`은
Timer 자체가 없으므로 이 설계에서 설정하지 않고 Worker Observability로 이관한다.

### DEC-B1-006: 운영 bucket 경계를 정하지 않는다

`percentiles-histogram: true`만 지정하고 SLO 기반 bucket 경계나
`service-level-objectives`는 지정하지 않는다. 운영 SLO와 stage baseline이 아직
없으므로, 지금 정한 경계는 근거가 없고 나중에 바꿀 때 시계열 호환성만 깨뜨린다.

### DEC-B1-007: 기본 프로필의 차단은 이중으로 유지한다

`application.yml`의 `management.endpoints.enabled-by-default: false`와 빈
`exposure.include`를 변경하지 않는다. `observability` 프로필이 아니면
Actuator 전용 체인 bean도 생성되지 않는다.

따라서 기본 프로필에서는 endpoint 비활성과 fallback `denyAll`이 각각 독립적으로
접근을 막는다. 둘 중 하나가 실수로 풀려도 나머지가 남는다.

### DEC-B1-008: #217 위에 stacked로 작업한다

PR #217은 `application.properties`를 `application.yml`로 옮기고
`application-observability.yml`을 신규 생성했다. 이 설계가 수정할 파일이 정확히
그 두 개다. `main`에서 분기하면 delete/modify 충돌과 add/add 충돌이 동시에
발생한다.

`./harness start --base chore/gh-215-structured-request-logging`으로 분기하고,
PR은 #217이 머지된 뒤에 올린다. #217이 리뷰에서 크게 바뀌면 rebase한다.

## 5. 구성 요소와 파일

| 파일 | 변경 |
| --- | --- |
| `build.gradle` | `runtimeOnly 'io.micrometer:micrometer-registry-prometheus'` 추가 |
| `build.gradle` 관측 의존성 주석 | #113 결정을 갱신하고 뒤집은 시점과 이유를 남긴다 |
| `src/main/resources/application.yml` | 변경 없음 |
| `src/main/resources/application-observability.yml` | management·histogram 블록 추가 |
| `src/main/java/com/dnd/qello/auth/config/ObservabilitySecurityConfiguration.java` | 신규 |
| `src/integrationTest/java/com/dnd/qello/ObservabilityEndpointExposureIntegrationTest.java` | 신규 |
| `src/integrationTest/java/com/dnd/qello/ObservabilityDisabledByDefaultIntegrationTest.java` | 신규 |
| `src/integrationTest/java/com/dnd/qello/ObservabilityMeterContractIntegrationTest.java` | 신규 |

## 6. 설정 계약

`application-observability.yml`에 다음 블록을 추가한다. 기존 ECS 로깅 설정은
그대로 둔다.

```yaml
management:
  server:
    port: 8081
  endpoints:
    enabled-by-default: false
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      enabled: true
      show-details: never
    prometheus:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        "[http.server.requests]": true
        "[qello.filtering.pipeline.latency]": true
        "[hikaricp.connections.acquire]": true
        "[hikaricp.connections.usage]": true
```

Meter 이름에는 점이 들어가므로 YAML map key를 `"[...]"` 대괄호로 감싼다.
감싸지 않으면 `http: server: requests:` 중첩 map으로 파싱되어 설정이 조용히
무시된다. PR #217이 properties를 yml로 옮기면서 생긴 차이이며, properties
문법을 그대로 옮기면 실패하는 지점이다.

`show-details: never`로 health 응답에서 DB·디스크 세부 정보를 감춘다.

## 7. 보안 체인 계약

```java
@Bean
@Order(-1)
@Profile("observability")
SecurityFilterChain observabilitySecurityFilterChain(HttpSecurity http)
```

- `@Order(-1)`로 기존 `@Order(0)` apiDocs 체인보다 앞에 둔다.
- `securityMatcher(EndpointRequest.to("health", "prometheus"))`
- 두 endpoint만 `permitAll`
- `SessionCreationPolicy.STATELESS`
- CSRF 비활성, `formLogin`·`httpBasic`·`logout` 비활성 — 기존 네 체인과 같은 형태
- `exceptionHandling`은 지정하지 않는다. `permitAll`이라 인증 실패 경로가 없다.

이 체인은 `observability` 프로필에서만 생성된다. 다른 프로필에서는 bean이
없으므로 `/actuator/**` 전체가 fallback `denyAll`로 간다.

## 8. 테스트 전략

### 실제 두 포트를 띄운다

`@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "management.server.port=0")`와
`@LocalServerPort`·`@LocalManagementPort`, `TestRestTemplate`을 쓴다. 저장소 관례에
따라 `@Value("${local.management.port}")`로 주입해도 `@LocalManagementPort`와
동일하게 resolved management port를 받는 방식으로 허용한다.

각 테스트 클래스의 `Created at` 주석에는 파일을 실제로 생성한 시각을 정확한
ISO 8601 형식으로 기록한다. 계획 문서의 예시 시각을 복사하지 않는다.

MockMvc는 servlet container를 띄우지 않아 포트 경계와 management child context를
재현하지 못한다. 이 설계의 핵심 주장이 "두 listener가 다르게 동작한다"이므로
MockMvc로는 검증할 수 없다.

이는 저장소의 기존 통합 테스트 선례와 다른 점이다.
`HttpRequestLoggingSecurityIntegrationTest`(#217)를 포함한 기존 테스트는
`@AutoConfigureMockMvc`를 쓴다. 여기서 MockMvc를 쓰지 않는 이유는 위와 같으며,
이 차이는 테스트 클래스 주석에 남긴다.

### 테스트 하네스 구성

세 테스트 클래스 모두 `PostgisContainerIntegrationTestSupport`를 상속한다.
Testcontainers PostgreSQL이 있어야 애플리케이션이 기동하고,
`hikaricp.connections.*` Meter도 실제 DataSource에서만 등록된다.

프로필 구성은 다음과 같다.

| 테스트 | 프로필 | 비고 |
| --- | --- | --- |
| 노출 경계 | `test`, `observability` | `observability`가 뒤에 와야 management 블록이 이긴다 |
| 기본 차단 회귀 | `test` | `observability` 없이 기본 상태를 확인한다 |
| Meter 계약 | `test`, `observability` | DB 호출 후 scrape |

`observability` 프로필의 `management.server.port: 8081`은 테스트에서
`management.server.port=0`으로 덮어 임의 포트를 받는다. 고정 포트를 쓰면 병렬
실행과 개발자 로컬 환경에서 충돌한다.

### 노출 경계 시나리오

| # | 요청 | 고정할 결과 |
| --- | --- | --- |
| 1 | management port `/actuator/health` | 200 |
| 2 | management port `/actuator/prometheus` | 200, Prometheus content type |
| 3 | management port `/actuator/env` | 실측한 차단 코드를 고정 |
| 4 | management port `/api/**` | 실측한 코드를 고정 |
| 5 | app port `/actuator/health` | 실측한 코드를 고정 |
| 6 | app port `/actuator/prometheus` | 실측한 코드를 고정 |
| 7 | app port 기존 API | 기존 인증 계약 불변 |
| 8 | 기본 프로필 management endpoint | 계속 닫힘 (별도 테스트 클래스) |

3~6행을 "실측 고정"으로 두는 이유는 `EndpointRequest` matcher가 management
포트와 app 포트에서 각각 어떻게 평가되는지가 이 설계의 유일한 미확정 지점이기
때문이다. 기대값을 먼저 적고 구현을 맞추지 않는다. 한 번 측정해 확정한 값을
계약으로 고정한다.

5행 또는 6행이 200으로 나오면 app listener에 관리 endpoint가 노출된다는
뜻이므로 DEC-B1-002의 전제가 깨진 것이다. 그때 `@ManagementContextConfiguration`
기반 child context 전용 보안 구성을 검토한다.

### Meter 계약 시나리오

- HTTP 요청 1건과 DB 접근 1건을 발생시킨 뒤 scrape 본문을 검사한다.
  `hikaricp.connections.acquire`는 connection을 최소 1회 획득한 뒤에야
  등록되므로 DB 호출이 전제 조건이다.
- 네 Meter 각각에 대해 `_bucket`, `_count`, `_sum`이 존재하는지 확인한다.
- tag key 집합이 bounded 값만인지 확인한다. 사용자 식별자, request ID,
  correlation ID, event ID, 예외 메시지가 tag에 없어야 한다.
- `qello.worker.batch.duration`과 `qello.provider.request.duration`은 이
  단계의 완료 증거에 포함하지 않는다.

### 회귀

- 기본 프로필에서 관리 endpoint가 닫혀 있는지 별도 테스트 클래스로 확인한다.
- 기존 인증 계약을 바꾸지 않았음을 app port 요청으로 확인한다.

## 9. 경로 B 분해와 의존성

| 단계 | 범위 | 선행 조건 |
| --- | --- | --- |
| B1 (이 설계) | 앱 관측 출구와 노출 경계 | PR #217 |
| B2 | Compose overlay, Prometheus·Grafana provisioning, 성능 전용 DB volume, 자원 조건 기록 | B1 머지 |
| B3 | k6 인증·fixture, baseline saturation 탐색, 변수 1개 before/after, API·Hikari E3 보고서 | B2 머지 |

각 단계는 별도 GitHub Issue, 별도 branch, 별도 `TASK.md`와 별도 승인을 가진다.
이 설계는 B1만 다룬다.

## 10. 위험과 완화

### YAML 대괄호 표기 누락

점이 포함된 Meter 이름을 따옴표 없이 쓰면 histogram 설정이 조용히 무시된다.
§8의 Meter 계약 테스트가 `_bucket` 존재를 직접 확인하므로 설정만 보고 통과했다고
판단할 수 없다.

### management child context 전제

DEC-B1-002는 child context가 부모의 보안 체인을 재사용한다는 전제 위에 있다.
전제가 틀리면 §8의 3~6행이 예상과 다른 값을 낸다. 그 값을 숨기거나 테스트를
맞추지 않고, 측정값을 근거로 child context 전용 구성을 별도로 검토한다.

### Hikari Meter 등록 시점

`hikaricp.connections.*`는 connection 획득 전에는 registry에 없다. DB 접근이
없는 테스트에서 검증하면 통과처럼 보이는 빈 결과가 나온다. Meter 계약 테스트는
DB 호출을 명시적 전제로 둔다.

### #217 리뷰 변경

base branch가 리뷰 중이므로 `application.yml`과 `application-observability.yml`이
바뀔 수 있다. 충돌은 로컬에서 해결하고 자동으로 정리하지 않는다. 이미 push한
뒤 rebase하면 `git push --force-with-lease`만 사용한다.

### 관측 stack이 실험 대상 자원을 소비함

이 설계 범위에서는 Prometheus 서버를 띄우지 않으므로 해당하지 않는다. B2에서
자원 조건 기록과 함께 다룬다.

### 로컬 결과의 해석 한계

이 단계가 답할 수 있는 질문은 "앱이 지표를 안전하게 노출하는가"뿐이다.
"부하에서 어떤 병목이 발생하는가"는 B3의 질문이다. 완료 보고에서 두 질문을
섞지 않는다.

## 11. 구현 게이트

다음 순서를 모두 지난 뒤에만 구현을 시작한다.

1. 사람이 이 spec을 검토하고 승인한다.
2. `/harness-issue`로 B1 GitHub Issue를 생성하고 Project 필드와 라벨을 연결한다.
3. `./harness start --base chore/gh-215-structured-request-logging`으로 branch를
   만든다.
4. `./harness task-init`으로 `TASK.md`를 생성하고 Issue 번호, Task ID와 Design ID를
   기록한다.
5. writing-plans로 구현 계획을 작성하고 사람이 승인한다.

필수 검증은 다음과 같다.

```bash
./gradlew integrationTest --tests '*Observability*'
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

PR은 #217이 머지된 뒤에 올린다.
