package com.dnd.qello.auth.token;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 앱 액세스 토큰 발급 설정.
//
// 서명 키는 환경변수로만 주입한다. HS256은 최소 32바이트(256bit) 키를 요구하는데, 짧은 키는
// 서명 시점에야 실패해 첫 발급 요청이 500이 된다. 그래서 바인딩 시점에 검사해 기동을 막는다(#349).
// 오류 메시지에는 키 값을 넣지 않는다. 저장소의 properties 파일이나 migration에 실제 키를 적지 않는다.
@ConfigurationProperties(prefix = "qello.auth.access-token")
public record AccessTokenProperties(
		@DefaultValue("qello") String issuer,
		@DefaultValue("qello-app") String audience,
		@DefaultValue("1800") long ttlSeconds,
		String secret) {

	static final int MIN_SECRET_BYTES = 32;

	public AccessTokenProperties {
		Objects.requireNonNull(secret, "qello.auth.access-token.secret은 필수입니다");
		if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalArgumentException(
					"qello.auth.access-token.secret은 UTF-8 기준 %d바이트 이상이어야 합니다".formatted(MIN_SECRET_BYTES));
		}
	}

	@Override
	public String toString() {
		return "AccessTokenProperties[issuer=%s, audience=%s, ttlSeconds=%s, secret=REDACTED]"
				.formatted(issuer, audience, ttlSeconds);
	}

}
