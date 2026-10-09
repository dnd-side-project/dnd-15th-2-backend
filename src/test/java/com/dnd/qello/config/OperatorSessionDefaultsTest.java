/*
 * Created at: 2026-10-09T20:42:16+09:00
 * Source scenario: TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-UNIT-005 through UNIT-006
 */
package com.dnd.qello.config;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import com.dnd.qello.auth.config.OperatorSessionProperties;

import static org.assertj.core.api.Assertions.assertThat;

// 환경변수가 섞이지 않도록 YAML 파일 하나만 property source로 두고 바인딩한다(RateLimitDefaultsTest와 같은 방식).
class OperatorSessionDefaultsTest {

	@Test
	@DisplayName("UNIT-005: 기본 설정은 미사용 8시간, 최대 12시간, Secure 쿠키이고 프록시 헤더를 읽지 않는다")
	void defaultProfile() throws IOException {
		Binder binder = binderFor("application.yml");

		assertThat(binder.bind("spring.session.timeout", Duration.class).get()).isEqualTo(Duration.ofHours(8));
		assertThat(binder.bind("server.servlet.session.cookie.secure", Boolean.class).get()).isTrue();
		assertThat(binder.bind("qello.auth.operator-session", OperatorSessionProperties.class).get().absoluteTimeout())
				.isEqualTo(Duration.ofHours(12));
		assertThat(binder.bind("server.forward-headers-strategy", String.class).isBound()).isFalse();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"application-dev.yml", "application-local.yml"})
	@DisplayName("UNIT-006: HTTP로 직접 노출하는 dev·local 프로필은 Secure 쿠키를 끄고 프록시 헤더를 읽지 않는다")
	void httpProfiles(String resource) throws IOException {
		Binder binder = binderFor(resource);

		assertThat(binder.bind("server.servlet.session.cookie.secure", Boolean.class).get()).isFalse();
		assertThat(binder.bind("server.forward-headers-strategy", String.class).isBound()).isFalse();
	}

	private static Binder binderFor(String resource) throws IOException {
		List<PropertySource<?>> documents = new YamlPropertySourceLoader().load(resource,
				new ClassPathResource(resource));
		MutablePropertySources sources = new MutablePropertySources();
		documents.forEach(sources::addLast);
		return new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources));
	}
}
