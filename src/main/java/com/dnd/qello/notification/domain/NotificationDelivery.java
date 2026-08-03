package com.dnd.qello.notification.domain;

import java.time.Instant;

public record NotificationDelivery(Long id, long notificationId, long pushDeviceId, DeliveryStatus status,
	int attemptCount, Instant nextAttemptAt, Instant createdAt, Instant sentAt, String providerMessageId) {

	public NotificationDelivery {
		if (notificationId <= 0 || pushDeviceId <= 0 || attemptCount < 0) throw new IllegalArgumentException("전달 값이 유효하지 않습니다");
		if (status == null || nextAttemptAt == null || createdAt == null) throw new IllegalArgumentException("전달 상태와 시각은 필수입니다");
		if ((status == DeliveryStatus.SENT) != (sentAt != null)) throw new IllegalArgumentException("SENT와 sentAt은 함께 존재해야 합니다");
	}

	public static NotificationDelivery pending(long notificationId, long pushDeviceId, Instant at) {
		return new NotificationDelivery(null, notificationId, pushDeviceId, DeliveryStatus.PENDING, 0, at, at, null, null);
	}

	public NotificationDelivery claimed(Instant at) {
		if (status != DeliveryStatus.PENDING && status != DeliveryStatus.FAILED) throw new IllegalStateException("처리 가능한 전달 상태가 아닙니다");
		return new NotificationDelivery(id, notificationId, pushDeviceId, DeliveryStatus.PROCESSING, attemptCount, at, createdAt, sentAt, providerMessageId);
	}

	public NotificationDelivery sent(Instant at, String providerMessageId) {
		if (status != DeliveryStatus.PROCESSING) throw new IllegalStateException("PROCESSING 상태만 전송 완료할 수 있습니다");
		return new NotificationDelivery(id, notificationId, pushDeviceId, DeliveryStatus.SENT, attemptCount, nextAttemptAt, createdAt, at, providerMessageId);
	}

	public NotificationDelivery failed(Instant nextAttemptAt, boolean dead) {
		if (status != DeliveryStatus.PROCESSING) throw new IllegalStateException("PROCESSING 상태만 실패 처리할 수 있습니다");
		return new NotificationDelivery(id, notificationId, pushDeviceId, dead ? DeliveryStatus.DEAD : DeliveryStatus.FAILED,
			attemptCount + 1, nextAttemptAt, createdAt, null, providerMessageId);
	}
}
