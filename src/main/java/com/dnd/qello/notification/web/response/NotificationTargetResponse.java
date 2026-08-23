package com.dnd.qello.notification.web.response;

import com.dnd.qello.notification.view.NotificationTargetDecision;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationTargetResponse(
	@Schema(description = "이 알림에서 원래 글로 넘어갈 수 있는지 여부") boolean navigable,
	@Schema(description = "넘어갈 수 없는 이유. 넘어갈 수 있으면 null입니다. EXPIRED는 기간이 끝난 질문글, "
		+ "HIDDEN은 숨겨진 글, BLOCKED는 차단한 상대의 글, GONE은 지워진 글입니다",
		allowableValues = {"GONE", "BLOCKED", "HIDDEN", "EXPIRED"})
	String reason,
	@Schema(description = "이 알림이 가리키는 글의 요약") NotificationTargetSummaryResponse target,
	@Schema(description = "넘어갈 수 없을 때 대신 보여줄 화면. 넘어갈 수 있으면 NONE, 기간이 끝난 질문글이면 "
		+ "수신함(INBOX), 그 밖에는 지도 홈(FEED_HOME)입니다",
		allowableValues = {"NONE", "FEED_HOME", "INBOX"})
	String fallback
) {
	public static NotificationTargetResponse from(NotificationTargetDecision decision) {
		return new NotificationTargetResponse(
			decision.navigable(),
			decision.reason() == null ? null : decision.reason().name(),
			NotificationTargetSummaryResponse.of(decision.targetKind(), decision.targetId(), decision.targetState()),
			decision.fallback().name());
	}
}
