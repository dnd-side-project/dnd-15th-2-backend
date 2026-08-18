/**
 * Created at: 2026-08-18T23:40:00+09:00
 * Source scenario: TEST-PLAN-GH-113-FILTERING-OBSERVABILITY-AND-GATE-INT-007
 */
package com.dnd.qello;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// #113: actuator를 지표 계측을 위해 추가했지만 web endpoint는 하나도 열지 않는다.
// 노출 설정을 되돌리면 관리 endpoint가 즉시 열리므로, 기본 설정에서 실제로
// 닫혀 있는지 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorExposureIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("INT-007: actuator endpoint는 기본 설정에서 노출되지 않는다")
	void actuatorEndpointsAreNotExposed() throws Exception {
		// SecurityConfiguration의 어떤 체인에도 매칭되지 않으므로 마지막 fallback
		// denyAll 체인이 받는다. 인증 자격이 없으므로 401이며, 노출 설정을
		// 되살리더라도 이 경로가 열려 있지 않음을 함께 확인한다.
		for (String path : new String[] {"/actuator", "/actuator/health", "/actuator/metrics", "/actuator/env"}) {
			mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		}
	}
}
