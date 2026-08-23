package com.dnd.qello.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;

// 재발급 성공 응답의 data.
@Schema(description = "기기 액세스 토큰 재발급 성공 응답")
public record DeviceTokenResponse(
	@Schema(description = "새로 발급한 API 호출용 액세스 토큰") String accessToken,
	@Schema(description = "액세스 토큰이 만료되기까지 남은 시간(초)") long expiresIn
) {

	@Override
	public String toString() {
		return "DeviceTokenResponse[accessToken=REDACTED, expiresIn=%s]".formatted(expiresIn);
	}

}
