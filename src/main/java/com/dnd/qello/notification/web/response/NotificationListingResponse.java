package com.dnd.qello.notification.web.response;

import java.time.Instant;
import java.util.List;

import com.dnd.qello.notification.view.NotificationListing;

public record NotificationListingResponse(List<NotificationCardResponse> notifications, Cursor nextCursor) {

	public NotificationListingResponse {
		notifications = List.copyOf(notifications);
	}

	public static NotificationListingResponse from(NotificationListing listing) {
		return new NotificationListingResponse(
			listing.items().stream().map(NotificationCardResponse::from).toList(),
			listing.nextCursor() == null ? null : Cursor.from(listing.nextCursor()));
	}

	public record Cursor(Instant createdAt, long notificationId) {
		static Cursor from(NotificationListing.Cursor cursor) {
			return new Cursor(cursor.createdAt(), cursor.notificationId());
		}
	}
}
