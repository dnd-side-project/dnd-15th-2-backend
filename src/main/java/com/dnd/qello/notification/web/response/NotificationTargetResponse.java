package com.dnd.qello.notification.web.response;

import com.dnd.qello.notification.view.NotificationTargetDecision;

public record NotificationTargetResponse(
	boolean navigable,
	String reason,
	NotificationTargetSummaryResponse target,
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
