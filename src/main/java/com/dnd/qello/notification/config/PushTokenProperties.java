package com.dnd.qello.notification.config;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;

import com.dnd.qello.notification.push.security.PushTokenKeyRing;

/** D-2 secret-backed push token AES/HMAC key ring 설정. */
@ConfigurationProperties(prefix = "qello.notification.push.token-protection")
@Profile("!test & !local & !integration")
public record PushTokenProperties(
	String currentKeyId,
	String currentEncryptionKeyBase64,
	String previousKeyId,
	String previousEncryptionKeyBase64,
	String fingerprintKeyBase64
) {

	private static final int KEY_LENGTH_BYTES = 32;
	private static final String INVALID_CONFIGURATION = "push token key configuration is invalid";

	public PushTokenProperties {
		previousKeyId = blankToNull(previousKeyId);
		previousEncryptionKeyBase64 = blankToNull(previousEncryptionKeyBase64);
		requirePresent(currentKeyId);
		validateKey(currentEncryptionKeyBase64);
		validateKey(fingerprintKeyBase64);
		if ((previousKeyId == null) != (previousEncryptionKeyBase64 == null)) {
			throw invalidConfiguration();
		}
		if (previousKeyId != null) {
			if (previousKeyId.equals(currentKeyId)) {
				throw invalidConfiguration();
			}
			validateKey(previousEncryptionKeyBase64);
		}
	}

	PushTokenKeyRing keyRing() {
		Map<String, byte[]> encryptionKeys = new LinkedHashMap<>();
		encryptionKeys.put(currentKeyId, decodeKey(currentEncryptionKeyBase64));
		if (previousKeyId != null) {
			encryptionKeys.put(previousKeyId, decodeKey(previousEncryptionKeyBase64));
		}
		return new PushTokenKeyRing(currentKeyId, encryptionKeys, decodeKey(fingerprintKeyBase64));
	}

	private static void requirePresent(String value) {
		if (value == null || value.isBlank()) {
			throw invalidConfiguration();
		}
	}

	private static void validateKey(String value) {
		if (decodeKey(value).length != KEY_LENGTH_BYTES) {
			throw invalidConfiguration();
		}
	}

	private static byte[] decodeKey(String value) {
		requirePresent(value);
		try {
			return Base64.getDecoder().decode(value);
		} catch (IllegalArgumentException exception) {
			throw invalidConfiguration();
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static IllegalArgumentException invalidConfiguration() {
		return new IllegalArgumentException(INVALID_CONFIGURATION);
	}
}
