/**
 * Created at: 2026-11-23T16:43:12+09:00
 * Source scenario: TEST-PLAN-GH-379-LOCATION-GATE-INT-001
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.gate.LocationCollectionGate;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "qello.location.collection.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocationCollectionGateDisabledIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH379-LOCATION-GATE";
	private static final String SENTINEL_LATITUDE = "37.512345";
	private static final String SENTINEL_LONGITUDE = "127.098765";
	private static final List<String> GUARDED_TABLES = List.of(
			"active_user_presence", "direction_post", "post_audience", "outbox_event");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private LocationCollectionGate gate;

	@Autowired
	private ActiveUserPresenceRepository presenceRepository;

	private long userId;
	private Instant existingLocationAt;

	@BeforeEach
	void setUp() {
		cleanUp();
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING
				""");
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES (?, 'KR', 'GH-379 Location Gate', 'REGION')
				""", REGION);
		userId = jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, coarse_region_code, status, locale, timezone, nickname)
				VALUES ('USER', 'KR', ?, 'ACTIVE', 'ko-KR', 'Asia/Seoul', 'gh379-gate-off')
				RETURNING id
				""", Long.class, REGION);
		// 게이트 도입 전에 저장된 좌표가 있어도 미리보기·발송이 그 좌표를 읽지 않아야 한다.
		existingLocationAt = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS);
		presenceRepository.save(ActiveUserPresence.create(userId, BigDecimal.valueOf(37.5),
				BigDecimal.valueOf(127), null, REGION, BigDecimal.TEN, true, existingLocationAt,
				existingLocationAt.plus(1, ChronoUnit.HOURS)));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM active_user_presence WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
	}

	@Test
	@DisplayName("게이트가 꺼져 있으면 위치 갱신은 503 DIR-APP-008이고 기존 위치와 행 수를 바꾸지 않으며 좌표를 응답하지 않는다")
	void rejectsPresenceUpdateWithoutTouchingStoredLocation() throws Exception {
		assertThat(gate.isEnabled()).isFalse();
		Map<String, Integer> before = rowCounts();
		byte[] ciphertext = jdbc.queryForObject(
				"SELECT position_ciphertext FROM active_user_presence WHERE user_id = ?", byte[].class, userId);

		String response = mockMvc.perform(authenticated(put("/api/v1/direction/presence"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"latitude":%s,"longitude":%s,"accuracyMeters":10,"receiveAllowed":true,"observedAt":"%s"}
						""".formatted(SENTINEL_LATITUDE, SENTINEL_LONGITUDE,
						Instant.now().truncatedTo(ChronoUnit.SECONDS))))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errorDetail.code").value("DIR-APP-008"))
				.andReturn().getResponse().getContentAsString();

		assertThat(response).doesNotContain(SENTINEL_LATITUDE, SENTINEL_LONGITUDE);
		assertThat(rowCounts()).isEqualTo(before);
		assertThat(presenceRepository.findByUserId(userId).orElseThrow().getLatitude())
				.isEqualByComparingTo("37.5");
		assertThat(jdbc.queryForObject("SELECT position_ciphertext FROM active_user_presence WHERE user_id = ?",
				byte[].class, userId)).isEqualTo(ciphertext);
		assertThat(jdbc.queryForObject("SELECT location_at FROM active_user_presence WHERE user_id = ?",
				java.sql.Timestamp.class, userId).toInstant()).isEqualTo(existingLocationAt);
	}

	@Test
	@DisplayName("게이트가 꺼져 있으면 미리보기는 저장된 위치가 있어도 503 DIR-APP-008이다")
	void rejectsPreview() throws Exception {
		Map<String, Integer> before = rowCounts();

		mockMvc.perform(authenticated(get("/api/v1/direction/preview")))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errorDetail.code").value("DIR-APP-008"));

		assertThat(rowCounts()).isEqualTo(before);
	}

	@Test
	@DisplayName("게이트가 꺼져 있으면 발송과 같은 멱등키 재요청이 모두 503 DIR-APP-008이고 글·원점·outbox를 만들지 않는다")
	void rejectsSubmitAndReplay() throws Exception {
		Map<String, Integer> before = rowCounts();
		String body = """
				{"approvedQuestionId":101,"schemeId":7,"segmentKey":"N","bodyText":"gh379","mediaIds":[]}
				""";

		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(authenticated(post("/api/v1/direction/posts"))
					.header("Idempotency-Key", "gh379-gate-off-key")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.errorDetail.code").value("DIR-APP-008"));
		}

		assertThat(rowCounts()).isEqualTo(before);
	}

	@Test
	@DisplayName("게이트가 꺼져 있어도 토큰이 없는 위치 갱신은 게이트보다 먼저 401이다")
	void authenticationPrecedesGate() throws Exception {
		mockMvc.perform(put("/api/v1/direction/presence")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"latitude":37.5,"longitude":127.0,"accuracyMeters":10,"receiveAllowed":true,"observedAt":"%s"}
						""".formatted(Instant.now().truncatedTo(ChronoUnit.SECONDS))))
				.andExpect(status().isUnauthorized());
	}

	private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
		return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
				.jwt(token -> token.subject(Long.toString(userId))));
	}

	private Map<String, Integer> rowCounts() {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String table : GUARDED_TABLES) {
			counts.put(table, jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class));
		}
		return counts;
	}
}
