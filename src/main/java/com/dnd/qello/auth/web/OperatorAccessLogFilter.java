package com.dnd.qello.auth.web;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import com.dnd.qello.auth.domain.OperatorAccessLogEntry;
import com.dnd.qello.auth.service.OperatorAccessLogRecorder;

// 운영자 API 호출마다 운영자 id·method·경로·응답 상태를 남긴다(#375).
//
// 인증된 운영자 요청만 남긴다(테스트 계획 D1). 익명 요청까지 남기면 인증 없는 스캐너가 행을 무제한
// 늘릴 수 있다. SecurityContextHolderFilter 바로 뒤에 두므로 CsrfFilter 등이 거절한 인증된 요청(403)도
// 남는다.
//
// 로그인(POST)·로그아웃(POST)·CSRF 발급(GET)은 걸러 둔다. 로그인과 로그아웃은 컨트롤러가 결과와 함께 따로
// 남기고, CSRF 발급은 상태를 바꾸지 않는다. method와 디코딩한 경로가 함께 맞을 때만 거른다. 다른 method로 온
// 인증된 요청은 API 호출로 남는다.
//
// 기록 실패는 응답을 바꾸지 않는다(D4). chain이 예외로 끝났으면 그 예외를 가리지 않고 suppressed로 붙인다.
//
// OperatorSessionAbsoluteTimeoutFilter와 같은 이유로 빈으로 등록하지 않는다. 빈이면 Spring Boot가
// 서블릿 필터로 모든 경로에 건다. SecurityConfiguration이 세션 기반 체인에만 직접 넣는다.
public class OperatorAccessLogFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(OperatorAccessLogFilter.class);

	private final OperatorAccessLogRecorder recorder;
	private final Clock clock;
	private final Set<String> excludedEndpoints;

	/**
	 * @param excludedEndpoints
	 *            거를 요청. {@link #endpoint(String, String)} 형식("POST /admin/login")이다
	 */
	public OperatorAccessLogFilter(OperatorAccessLogRecorder recorder, Clock clock, Set<String> excludedEndpoints) {
		this.recorder = Objects.requireNonNull(recorder, "recorder");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.excludedEndpoints = Set.copyOf(excludedEndpoints);
	}

	public static String endpoint(String method, String path) {
		return method + " " + path;
	}

	// UrlPathHelper는 경로를 디코딩하고 ';' 뒤 내용을 뗀다. Spring MVC가 매칭하는 경로와 같은 값으로 비교해
	// 인코딩한 경로가 제외 대상을 비켜 가지 않게 한다.
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
		return excludedEndpoints.contains(endpoint(request.getMethod(), path));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Long operatorUserId = OperatorAccessContext.currentOperatorId();
		if (operatorUserId == null) {
			filterChain.doFilter(request, response);
			return;
		}

		Throwable chainFailure = null;
		try {
			filterChain.doFilter(request, response);
		} catch (IOException | ServletException | RuntimeException | Error exception) {
			chainFailure = exception;
			throw exception;
		} finally {
			record(operatorUserId, request, response, chainFailure);
		}
	}

	// recordIndependently는 저장소 실패를 이미 삼킨다. 여기서 한 번 더 막는 것은 항목 생성이나 프록시 단계의
	// 실패가 chain 예외를 가리거나 이미 커밋된 응답을 500으로 바꾸지 않게 하기 위해서다.
	private void record(long operatorUserId, HttpServletRequest request, HttpServletResponse response,
			Throwable chainFailure) {
		try {
			recorder.recordIndependently(OperatorAccessLogEntry.apiCall(operatorUserId,
					OperatorAccessContext.request(request), completionStatus(response, chainFailure != null),
					clock.instant()));
		} catch (RuntimeException recordingFailure) {
			if (chainFailure != null) {
				chainFailure.addSuppressed(recordingFailure);
				return;
			}
			log.atError()
					.addKeyValue("requestPath", request.getRequestURI())
					.addKeyValue("exception", recordingFailure.getClass().getName())
					.log("operator_access_log_write_failed");
		}
	}

	// HttpRequestLoggingFilter와 같은 규칙이다. 예외로 끝났는데 상태가 아직 성공이면 500으로 본다.
	private static int completionStatus(HttpServletResponse response, boolean failed) {
		if (failed && response.getStatus() < HttpServletResponse.SC_BAD_REQUEST) {
			return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		return response.getStatus();
	}
}
