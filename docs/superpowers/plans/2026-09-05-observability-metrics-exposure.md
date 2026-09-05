# Prometheus 지표 노출 경계와 management port 분리 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `observability` 프로필에서만 별도 management port(8081)로 `health`와 `prometheus` 두 endpoint를 열고, 그 노출 경계를 실제 두 포트를 띄우는 통합 테스트로 고정한다.

**Architecture:** Prometheus registry를 `runtimeOnly`로 추가하고 `application-observability.yml`에 management port와 endpoint 노출을 선언한다. management child context는 부모의 `springSecurityFilterChain`을 재사용하므로, child context 전용 보안 구성 대신 부모 context에 `@Order(-1)` Actuator 전용 `SecurityFilterChain`을 하나 추가한다. matcher는 `EndpointRequest.to("health", "prometheus")`로 한정하고, 나머지 `/actuator/**`는 기존 fallback `denyAll`이 처리한다.

**Tech Stack:** Spring Boot 3.5.16, Spring Security, Micrometer, `micrometer-registry-prometheus`, JUnit 5, Testcontainers PostgreSQL(PostGIS), AssertJ

**Spec:** `docs/superpowers/specs/2026-09-05-observability-metrics-exposure-design.md`

## Global Constraints

- 브랜치는 `chore/gh-218-observability-metrics-exposure`, base는 `chore/gh-215-structured-request-logging`이다. PR은 #217 머지 후에 올린다.
- 커밋 메시지 형식은 `<type>(<scope>): <summary> (#218)`이며 Issue 번호는 필수다. 이 작업의 type은 `chore`다.
- Java 들여쓰기는 탭이다. 기존 `SecurityConfiguration`의 형식을 그대로 따른다.
- 모든 테스트 메서드에 `@DisplayName`을 붙인다.
- 모든 테스트 클래스 상단에 ISO 8601 생성 시각과 `Source scenario: TEST-PLAN-GH-218-OBSERVABILITY-METRICS-EXPOSURE-INT-<NNN>`을 주석으로 기록한다.
- 통합 테스트는 `PostgisContainerIntegrationTestSupport`를 상속한다.
- `EndpointRequest.toAnyEndpoint()`를 쓰지 않는다.
- 존재하지 않는 Meter 이름을 설정에 적지 않는다. histogram 대상은 `http.server.requests`, `qello.filtering.pipeline.latency`, `hikaricp.connections.acquire`, `hikaricp.connections.usage` 네 개뿐이다.
- YAML에서 점이 포함된 Meter 이름은 `"[...]"` 대괄호로 감싼다.
- 기존 `SecurityConfiguration`의 다섯 체인, `HttpRequestLoggingFilter`, DB schema, migration과 도메인 API를 변경하지 않는다.
- Secret, 토큰, 계정 식별자, `.env` 값을 코드·테스트·문서에 기록하지 않는다.

---

### Task 1: 계획 산출물 커밋

설계 문서, `TASK.md` 계약, 이 구현 계획을 하나의 검토 목적으로 커밋한다. 코드 변경은 없다.

**Files:**
- Create: `docs/superpowers/specs/2026-09-05-observability-metrics-exposure-design.md` (이미 작성됨)
- Create: `docs/superpowers/plans/2026-09-05-observability-metrics-exposure.md` (이 파일)
- Modify: `TASK.md` (이미 작성됨)

**Interfaces:**
- Consumes: 없음
- Produces: `DEC-B1-001` ~ `DEC-B1-008` 결정 ID. 이후 모든 Task가 이 ID로 근거를 참조한다.

- [ ] **Step 1: placeholder가 남아 있지 않은지 확인**

```bash
rg -n "T[B]D|T[O]DO|PLACE[H]OLDER" TASK.md \
  docs/superpowers/specs/2026-09-05-observability-metrics-exposure-design.md \
  docs/superpowers/plans/2026-09-05-observability-metrics-exposure.md
```

Expected: 일치하는 줄이 없다 (rg exit code 1).

- [ ] **Step 2: 공백 오류 확인**

```bash
git diff --check
```

Expected: 출력 없음.

- [ ] **Step 3: 커밋**

```bash
git add TASK.md \
  docs/superpowers/specs/2026-09-05-observability-metrics-exposure-design.md \
  docs/superpowers/plans/2026-09-05-observability-metrics-exposure.md
git commit -m "chore(observability): add metrics exposure design and task contract (#218)"
```

---

### Task 2: management port 분리와 Actuator 전용 보안 체인

`health`와 `prometheus`가 management port에서 200을 반환하게 만든다. registry 의존성, 설정, 보안 체인 셋이 모두 있어야 이 결과가 나오므로 하나의 검토 단위로 묶는다.

**Files:**
- Modify: `build.gradle` (Micrometer 주석 블록과 `spring-boot-starter-actuator` 선언 부근)
- Modify: `src/main/resources/application-observability.yml`
- Create: `src/main/java/com/dnd/qello/auth/config/ObservabilitySecurityConfiguration.java`
- Test: `src/integrationTest/java/com/dnd/qello/ObservabilityEndpointExposureIntegrationTest.java`

**Interfaces:**
- Consumes: `PostgisContainerIntegrationTestSupport` (같은 package `com.dnd.qello`, package-private abstract class)
- Produces:
  - `ObservabilitySecurityConfiguration.HEALTH_ENDPOINT_ID` = `"health"` (public static final String)
  - `ObservabilitySecurityConfiguration.PROMETHEUS_ENDPOINT_ID` = `"prometheus"` (public static final String)
  - `observabilitySecurityFilterChain(HttpSecurity)` bean, `@Order(-1)`, `@Profile("observability")`
  - 테스트 클래스가 노출하는 관례: management port는 `@Value("${local.management.port}")`로 읽는다.

- [ ] **Step 1: 실패하는 통합 테스트를 작성한다**

Create `src/integrationTest/java/com/dnd/qello/ObservabilityEndpointExposureIntegrationTest.java`:

```java
/**
 * Created at: 파일 생성 시점의 실제 ISO 8601 시각
 * Source scenario: TEST-PLAN-GH-218-OBSERVABILITY-METRICS-EXPOSURE-INT-001 through INT-002
 */
package com.dnd.qello;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "management.server.port=0")
@ActiveProfiles({"test", "observability"})
class ObservabilityEndpointExposureIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@LocalServerPort
	private int appPort;

	// @LocalManagementPort 대신 property를 직접 읽는다. actuator 전용 애노테이션의
	// package가 Spring Boot 버전에 따라 달라 import가 깨질 수 있고,
	// local.management.port는 management context가 뜨면 항상 설정된다.
	@Value("${local.management.port}")
	private int managementPort;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	@DisplayName("INT-001: management port의 health endpoint는 200을 반환한다")
	void exposesHealthOnManagementPort() {
		ResponseEntity<String> response = get(managementPort, "/actuator/health");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
	}

	@Test
	@DisplayName("INT-002: management port의 prometheus endpoint는 200과 text/plain 본문을 반환한다")
	void exposesPrometheusOnManagementPort() {
		ResponseEntity<String> response = get(managementPort, "/actuator/prometheus");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getContentType()).isNotNull();
		assertThat(response.getHeaders().getContentType().toString()).contains("text/plain");
		assertThat(response.getBody()).contains("jvm_memory_used_bytes");
	}

	private ResponseEntity<String> get(int port, String path) {
		return restTemplate.getForEntity("http://localhost:" + port + path, String.class);
	}
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run:
```bash
./gradlew integrationTest --tests '*ObservabilityEndpointExposureIntegrationTest'
```

Expected: FAIL. `management.server.port` 지정으로 management context는 뜨지만 `prometheus` endpoint가 없어 404이거나, endpoint가 꺼져 있어 실패한다. 두 테스트 모두 200을 얻지 못한다.

- [ ] **Step 3: Prometheus registry 의존성을 추가한다**

`build.gradle`의 Micrometer 주석 블록을 아래로 교체하고, `spring-boot-starter-actuator` 선언 바로 뒤에 `runtimeOnly` 한 줄을 추가한다.

```groovy
	// Micrometer의 MeterRegistry로 계측하고(#113), 노출은 observability 프로필에서만
	// 연다(#218). registry를 runtimeOnly로 두는 이유는 production source가 Prometheus
	// 클래스를 직접 참조하지 않기 때문이다 — 보안 체인 matcher도 endpoint ID 문자열을
	// 쓴다. classpath에 있으면 registry는 항상 생성되지만 값은 프로세스 안에만 머물고,
	// 노출 여부는 endpoint 설정과 SecurityFilterChain이 단독으로 결정한다.
	//
	// 기본 프로필에서는 application.yml이 관리 endpoint를 전부 닫는다. 열린 관리
	// endpoint 자체가 공격면이므로 기본값은 계속 닫힌 상태다.
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
```

- [ ] **Step 4: observability 프로필 설정을 추가한다**

`src/main/resources/application-observability.yml`의 기존 `logging:` 블록은 그대로 두고, 파일 끝에 아래를 추가한다.

```yaml

management:
  server:
    # API listener와 관리 listener를 분리한다(#218 DEC-B1-001). host 비공개는
    # bind address가 아니라 Compose의 port 경계가 보장하므로 address를 묶지 않는다.
    # Prometheus 컨테이너가 다른 컨테이너에서 이 포트에 접근해야 한다.
    port: 8081
  endpoints:
    enabled-by-default: false
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      enabled: true
      # DB·디스크 세부 상태는 관리 포트에서도 노출하지 않는다.
      show-details: never
    prometheus:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
```

- [ ] **Step 5: Actuator 전용 보안 체인을 추가한다**

Create `src/main/java/com/dnd/qello/auth/config/ObservabilitySecurityConfiguration.java`:

```java
package com.dnd.qello.auth.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// 관측 endpoint를 observability 프로필에서만 연다(#218).
//
// management.server.port로 포트를 분리해도 management child context는 부모의
// springSecurityFilterChain을 재사용한다. 따라서 이 체인이 없으면
// SecurityConfiguration의 fallback denyAll이 health와 prometheus까지 막는다.
// 반대로 custom SecurityFilterChain이 하나라도 있으면 Actuator 기본 보안
// 자동 설정은 back off하므로, 관리 포트의 인가 규칙은 이 체인이 단독으로 정한다.
// 근거는 docs/superpowers/specs/2026-09-05-observability-metrics-exposure-design.md
// DEC-B1-002에 있다.
@Configuration(proxyBeanMethods = false)
@Profile("observability")
public class ObservabilitySecurityConfiguration {

	public static final String HEALTH_ENDPOINT_ID = "health";
	public static final String PROMETHEUS_ENDPOINT_ID = "prometheus";

	// EndpointRequest.toAnyEndpoint()를 쓰지 않는다(DEC-B1-003). 그것을 쓰면 나중에
	// 다른 endpoint를 활성화하는 순간 그 endpoint까지 자동으로 permitAll 아래로
	// 들어온다. 노출 목록이 보안 체인과 따로 자라지 않도록 두 ID를 직접 적는다.
	//
	// SecurityConfiguration의 apiDocs 체인(@Order(0))보다 앞선다. 이 matcher에
	// 걸리지 않는 나머지 /actuator/**는 기존 fallback denyAll이 처리한다.
	@Bean
	@Order(-1)
	SecurityFilterChain observabilitySecurityFilterChain(HttpSecurity http) throws Exception {
		return http
			.securityMatcher(EndpointRequest.to(HEALTH_ENDPOINT_ID, PROMETHEUS_ENDPOINT_ID))
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.logout(logout -> logout.disable())
			.build();
	}
}
```

- [ ] **Step 6: 테스트를 실행해 통과를 확인한다**

Run:
```bash
./gradlew integrationTest --tests '*ObservabilityEndpointExposureIntegrationTest'
```

Expected: PASS, 2 tests.

실패하면 출력의 실제 status code를 그대로 기록하고 멈춘다. 테스트를 통과시키려고 `permitAll` 범위나 기존 체인의 matcher를 넓히지 않는다.

- [ ] **Step 7: 커밋**

```bash
git add build.gradle \
  src/main/resources/application-observability.yml \
  src/main/java/com/dnd/qello/auth/config/ObservabilitySecurityConfiguration.java \
  src/integrationTest/java/com/dnd/qello/ObservabilityEndpointExposureIntegrationTest.java
git commit -m "chore(observability): expose health and prometheus on management port (#218)"
```

---

### Task 3: 노출 경계 negative 계약 고정

열린 두 endpoint 외의 경로가 어떻게 막히는지 실측해 계약으로 고정한다. 여기가 `DEC-B1-002` 전제를 검증하는 지점이다.

**Files:**
- Modify: `src/integrationTest/java/com/dnd/qello/ObservabilityEndpointExposureIntegrationTest.java`
- Create: `src/integrationTest/java/com/dnd/qello/ObservabilityDisabledByDefaultIntegrationTest.java`

**Interfaces:**
- Consumes: Task 2가 만든 `ObservabilityEndpointExposureIntegrationTest`의 `get(int port, String path)` private helper와 `appPort`·`managementPort` 필드
- Produces: 없음 (테스트 전용)

- [ ] **Step 1: 실측용 임시 테스트를 추가한다**

`ObservabilityEndpointExposureIntegrationTest`에 아래 메서드를 추가한다. 이 메서드는 status code를 출력만 하며, Step 3에서 실제 단언으로 교체한다.

```java
	@Test
	@DisplayName("MEASURE: 차단 대상 경로의 실제 status code를 관측한다")
	void measureBlockedPaths() {
		System.out.println("management /actuator/env    = " + get(managementPort, "/actuator/env").getStatusCode().value());
		System.out.println("management /api/v1/direction/inbox     = " + get(managementPort, "/api/v1/direction/inbox").getStatusCode().value());
		System.out.println("app /actuator/health        = " + get(appPort, "/actuator/health").getStatusCode().value());
		System.out.println("app /actuator/prometheus    = " + get(appPort, "/actuator/prometheus").getStatusCode().value());
	}
```

- [ ] **Step 2: 실행해 네 값을 관측한다**

Run:
```bash
./gradlew integrationTest --tests '*ObservabilityEndpointExposureIntegrationTest' --info 2>&1 | grep -E "^(management|app) "
```

Expected: 네 줄이 출력된다. 관측한 값을 그대로 적어 둔다.

`app /actuator/health` 또는 `app /actuator/prometheus`가 **200이면 여기서 멈춘다.** app listener에 관리 endpoint가 노출된다는 뜻이므로 `DEC-B1-002` 전제가 깨진 것이다. 관측값과 함께 사람에게 보고하고, 설계 문서 §10의 지침에 따라 child context 전용 보안 구성을 재검토한다. 테스트를 관측값에 맞춰 통과시키지 않는다.

- [ ] **Step 3: 관측값을 단언으로 고정한다**

`measureBlockedPaths`를 삭제하고 아래 두 메서드로 교체한다. `<관측값>` 자리에는 Step 2에서 실제로 본 숫자를 넣는다.

```java
	@Test
	@DisplayName("INT-003: management port에서 노출 목록 밖 endpoint와 API 경로는 열리지 않는다")
	void blocksNonExposedPathsOnManagementPort() {
		int envStatus = get(managementPort, "/actuator/env").getStatusCode().value();
		int apiStatus = get(managementPort, "/api/v1/direction/inbox").getStatusCode().value();

		// 보안 불변식은 "200이 아니다"이고, 정확한 코드는 실측해 고정한 계약이다.
		assertThat(envStatus).isNotEqualTo(200).isEqualTo(<관측값>);
		assertThat(apiStatus).isNotEqualTo(200).isEqualTo(<관측값>);
	}

	@Test
	@DisplayName("INT-004: app port에서는 관리 endpoint가 열리지 않는다")
	void keepsManagementEndpointsClosedOnAppPort() {
		int healthStatus = get(appPort, "/actuator/health").getStatusCode().value();
		int prometheusStatus = get(appPort, "/actuator/prometheus").getStatusCode().value();

		assertThat(healthStatus).isNotEqualTo(200).isEqualTo(<관측값>);
		assertThat(prometheusStatus).isNotEqualTo(200).isEqualTo(<관측값>);
	}

	// @Order(-1) 관측 체인이 EndpointRequest matcher를 넘어 /api/**까지 삼키면
	// 인증 없이 도메인 API가 열린다. 기존 인증 계약이 그대로인지 확인한다.
	@Test
	@DisplayName("INT-009: 관측 체인 추가 후에도 app port의 도메인 API 인증 계약이 유지된다")
	void keepsAppApiAuthenticationContractUnchanged() {
		int status = get(appPort, "/api/v1/direction/inbox").getStatusCode().value();

		assertThat(status)
			.as("토큰 없는 인증 필요 endpoint")
			.isEqualTo(401);
	}
```

- [ ] **Step 4: 기본 프로필 회귀 테스트를 작성한다**

Create `src/integrationTest/java/com/dnd/qello/ObservabilityDisabledByDefaultIntegrationTest.java`:

```java
/**
 * Created at: 파일 생성 시점의 실제 ISO 8601 시각
 * Source scenario: TEST-PLAN-GH-218-OBSERVABILITY-METRICS-EXPOSURE-INT-005
 */
package com.dnd.qello;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// observability 프로필 없이 기동한다. 관리 endpoint는 endpoint 비활성과
// fallback denyAll 두 겹으로 닫혀 있어야 한다(#218 DEC-B1-007).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ObservabilityDisabledByDefaultIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@LocalServerPort
	private int appPort;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	@DisplayName("INT-005: 기본 프로필에서는 관리 endpoint가 열리지 않는다")
	void keepsManagementEndpointsClosedWithoutObservabilityProfile() {
		int healthStatus = restTemplate
			.getForEntity("http://localhost:" + appPort + "/actuator/health", String.class)
			.getStatusCode().value();
		int prometheusStatus = restTemplate
			.getForEntity("http://localhost:" + appPort + "/actuator/prometheus", String.class)
			.getStatusCode().value();

		assertThat(healthStatus).isNotEqualTo(200);
		assertThat(prometheusStatus).isNotEqualTo(200);
	}

	@Test
	@DisplayName("INT-006: 기본 프로필에서는 Actuator 전용 보안 체인 bean이 생성되지 않는다")
	void doesNotRegisterObservabilitySecurityChainWithoutProfile() {
		assertThat(applicationContext.containsBean("observabilitySecurityFilterChain")).isFalse();
	}
}
```

- [ ] **Step 5: 두 테스트 클래스를 실행해 통과를 확인한다**

Run:
```bash
./gradlew integrationTest --tests '*ObservabilityEndpointExposureIntegrationTest' --tests '*ObservabilityDisabledByDefaultIntegrationTest'
```

Expected: PASS, 7 tests (노출 5 + 기본 2).

- [ ] **Step 6: 커밋**

```bash
git add src/integrationTest/java/com/dnd/qello/ObservabilityEndpointExposureIntegrationTest.java \
  src/integrationTest/java/com/dnd/qello/ObservabilityDisabledByDefaultIntegrationTest.java
git commit -m "chore(observability): pin actuator exposure boundary contract (#218)"
```

---

### Task 4: histogram 활성화와 Meter 계약 검증

실존 Timer 4종에 histogram을 켜고, scrape 본문에 bucket 시계열이 실제로 나오는지와 tag가 bounded인지 확인한다.

**Files:**
- Modify: `src/main/resources/application-observability.yml`
- Test: `src/integrationTest/java/com/dnd/qello/ObservabilityMeterContractIntegrationTest.java`

**Interfaces:**
- Consumes:
  - `com.dnd.qello.filtering.observability.FilteringMetrics#recordPipeline(String path, String language, String outcome, java.time.Duration elapsed)` — tag 값은 `FilteringMetricTags`의 허용 형태 `[A-Z][A-Z0-9_]{0,39}`를 따라야 한다.
  - `javax.sql.DataSource` — connection을 한 번 열어야 `hikaricp.connections.*` Timer가 registry에 등록된다.
- Produces: 없음 (테스트 전용)

- [ ] **Step 1: 실패하는 Meter 계약 테스트를 작성한다**

Create `src/integrationTest/java/com/dnd/qello/ObservabilityMeterContractIntegrationTest.java`:

```java
/**
 * Created at: 파일 생성 시점의 실제 ISO 8601 시각
 * Source scenario: TEST-PLAN-GH-218-OBSERVABILITY-METRICS-EXPOSURE-INT-007 through INT-008
 */
package com.dnd.qello;

import java.sql.Connection;
import java.time.Duration;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.filtering.observability.FilteringMetrics;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "management.server.port=0")
@ActiveProfiles({"test", "observability"})
class ObservabilityMeterContractIntegrationTest extends PostgisContainerIntegrationTestSupport {

	// Prometheus는 Timer 이름의 점을 밑줄로 바꾸고 단위 suffix를 붙인다.
	private static final List<String> HISTOGRAM_PREFIXES = List.of(
			"http_server_requests_seconds",
			"qello_filtering_pipeline_latency_seconds",
			"hikaricp_connections_acquire_seconds",
			"hikaricp_connections_usage_seconds");

	// tag로 새어 나오면 안 되는 값의 키. 지표는 "지금 밀리는가"에 답하고,
	// "누가 무엇에서 실패했는가"는 로그가 답한다.
	private static final List<String> FORBIDDEN_TAG_KEYS = List.of(
			"userId", "user_id", "requestId", "request_id",
			"correlationId", "correlation_id", "eventId", "event_id",
			"postId", "post_id", "nickname", "token", "exception_message");

	@LocalServerPort
	private int appPort;

	@Value("${local.management.port}")
	private int managementPort;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private FilteringMetrics filteringMetrics;

	@Autowired
	private DataSource dataSource;

	@BeforeEach
	void recordMeterSamples() throws Exception {
		// http.server.requests를 등록시킨다. 인증 실패든 성공이든 Timer는 기록된다.
		restTemplate.getForEntity("http://localhost:" + appPort + "/api/v1/direction/inbox", String.class);

		// qello.filtering.pipeline.latency Timer를 등록시킨다.
		// tag 값은 FilteringMetricTags의 ENUM_TOKEN 형태를 따른다.
		filteringMetrics.recordPipeline("ANSWER", "KOREAN", "SUCCESS", Duration.ofMillis(5));

		// hikaricp.connections.* Timer는 connection을 최소 한 번 획득해야 등록된다.
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.isValid(2)).isTrue();
		}
	}

	@Test
	@DisplayName("INT-007: scrape 본문에 네 Timer의 bucket, count, sum 시계열이 존재한다")
	void exposesHistogramSeriesForConfiguredTimers() {
		String scrape = scrape();

		for (String prefix : HISTOGRAM_PREFIXES) {
			assertThat(scrape)
				.as("%s_bucket", prefix)
				.contains(prefix + "_bucket");
			assertThat(scrape)
				.as("%s_count", prefix)
				.contains(prefix + "_count");
			assertThat(scrape)
				.as("%s_sum", prefix)
				.contains(prefix + "_sum");
		}
	}

	@Test
	@DisplayName("INT-008: 노출된 지표의 tag에 사용자·요청 식별자가 없다")
	void keepsExposedTagsBounded() {
		String scrape = scrape();

		for (String forbidden : FORBIDDEN_TAG_KEYS) {
			assertThat(scrape)
				.as("금지 tag 키 %s", forbidden)
				.doesNotContain(forbidden + "=");
		}
	}

	private String scrape() {
		return restTemplate
			.getForEntity("http://localhost:" + managementPort + "/actuator/prometheus", String.class)
			.getBody();
	}
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run:
```bash
./gradlew integrationTest --tests '*ObservabilityMeterContractIntegrationTest'
```

Expected: INT-007이 FAIL. histogram을 아직 켜지 않아 `_bucket` 시계열이 없고, `_count`·`_sum`만 존재한다. INT-008은 이 시점에도 통과할 수 있다.

- [ ] **Step 3: histogram 설정을 추가한다**

`src/main/resources/application-observability.yml`의 `management.metrics` 블록에 `distribution`을 추가한다. Task 2에서 넣은 `tags`와 같은 깊이다.

```yaml
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      # Meter 이름에 점이 있으므로 대괄호로 감싼다. 감싸지 않으면 중첩 map으로
      # 파싱돼 설정이 조용히 무시된다.
      #
      # 실제로 등록되는 Timer만 적는다(#218 DEC-B1-005). 존재하지 않는 이름을
      # 적어 두면 Spring Boot가 오류 없이 무시해, 설정만 보고 관측이 된다고
      # 오해하게 된다. worker batch와 provider Timer는 계측 자체가 없어
      # 후속 Worker Observability 작업에서 함께 추가한다.
      #
      # 운영 SLO가 없으므로 bucket 경계를 고정하지 않는다(DEC-B1-006).
      percentiles-histogram:
        "[http.server.requests]": true
        "[qello.filtering.pipeline.latency]": true
        "[hikaricp.connections.acquire]": true
        "[hikaricp.connections.usage]": true
```

- [ ] **Step 4: 테스트를 실행해 통과를 확인한다**

Run:
```bash
./gradlew integrationTest --tests '*ObservabilityMeterContractIntegrationTest'
```

Expected: PASS, 2 tests.

INT-007이 특정 prefix에서만 실패하면 그 Meter가 등록되지 않은 것이다. 대괄호 표기를 지우거나 단언을 느슨하게 바꾸지 말고, 해당 Meter가 왜 등록되지 않았는지 확인한다.

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/application-observability.yml \
  src/integrationTest/java/com/dnd/qello/ObservabilityMeterContractIntegrationTest.java
git commit -m "chore(observability): enable histograms for existing timers (#218)"
```

---

### Task 5: 전체 검증과 결과 기록

저장소 필수 검증을 실행하고 결과를 `TASK.md`에 기록한다.

**Files:**
- Modify: `TASK.md` (Completion criteria 체크와 Final verification contract 추가)

**Interfaces:**
- Consumes: Task 2~4의 모든 변경
- Produces: 없음

- [ ] **Step 1: Observability 테스트 전체를 실행한다**

Run:
```bash
./gradlew integrationTest --tests '*Observability*'
```

Expected: PASS, 9 tests (노출 5 + 기본 2 + Meter 2).

- [ ] **Step 2: Gradle 전체 검증을 실행한다**

Run:
```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL. Spotless, Checkstyle, ArchUnit, 단위 테스트와 통합 테스트가 모두 통과한다.

새 클래스가 convention 검사에 걸리면 검사를 끄거나 baseline에 추가하지 말고 코드를 규칙에 맞춘다.

- [ ] **Step 3: 저장소 harness 검증을 실행한다**

Run:
```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

Expected: 모두 통과. `git diff --check`는 출력이 없다.

- [ ] **Step 4: base 대비 변경 파일이 범위 안인지 확인한다**

Run:
```bash
git diff --name-only origin/chore/gh-215-structured-request-logging...HEAD
```

Expected: 아래 9개만 나온다.

```text
TASK.md
build.gradle
docs/superpowers/plans/2026-09-05-observability-metrics-exposure.md
docs/superpowers/specs/2026-09-05-observability-metrics-exposure-design.md
src/integrationTest/java/com/dnd/qello/ObservabilityDisabledByDefaultIntegrationTest.java
src/integrationTest/java/com/dnd/qello/ObservabilityEndpointExposureIntegrationTest.java
src/integrationTest/java/com/dnd/qello/ObservabilityMeterContractIntegrationTest.java
src/main/java/com/dnd/qello/auth/config/ObservabilitySecurityConfiguration.java
src/main/resources/application-observability.yml
```

목록에 다른 파일이 있으면 멈추고 이유를 보고한다.

- [ ] **Step 5: 민감정보가 없는지 확인한다**

Run:
```bash
rg -n "userId|nickname|token|password|latitude|longitude|arn:aws" \
  src/main/java/com/dnd/qello/auth/config/ObservabilitySecurityConfiguration.java \
  src/main/resources/application-observability.yml \
  src/integrationTest/java/com/dnd/qello/Observability*.java
```

Expected: 일치하는 줄이 없다.

- [ ] **Step 6: `TASK.md`에 검증 결과를 기록한다**

Completion criteria의 체크박스를 실제 실행 결과에 맞춰 표시하고, 파일 끝에 아래 블록을 추가한다. 실행하지 않은 검증을 통과로 적지 않는다.

```text
## Final verification contract

status: <PASS|FAIL|BLOCKED>
issue_number: 218
task_id: GH-218-OBSERVABILITY-METRICS-EXPOSURE
design_id: APP-DESIGN-GH-218-001
changed_files: <git diff --name-only 결과>
executed_checks: <실행한 명령 목록>
passed_checks: <통과한 명령 목록>
failed_checks: <실패한 명령 또는 none>
blocked_checks: <차단된 명령 또는 none>
assumptions: 로컬 Testcontainers PostgreSQL이 persistence를 대표한다; management port는 테스트에서 0으로 덮어 임의 포트를 쓴다
risks: management child context의 보안 체인 재사용 동작은 Spring Boot 3.5.16 기준 실측값이며 버전 상향 시 재확인이 필요하다
required_human_decisions: PR은 #217 머지 후에 올린다
```

- [ ] **Step 7: 커밋**

```bash
git add TASK.md
git commit -m "chore(observability): record exposure boundary verification (#218)"
```

---

## 실행 후 남는 일

- PR은 #217이 머지된 뒤에 `/harness-pr`로 올린다. 그 전에 `./harness sync`로 base를 반영한다.
- 경로 B2(Compose overlay, Prometheus·Grafana provisioning)와 B3(k6 부하 실험)는 각각 별도 Issue로 진행한다.
- `qello.worker.batch.duration`과 `qello.provider.request.duration` Timer 신규 계측은 Worker Observability Issue에서 histogram 설정과 함께 추가한다.
