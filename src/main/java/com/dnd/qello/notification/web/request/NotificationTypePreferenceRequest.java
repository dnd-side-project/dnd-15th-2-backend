package com.dnd.qello.notification.web.request;

import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationTypePreferenceRequest(
	@Schema(description = "알림 종류", allowableValues = {
		"ANSWER_RECEIVED",
		"ANSWER_REACTED",
		"DIRECTION_POST_RECEIVED",
		"REPORT_RESOLVED",
		"QUESTION_PROPOSAL_REVIEWED",
		"QUESTION_RECOMMENDED"
	}, requiredMode = Schema.RequiredMode.REQUIRED)
	String type,

	@Schema(description = "해당 종류의 push 허용 여부", requiredMode = Schema.RequiredMode.REQUIRED)
	Boolean enabled
) {
	public NotificationType typeOrThrow() {
		if (type == null) {
			throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "preferences",
				"알림 종류를 정확히 지정해야 합니다.");
		}
		try {
			return NotificationType.valueOf(type);
		}
		catch (IllegalArgumentException exception) {
			throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "preferences",
				"알림 종류가 올바르지 않습니다.");
		}
	}

	public boolean enabledOrThrow() {
		if (enabled == null) {
			throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "preferences",
				"알림 종류별 enabled 값은 필수입니다.");
		}
		return enabled;
	}
}
