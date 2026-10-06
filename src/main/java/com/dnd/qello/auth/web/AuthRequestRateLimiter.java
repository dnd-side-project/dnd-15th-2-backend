package com.dnd.qello.auth.web;

import java.time.Clock;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import com.dnd.qello.auth.config.AuthRateLimitProperties;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.common.ratelimit.ClientAddressKey;
import com.dnd.qello.common.ratelimit.FixedWindowRateLimiter;

// 인증 없이 열린 경로의 클라이언트 IP 단위 요청 한도(#315).
//
// 컨트롤러에 도달한 요청을 성공·실패와 무관하게 센다. 실패한 로그인과 등록 시도가 막아야 할
// 대상이기 때문이다. 검사는 서비스 호출보다 먼저 해서, 거절된 요청이 계정·자격증명을 만들거나
// 운영자 로그인 실패 횟수를 올리지 않게 한다. 실패 횟수가 오르면 남의 IP 한도가 그 계정의 잠금으로
// 번진다.
@Component
public class AuthRequestRateLimiter {

	private final FixedWindowRateLimiter deviceRegistration;
	private final FixedWindowRateLimiter tokenReissue;
	private final FixedWindowRateLimiter operatorLogin;

	public AuthRequestRateLimiter(AuthRateLimitProperties properties, Clock clock) {
		this.deviceRegistration = new FixedWindowRateLimiter(properties.deviceRegistration(), clock);
		this.tokenReissue = new FixedWindowRateLimiter(properties.tokenReissue(), clock);
		this.operatorLogin = new FixedWindowRateLimiter(properties.operatorLogin(), clock);
	}

	public void checkDeviceRegistration(HttpServletRequest request) {
		check(deviceRegistration, request);
	}

	public void checkTokenReissue(HttpServletRequest request) {
		check(tokenReissue, request);
	}

	public void checkOperatorLogin(HttpServletRequest request) {
		check(operatorLogin, request);
	}

	private static void check(FixedWindowRateLimiter limiter, HttpServletRequest request) {
		if (!limiter.tryAcquire(ClientAddressKey.of(request))) {
			throw new AuthException(AuthErrorCode.RATE_LIMIT_EXCEEDED, null, "요청 한도를 넘었습니다");
		}
	}
}
