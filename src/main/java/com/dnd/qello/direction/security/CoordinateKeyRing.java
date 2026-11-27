package com.dnd.qello.direction.security;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

public final class CoordinateKeyRing {

	private final String currentKeyId;
	private final Map<String, SecretKeySpec> keys;

	public CoordinateKeyRing(String currentKeyId, Map<String, byte[]> keys) {
		if (!validKeyId(currentKeyId) || keys == null || keys.isEmpty() || keys.size() > 2
				|| !keys.containsKey(currentKeyId)) {
			throw invalidConfiguration();
		}
		Map<String, SecretKeySpec> copiedKeys = new HashMap<>();
		for (Map.Entry<String, byte[]> entry : keys.entrySet()) {
			byte[] material = entry.getValue();
			if (!validKeyId(entry.getKey()) || material == null || material.length != 32) {
				throw invalidConfiguration();
			}
			copiedKeys.put(entry.getKey(), new SecretKeySpec(material, "AES"));
		}
		this.currentKeyId = currentKeyId;
		this.keys = Map.copyOf(copiedKeys);
	}

	String currentKeyId() {
		return currentKeyId;
	}

	SecretKeySpec key(String keyId) {
		SecretKeySpec key = keys.get(keyId);
		if (key == null) {
			throw new CoordinateProtectionException(CoordinateProtectionException.Reason.UNKNOWN_KEY);
		}
		return key;
	}

	private static boolean validKeyId(String keyId) {
		return keyId != null && !keyId.isBlank() && keyId.getBytes(StandardCharsets.UTF_8).length <= 255;
	}

	private static CoordinateProtectionException invalidConfiguration() {
		return new CoordinateProtectionException(CoordinateProtectionException.Reason.INVALID_CONFIGURATION);
	}

	@Override
	public String toString() {
		return "CoordinateKeyRing[REDACTED]";
	}
}
