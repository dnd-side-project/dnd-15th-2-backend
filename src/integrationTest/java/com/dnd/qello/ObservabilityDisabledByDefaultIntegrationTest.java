/**
 * Created at: 2026-09-05T18:27:18+09:00
 * Source scenario: TEST-PLAN-GH-218-OBSERVABILITY-METRICS-EXPOSURE-INT-005 through INT-006
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
// MockMvc는 실제 listener를 열지 않아 app port의 노출 경계를 검증할 수 없다.
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
