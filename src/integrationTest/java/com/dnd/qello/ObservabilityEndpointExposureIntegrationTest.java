/**
 * Created at: 2026-09-05T18:15:58+09:00
 * Source scenario: TEST-PLAN-GH-218-OBSERVABILITY-METRICS-EXPOSURE-INT-001 through INT-002
 */
package com.dnd.qello;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
// Spring Boot 테스트는 외부 MeterRegistry를 기본 비활성화하므로 실제 Prometheus
// endpoint 자동 설정을 검증하려면 observability auto-configuration을 복원해야 한다.
@AutoConfigureObservability
@ActiveProfiles({"test", "observability"})
class ObservabilityEndpointExposureIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@LocalServerPort
	private int appPort;

	// MockMvc는 실제 listener를 열지 않아 app port와 management child context의 경계를
	// 검증할 수 없다. @LocalManagementPort 대신 property를 직접 읽으면 Spring Boot
	// 버전에 따른 actuator 전용 애노테이션 package 변경에도 영향을 받지 않는다.
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
