package com.dnd.qello.notification.push;

import java.time.Instant;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

/**
 * DB claim transaction이 worker에 넘기는 최소 lease 경계다.
 * token, payload와 같은 발송 데이터는 claim 결과에 포함하지 않는다.
 */
public record ClaimedPushDelivery(long deliveryId, int generation, Instant leaseUntil) {

	public ClaimedPushDelivery {
		if (deliveryId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, "deliveryId",
				"deliveryId는 양수여야 합니다.");
		}
		if (generation <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "generation",
				"generation은 양수여야 합니다.");
		}
		if (leaseUntil == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "leaseUntil",
				"leaseUntil은 필수입니다.");
		}
	}
}
