package com.dnd.qello.account.domain;

public record PasswordHash(String value) {

	public PasswordHash {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("passwordHash는 비어 있을 수 없습니다");
		}
	}

	@Override
	public String toString() {
		return "PasswordHash[REDACTED]";
	}

}
