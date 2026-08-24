package com.dnd.qello.notification.push;

import java.util.Objects;

import com.dnd.qello.notification.domain.NotificationType;

/** dispatch snapshot을 privacy-safe FCM data payload로 변환한다. */
public final class PushPayloadFactory {

	public PushPayload create(PushDispatchContext context) {
		Objects.requireNonNull(context, "context");
		NotificationType type = context.notification().notificationType();
		boolean hasRemainingTime = type == NotificationType.DIRECTION_POST_RECEIVED
			&& context.targetValidity().hasRemainingTime();
		return new PushPayload(type.name(), "1", Boolean.toString(hasRemainingTime));
	}
}
