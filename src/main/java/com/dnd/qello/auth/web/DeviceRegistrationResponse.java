package com.dnd.qello.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;

// 등록 성공 응답의 data. deviceSecret은 이 응답에서만 평문으로 나가고 이후 어디에도 남지 않는다.
@Schema(description = "기기 등록 성공 시 계정·기기 비밀값·첫 액세스 토큰을 담는 응답")
public record DeviceRegistrationResponse(
	@Schema(description = "새로 만든 계정 식별자") long userId,
	@Schema(description = "기기 토큰 재발급에 사용할 비밀값입니다. 이 응답에서만 평문으로 반환됩니다.") String deviceSecret,
	@Schema(description = "등록 직후 API 호출에 사용할 액세스 토큰") String accessToken,
	@Schema(description = "액세스 토큰이 만료되기까지 남은 시간(초)") long expiresIn
) {

	@Override
	public String toString() {
		return "DeviceRegistrationResponse[userId=%s, deviceSecret=REDACTED, accessToken=REDACTED, expiresIn=%s]"
			.formatted(userId, expiresIn);
	}

}
