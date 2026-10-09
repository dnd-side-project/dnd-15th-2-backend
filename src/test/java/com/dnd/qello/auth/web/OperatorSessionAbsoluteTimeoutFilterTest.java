/*
 * Created at: 2026-10-09T20:42:16+09:00
 * Source scenario: TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-UNIT-001 through UNIT-003
 */
package com.dnd.qello.auth.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorSessionAbsoluteTimeoutFilterTest {

	private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(12);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("UNIT-001: 생성 후 12시간 - 1ms인 세션은 유지하고 다음 필터로 넘긴다")
	void keepsSessionBeforeAbsoluteTimeout() throws Exception {
		MockHttpSession session = new MockHttpSession();
		MockHttpServletRequest request = requestWith(session);
		MockFilterChain chain = new MockFilterChain();

		filterAt(createdAt(session).plus(ABSOLUTE_TIMEOUT).minusMillis(1))
				.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(session.isInvalid()).isFalse();
		assertThat(chain.getRequest()).isSameAs(request);
	}

	@ParameterizedTest(name = "생성 후 12시간 + {0}ms")
	@ValueSource(longs = {0, 1})
	@DisplayName("UNIT-002: 생성 후 12시간 정각부터 세션을 무효화하고 인증을 지운 뒤 응답을 쓰지 않고 다음 필터로 넘긴다")
	void invalidatesSessionFromAbsoluteTimeout(long millisPastTimeout) throws Exception {
		MockHttpSession session = new MockHttpSession();
		MockHttpServletRequest request = requestWith(session);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		SecurityContextHolder.getContext()
				.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("1", null, List.of()));

		filterAt(createdAt(session).plus(ABSOLUTE_TIMEOUT).plusMillis(millisPastTimeout))
				.doFilter(request, response, chain);

		assertThat(session.isInvalid()).isTrue();
		assertThat(request.getSession(false)).isNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(response.isCommitted()).isFalse();
		assertThat(response.getContentAsString()).isEmpty();
	}

	@Test
	@DisplayName("UNIT-003: 세션이 없는 요청은 세션을 만들지 않고 다음 필터로 넘긴다")
	void passesThroughWithoutCreatingSession() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/csrf");
		MockFilterChain chain = new MockFilterChain();

		filterAt(Instant.parse("2026-10-09T00:00:00Z")).doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(request.getSession(false)).isNull();
		assertThat(chain.getRequest()).isSameAs(request);
	}

	private static OperatorSessionAbsoluteTimeoutFilter filterAt(Instant now) {
		return new OperatorSessionAbsoluteTimeoutFilter(Clock.fixed(now, ZoneOffset.UTC), ABSOLUTE_TIMEOUT);
	}

	private static Instant createdAt(MockHttpSession session) {
		return Instant.ofEpochMilli(session.getCreationTime());
	}

	private static MockHttpServletRequest requestWith(MockHttpSession session) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/filtering/manual-review-cases");
		request.setSession(session);
		return request;
	}
}
