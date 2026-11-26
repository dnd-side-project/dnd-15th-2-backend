package com.dnd.qello.auth.config;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 운영자 비밀번호 변경 주기(#376, 위치기반서비스 점검표 C4).
//
// 마지막 변경 후 maxAge가 지나면 다음 로그인부터 비밀번호를 바꾸기 전까지 운영자 API를 막는다. 판정은 로그인할 때
// 한 번 한다. 세션 도중에 만료되면 다음 로그인까지 반영되지 않고, 세션 절대 만료(operator-session)가 그 상한이다.
// 값이 빠지거나 0 이하면 주기가 사라지거나 모든 로그인이 바로 변경을 요구하므로 바인딩 시점에 실패시킨다.
@ConfigurationProperties(prefix = "qello.auth.operator-password")
public record OperatorPasswordProperties(Duration maxAge) {

	public OperatorPasswordProperties {
		Objects.requireNonNull(maxAge, "qello.auth.operator-password.max-age는 필수입니다");
		if (maxAge.isZero() || maxAge.isNegative()) {
			throw new IllegalArgumentException("qello.auth.operator-password.max-age는 0보다 커야 합니다");
		}
	}
}
