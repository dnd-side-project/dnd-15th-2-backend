/**
 * Created at: 2026-08-07T20:52:09+09:00
 * Source scenario: TEST-PLAN-GH-73-DEVICE-AUTH-INT-001 through INT-009,
 * TEST-PLAN-GH-88-COUNTRY-ONBOARDING-INT-001 through INT-002
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "account-persistence"})
class DeviceAuthIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-COUNTRY";
	private static final String COUNTRY_CODE = "KR";
	private static final String INSTALLATION_ID = "installation-a";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("DELETE FROM device_credential");
		jdbcTemplate.update("DELETE FROM user_account");
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", REGION_CODE);
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", COUNTRY_CODE);
		jdbcTemplate.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, NULL, 'Korea', 'COUNTRY'), (?, ?, 'Test Region', 'REGION')
			""", COUNTRY_CODE, REGION_CODE, COUNTRY_CODE);
	}

	@Test
	@DisplayName("등록에 성공하면 201과 함께 계정, 평문 시크릿, 액세스 토큰을 응답한다")
	void registersDeviceAndReturnsSecretOnce() throws Exception {
		mockMvc.perform(register(INSTALLATION_ID))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("success"))
			.andExpect(jsonPath("$.data.userId").isNumber())
			.andExpect(jsonPath("$.data.deviceSecret").isNotEmpty())
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.data.expiresIn").value(1800));
	}

	@Test
	@DisplayName("등록 응답의 deviceSecret은 DB에 평문으로 남지 않는다")
	void neverPersistsRawDeviceSecret() throws Exception {
		MvcResult result = mockMvc.perform(register(INSTALLATION_ID))
			.andExpect(status().isCreated())
			.andReturn();
		String rawSecret = dataNode(result).get("deviceSecret").asText();

		String storedHash = jdbcTemplate.queryForObject(
			"SELECT secret_hash FROM device_credential WHERE installation_id = ?",
			String.class, INSTALLATION_ID);

		assertThat(storedHash).doesNotContain(rawSecret);
		assertThat(result.getResponse().getContentAsString()).doesNotContain(storedHash);
	}

	@Test
	@DisplayName("ACTIVE 자격증명이 있는 installationId로 재등록하면 409를 받는다")
	void rejectsReRegistrationOfActiveInstallation() throws Exception {
		mockMvc.perform(register(INSTALLATION_ID)).andExpect(status().isCreated());

		mockMvc.perform(register(INSTALLATION_ID))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-005"));
	}

	@Test
	@DisplayName("등록한 자격증명으로 토큰을 재발급받을 수 있다")
	void reissuesTokenWithRegisteredCredential() throws Exception {
		MvcResult registered = mockMvc.perform(register(INSTALLATION_ID))
			.andExpect(status().isCreated())
			.andReturn();
		String rawSecret = dataNode(registered).get("deviceSecret").asText();

		mockMvc.perform(reissue(INSTALLATION_ID, rawSecret))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("success"))
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.data.expiresIn").value(1800));
	}

	@Test
	@DisplayName("잘못된 deviceSecret으로 재발급을 요청하면 401을 받는다")
	void rejectsReissueWithWrongSecret() throws Exception {
		mockMvc.perform(register(INSTALLATION_ID)).andExpect(status().isCreated());

		mockMvc.perform(reissue(INSTALLATION_ID, "wrong-secret-value"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-006"));
	}

	@Test
	@DisplayName("차단된 계정은 자격증명이 맞아도 재발급 시 403을 받는다")
	void rejectsReissueForBlockedAccount() throws Exception {
		MvcResult registered = mockMvc.perform(register(INSTALLATION_ID))
			.andExpect(status().isCreated())
			.andReturn();
		String rawSecret = dataNode(registered).get("deviceSecret").asText();
		jdbcTemplate.update("UPDATE user_account SET status = 'BLOCKED' WHERE coarse_region_code = ?", REGION_CODE);

		mockMvc.perform(reissue(INSTALLATION_ID, rawSecret))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-003"));
	}

	@Test
	@DisplayName("발급받은 액세스 토큰으로 보호된 /api/** 경로에 인증된 상태로 접근한다")
	void issuedAccessTokenAuthenticatesProtectedApiPath() throws Exception {
		MvcResult registered = mockMvc.perform(register(INSTALLATION_ID))
			.andExpect(status().isCreated())
			.andReturn();
		String issuedToken = dataNode(registered).get("accessToken").asText();

		// 매핑된 핸들러가 없어 404가 나더라도, Security 필터를 통과했다는 사실이 중요하다.
		// 토큰 없이 호출하면(다른 테스트) 필터에서 401로 막힌다.
		mockMvc.perform(get("/api/v1/anything").header(HttpHeaders.AUTHORIZATION, "Bearer " + issuedToken))
			.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
	}

	@Test
	@DisplayName("유효하지 않은 토큰으로 보호된 /api/** 경로에 접근하면 401을 받는다")
	void rejectsProtectedApiPathWithoutValidToken() throws Exception {
		mockMvc.perform(get("/api/v1/anything")
				.header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("installationId 없는 등록 요청은 400을 받는다")
	void rejectsRegistrationWithoutInstallationId() throws Exception {
		mockMvc.perform(post("/api/v1/auth/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"platform":"IOS","countryCode":"%s","coarseRegionCode":"%s","locale":"ko-KR","timezone":"Asia/Seoul"}
					""".formatted(COUNTRY_CODE, REGION_CODE)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("국가가 없는 등록 요청은 계정과 자격증명을 만들지 않고 400을 반환한다")
	void rejectsRegistrationWithoutCountryBeforePersistence() throws Exception {
		mockMvc.perform(post("/api/v1/auth/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"installationId":"missing-country","platform":"IOS","coarseRegionCode":"%s",
					 "locale":"ko-KR","timezone":"Asia/Seoul"}
					""".formatted(REGION_CODE)))
			.andExpect(status().isBadRequest());

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_account", Integer.class)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM device_credential", Integer.class)).isZero();
	}

	@Test
	@DisplayName("국가와 기준 지역이 다르면 계정과 자격증명을 만들지 않고 400을 반환한다")
	void rejectsRegistrationWhenCountryDoesNotMatchRegion() throws Exception {
		mockMvc.perform(post("/api/v1/auth/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"installationId":"mismatched-country","platform":"IOS","countryCode":"US",
					 "coarseRegionCode":"%s","locale":"ko-KR","timezone":"Asia/Seoul"}
					""".formatted(REGION_CODE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("AUT-VAL-004"));

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_account", Integer.class)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM device_credential", Integer.class)).isZero();
	}

	private MockHttpServletRequestBuilder register(String installationId) {
		return post("/api/v1/auth/devices")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "installationId": "%s",
				  "platform": "IOS",
				  "countryCode": "%s",
				  "coarseRegionCode": "%s",
				  "locale": "ko-KR",
				  "timezone": "Asia/Seoul",
				  "nickname": "바람"
				}
				""".formatted(installationId, COUNTRY_CODE, REGION_CODE));
	}

	private MockHttpServletRequestBuilder reissue(
		String installationId, String deviceSecret
	) {
		return post("/api/v1/auth/token")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"installationId": "%s", "deviceSecret": "%s"}
				""".formatted(installationId, deviceSecret));
	}

	private JsonNode dataNode(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
	}

}
