package com.dnd.qello.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;

// 재발급 성공 응답의 data.
@Schema(description = "기기 액세스 토큰 재발급 성공 응답")
public record DeviceTokenResponse(
		@Schema(description = "새로 발급한 API 호출용 액세스 토큰") String accessToken,
		@Schema(description = "액세스 토큰이 만료되기까지 남은 시간(초)") long expiresIn,
		@Schema(description = "계정 상태. WITHDRAWAL_PENDING이면 탈퇴 유예 중이라 철회 외의 쓰기 API는 403을 반환합니다", allowableValues = {
				"ACTIVE", "WITHDRAWAL_PENDING"}) String accountStatus){

	@Override
	public String toString() {
		return "DeviceTokenResponse[accessToken=REDACTED, expiresIn=%s, accountStatus=%s]"
				.formatted(expiresIn, accountStatus);
	}

}
