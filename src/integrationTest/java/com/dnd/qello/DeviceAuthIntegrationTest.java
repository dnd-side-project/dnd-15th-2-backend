/**
 * Created at: 2026-08-07T20:52:09+09:00
 * Source scenario: TEST-PLAN-GH-73-DEVICE-AUTH-INT-001 through INT-009,
 * TEST-PLAN-GH-88-COUNTRY-ONBOARDING-INT-001 through INT-002,
 * TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-INT-001 through INT-003
 */
package com.dnd.qello;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "account-persistence"})
class DeviceAuthIntegrationTest extends PostgisContainerIntegrationTestSupport {

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
		// V29가 COUNTRY 행을 시드하지만 테스트가 시드에 의존하지 않도록 없을 때만 넣는다.
		// 다른 테스트 클래스의 행이 KR을 참조할 수 있어 삭제 후 재삽입하지 않는다.
		jdbcTemplate.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES (?, NULL, 'Korea', 'COUNTRY')
				ON CONFLICT (code) DO NOTHING
				""", COUNTRY_CODE);
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
		jdbcTemplate.update("UPDATE user_account SET status = 'BLOCKED' WHERE country_code = ?", COUNTRY_CODE);

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
						{"platform":"IOS","countryCode":"%s","locale":"ko-KR","timezone":"Asia/Seoul"}
						""".formatted(COUNTRY_CODE)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("국가가 없는 등록 요청은 계정과 자격증명을 만들지 않고 400을 반환한다")
	void rejectsRegistrationWithoutCountryBeforePersistence() throws Exception {
		mockMvc.perform(post("/api/v1/auth/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"installationId":"missing-country","platform":"IOS",
						 "locale":"ko-KR","timezone":"Asia/Seoul"}
						"""))
				.andExpect(status().isBadRequest());

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_account", Integer.class)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM device_credential", Integer.class)).isZero();
	}

	@Test
	@DisplayName("INT-001: 지역코드 없이 등록하면 국가코드가 국가와 기준 지역에 함께 저장된다")
	void storesCountryCodeAsCoarseRegionWithoutRegionInput() throws Exception {
		mockMvc.perform(register(INSTALLATION_ID)).andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForList("SELECT country_code, coarse_region_code FROM user_account"))
				.singleElement()
				.satisfies(row -> {
					assertThat(row.get("country_code")).isEqualTo(COUNTRY_CODE);
					assertThat(row.get("coarse_region_code")).isEqualTo(COUNTRY_CODE);
				});
	}

	@Test
	@DisplayName("INT-002: 이전 앱처럼 coarseRegionCode를 함께 보내도 등록되고 그 값은 저장하지 않는다")
	void ignoresLegacyCoarseRegionCodeField() throws Exception {
		mockMvc.perform(post("/api/v1/auth/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"installationId":"legacy-app","platform":"IOS","countryCode":"%s",
						 "coarseRegionCode":"XX-99","locale":"ko-KR","timezone":"Asia/Seoul"}
						""".formatted(COUNTRY_CODE)))
				.andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForObject(
				"SELECT coarse_region_code FROM user_account", String.class)).isEqualTo(COUNTRY_CODE);
	}

	@Test
	@DisplayName("INT-003: 지원하지 않는 국가 코드는 계정과 자격증명을 만들지 않고 400을 반환한다")
	void rejectsUnsupportedCountryBeforePersistence() throws Exception {
		mockMvc.perform(post("/api/v1/auth/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"installationId":"unsupported-country","platform":"IOS","countryCode":"ZZ",
						 "locale":"ko-KR","timezone":"Asia/Seoul"}
						"""))
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
						  "locale": "ko-KR",
						  "timezone": "Asia/Seoul",
						  "nickname": "바람"
						}
						""".formatted(installationId, COUNTRY_CODE));
	}

	private MockHttpServletRequestBuilder reissue(
			String installationId, String deviceSecret) {
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
