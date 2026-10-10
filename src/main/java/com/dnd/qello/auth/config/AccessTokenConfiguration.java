package com.dnd.qello.auth.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import com.dnd.qello.auth.token.AccessTokenProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

// 앱 액세스 토큰의 서명·검증에 쓰는 Nimbus 구현 Bean.
//
// 단일 서비스이므로 비대칭 키 없이 HS256을 쓴다. 서명 키는
// AccessTokenProperties.secret(환경변수 전용)에서만 읽는다.
@Configuration(proxyBeanMethods = false)
public class AccessTokenConfiguration {

	private static final String ROLE_CLAIM = "role";
	private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

	@Bean
	JwtEncoder jwtEncoder(AccessTokenProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
	}

	// 기본 검증(만료·nbf)에 발급자와 대상을 더한다. 같은 키로 서명됐더라도 이 앱이 앱 사용자에게
	// 발급한 토큰이 아니면 거절한다(#349).
	@Bean
	JwtDecoder jwtDecoder(AccessTokenProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(properties))
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(List.of(
				new JwtIssuerValidator(properties.issuer()),
				new JwtAudienceValidator(properties.audience()))));
		return decoder;
	}

	// role 클레임(AccessTokenIssuer가 AccountRole 이름을 넣는다)을 ROLE_<role> 권한으로 바꿔
	// 앱 API 체인이 hasRole("USER")로 인가할 수 있게 한다.
	@Bean
	JwtAuthenticationConverter accessTokenAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
		authoritiesConverter.setAuthoritiesClaimName(ROLE_CLAIM);
		authoritiesConverter.setAuthorityPrefix(ROLE_AUTHORITY_PREFIX);

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
		return converter;
	}

	private SecretKeySpec signingKey(AccessTokenProperties properties) {
		return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

}
