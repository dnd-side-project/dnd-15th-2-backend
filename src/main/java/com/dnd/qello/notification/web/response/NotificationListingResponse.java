package com.dnd.qello.notification.web.response;

import java.time.Instant;
import java.util.List;

import com.dnd.qello.notification.view.NotificationListing;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationListingResponse(
	@Schema(description = "받은 알림 목록. 최신순입니다") List<NotificationCardResponse> notifications,
	@Schema(description = "다음 쪽 커서. 마지막 쪽이면 null입니다") Cursor nextCursor
) {

	public NotificationListingResponse {
		notifications = List.copyOf(notifications);
	}

	public static NotificationListingResponse from(NotificationListing listing) {
		return new NotificationListingResponse(
			listing.items().stream().map(NotificationCardResponse::from).toList(),
			listing.nextCursor() == null ? null : Cursor.from(listing.nextCursor()));
	}

	@Schema(name = "NotificationCursor")
	public record Cursor(
		@Schema(description = "다음 쪽 조회에 쓸 알림 도착 시각. cursorCreatedAt에 그대로 넣습니다") Instant createdAt,
		@Schema(description = "다음 쪽 조회에 쓸 알림 식별자. cursorNotificationId에 그대로 넣습니다") long notificationId
	) {
		static Cursor from(NotificationListing.Cursor cursor) {
			return new Cursor(cursor.createdAt(), cursor.notificationId());
		}
	}
}
