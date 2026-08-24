package com.dnd.qello.notification.push.security;

public record ProtectedPushToken(byte[] envelope, String fingerprint) {

	public ProtectedPushToken {
		if (envelope == null || envelope.length == 0 || fingerprint == null || fingerprint.isBlank()) {
			throw new IllegalArgumentException("[REDACTED]");
		}
		envelope = envelope.clone();
	}

	@Override
	public byte[] envelope() {
		return envelope.clone();
	}
}
