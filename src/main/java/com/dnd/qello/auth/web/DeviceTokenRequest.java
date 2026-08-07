package com.dnd.qello.auth.web;

import jakarta.validation.constraints.NotBlank;

// 토큰 재발급 요청 본문.
public record DeviceTokenRequest(
	@NotBlank(message = "installationId는 필수입니다") String installationId,
	@NotBlank(message = "deviceSecret은 필수입니다") String deviceSecret
) {

	@Override
	public String toString() {
		return "DeviceTokenRequest[installationId=%s, deviceSecret=REDACTED]".formatted(installationId);
	}

}
