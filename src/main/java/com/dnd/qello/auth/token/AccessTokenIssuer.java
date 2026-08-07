package com.dnd.qello.auth.token;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import com.dnd.qello.account.domain.AccountRole;

// 앱 액세스 토큰 발급. NimbusJwtEncoder에 서명·직렬화를 위임하고 클레임 구성만 책임진다.
//
// 클레임 구성은 docs/product/AUTH_DESIGN.md 4.5절을 따른다: role로 인가를,
// did(device_credential id)로 토큰이 어느 기기 자격증명에서 나왔는지 연결한다.
@Component
public class AccessTokenIssuer {

	private static final String ROLE_CLAIM = "role";
	private static final String DEVICE_CREDENTIAL_ID_CLAIM = "did";

	private final JwtEncoder jwtEncoder;
	private final AccessTokenProperties properties;
	private final Clock clock;

	public AccessTokenIssuer(JwtEncoder jwtEncoder, AccessTokenProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	public IssuedAccessToken issue(long userId, AccountRole role, long deviceCredentialId) {
		Instant issuedAt = Instant.now(clock);
		Instant expiresAt = issuedAt.plusSeconds(properties.ttlSeconds());

		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(String.valueOf(userId))
			.audience(List.of(properties.audience()))
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.id(UUID.randomUUID().toString())
			.claim(ROLE_CLAIM, role.name())
			.claim(DEVICE_CREDENTIAL_ID_CLAIM, deviceCredentialId)
			.build();

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
		return new IssuedAccessToken(jwt.getTokenValue(), properties.ttlSeconds());
	}

}
