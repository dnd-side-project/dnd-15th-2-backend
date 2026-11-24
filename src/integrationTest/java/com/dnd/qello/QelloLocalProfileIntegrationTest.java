/**
 * Created at: 2026-08-03T15:39:58+09:00
 * Source scenario: TEST-PLAN-GH-30-LOCAL-PROFILE-INTEGRATION
 * Extended at: 2026-08-03T16:09:35+09:00
 * Extension scenario: TEST-PLAN-GH-31-LOCAL-PROFILE-CONTAINER-INTEGRATION
 * Extended at: 2026-11-23T16:43:12+09:00
 * Source scenario: TEST-PLAN-GH-379-LOCATION-GATE-INT-005
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-012
 */
package com.dnd.qello;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.direction.gate.LocationCollectionGate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"qello.notification.push.policy.bundle-window=PT10M",
		"qello.notification.push.policy.max-delay=PT8H",
		"qello.notification.push.policy.daily-limit=5",
		"qello.notification.push.policy.direction-reserved=2",
		"qello.notification.push.policy.recommendation-min-interval=PT24H"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class QelloLocalProfileIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@Autowired
	private Environment environment;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private LocationCollectionGate locationCollectionGate;

	@Autowired
	private MockMvc mvc;

	@Test
	@DisplayName("local 프로필이 추적되지 않는 설정 대신 Testcontainers PostgreSQL에 연결된다")
	void contextLoadsWithLocalProfile() {
		assertThat(environment.matchesProfiles("local")).isTrue();
		assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
				.isEqualTo("qello_test");
	}

	@Test
	@DisplayName("local 프로필은 신고 수리 참조 없이 위치 수집 게이트를 켠 채 기동한다")
	void localProfileOpensLocationCollectionGate() {
		assertThat(locationCollectionGate.isEnabled()).isTrue();
		assertThat(environment.getProperty("qello.location.consent.current-terms-version")).isEqualTo("dev-v1");
	}

	@Test
	@DisplayName("INT-012: local도 무기록 사용자는 거부하고 명시적 dev-v1 동의 뒤에만 위치를 저장한다")
	void localProfileRequiresExplicitConsent() throws Exception {
		long user = jdbcTemplate.queryForObject("""
				INSERT INTO user_account (country_code, coarse_region_code, locale, timezone, nickname)
				VALUES ('KR', 'KR', 'ko-KR', 'UTC', 'consent-local-381') RETURNING id
				""", Long.class);
		String body = """
				{"latitude":37.5,"longitude":127.0,"accuracyMeters":1,"receiveAllowed":true,"observedAt":"%s"}
				""".formatted(java.time.Instant.now());
		try {
			mvc.perform(put("/api/v1/direction/presence")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
							.jwt(token -> token.subject(Long.toString(user))))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.errorDetail.code").value("DIR-APP-009"));
			mvc.perform(post("/api/v1/location-consents")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
							.jwt(token -> token.subject(Long.toString(user))))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"termsVersion\":\"dev-v1\"}")).andExpect(status().isOk());
			mvc.perform(put("/api/v1/direction/presence")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
							.jwt(token -> token.subject(Long.toString(user))))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(true));
		} finally {
			jdbcTemplate.update("DELETE FROM active_user_presence WHERE user_id = ?", user);
			jdbcTemplate.update("DELETE FROM location_consent_history WHERE user_id = ?", user);
			jdbcTemplate.update("DELETE FROM user_account WHERE id = ?", user);
		}
	}

}
