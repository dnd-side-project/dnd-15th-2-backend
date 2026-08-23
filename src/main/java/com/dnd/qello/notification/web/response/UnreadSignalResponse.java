package com.dnd.qello.notification.web.response;

import java.time.Instant;

import com.dnd.qello.notification.view.UnreadSignal;

import io.swagger.v3.oas.annotations.media.Schema;

public record UnreadSignalResponse(
	@Schema(description = "지도 홈에 알림 점을 띄울지 여부. 알림함을 마지막으로 연 뒤 새 알림이 왔으면 true입니다") boolean hasUnseen,
	@Schema(description = "아직 읽지 않은 알림 개수. 알림함을 열기만 해서는 줄지 않습니다") long unreadCount,
	@Schema(description = "알림함을 마지막으로 연 시각. 한 번도 연 적이 없으면 null입니다") Instant seenAt
) {

	public static UnreadSignalResponse from(UnreadSignal signal) {
		return new UnreadSignalResponse(signal.hasUnseen(), signal.unreadCount(), signal.seenAt());
	}
}
