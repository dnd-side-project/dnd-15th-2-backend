/*
 * Created at: 2026-11-22T22:12:29+09:00
 * Source scenario: TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-007 through UNIT-013
 */
package com.dnd.qello.auth.web;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dnd.qello.auth.domain.OperatorAccessEvent;
import com.dnd.qello.auth.domain.OperatorAccessLogEntry;
import com.dnd.qello.auth.domain.OperatorAccessRequest;
import com.dnd.qello.auth.repository.OperatorAccessLogRepository;
import com.dnd.qello.auth.service.OperatorAccessLogRecorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorAccessLogFilterTest {

	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");
	private static final String REPORT_CASES = "/api/v1/operator/report-cases";

	private static final Set<String> EXCLUDED = Set.of(
			OperatorAccessLogFilter.endpoint("POST", "/admin/login"),
			OperatorAccessLogFilter.endpoint("POST", "/admin/logout"),
			OperatorAccessLogFilter.endpoint("GET", "/admin/csrf"));

	private final FakeRepository repository = new FakeRepository();
	private final OperatorAccessLogFilter filter = new OperatorAccessLogFilter(
			new OperatorAccessLogRecorder(repository), Clock.fixed(NOW, ZoneOffset.UTC), EXCLUDED);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("UNIT-007: 인증된 운영자 요청은 query 없는 경로·method·상태·고정 시각으로 한 건 기록한다")
	void recordsAuthenticatedOperatorCall() throws Exception {
		authenticateOperator("42");
		MockHttpServletRequest request = request("GET", REPORT_CASES);
		request.setQueryString("queue=URGENT");

		filter.doFilter(request, new MockHttpServletResponse(), respondingWith(200));

		assertThat(repository.entries).singleElement().satisfies(entry -> {
			assertThat(entry.eventType()).isEqualTo(OperatorAccessEvent.API_CALL);
			assertThat(entry.operatorUserId()).isEqualTo(42L);
			assertThat(entry.request()).isEqualTo(new OperatorAccessRequest("192.0.2.10", "GET", REPORT_CASES));
			assertThat(entry.httpStatus()).isEqualTo(200);
			assertThat(entry.resultCode()).isNull();
			assertThat(entry.occurredAt()).isEqualTo(NOW);
		});
	}

	@ParameterizedTest
	@DisplayName("UNIT-008: 다음 필터가 쓴 오류 상태를 그대로 기록한다")
	@ValueSource(ints = {403, 404})
	void recordsStatusWrittenDownstream(int status) throws Exception {
		authenticateOperator("42");

		filter.doFilter(request("POST", REPORT_CASES + "/7/review"), new MockHttpServletResponse(),
				respondingWith(status));

		assertThat(repository.entries).singleElement()
				.satisfies(entry -> assertThat(entry.httpStatus()).isEqualTo(status));
	}

	@Test
	@DisplayName("UNIT-009: chain 예외는 상태가 성공이면 500으로 기록하고 같은 예외를 다시 던진다")
	void recordsServerErrorAndRethrowsChainFailure() {
		authenticateOperator("42");
		IllegalStateException runtime = new IllegalStateException("runtime");
		ServletException servlet = new ServletException("servlet");
		IOException io = new IOException("io");

		assertThatThrownBy(() -> filter.doFilter(request("GET", REPORT_CASES), new MockHttpServletResponse(),
				(req, res) -> {
					throw runtime;
				})).isSameAs(runtime);
		assertThatThrownBy(() -> filter.doFilter(request("GET", REPORT_CASES), new MockHttpServletResponse(),
				(req, res) -> {
					throw servlet;
				})).isSameAs(servlet);
		assertThatThrownBy(() -> filter.doFilter(request("GET", REPORT_CASES), new MockHttpServletResponse(),
				(req, res) -> {
					throw io;
				})).isSameAs(io);

		assertThat(repository.entries).hasSize(3).allSatisfy(entry -> assertThat(entry.httpStatus()).isEqualTo(500));
	}

	@Test
	@DisplayName("UNIT-009: chain 예외 전에 이미 4xx가 쓰였으면 그 상태를 기록한다")
	void keepsClientErrorStatusOnChainFailure() {
		authenticateOperator("42");
		IllegalStateException failure = new IllegalStateException("after status");

		assertThatThrownBy(() -> filter.doFilter(request("GET", REPORT_CASES), new MockHttpServletResponse(),
				(req, res) -> {
					((MockHttpServletResponse) res).setStatus(403);
					throw failure;
				}))
				.isSameAs(failure);

		assertThat(repository.entries).singleElement()
				.satisfies(entry -> assertThat(entry.httpStatus()).isEqualTo(403));
	}

	@ParameterizedTest
	@DisplayName("UNIT-010: 로그인·로그아웃·CSRF 발급 경로는 기록하지 않고 다음 필터를 한 번 호출한다")
	@CsvSource({"POST, /admin/login", "POST, /admin/logout", "GET, /admin/csrf"})
	void skipsExcludedPaths(String method, String path) throws Exception {
		authenticateOperator("42");
		AtomicInteger calls = new AtomicInteger();

		filter.doFilter(request(method, path), new MockHttpServletResponse(), (req, res) -> calls.incrementAndGet());

		assertThat(calls).hasValue(1);
		assertThat(repository.entries).isEmpty();
	}

	@Test
	@DisplayName("UNIT-011: 인증이 없거나 익명이거나 운영자 권한이 없으면 기록하지 않는다")
	void skipsRequestsWithoutAuthenticatedOperator() throws Exception {
		filter.doFilter(request("GET", REPORT_CASES), new MockHttpServletResponse(), respondingWith(401));

		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
				"key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
		filter.doFilter(request("GET", REPORT_CASES), new MockHttpServletResponse(), respondingWith(401));

		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				"42", null, AuthorityUtils.createAuthorityList("ROLE_USER")));
		filter.doFilter(request("GET", REPORT_CASES), new MockHttpServletResponse(), respondingWith(403));

		assertThat(repository.entries).isEmpty();
	}

	@Test
	@DisplayName("UNIT-010: 제외 경로라도 method가 다르면 인증된 요청으로 기록하고, 인코딩한 경로는 디코딩해 거른다")
	void excludesOnlyExactMethodAndDecodedPath() throws Exception {
		authenticateOperator("42");

		filter.doFilter(request("GET", "/admin/logout"), new MockHttpServletResponse(), respondingWith(405));
		filter.doFilter(request("POST", "/admin/%6cogout"), new MockHttpServletResponse(), respondingWith(200));

		assertThat(repository.entries).singleElement().satisfies(entry -> {
			assertThat(entry.request().httpMethod()).isEqualTo("GET");
			assertThat(entry.request().requestPath()).isEqualTo("/admin/logout");
			assertThat(entry.httpStatus()).isEqualTo(405);
		});
	}

	@Test
	@DisplayName("UNIT-012: recorder가 예외를 던져도 필터는 예외를 던지지 않고 응답 상태를 바꾸지 않는다")
	void keepsResponseWhenRecorderThrows() {
		authenticateOperator("42");
		OperatorAccessLogFilter failingFilter = new OperatorAccessLogFilter(
				new ThrowingRecorder(), Clock.fixed(NOW, ZoneOffset.UTC), EXCLUDED);
		MockHttpServletResponse response = new MockHttpServletResponse();

		assertThatCode(() -> failingFilter.doFilter(request("GET", REPORT_CASES), response, respondingWith(200)))
				.doesNotThrowAnyException();

		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("UNIT-012: chain 예외와 recorder 예외가 함께 나면 chain 예외를 던지고 recorder 예외는 suppressed로 붙인다")
	void keepsChainFailureWhenRecorderAlsoThrows() {
		authenticateOperator("42");
		OperatorAccessLogFilter failingFilter = new OperatorAccessLogFilter(
				new ThrowingRecorder(), Clock.fixed(NOW, ZoneOffset.UTC), EXCLUDED);
		IllegalStateException chainFailure = new IllegalStateException("handler failed");

		assertThatThrownBy(() -> failingFilter.doFilter(request("GET", REPORT_CASES), new MockHttpServletResponse(),
				(req, res) -> {
					throw chainFailure;
				}))
				.isSameAs(chainFailure);
		assertThat(chainFailure.getSuppressed()).singleElement()
				.satisfies(suppressed -> assertThat(suppressed).hasMessage("recorder unavailable"));
	}

	@Test
	@DisplayName("UNIT-012: 저장소가 실패해도 필터는 예외를 던지지 않고 응답 상태를 바꾸지 않는다")
	void keepsResponseWhenRepositoryFails() {
		authenticateOperator("42");
		repository.failure = new IllegalStateException("database unavailable");
		MockHttpServletResponse response = new MockHttpServletResponse();

		assertThatCode(() -> filter.doFilter(request("GET", REPORT_CASES), response, respondingWith(200)))
				.doesNotThrowAnyException();

		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("UNIT-013: 512자를 넘는 경로는 512자로 잘라 기록한다")
	void truncatesLongPath() throws Exception {
		authenticateOperator("42");
		String longPath = "/admin/" + "p".repeat(600);

		filter.doFilter(request("GET", longPath), new MockHttpServletResponse(), respondingWith(404));

		assertThat(repository.entries).singleElement().satisfies(entry -> assertThat(entry.request().requestPath())
				.hasSize(OperatorAccessRequest.MAX_REQUEST_PATH_LENGTH)
				.isEqualTo(longPath.substring(0, OperatorAccessRequest.MAX_REQUEST_PATH_LENGTH)));
	}

	private static void authenticateOperator(String principal) {
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				principal, null, AuthorityUtils.createAuthorityList("ROLE_OPERATOR")));
	}

	private static MockHttpServletRequest request(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr("192.0.2.10");
		return request;
	}

	private static FilterChain respondingWith(int status) {
		return (req, res) -> ((MockHttpServletResponse) res).setStatus(status);
	}

	private static final class ThrowingRecorder extends OperatorAccessLogRecorder {

		ThrowingRecorder() {
			super(entry -> {
			});
		}

		@Override
		public void recordIndependently(OperatorAccessLogEntry entry) {
			throw new IllegalStateException("recorder unavailable");
		}
	}

	private static final class FakeRepository implements OperatorAccessLogRepository {

		private final List<OperatorAccessLogEntry> entries = new ArrayList<>();
		private RuntimeException failure;

		@Override
		public void append(OperatorAccessLogEntry entry) {
			if (failure != null) {
				throw failure;
			}
			entries.add(entry);
		}
	}
}
