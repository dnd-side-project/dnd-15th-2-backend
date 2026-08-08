package com.dnd.qello.auth.web;

// 재발급 성공 응답의 data.
public record DeviceTokenResponse(String accessToken, long expiresIn) {

	@Override
	public String toString() {
		return "DeviceTokenResponse[accessToken=REDACTED, expiresIn=%s]".formatted(expiresIn);
	}

}
