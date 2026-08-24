package com.dnd.qello.notification.push.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.crypto.spec.SecretKeySpec;

public final class PushTokenKeyRing {

	private static final int KEY_LENGTH_BYTES = 32;

	private final String currentKeyId;
	private final Map<String, byte[]> encryptionKeys;
	private final byte[] fingerprintKey;

	public PushTokenKeyRing(String currentKeyId, Map<String, byte[]> encryptionKeys, byte[] fingerprintKey) {
		if (currentKeyId == null || currentKeyId.isBlank() || currentKeyId.getBytes().length > 255) {
			throw new IllegalArgumentException("[REDACTED]");
		}
		Objects.requireNonNull(encryptionKeys, "encryptionKeys");
		if (encryptionKeys.isEmpty() || !encryptionKeys.containsKey(currentKeyId)) {
			throw new IllegalArgumentException("[REDACTED]");
		}
		this.currentKeyId = currentKeyId;
		this.encryptionKeys = copyEncryptionKeys(encryptionKeys);
		this.fingerprintKey = copyKey(fingerprintKey);
	}

	String currentKeyId() {
		return currentKeyId;
	}

	SecretKeySpec currentEncryptionKey() {
		return encryptionKey(currentKeyId);
	}

	SecretKeySpec encryptionKey(String keyId) {
		byte[] key = encryptionKeys.get(keyId);
		if (key == null) {
			throw new PushTokenProtectionException();
		}
		return new SecretKeySpec(key.clone(), "AES");
	}

	byte[] fingerprintKey() {
		return fingerprintKey.clone();
	}

	private static Map<String, byte[]> copyEncryptionKeys(Map<String, byte[]> encryptionKeys) {
		Map<String, byte[]> copiedKeys = new LinkedHashMap<>();
		for (Map.Entry<String, byte[]> entry : encryptionKeys.entrySet()) {
			String keyId = entry.getKey();
			if (keyId == null || keyId.isBlank() || keyId.getBytes().length > 255) {
				throw new IllegalArgumentException("[REDACTED]");
			}
			copiedKeys.put(keyId, copyKey(entry.getValue()));
		}
		return Map.copyOf(copiedKeys);
	}

	private static byte[] copyKey(byte[] key) {
		if (key == null || key.length != KEY_LENGTH_BYTES) {
			throw new IllegalArgumentException("[REDACTED]");
		}
		return key.clone();
	}
}
