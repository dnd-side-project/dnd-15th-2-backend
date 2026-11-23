/**
 * Created at: 2026-11-23T13:52:37Z
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-012
 */
package com.dnd.qello;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.account.repository.AccountLockRepository;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.direction.consent.LocationConsentProperties;
import com.dnd.qello.direction.consent.LocationConsentRepository;
import com.dnd.qello.direction.consent.LocationConsentService;
import com.dnd.qello.direction.gate.LocationCollectionGate;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocationConsentConfigurationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;

	@ParameterizedTest
	@CsvSource({"dev,dev-v1", "local,dev-v1", "test,test-v1"})
	@DisplayName("INT-012: 실제 dev/local/test YAML은 명시적 합성 버전으로 동의 서비스를 기동한다")
	void realProfileYamlBindsReviewedVersion(String profile, String version) {
		context(profile, true).run(started -> {
			assertThat(started).hasNotFailed();
			assertThat(started.getBean(LocationConsentProperties.class).currentTermsVersion()).isEqualTo(version);
			assertThat(started.getBean(LocationConsentService.class)).isNotNull();
		});
	}

	@Test
	@DisplayName("INT-012: 기본 YAML은 게시 버전이 없어 B1 off면 기동하고 on이면 기동하지 않는다")
	void missingDefaultVersionDependsOnCollectionGate() {
		context("default", false).run(started -> {
			assertThat(started).hasNotFailed();
			assertThat(started.getBean(LocationConsentProperties.class).currentTermsVersion()).isNull();
		});
		context("default", true).run(started -> assertThat(started).hasFailed()
				.getFailure().hasRootCauseInstanceOf(IllegalStateException.class)
				.hasStackTraceContaining("qello.location.consent.current-terms-version"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"default", "dev", "local", "test"})
	@DisplayName("INT-012: 모든 profile은 B1 on에서 공백·앞뒤 공백·길이 초과 버전으로 기동하지 않는다")
	void invalidVersionNeverStartsEnabledCollection(String profile) {
		for (String version : List.of("", " ", " test-v1", "test-v1 ", "x".repeat(101))) {
			context(profile, true).withInitializer(started -> started.getEnvironment().getPropertySources()
					.addFirst(new MapPropertySource("invalid-consent-version",
							Map.of("qello.location.consent.current-terms-version", version))))
					.run(started -> assertThat(started).hasFailed().getFailure()
							.hasRootCauseInstanceOf(IllegalStateException.class));
		}
	}

	@Test
	@DisplayName("INT-012: test YAML 정상 기동도 기존 미동의 사용자의 위치 갱신을 우회하지 않는다")
	void bootedTestProfileStillRejectsExistingNonconsentingUser() throws Exception {
		long user = jdbc.queryForObject("""
				INSERT INTO user_account (country_code, coarse_region_code, locale, timezone, nickname)
				VALUES ('KR', 'KR', 'ko-KR', 'UTC', ?) RETURNING id
				""", Long.class, "consent-config-" + SEQUENCE.incrementAndGet());
		try {
			mvc.perform(put("/api/v1/direction/presence")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
							.jwt(token -> token.subject(Long.toString(user))))
					.contentType(MediaType.APPLICATION_JSON)
					.content(
							"""
									{"latitude":37.5,"longitude":127.0,"accuracyMeters":1,"receiveAllowed":true,"observedAt":"%s"}
									"""
									.formatted(java.time.Instant.now())))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.errorDetail.code").value("DIR-APP-009"));
			assertThat(jdbc.queryForObject("SELECT count(*) FROM active_user_presence WHERE user_id = ?", Integer.class,
					user))
					.isZero();
		} finally {
			jdbc.update("DELETE FROM user_account WHERE id = ?", user);
		}
	}

	private ApplicationContextRunner context(String profile, boolean enabled) {
		return new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
				.withUserConfiguration(Binding.class).withPropertyValues("spring.profiles.active=" + profile,
						"qello.location.collection.enabled=" + enabled,
						"qello.location.collection.lbs-registration-reference=TEST-ONLY-LBS-REGISTRATION-FIXTURE");
	}

	@EnableConfigurationProperties(LocationConsentProperties.class)
	@Import({LocationConsentService.class, LocationCollectionGate.class})
	static class Binding {
		@Bean
		LocationConsentRepository history() {
			return mock(LocationConsentRepository.class);
		}
		@Bean
		AccountRepository accounts() {
			return mock(AccountRepository.class);
		}
		@Bean
		AccountLockRepository locks() {
			return mock(AccountLockRepository.class);
		}
		@Bean
		ActiveUserPresenceRepository presence() {
			return mock(ActiveUserPresenceRepository.class);
		}
		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}
	}
}
