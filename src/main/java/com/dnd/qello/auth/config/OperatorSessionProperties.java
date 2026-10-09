package com.dnd.qello.auth.config;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 운영자 세션의 최대 수명(AUTH_DESIGN.md 2절, #342).
//
// 미사용 만료는 spring.session.timeout이 맡는다. Spring Session에는 로그인 시각 기준의 상한이 없어
// OperatorSessionAbsoluteTimeoutFilter가 이 값으로 판정한다. 값이 빠지거나 0 이하면 상한이 사라지거나
// 모든 세션이 바로 끝나므로 바인딩 시점에 실패시킨다.
@ConfigurationProperties(prefix = "qello.auth.operator-session")
public record OperatorSessionProperties(Duration absoluteTimeout) {

	public OperatorSessionProperties {
		Objects.requireNonNull(absoluteTimeout, "qello.auth.operator-session.absolute-timeout은 필수입니다");
		if (absoluteTimeout.isZero() || absoluteTimeout.isNegative()) {
			throw new IllegalArgumentException("qello.auth.operator-session.absolute-timeout은 0보다 커야 합니다");
		}
	}
}
