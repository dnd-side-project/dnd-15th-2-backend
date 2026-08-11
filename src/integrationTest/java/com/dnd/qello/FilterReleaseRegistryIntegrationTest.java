/**
 * Created at: 2026-08-11T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-104-RELEASE-REGISTRY-INT-001 through INT-006
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorSeedService;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "account-persistence"})
class FilterReleaseRegistryIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-COUNTRY";
	private static final String LOGIN_ID = "qello-admin";
	private static final String PASSWORD = "example-operator-password";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private OperatorSeedService operatorSeedService;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private FilterReleaseRepository filterReleaseRepository;

	private String csrfHeaderName;
	private String csrfToken;
	private Cookie[] csrfCookies;
	private Cookie sessionCookie;

	@BeforeEach
	void seedOperatorAndLogin() throws Exception {
		jdbcTemplate.update("DELETE FROM release_promotion_history");
		jdbcTemplate.update("DELETE FROM filter_job");
		jdbcTemplate.update("DELETE FROM filter_release");
		jdbcTemplate.update("DELETE FROM operator_credential");
		jdbcTemplate.update("DELETE FROM user_account");
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", REGION_CODE);
		jdbcTemplate.update("""
			INSERT INTO region_code (code, display_name, level)
			VALUES (?, 'Test Country', 'COUNTRY')
			""", REGION_CODE);
		operatorSeedService.seedIfAbsent(
			LoginId.of(LOGIN_ID), new RawPassword(PASSWORD),
			"qello-admin", REGION_CODE, "ko-KR", "Asia/Seoul");

		MvcResult issued = mockMvc.perform(get("/admin/csrf")).andReturn();
		JsonNode data = objectMapper.readTree(issued.getResponse().getContentAsString()).get("data");
		csrfHeaderName = data.get("headerName").asText();
		csrfToken = data.get("token").asText();
		csrfCookies = issued.getResponse().getCookies();

		MvcResult loggedIn = mockMvc.perform(withCsrf(post("/admin/login"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(LOGIN_ID, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		sessionCookie = loggedIn.getResponse().getCookie("SESSION");
	}

	@Test
	@DisplayName("candidate 생성부터 승격까지 전체 파이프라인을 통과하면 authoritative가 된다")
	void promotesCandidateThroughFullPipeline() throws Exception {
		long releaseId = createCandidate("norm-v1", "ruleset-v1", "category-v1", "text-moderation-2026-08");

		transition(releaseId, "offline-evaluation")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("OFFLINE_EVALUATED"));
		transition(releaseId, "shadow")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("SHADOW"));
		transition(releaseId, "canary")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANARY"));
		transition(releaseId, "promote")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PROMOTED"))
			.andExpect(jsonPath("$.data.promotedAt").exists());

		FilterRelease promoted = filterReleaseRepository.findCurrentlyPromoted().orElseThrow();
		assertThat(promoted.id()).isEqualTo(releaseId);
	}

	@Test
	@DisplayName("단계를 건너뛴 승격 시도는 409로 거절된다")
	void rejectsPromotingWithoutPassingThroughStages() throws Exception {
		long releaseId = createCandidate("norm-v1", "ruleset-v1", "category-v1", "text-moderation-2026-08");

		transition(releaseId, "promote")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorDetail.code").value("FLT-DOM-004"));
	}

	@Test
	@DisplayName("새 release를 승격하면 기존 PROMOTED release는 ROLLED_BACK으로 내려가고, rollback으로 다시 올릴 수 있다")
	void promotingNewReleaseRollsBackThePreviousOneAndRollbackRestoresIt() throws Exception {
		long first = promoteFullPipeline("norm-v1", "ruleset-v1", "category-v1", "model-v1");
		long second = promoteFullPipeline("norm-v2", "ruleset-v2", "category-v2", "model-v2");

		assertThat(filterReleaseRepository.findCurrentlyPromoted().orElseThrow().id()).isEqualTo(second);
		assertThat(filterReleaseRepository.findById(first).orElseThrow().status().name()).isEqualTo("ROLLED_BACK");

		transition(first, "rollback")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PROMOTED"));

		assertThat(filterReleaseRepository.findCurrentlyPromoted().orElseThrow().id()).isEqualTo(first);
		assertThat(filterReleaseRepository.findById(second).orElseThrow().status().name()).isEqualTo("ROLLED_BACK");

		Integer promotedRowCount = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM filter_release WHERE status = 'PROMOTED'", Integer.class);
		assertThat(promotedRowCount).isEqualTo(1);
	}

	@Test
	@DisplayName("DB는 동시에 두 release가 PROMOTED가 되는 것을 유일성 제약으로 거절한다")
	void databaseRejectsTwoSimultaneouslyPromotedReleases() throws Exception {
		promoteFullPipeline("norm-v1", "ruleset-v1", "category-v1", "model-v1");

		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO filter_release
				(normalization_ref, local_ruleset_ref, category_mapping_ref, model_snapshot, status, promoted_at)
			VALUES ('norm-x', 'ruleset-x', 'category-x', 'model-x', 'PROMOTED', now())
			""")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("세션이 없으면 registry endpoint는 401로 거절된다")
	void rejectsUnauthenticatedAccess() throws Exception {
		mockMvc.perform(get("/admin/filtering/releases"))
			.andExpect(status().isUnauthorized());
	}

	private long promoteFullPipeline(String normRef, String rulesetRef, String categoryRef, String modelSnapshot)
		throws Exception {
		long releaseId = createCandidate(normRef, rulesetRef, categoryRef, modelSnapshot);
		transition(releaseId, "offline-evaluation").andExpect(status().isOk());
		transition(releaseId, "shadow").andExpect(status().isOk());
		transition(releaseId, "canary").andExpect(status().isOk());
		transition(releaseId, "promote").andExpect(status().isOk());
		return releaseId;
	}

	private long createCandidate(String normRef, String rulesetRef, String categoryRef, String modelSnapshot)
		throws Exception {
		MvcResult result = mockMvc.perform(withSession(withCsrf(post("/admin/filtering/releases")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"normalizationRef":"%s","localRulesetRef":"%s","categoryMappingRef":"%s","modelSnapshot":"%s"}
					""".formatted(normRef, rulesetRef, categoryRef, modelSnapshot)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
	}

	private ResultActions transition(long releaseId, String action) throws Exception {
		return mockMvc.perform(
			withSession(withCsrf(post("/admin/filtering/releases/%d/%s".formatted(releaseId, action)))));
	}

	private MockHttpServletRequestBuilder withSession(MockHttpServletRequestBuilder request) {
		return request.cookie(sessionCookie);
	}

	private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
		return request
			.header(csrfHeaderName, csrfToken)
			.cookie(csrfCookies);
	}
}
