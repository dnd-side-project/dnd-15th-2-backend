package com.dnd.qello.notification.push.group;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.push.PushDispatchContext;

/** group member notification의 기기별 delivery와 한 snapshot eligibility 경계다. */
public record PushDispatchMemberContext(
	long deliveryId,
	PushDispatchContext dispatchContext) {

	public PushDispatchMemberContext {
		if (deliveryId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, "deliveryId",
				"deliveryId는 양수여야 합니다.");
		}
		if (dispatchContext == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "dispatchContext",
				"dispatch context는 필수입니다.");
		}
		if (dispatchContext.delivery().id() == null || dispatchContext.delivery().id() != deliveryId) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "deliveryId",
				"member deliveryId가 context와 일치해야 합니다.");
		}
	}
}
