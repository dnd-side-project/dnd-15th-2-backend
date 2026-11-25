package com.dnd.qello.direction.security;

import java.nio.charset.StandardCharsets;

public enum CoordinatePurpose {

	ACTIVE_USER_PRESENCE("active_user_presence.position"), POST_AUDIENCE("post_audience.origin_position");

	private final String binding;

	CoordinatePurpose(String binding) {
		this.binding = binding;
	}

	byte[] bindingBytes() {
		return binding.getBytes(StandardCharsets.UTF_8);
	}
}
