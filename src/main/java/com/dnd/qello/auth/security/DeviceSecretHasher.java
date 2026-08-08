package com.dnd.qello.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.dnd.qello.auth.domain.SecretHash;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

// device_secret 해시 전용. bcrypt를 쓰지 않는다.
//
// device_secret은 SecureRandom 256bit라 무차별 대입이 불가능하다. bcrypt는 salt가 매번
// 달라 WHERE hash = ? 조회가 불가능하고 요청마다 수십~수백 ms를 소모한다. 인덱스 조회가
// 필요한 고엔트로피 값에는 고정 해시(SHA-256)가 맞다. 근거는
// docs/product/AUTH_DESIGN.md 4.2절에 있다.
@Component
public class DeviceSecretHasher {

	private static final String ALGORITHM = "SHA-256";

	public SecretHash hash(DeviceSecret deviceSecret) {
		return new SecretHash(HexFormat.of().formatHex(digest(deviceSecret.value())));
	}

	private byte[] digest(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
			return digest.digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException exception) {
			throw new AuthException(
				AuthErrorCode.INVALID_CREDENTIAL_STATE,
				"secretHash",
				"해시 알고리즘을 사용할 수 없습니다",
				exception
			);
		}
	}

}
