package com.dnd.qello.auth.token;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 앱 액세스 토큰 발급 설정.
//
// 서명 키는 환경변수로만 주입한다. HS256은 최소 32바이트(256bit) 키를 요구하므로
// 짧은 값을 넣으면 서명 시점에 실패한다. 저장소의 properties 파일이나 migration에
// 실제 키를 적지 않는다.
@ConfigurationProperties(prefix = "qello.auth.access-token")
public record AccessTokenProperties(
	@DefaultValue("qello") String issuer,
	@DefaultValue("qello-app") String audience,
	@DefaultValue("1800") long ttlSeconds,
	String secret
) {

	@Override
	public String toString() {
		return "AccessTokenProperties[issuer=%s, audience=%s, ttlSeconds=%s, secret=REDACTED]"
			.formatted(issuer, audience, ttlSeconds);
	}

}
