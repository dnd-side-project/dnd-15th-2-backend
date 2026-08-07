package com.dnd.qello.common.web.response;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ApiStatus {

	SUCCESS("success"),
	ERROR("error");

	private final String value;

	ApiStatus(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}
}
