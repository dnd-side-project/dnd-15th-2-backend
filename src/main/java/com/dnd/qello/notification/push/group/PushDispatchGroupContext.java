package com.dnd.qello.notification.push.group;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.push.PushDispatchContext;

/** 한 read snapshot의 group·설정·member eligibility 경계다. token 평문과 본문은 포함하지 않는다. */
public record PushDispatchGroupContext(
	PushDispatchGroup group,
	ZoneId budgetZone,
	NotificationPreferenceSnapshot preference,
	Instant lastRecommendationAttemptAt,
	List<PushDispatchMemberContext> members) {

	public PushDispatchGroupContext {
		if (group == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "group",
				"group은 필수입니다.");
		}
		if (budgetZone == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "budgetZone",
				"budgetZone은 필수입니다.");
		}
		if (preference == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "preference",
				"알림 설정은 필수입니다.");
		}
		if (members == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "members",
				"members는 필수입니다.");
		}
		for (PushDispatchMemberContext member : members) {
			if (member == null) {
				throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "members",
					"member는 필수입니다.");
			}
		}
		members = List.copyOf(members);
		if (preference.userId() != group.recipientId()) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "preference",
				"알림 설정 사용자가 group 수신자와 일치해야 합니다.");
		}
		for (PushDispatchMemberContext member : members) {
			PushDispatchContext dispatchContext = member.dispatchContext();
			if (dispatchContext.notification().recipientId() != group.recipientId()) {
				throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "recipientId",
					"notification 수신자가 group 수신자와 일치해야 합니다.");
			}
			if (dispatchContext.notification().notificationType() != group.notificationType()) {
				throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "notificationType",
					"notification 종류가 group 종류와 일치해야 합니다.");
			}
			if (dispatchContext.device().userId() != group.recipientId()) {
				throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "deviceUserId",
					"기기 소유자가 group 수신자와 일치해야 합니다.");
			}
		}
	}
}
