package com.dnd.qello.notification.push;

import java.util.Objects;

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.PushDevice;

/**
 * Provider 호출 직전에 읽은 push dispatch 권위값의 경계다.
 *
 * <p>이 객체는 worker와 정책 사이에서만 사용하며, payload로 직렬화하지 않는다.
 * token 원문과 콘텐츠 본문은 이 경계에 포함되지 않는다.</p>
 */
public record PushDispatchContext(
	NotificationDelivery delivery,
	Notification notification,
	PushDevice device,
	Long actorId,
	DispatchValiditySnapshot targetValidity) {

	public PushDispatchContext {
		Objects.requireNonNull(delivery, "delivery");
		Objects.requireNonNull(notification, "notification");
		Objects.requireNonNull(device, "device");
		Objects.requireNonNull(targetValidity, "targetValidity");
	}

	/**
	 * 같은 snapshot에서 계산한 제한된 eligibility 입력값이다. 외부 식별자나 콘텐츠를
	 * 포함하지 않으며, worker가 SQL 결과를 다시 해석하지 않도록 boolean 경계를 둔다.
	 */
	public record DispatchValiditySnapshot(
		boolean recipientActive,
		boolean actorActive,
		boolean preferenceEnabled,
		boolean blockedInEitherDirection,
		boolean targetValid,
		boolean hasRemainingTime) {
	}
}
