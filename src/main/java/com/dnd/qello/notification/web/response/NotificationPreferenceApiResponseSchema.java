package com.dnd.qello.notification.web.response;

import java.time.Instant;

import com.dnd.qello.common.web.response.ApiStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "NotificationPreferenceApiResponse", description = "알림 설정 변경 성공 응답")
public record NotificationPreferenceApiResponseSchema(
		@Schema(description = "응답 상태", requiredMode = Schema.RequiredMode.REQUIRED) ApiStatus status,
		@Schema(description = "저장된 알림 설정", requiredMode = Schema.RequiredMode.REQUIRED) NotificationPreferenceResponse data,
		@Schema(description = "응답 시각", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp) {
}
