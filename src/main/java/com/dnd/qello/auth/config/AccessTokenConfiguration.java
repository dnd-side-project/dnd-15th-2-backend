package com.dnd.qello.auth.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.dnd.qello.auth.token.AccessTokenProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

// 앱 액세스 토큰의 서명·검증에 쓰는 Nimbus 구현 Bean.
//
// 단일 서비스이므로 비대칭 키 없이 HS256을 쓴다. 서명 키는
// AccessTokenProperties.secret(환경변수 전용)에서만 읽는다.
@Configuration(proxyBeanMethods = false)
public class AccessTokenConfiguration {

	@Bean
	JwtEncoder jwtEncoder(AccessTokenProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
	}

	@Bean
	JwtDecoder jwtDecoder(AccessTokenProperties properties) {
		return NimbusJwtDecoder.withSecretKey(signingKey(properties))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
	}

	private SecretKeySpec signingKey(AccessTokenProperties properties) {
		return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

}
