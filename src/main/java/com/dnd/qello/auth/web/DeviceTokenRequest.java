package com.dnd.qello.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 토큰 재발급 요청 본문.
@Schema(description = "등록된 기기의 액세스 토큰을 재발급할 때 보내는 정보")
public record DeviceTokenRequest(
	@Schema(description = "토큰을 재발급할 기기의 설치 식별자")
	@NotBlank(message = "installationId는 필수입니다") String installationId,
	@Schema(description = "등록 응답에서 받은 기기 비밀값")
	@NotBlank(message = "deviceSecret은 필수입니다") String deviceSecret
) {

	@Override
	public String toString() {
		return "DeviceTokenRequest[installationId=%s, deviceSecret=REDACTED]".formatted(installationId);
	}

}
