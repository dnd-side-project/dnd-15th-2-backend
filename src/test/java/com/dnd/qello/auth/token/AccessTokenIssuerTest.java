/*
 * Created at: 2026-08-07T20:52:09+09:00
 * Source scenario: TEST-PLAN-GH-73-ACCESS-TOKEN-ISSUER-UNIT-001 through UNIT-004
 */
package com.dnd.qello.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.dnd.qello.account.domain.AccountRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class AccessTokenIssuerTest {

	private static final Instant NOW = Instant.parse("2026-08-07T09:00:00Z");
	private static final String SECRET = "test-only-access-token-signing-key-32-bytes-min";

	private JwtDecoder jwtDecoder;
	private AccessTokenIssuer issuer;

	@BeforeEach
	void setUp() {
		SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		// 기본 검증기는 실제 시스템 시계로 만료를 판정한다. 발급도 고정 Clock으로 하므로
		// 검증도 같은 시각 기준으로 맞추지 않으면 테스트가 실행 시각에 따라 흔들린다.
		JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
		timestampValidator.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
		decoder.setJwtValidator(timestampValidator);
		jwtDecoder = decoder;

		AccessTokenProperties properties = new AccessTokenProperties("qello", "qello-app", 1800, SECRET);
		issuer = new AccessTokenIssuer(jwtEncoder, properties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("발급한 토큰은 TTL 1800초와 함께 반환된다")
	void issuesTokenWithConfiguredTtl() {
		IssuedAccessToken token = issuer.issue(1024L, AccountRole.USER, 5521L);

		assertThat(token.expiresInSeconds()).isEqualTo(1800);
		assertThat(token.value()).isNotBlank();
	}

	@Test
	@DisplayName("클레임에 iss·sub·aud·role·did가 담긴다")
	void embedsExpectedClaims() {
		IssuedAccessToken token = issuer.issue(1024L, AccountRole.USER, 5521L);

		Jwt decoded = jwtDecoder.decode(token.value());

		// iss는 "qello"처럼 URL 형식이 아니므로 getIssuer()(URL 변환)가 아니라
		// 원본 클레임 문자열로 비교한다.
		assertThat(decoded.getClaimAsString("iss")).isEqualTo("qello");
		assertThat(decoded.getSubject()).isEqualTo("1024");
		assertThat(decoded.getAudience()).containsExactly("qello-app");
		assertThat(decoded.getClaimAsString("role")).isEqualTo("USER");
		assertThat(decoded.getClaim("did").toString()).isEqualTo("5521");
		assertThat(decoded.getId()).isNotBlank();
	}

	@Test
	@DisplayName("만료는 발급 시각으로부터 TTL만큼 뒤다")
	void expiresAfterConfiguredTtl() {
		IssuedAccessToken token = issuer.issue(1024L, AccountRole.USER, 5521L);

		Jwt decoded = jwtDecoder.decode(token.value());

		assertThat(decoded.getIssuedAt()).isEqualTo(NOW);
		assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plusSeconds(1800));
	}

}
