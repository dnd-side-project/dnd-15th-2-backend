/*
 * Created at: 2026-10-06T14:26:52+09:00
 * Source scenario: TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-015
 */
package com.dnd.qello.config;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import com.dnd.qello.account.config.NicknameChangeProperties;
import com.dnd.qello.auth.config.AuthRateLimitProperties;
import com.dnd.qello.common.ratelimit.RateLimitPolicy;

import static org.assertj.core.api.Assertions.assertThat;

// 환경변수가 섞이지 않도록 application.yml만 property source로 두고 production과 같은 record로 바인딩한다.
class RateLimitDefaultsTest {

	private Binder binder;

	@BeforeEach
	void setUp() throws IOException {
		List<PropertySource<?>> documents = new YamlPropertySourceLoader().load("application",
				new ClassPathResource("application.yml"));
		MutablePropertySources sources = new MutablePropertySources();
		documents.forEach(sources::addLast);
		binder = new Binder(ConfigurationPropertySources.from(sources),
				new PropertySourcesPlaceholdersResolver(sources));
	}

	@Test
	@DisplayName("UNIT-015: 인증 경로 IP 한도 기본값은 등록 10회/1시간, 재발급 60회/1시간, 운영자 로그인 20회/15분이다")
	void authRateLimitDefaults() {
		AuthRateLimitProperties properties = binder.bind("qello.auth.rate-limit", AuthRateLimitProperties.class).get();

		assertThat(properties.deviceRegistration()).isEqualTo(new RateLimitPolicy(10, Duration.ofHours(1)));
		assertThat(properties.tokenReissue()).isEqualTo(new RateLimitPolicy(60, Duration.ofHours(1)));
		assertThat(properties.operatorLogin()).isEqualTo(new RateLimitPolicy(20, Duration.ofMinutes(15)));
	}

	@Test
	@DisplayName("UNIT-015: 닉네임 변경 기본값은 주기 30일, 시도 한도 10회/1일이다")
	void nicknameChangeDefaults() {
		NicknameChangeProperties properties = binder
				.bind("qello.account.nickname-change", NicknameChangeProperties.class).get();

		assertThat(properties.cooldown()).isEqualTo(Duration.ofDays(30));
		assertThat(properties.attemptRateLimit()).isEqualTo(new RateLimitPolicy(10, Duration.ofDays(1)));
	}
}
