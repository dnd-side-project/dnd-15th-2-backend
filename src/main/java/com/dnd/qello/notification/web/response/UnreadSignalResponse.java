package com.dnd.qello.notification.web.response;

import java.time.Instant;

import com.dnd.qello.notification.view.UnreadSignal;

public record UnreadSignalResponse(boolean hasUnseen, long unreadCount, Instant seenAt) {

	public static UnreadSignalResponse from(UnreadSignal signal) {
		return new UnreadSignalResponse(signal.hasUnseen(), signal.unreadCount(), signal.seenAt());
	}
}
