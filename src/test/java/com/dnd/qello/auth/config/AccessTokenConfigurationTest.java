/*
 * Created at: 2026-10-10T15:00:12+09:00
 * Source scenario: TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-005 through UNIT-013
 */
package com.dnd.qello.auth.config;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import com.dnd.qello.auth.token.AccessTokenProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AccessTokenConfigurationTest {

	private static final String SECRET = "test-only-access-token-signing-key-32-bytes-min";
	private static final String ISSUER = "qello";
	private static final String AUDIENCE = "qello-app";

	private final AccessTokenConfiguration configuration = new AccessTokenConfiguration();
	private final AccessTokenProperties properties = new AccessTokenProperties(ISSUER, AUDIENCE, 1800, SECRET);
	private final JwtEncoder encoder = configuration.jwtEncoder(properties);
	private final JwtDecoder decoder = configuration.jwtDecoder(properties);

	@Test
	@DisplayName("UNIT-005: 설정과 같은 iss·aud에 유효 기간 안의 토큰은 디코딩된다")
	void decodesTokenWithConfiguredIssuerAndAudience() {
		Jwt jwt = decoder.decode(token(claims -> {
		}));

		assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
		assertThat(jwt.getAudience()).containsExactly(AUDIENCE);
	}

	@Test
	@DisplayName("UNIT-006: iss가 다른 토큰은 검증에 실패한다")
	void rejectsTokenWithDifferentIssuer() {
		String token = token(claims -> claims.issuer("another-service"));

		assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(token));
	}

	@Test
	@DisplayName("UNIT-007: aud에 설정 audience가 없는 토큰은 검증에 실패한다")
	void rejectsTokenWithDifferentAudience() {
		String token = token(claims -> claims.audience(List.of("another-app")));

		assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(token));
	}

	@Test
	@DisplayName("UNIT-007: aud 클레임이 없는 토큰은 검증에 실패한다")
	void rejectsTokenWithoutAudience() {
		String token = token(claims -> claims.claims(values -> values.remove("aud")));

		assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(token));
	}

	@Test
	@DisplayName("UNIT-008: aud에 다른 값이 함께 있어도 설정 audience가 포함되면 디코딩된다")
	void acceptsTokenWhoseAudienceContainsConfiguredValue() {
		Jwt jwt = decoder.decode(token(claims -> claims.audience(List.of("another-app", AUDIENCE))));

		assertThat(jwt.getAudience()).contains(AUDIENCE);
	}

	@Test
	@DisplayName("UNIT-009: 허용 오차를 넘겨 만료된 토큰은 검증에 실패한다")
	void rejectsExpiredToken() {
		Instant issuedAt = Instant.now().minusSeconds(3600);
		String token = token(claims -> claims.issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(1800)));

		assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(token));
	}

	@Test
	@DisplayName("UNIT-010: role=USER 클레임은 ROLE_USER 권한 하나로 바뀐다")
	void convertsUserRoleClaimToRoleAuthority() {
		assertThat(authorities(claims -> claims.claim("role", "USER"))).containsExactly("ROLE_USER");
	}

	@Test
	@DisplayName("UNIT-011: role 클레임이 없으면 권한 없이 인증 객체를 만든다")
	void convertsMissingRoleClaimToNoAuthorities() {
		assertThat(authorities(claims -> {
		})).isEmpty();
	}

	@Test
	@DisplayName("UNIT-011: role 클레임이 빈 문자열이면 권한 없이 인증 객체를 만든다")
	void convertsBlankRoleClaimToNoAuthorities() {
		assertThat(authorities(claims -> claims.claim("role", ""))).isEmpty();
	}

	@Test
	@DisplayName("UNIT-011: role 클레임이 문자열이 아니면 권한 없이 인증 객체를 만든다")
	void convertsNonStringRoleClaimToNoAuthorities() {
		assertThat(authorities(claims -> claims.claim("role", 1))).isEmpty();
	}

	@Test
	@DisplayName("UNIT-012: 31바이트 secret이 설정되면 컨텍스트가 기동하지 않는다")
	void failsToStartWithShortSecret() {
		contextRunner()
				.withPropertyValues("qello.auth.access-token.secret=test-only-signing-key-31-bytes!")
				.run(context -> assertThat(context).hasFailed()
						.getFailure()
						.rootCause()
						.isInstanceOf(IllegalArgumentException.class)
						.hasMessageContaining("qello.auth.access-token.secret"));
	}

	@Test
	@DisplayName("UNIT-013: secret 설정이 없으면 컨텍스트가 기동하지 않는다")
	void failsToStartWithoutSecret() {
		contextRunner()
				.run(context -> assertThat(context).hasFailed()
						.getFailure()
						.rootCause()
						.hasMessageContaining("qello.auth.access-token.secret"));
	}

	@Test
	@DisplayName("UNIT-012: 32바이트 이상 secret이면 디코더와 인코더 빈이 만들어진다")
	void startsWithLongEnoughSecret() {
		contextRunner()
				.withPropertyValues("qello.auth.access-token.secret=" + SECRET)
				.run(context -> assertThat(context).hasNotFailed()
						.hasSingleBean(JwtDecoder.class)
						.hasSingleBean(JwtEncoder.class));
	}

	private String token(Consumer<JwtClaimsSet.Builder> customizer) {
		Instant issuedAt = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject("1024")
				.audience(List.of(AUDIENCE))
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plusSeconds(1800));
		customizer.accept(claims);
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

	private List<String> authorities(Consumer<JwtClaimsSet.Builder> customizer) {
		JwtAuthenticationConverter converter = configuration.accessTokenAuthenticationConverter();
		return converter.convert(decoder.decode(token(customizer))).getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.toList();
	}

	private static ApplicationContextRunner contextRunner() {
		return new ApplicationContextRunner()
				.withUserConfiguration(AccessTokenPropertiesConfiguration.class, AccessTokenConfiguration.class);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(AccessTokenProperties.class)
	static class AccessTokenPropertiesConfiguration {
	}
}
