package com.dnd.qello.common.web;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

public final class HttpRequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger HTTP_REQUEST_LOG = LoggerFactory.getLogger("HTTP_REQUEST");
	private static final String REQUEST_ID_HEADER = "X-Request-ID";
	private static final String REQUEST_ID_MDC_KEY = "requestId";
	private static final String UNRESOLVED_ROUTE = "UNRESOLVED";
	private static final Pattern TRUSTED_REQUEST_ID = Pattern.compile(
			"[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		List<String> requestIds = Collections.list(request.getHeaders(REQUEST_ID_HEADER));
		String requestId = trustedRequestId(requestIds);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		MDC.put(REQUEST_ID_MDC_KEY, requestId);
		long startedAtNanos = System.nanoTime();
		boolean failed = false;

		try {
			filterChain.doFilter(request, response);
		} catch (IOException | ServletException | RuntimeException | Error exception) {
			failed = true;
			throw exception;
		} finally {
			try {
				HTTP_REQUEST_LOG.atInfo()
						.addKeyValue("route", route(request))
						.addKeyValue("method", request.getMethod().toUpperCase(Locale.ROOT))
						.addKeyValue("status", completionStatus(response, failed))
						.addKeyValue("durationMs", elapsedMillis(startedAtNanos))
						.log("http_request_completed");
			} finally {
				MDC.remove(REQUEST_ID_MDC_KEY);
			}
		}
	}

	private String trustedRequestId(List<String> requestIds) {
		if (requestIds.size() == 1 && TRUSTED_REQUEST_ID.matcher(requestIds.getFirst()).matches()) {
			return requestIds.getFirst();
		}
		return UUID.randomUUID().toString();
	}

	private String route(HttpServletRequest request) {
		Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (route instanceof String routePattern && !routePattern.isBlank()) {
			return routePattern;
		}
		return UNRESOLVED_ROUTE;
	}

	private int completionStatus(HttpServletResponse response, boolean failed) {
		if (failed && response.getStatus() < HttpServletResponse.SC_BAD_REQUEST) {
			return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		return response.getStatus();
	}

	private long elapsedMillis(long startedAtNanos) {
		return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAtNanos));
	}
}
