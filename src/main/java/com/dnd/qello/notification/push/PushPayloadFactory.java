package com.dnd.qello.notification.push;

import java.util.Objects;

import com.dnd.qello.notification.domain.NotificationType;

/** type/count/hasRemainingTime만 가진 privacy-safe FCM data payload를 만든다. */
public final class PushPayloadFactory {

	public PushPayload create(NotificationType type, int count, boolean hasRemainingTime) {
		Objects.requireNonNull(type, "type");
		boolean remainingTime = type == NotificationType.DIRECTION_POST_RECEIVED && hasRemainingTime;
		return new PushPayload(type.name(), Integer.toString(count), Boolean.toString(remainingTime));
	}
}
