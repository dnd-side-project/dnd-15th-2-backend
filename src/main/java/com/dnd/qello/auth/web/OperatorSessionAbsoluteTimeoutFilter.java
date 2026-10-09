package com.dnd.qello.auth.web;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// 운영자 세션의 최대 수명을 강제한다(AUTH_DESIGN.md 2절, #342).
//
// Spring Session은 미사용 만료만 판정하고 로그인 시각 기준의 상한은 없다. 로그인할 때 세션을 새로
// 발급하므로(OperatorLoginController) 세션 생성 시각이 곧 로그인 시각이다.
//
// 만료된 세션은 무효화만 하고 요청은 익명으로 계속 보낸다. 여기서 401을 직접 쓰면 오래된 쿠키를 가진
// 브라우저가 /admin/csrf와 /admin/login까지 막혀 다시 로그인할 수 없다. 보호 경로의 401은 기존
// 진입점(AuthEntryPoints)이 낸다.
//
// 빈으로 등록하면 Spring Boot가 서블릿 필터로 모든 경로에 걸어 앱 API 요청도 운영자 세션을 지우게 된다.
// 그래서 SecurityConfiguration이 세션 기반 체인에만 직접 만들어 넣는다.
public class OperatorSessionAbsoluteTimeoutFilter extends OncePerRequestFilter {

	private final Clock clock;
	private final Duration absoluteTimeout;

	public OperatorSessionAbsoluteTimeoutFilter(Clock clock, Duration absoluteTimeout) {
		this.clock = Objects.requireNonNull(clock, "clock");
		this.absoluteTimeout = Objects.requireNonNull(absoluteTimeout, "absoluteTimeout");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null && isPastAbsoluteTimeout(session)) {
			session.invalidate();
			SecurityContextHolder.clearContext();
		}
		filterChain.doFilter(request, response);
	}

	private boolean isPastAbsoluteTimeout(HttpSession session) {
		Instant expiresAt = Instant.ofEpochMilli(session.getCreationTime()).plus(absoluteTimeout);
		return !clock.instant().isBefore(expiresAt);
	}
}
