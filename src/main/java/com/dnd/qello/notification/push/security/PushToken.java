package com.dnd.qello.notification.push.security;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class PushToken {

	private static final String REDACTED = "[REDACTED]";
	private static final int MAX_TOKEN_BYTES = 4096;

	private final String value;

	private PushToken(String value) {
		this.value = value;
	}

	public static PushToken of(String value) {
		if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > MAX_TOKEN_BYTES) {
			throw new IllegalArgumentException(REDACTED);
		}
		return new PushToken(value);
	}

	byte[] utf8Bytes() {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public String toString() {
		return REDACTED;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PushToken pushToken)) {
			return false;
		}
		return value.equals(pushToken.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(value);
	}
}
