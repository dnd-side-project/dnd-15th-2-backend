/*
 * Created at: 2026-11-24T23:50:07+09:00
 * Source scenario: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-017
 */
package com.dnd.qello.auth.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dnd.qello.auth.domain.OperatorAccessEvent;
import com.dnd.qello.auth.domain.OperatorAccessLogEntry;
import com.dnd.qello.auth.repository.OperatorAccessLogRepository;
import com.dnd.qello.auth.service.OperatorAccessLogRecorder;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorAccessContextTest {

	private static final Instant NOW = Instant.parse("2026-11-24T14:00:00Z");

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("UNIT-017: 비밀번호를 바꿔야 하는 세션도 운영자 id를 꺼낸다")
	void extractsOperatorIdFromPasswordChangeSession() {
		authenticate("42", "ROLE_OPERATOR_PASSWORD_CHANGE");

		assertThat(OperatorAccessContext.currentOperatorId()).isEqualTo(42L);
		assertThat(OperatorAccessContext.isPasswordChangeSession()).isTrue();
	}

	@Test
	@DisplayName("UNIT-017: 정상 운영자 세션은 운영자 id를 꺼내고 변경 필요 세션이 아니다")
	void extractsOperatorIdFromFullSession() {
		authenticate("42", "ROLE_OPERATOR");

		assertThat(OperatorAccessContext.currentOperatorId()).isEqualTo(42L);
		assertThat(OperatorAccessContext.isPasswordChangeSession()).isFalse();
	}

	@Test
	@DisplayName("UNIT-017: 운영자 세션 권한이 아니면 운영자 id가 없다")
	void ignoresNonOperatorAuthority() {
		authenticate("42", "ROLE_USER");

		assertThat(OperatorAccessContext.currentOperatorId()).isNull();
		assertThat(OperatorAccessContext.isPasswordChangeSession()).isFalse();
	}

	@Test
	@DisplayName("UNIT-017: 접근 기록 필터는 비밀번호를 바꿔야 하는 세션의 거부된 호출도 운영자 id와 함께 남긴다")
	void accessLogFilterRecordsPasswordChangeSession() throws Exception {
		List<OperatorAccessLogEntry> entries = new ArrayList<>();
		OperatorAccessLogRepository repository = entries::add;
		OperatorAccessLogFilter filter = new OperatorAccessLogFilter(
				new OperatorAccessLogRecorder(repository), Clock.fixed(NOW, ZoneOffset.UTC), Set.of());
		authenticate("42", "ROLE_OPERATOR_PASSWORD_CHANGE");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/operator/report-cases");
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(403);

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(entries).singleElement().satisfies(entry -> {
			assertThat(entry.eventType()).isEqualTo(OperatorAccessEvent.API_CALL);
			assertThat(entry.operatorUserId()).isEqualTo(42L);
			assertThat(entry.httpStatus()).isEqualTo(403);
		});
	}

	private static void authenticate(String principal, String authority) {
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				principal, null, AuthorityUtils.createAuthorityList(authority)));
	}
}
