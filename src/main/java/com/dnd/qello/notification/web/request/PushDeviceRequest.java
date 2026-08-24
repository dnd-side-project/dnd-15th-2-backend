package com.dnd.qello.notification.web.request;

import com.dnd.qello.notification.service.PushDeviceCommand;

import io.swagger.v3.oas.annotations.media.Schema;

public record PushDeviceRequest(
	@Schema(description = "FCM registration token을 발급한 기기 플랫폼", requiredMode = Schema.RequiredMode.REQUIRED,
		allowableValues = {"ANDROID", "IOS"})
	String platform,

	@Schema(
		description = "FCM registration token. 서버는 원문을 응답에 돌려주지 않고, 오류와 로그에서도 redaction된 형태만 사용합니다.",
		requiredMode = Schema.RequiredMode.REQUIRED)
	String token
) {
	public PushDeviceCommand toCommand() {
		return new PushDeviceCommand(platform, token);
	}
}
