package com.dnd.qello.auth.security;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

// device_secret 생성 전용. SecureRandom 32바이트를 base64url(패딩 없음)로 인코딩한다.
@Component
public class DeviceSecretGenerator {

	private static final int SECRET_BYTE_LENGTH = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	public DeviceSecret generate() {
		byte[] bytes = new byte[SECRET_BYTE_LENGTH];
		secureRandom.nextBytes(bytes);
		return new DeviceSecret(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
	}

}
