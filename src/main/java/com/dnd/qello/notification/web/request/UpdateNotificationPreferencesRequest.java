package com.dnd.qello.notification.web.request;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.service.UpdateNotificationPreferences;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateNotificationPreferencesRequest(
	@Schema(description = "앱 push 전체 허용 여부", requiredMode = Schema.RequiredMode.REQUIRED)
	Boolean pushEnabled,

	@Schema(description = "방해 금지 시간. 끄려면 null, 켜려면 세 필드를 모두 지정한다.")
	QuietHoursRequest quietHours,

	@Schema(description = "정확히 6종의 종류별 설정", requiredMode = Schema.RequiredMode.REQUIRED)
	List<NotificationTypePreferenceRequest> preferences
) {
	public UpdateNotificationPreferences toCommand() {
		if (pushEnabled == null) {
			throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "pushEnabled",
				"pushEnabled는 필수입니다.");
		}
		return new UpdateNotificationPreferences(
			pushEnabled,
			quietHours == null ? null : quietHours.toDomain(),
			toCompleteEnumMap(preferences));
	}

	private static EnumMap<NotificationType, Boolean> toCompleteEnumMap(
		List<NotificationTypePreferenceRequest> preferences) {
		if (preferences == null || preferences.size() != NotificationType.values().length) {
			throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "preferences",
				"알림 6종 설정을 정확히 한 번씩 지정해야 합니다.");
		}
		EnumSet<NotificationType> seen = EnumSet.noneOf(NotificationType.class);
		EnumMap<NotificationType, Boolean> values = new EnumMap<>(NotificationType.class);
		for (NotificationTypePreferenceRequest preference : preferences) {
			if (preference == null) {
				throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "preferences",
					"알림 6종 설정을 정확히 한 번씩 지정해야 합니다.");
			}
			NotificationType type = preference.typeOrThrow();
			if (!seen.add(type)) {
				throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "preferences",
					"알림 종류를 중복 지정할 수 없습니다.");
			}
			values.put(type, preference.enabledOrThrow());
		}
		if (!seen.equals(EnumSet.allOf(NotificationType.class))) {
			throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "preferences",
				"알림 6종 설정을 정확히 한 번씩 지정해야 합니다.");
		}
		return values;
	}
}
