package com.dnd.qello.notification.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationTypePreferenceResponse(
	@Schema(description = "알림 종류", allowableValues = {
		"ANSWER_RECEIVED",
		"ANSWER_REACTED",
		"DIRECTION_POST_RECEIVED",
		"REPORT_RESOLVED",
		"QUESTION_PROPOSAL_REVIEWED",
		"QUESTION_RECOMMENDED"
	})
	String type,
	@Schema(description = "이 종류의 푸시 알림을 받을지 여부") boolean enabled
) {
}
