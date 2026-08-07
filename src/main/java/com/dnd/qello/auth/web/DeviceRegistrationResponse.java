package com.dnd.qello.auth.web;

// 등록 성공 응답의 data. deviceSecret은 이 응답에서만 평문으로 나가고 이후 어디에도 남지 않는다.
public record DeviceRegistrationResponse(long userId, String deviceSecret, String accessToken, long expiresIn) {

	@Override
	public String toString() {
		return "DeviceRegistrationResponse[userId=%s, deviceSecret=REDACTED, accessToken=REDACTED, expiresIn=%s]"
			.formatted(userId, expiresIn);
	}

}
