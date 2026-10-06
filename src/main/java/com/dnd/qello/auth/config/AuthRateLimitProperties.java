package com.dnd.qello.auth.config;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.dnd.qello.common.ratelimit.RateLimitPolicy;

// 인증 없이 열린 경로의 클라이언트 IP 단위 요청 한도(#315, AUTH_DESIGN.md 8.2절).
//
// 기본값은 application.yml에 두고 운영에서는 환경변수로 덮어쓴다. 값이 빠지면 한도 없이
// 기동하지 않도록 바인딩 시점에 실패시킨다.
@ConfigurationProperties(prefix = "qello.auth.rate-limit")
public record AuthRateLimitProperties(
		RateLimitPolicy deviceRegistration,
		RateLimitPolicy tokenReissue,
		RateLimitPolicy operatorLogin) {

	public AuthRateLimitProperties {
		Objects.requireNonNull(deviceRegistration, "qello.auth.rate-limit.device-registration은 필수입니다");
		Objects.requireNonNull(tokenReissue, "qello.auth.rate-limit.token-reissue는 필수입니다");
		Objects.requireNonNull(operatorLogin, "qello.auth.rate-limit.operator-login은 필수입니다");
	}
}
