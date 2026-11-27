package com.dnd.qello.direction.config;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.dnd.qello.direction.security.CoordinateKeyRing;
import com.dnd.qello.direction.security.CoordinateProtectionException;

@ConfigurationProperties(prefix = "qello.direction.coordinate-protection")
public record CoordinateProtectionProperties(
		String currentKeyId,
		String currentKeyBase64,
		String previousKeyId,
		String previousKeyBase64) {

	public CoordinateProtectionProperties {
		buildKeyRing(currentKeyId, currentKeyBase64, previousKeyId, previousKeyBase64);
	}

	public CoordinateKeyRing keyRing() {
		return buildKeyRing(currentKeyId, currentKeyBase64, previousKeyId, previousKeyBase64);
	}

	private static CoordinateKeyRing buildKeyRing(String currentId, String currentBase64,
			String previousId, String previousBase64) {
		if (currentId == null || currentId.isBlank() || currentBase64 == null || currentBase64.isBlank()) {
			throw invalidConfiguration();
		}
		boolean hasPreviousId = previousId != null && !previousId.isBlank();
		boolean hasPreviousKey = previousBase64 != null && !previousBase64.isBlank();
		if (hasPreviousId != hasPreviousKey || currentId.equals(previousId)) {
			throw invalidConfiguration();
		}
		Map<String, byte[]> keys = new HashMap<>();
		keys.put(currentId, decode(currentBase64));
		if (hasPreviousId) {
			keys.put(previousId, decode(previousBase64));
		}
		return new CoordinateKeyRing(currentId, keys);
	}

	private static byte[] decode(String base64) {
		try {
			return Base64.getDecoder().decode(base64);
		} catch (IllegalArgumentException exception) {
			throw invalidConfiguration();
		}
	}

	private static CoordinateProtectionException invalidConfiguration() {
		return new CoordinateProtectionException(CoordinateProtectionException.Reason.INVALID_CONFIGURATION);
	}

	@Override
	public String toString() {
		return "CoordinateProtectionProperties[REDACTED]";
	}
}
