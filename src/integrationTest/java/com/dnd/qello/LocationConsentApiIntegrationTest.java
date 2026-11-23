/**
 * Created at: 2026-11-23T13:52:37Z
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-004
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-005
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-006
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-011
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-014
 */
package com.dnd.qello;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dnd.qello.direction.gate.LocationCollectionGate;
import com.dnd.qello.direction.gate.LocationCollectionGateProperties;
import com.dnd.qello.filtering.moderation.NicknameModerationChecker;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.dnd.qello.notification.push.PushProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LocationConsentApiIntegrationTest.FixedTime.class)
class LocationConsentApiIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant AT = Instant.parse("2026-11-23T12:00:00Z");
	private static final AtomicInteger SEQUENCE = new AtomicInteger();
	private static final List<String> GUARDED = List.of("active_user_presence", "location_consent_history",
			"direction_post", "post_audience", "outbox_event", "filter_job", "filter_job_status_history");

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ObjectMapper mapper;
	@Autowired
	private LocationCollectionGate gate;
	@Autowired
	private FilterReleaseRegistryService releases;
	@MockitoSpyBean
	private PushProvider push;
	@MockitoSpyBean
	private NicknameModerationChecker moderation;

	private final List<Long> users = new ArrayList<>();
	private LocationCollectionGateProperties originalGate;
	private long user;
	private long other;
	private long question;
	private long scheme;

	@BeforeEach
	void setup() {
		originalGate = (LocationCollectionGateProperties) ReflectionTestUtils.getField(gate, "properties");
		user = account("USER", "ACTIVE");
		other = account("USER", "ACTIVE");
		clearInvocations(push, moderation);
	}

	@AfterEach
	void cleanup() {
		ReflectionTestUtils.setField(gate, "properties", originalGate);
		for (long id : users) {
			List<Long> posts = jdbc.queryForList("SELECT id FROM direction_post WHERE sender_id = ?", Long.class, id);
			for (long postId : posts) {
				jdbc.update("""
						DELETE FROM filter_job_status_history WHERE filter_job_id IN
						(SELECT id FROM filter_job WHERE target_type = 'DIRECTION_POST' AND target_id = ?)
						""", postId);
				jdbc.update("DELETE FROM filter_job WHERE target_type = 'DIRECTION_POST' AND target_id = ?", postId);
				jdbc.update("DELETE FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?",
						postId);
				jdbc.update("DELETE FROM post_audience WHERE post_id = ?", postId);
				jdbc.update("DELETE FROM direction_post WHERE id = ?", postId);
			}
			jdbc.update("DELETE FROM approved_question WHERE approved_by = ?", id);
			jdbc.update("DELETE FROM active_user_presence WHERE user_id = ?", id);
			jdbc.update("DELETE FROM location_consent_history WHERE user_id = ?", id);
			jdbc.update("DELETE FROM user_account WHERE id = ?", id);
		}
		users.clear();
	}

	@ParameterizedTest
	@CsvSource({"NONE,false", "NONE,true", "WITHDRAW,false", "WITHDRAW,true", "OLD,false", "OLD,true"})
	@DisplayName("INT-004: 무기록·철회·구버전은 기존 presence 유무와 무관하게 PUT을 거부하고 전체 행을 보존한다")
	void rejectsPresenceWithoutCurrentConsent(String state, boolean present) throws Exception {
		seedState(state);
		if (present) {
			presence();
		}
		var before = snapshot();
		String response = mvc
				.perform(auth(put("/api/v1/direction/presence"), user).contentType(MediaType.APPLICATION_JSON)
						.content(presenceBody()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorDetail.code").value("DIR-APP-009"))
				.andReturn().getResponse().getContentAsString();
		assertThat(snapshot()).isEqualTo(before);
		assertThat(response).doesNotContain("37.512345", "127.098765");
		verifyNoInteractions(push, moderation);
	}

	@ParameterizedTest
	@CsvSource({"NONE,true", "WITHDRAW,true", "OLD,true", "NONE,false", "WITHDRAW,false", "OLD,false"})
	@DisplayName("INT-005: 기존 replay도 B1이 꺼지면 503, 켜지면 미동의 403이고 DB와 외부 호출은 불변이다")
	void rejectsPreviewAndExistingReplayBeforeSideEffects(String state, boolean enabled) throws Exception {
		prepareSend();
		consent();
		mvc.perform(auth(put("/api/v1/direction/presence"), user).contentType(MediaType.APPLICATION_JSON)
				.content(presenceBody())).andExpect(status().isOk());
		mvc.perform(submit()).andExpect(status().isAccepted());
		jdbc.update("DELETE FROM location_consent_history WHERE user_id = ?", user);
		seedState(state);
		ReflectionTestUtils.setField(gate, "properties", new LocationCollectionGateProperties(enabled,
				"TEST-ONLY-LBS-REGISTRATION-FIXTURE"));
		var before = snapshot();
		for (int attempt = 0; attempt < 2; attempt++) {
			mvc.perform(auth(get("/api/v1/direction/preview"), user))
					.andExpect(status().is(enabled ? 403 : 503))
					.andExpect(jsonPath("$.errorDetail.code").value(enabled ? "DIR-APP-009" : "DIR-APP-008"));
			mvc.perform(submit()).andExpect(status().is(enabled ? 403 : 503))
					.andExpect(jsonPath("$.errorDetail.code").value(enabled ? "DIR-APP-009" : "DIR-APP-008"));
		}
		assertThat(snapshot()).isEqualTo(before);
		verifyNoInteractions(push, moderation);
	}

	@Test
	@DisplayName("INT-006: 현재 동의 후 PUT·preview·submit은 성공하고 동일 키 replay는 추가 쓰기가 없다")
	void currentConsentAllowsAllApisAndIdempotentReplay() throws Exception {
		prepareSend();
		consent();
		mvc.perform(auth(put("/api/v1/direction/presence"), user).contentType(MediaType.APPLICATION_JSON)
				.content(presenceBody())).andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(true));
		mvc.perform(auth(get("/api/v1/direction/preview"), user)).andExpect(status().isOk());
		String first = mvc.perform(submit()).andExpect(status().isAccepted()).andReturn().getResponse()
				.getContentAsString();
		var before = snapshot();
		String replay = mvc.perform(submit()).andExpect(status().isAccepted()).andReturn().getResponse()
				.getContentAsString();
		assertThat(mapper.readTree(replay).get("data")).isEqualTo(mapper.readTree(first).get("data"));
		assertThat(snapshot()).isEqualTo(before);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM direction_post WHERE sender_id = ?", Integer.class, user))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT ST_Y(position::geometry) FROM active_user_presence WHERE user_id = ?",
				Double.class, user)).isEqualTo(37.512345);
	}

	@Test
	@DisplayName("INT-011: 세 동의 API는 인증 subject만 사용하며 위조 대상 이력과 잘못된 요청은 DB를 바꾸지 않는다")
	void authenticationSubjectAndValidationProtectHistory() throws Exception {
		for (var request : List.of(post("/api/v1/location-consents").contentType(MediaType.APPLICATION_JSON)
				.content("{\"termsVersion\":\"test-v1\"}"), post("/api/v1/location-consents/withdrawals"),
				get("/api/v1/location-consents/current"))) {
			mvc.perform(request).andExpect(status().isUnauthorized());
		}
		var before = snapshot();
		for (String body : List.of("{}", "{\"termsVersion\":\" \"}", "{\"termsVersion\":\" test-v1\"}",
				"{\"termsVersion\":\"test-v1 \"}", "{\"termsVersion\":\"" + "x".repeat(101) + "\"}", "{")) {
			mvc.perform(
					auth(post("/api/v1/location-consents"), user).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
		}
		String mismatch = mvc
				.perform(auth(post("/api/v1/location-consents"), user).contentType(MediaType.APPLICATION_JSON)
						.content("{\"termsVersion\":\"secret-sentinel-381\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorDetail.code").value("DIR-APP-010"))
				.andReturn().getResponse().getContentAsString();
		assertThat(mismatch).doesNotContain("secret-sentinel-381");
		assertThat(snapshot()).isEqualTo(before);
		mvc.perform(auth(post("/api/v1/location-consents"), user).contentType(MediaType.APPLICATION_JSON)
				.content("{\"termsVersion\":\"test-v1\",\"userId\":" + other + "}")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM location_consent_history WHERE user_id = ?",
				Integer.class, other)).isZero();
		mvc.perform(auth(post("/api/v1/location-consents/withdrawals"), user).contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":" + other + "}")).andExpect(status().isOk());
		assertThat(jdbc.queryForList("SELECT action FROM location_consent_history WHERE user_id = ? ORDER BY id",
				String.class, user)).containsExactly("CONSENT", "WITHDRAW");
		for (String subject : List.of("0", "-1", "invalid")) {
			mvc.perform(get("/api/v1/location-consents/current")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
							.jwt(token -> token.subject(subject))))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	@DisplayName("INT-011: 비활성·운영자 계정은 동의·철회·상태조회에서 기존 권한 오류로 거부된다")
	void rejectsIneligibleAccountsWithoutWritingHistory() throws Exception {
		for (String state : List.of("BLOCKED", "DELETED", "WITHDRAWAL_PENDING", "OPERATOR")) {
			long id = account(state.equals("OPERATOR") ? "OPERATOR" : "USER",
					state.equals("OPERATOR") ? "ACTIVE" : state);
			for (var request : List.of(post("/api/v1/location-consents").contentType(MediaType.APPLICATION_JSON)
					.content("{\"termsVersion\":\"test-v1\"}"), post("/api/v1/location-consents/withdrawals"),
					get("/api/v1/location-consents/current"))) {
				mvc.perform(auth(request, id)).andExpect(status().isForbidden())
						.andExpect(jsonPath("$.errorDetail.code").value("DIR-APP-007"));
			}
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM location_consent_history", Integer.class)).isZero();
	}

	private long account(String role, String status) {
		long id = jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, coarse_region_code, status, locale, timezone, nickname,
					deleted_at, withdrawal_requested_at)
				VALUES (?, CASE WHEN ? = 'USER' THEN 'KR' END, 'KR', ?, 'ko-KR', 'UTC', ?,
					CASE WHEN ? = 'DELETED' THEN now() END, CASE WHEN ? = 'WITHDRAWAL_PENDING' THEN now() END)
				RETURNING id
				""", Long.class, role, role, status, "consent-api-" + SEQUENCE.incrementAndGet(), status, status);
		users.add(id);
		return id;
	}

	private void seedState(String state) {
		if (!state.equals("NONE")) {
			jdbc.update(
					"""
							INSERT INTO location_consent_history (user_id, terms_version, action, recorded_at) VALUES (?, ?, ?, ?)
							""",
					user, state.equals("OLD") ? "test-v2" : "test-v1",
					state.equals("WITHDRAW") ? "WITHDRAW" : "CONSENT", Timestamp.from(AT));
		}
	}

	private void presence() {
		jdbc.update("""
				INSERT INTO active_user_presence (user_id, position, coarse_region_code, accuracy_m,
					receive_allowed, location_at, expires_at)
				VALUES (?, ST_SetSRID(ST_MakePoint(127,37.5),4326)::geography, 'KR', 1, true, ?, ?)
				""", user, Timestamp.from(AT.minusSeconds(60)), Timestamp.from(AT.plusSeconds(3600)));
	}

	private void consent() throws Exception {
		mvc.perform(auth(post("/api/v1/location-consents"), user).contentType(MediaType.APPLICATION_JSON)
				.content("{\"termsVersion\":\"test-v1\"}")).andExpect(status().isOk());
	}

	private void prepareSend() {
		question = jdbc.queryForObject(
				"""
						INSERT INTO approved_question (source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
						VALUES ('OPERATOR', 'ACTIVE', 'consent question', 'TEXT', ?, ?, ?) RETURNING id
						""",
				Long.class, Timestamp.from(AT.minusSeconds(60)), Timestamp.from(AT), user);
		scheme = jdbc.queryForObject("SELECT id FROM direction_scheme WHERE code = 'OCTANT' AND status = 'ACTIVE'",
				Long.class);
		AnswerModerationReleaseTestFixture.promotedRelease(releases, 1L);
	}

	private MockHttpServletRequestBuilder submit() {
		return auth(post("/api/v1/direction/posts"), user).header("Idempotency-Key", "consent-replay-" + user)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"approvedQuestionId":%d,"schemeId":%d,"segmentKey":"N","bodyText":"consent body","mediaIds":[]}
						""".formatted(question, scheme));
	}

	private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, long id) {
		return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
				.jwt(token -> token.subject(Long.toString(id))));
	}

	private String presenceBody() {
		return """
				{"latitude":37.512345,"longitude":127.098765,"accuracyMeters":1,"receiveAllowed":true,"observedAt":"%s"}
				""".formatted(AT);
	}

	private Map<String, String> snapshot() {
		Map<String, String> rows = new LinkedHashMap<>();
		for (String table : GUARDED) {
			rows.put(table, jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),"
					+ " '[]'::jsonb)::text FROM " + table + " t", String.class));
		}
		return rows;
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock consentApiClock() {
			return Clock.fixed(AT, ZoneOffset.UTC);
		}
	}
}
