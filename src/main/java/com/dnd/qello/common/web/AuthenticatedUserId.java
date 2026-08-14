package com.dnd.qello.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/** HTTP 경계에서 JWT subject를 양의 사용자 식별자로 변환한다. */
public final class AuthenticatedUserId {

	private AuthenticatedUserId() {
	}

	public static long require(Authentication authentication) {
		if (authentication == null || authentication.getName() == null) {
			throw unauthorized();
		}
		try {
			long userId = Long.parseLong(authentication.getName());
			if (userId <= 0) {
				throw unauthorized();
			}
			return userId;
		} catch (NumberFormatException exception) {
			throw unauthorized();
		}
	}

	private static ResponseStatusException unauthorized() {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 사용자 정보가 유효하지 않습니다");
	}
}
