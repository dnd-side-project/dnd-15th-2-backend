package com.dnd.qello.account.security;

public record RawPassword(String value) {

	public RawPassword {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("password는 비어 있을 수 없습니다");
		}
	}

	@Override
	public String toString() {
		return "RawPassword[REDACTED]";
	}

}
