package com.dnd.qello.direction.security;

public final class CoordinateProtectionException extends RuntimeException {

	private final Reason reason;

	public CoordinateProtectionException(Reason reason) {
		super("Coordinate protection failed: " + reason);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	public enum Reason {
		INVALID_CONFIGURATION, INVALID_COORDINATE, INVALID_CONTEXT, INVALID_ENVELOPE, UNKNOWN_KEY, AUTHENTICATION_FAILED, ENCRYPTION_FAILED
	}
}
