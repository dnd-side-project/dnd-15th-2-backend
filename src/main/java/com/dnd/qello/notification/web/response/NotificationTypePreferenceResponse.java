package com.dnd.qello.notification.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationTypePreferenceResponse(
	@Schema(allowableValues = {
		"ANSWER_RECEIVED",
		"ANSWER_REACTED",
		"DIRECTION_POST_RECEIVED",
		"REPORT_RESOLVED",
		"QUESTION_PROPOSAL_REVIEWED",
		"QUESTION_RECOMMENDED"
	})
	String type,
	boolean enabled
) {
}
