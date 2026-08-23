package com.dnd.qello.notification.web.response;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationPreferenceResponse(
	@Schema(description = "앱 푸시 알림 전체 허용 여부") boolean pushEnabled,
	@Schema(description = "알림을 받지 않을 시간대. 설정하지 않았으면 null입니다") QuietHoursResponse quietHours,
	@Schema(description = "알림 6종별 허용 여부. 항상 6개가 모두 들어 있습니다") List<NotificationTypePreferenceResponse> preferences,
	@Schema(description = "알림함 기록 정책. 푸시를 꺼도 알림함에는 항상 쌓이므로 값은 언제나 ALWAYS_RECORD입니다") InboxRecordingPolicy inboxRecordingPolicy
) {
	public static NotificationPreferenceResponse from(NotificationPreferenceSnapshot snapshot) {
		return new NotificationPreferenceResponse(
			snapshot.pushEnabled(),
			snapshot.quietHours() == null ? null : QuietHoursResponse.from(snapshot.quietHours()),
			toResponses(snapshot.typeEnabled()),
			InboxRecordingPolicy.ALWAYS_RECORD);
	}

	private static List<NotificationTypePreferenceResponse> toResponses(Map<NotificationType, Boolean> typeEnabled) {
		return Arrays.stream(NotificationType.values())
			.map(type -> new NotificationTypePreferenceResponse(type.name(), typeEnabled.get(type)))
			.toList();
	}
}
