/*
 * Created at: 2026-11-24T23:50:07+09:00
 * Source scenario: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-016
 */
package com.dnd.qello.auth.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.assertj.core.api.Assertions.assertThat;

class AuthEntryPointsTest {

	private static final Instant NOW = Instant.parse("2026-11-24T14:00:00Z");

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
	private final AuthEntryPoints entryPoints = new AuthEntryPoints(
			new ApiErrorResponseFactory(Clock.fixed(NOW, ZoneOffset.UTC)), objectMapper);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("UNIT-016: 비밀번호를 바꿔야 하는 세션의 권한 거부는 403 AUT-APP-008이다")
	void passwordChangeSessionGetsChangeRequiredCode() throws Exception {
		authenticate("ROLE_OPERATOR_PASSWORD_CHANGE");

		MockHttpServletResponse response = deny(new AccessDeniedException("Access Denied"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(errorCode(response)).isEqualTo("AUT-APP-008");
	}

	@ParameterizedTest(name = "{0}")
	@DisplayName("UNIT-016: 비밀번호를 바꿔야 하는 세션이라도 CSRF 거부는 공통 403이다")
	@MethodSource("csrfFailures")
	void csrfFailureKeepsCommonForbidden(String name, AccessDeniedException exception) throws Exception {
		authenticate("ROLE_OPERATOR_PASSWORD_CHANGE");

		MockHttpServletResponse response = deny(exception);

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(errorCode(response)).isEqualTo("CMN-DOM-001");
	}

	@Test
	@DisplayName("UNIT-016: 정상 운영자 세션의 권한 거부는 공통 403이다")
	void fullOperatorKeepsCommonForbidden() throws Exception {
		authenticate("ROLE_OPERATOR");

		MockHttpServletResponse response = deny(new AccessDeniedException("Access Denied"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(errorCode(response)).isEqualTo("CMN-DOM-001");
	}

	@Test
	@DisplayName("UNIT-016: 응답 본문에 예외 메시지를 담지 않는다")
	void responseCarriesNoExceptionMessage() throws Exception {
		authenticate("ROLE_OPERATOR_PASSWORD_CHANGE");

		MockHttpServletResponse response = deny(new AccessDeniedException("internal-detail-xyz"));

		assertThat(response.getContentAsString()).doesNotContain("internal-detail-xyz");
	}

	static Stream<Object[]> csrfFailures() {
		return Stream.of(
				new Object[]{"InvalidCsrfTokenException",
						new InvalidCsrfTokenException(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "expected"),
								"actual")},
				new Object[]{"MissingCsrfTokenException", new MissingCsrfTokenException("actual")});
	}

	private MockHttpServletResponse deny(AccessDeniedException exception) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		entryPoints.forbidden().handle(new MockHttpServletRequest("GET", "/api/v1/operator/report-cases"), response,
				exception);
		return response;
	}

	private String errorCode(MockHttpServletResponse response) throws Exception {
		JsonNode body = objectMapper.readTree(response.getContentAsString());
		return body.get("errorDetail").get("code").asText();
	}

	private static void authenticate(String authority) {
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				"42", null, AuthorityUtils.createAuthorityList(authority)));
	}
}
