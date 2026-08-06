package com.dnd.qello.notification.domain;

import java.time.Instant;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

public record PushDevice(Long id, long userId, PushPlatform platform, byte[] tokenCiphertext,
	String tokenFingerprint, PushDeviceStatus status, Instant lastSeenAt, Instant revokedAt) {

	public PushDevice {
		if (userId <= 0 || platform == null || tokenCiphertext == null || tokenCiphertext.length == 0
			|| tokenFingerprint == null || tokenFingerprint.isBlank() || status == null || lastSeenAt == null) {
			throw new NotificationException(
				NotificationErrorCode.REQUIRED_VALUE_MISSING, null, "push device 값이 유효하지 않습니다");
		}
		if ((status == PushDeviceStatus.REVOKED) != (revokedAt != null)) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATE, "revokedAt", "REVOKED와 revokedAt은 함께 존재해야 합니다");
		}
	}
}
