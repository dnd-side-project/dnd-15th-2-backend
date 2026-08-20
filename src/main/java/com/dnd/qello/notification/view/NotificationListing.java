package com.dnd.qello.notification.view;

import java.time.Instant;
import java.util.List;

/**
 * 알림함 목록 페이지. {@code nextCursor}는 반환 건수가 요청 {@code limit}과 같을
 * 때만 채운다 — 그보다 적으면 마지막 페이지이므로 {@code null}이다.
 */
public record NotificationListing(List<NotificationCard> items, Cursor nextCursor) {

	public NotificationListing {
		items = List.copyOf(items);
	}

	public record Cursor(Instant createdAt, long notificationId) {

		public Cursor {
			if (createdAt == null) {
				throw new IllegalArgumentException("createdAt must not be null");
			}
		}
	}
}
