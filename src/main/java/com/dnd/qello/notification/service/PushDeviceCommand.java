package com.dnd.qello.notification.service;

import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.push.security.PushToken;

public record PushDeviceCommand(PushPlatform platform, PushToken token) {

	public PushDeviceCommand {
		if (platform == null) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST, "platform", "platform은 필수입니다.");
		}
		if (token == null) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST, "token", "token은 필수입니다.");
		}
	}

	public PushDeviceCommand(String platform, String token) {
		this(requirePlatform(platform), requireToken(token));
	}

	private static PushPlatform requirePlatform(String platform) {
		if (platform == null || platform.isBlank()) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST, "platform", "platform은 필수입니다.");
		}
		try {
			return PushPlatform.valueOf(platform);
		}
		catch (IllegalArgumentException exception) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST, "platform", "platform 값이 올바르지 않습니다.");
		}
	}

	private static PushToken requireToken(String token) {
		try {
			return PushToken.of(token);
		}
		catch (IllegalArgumentException exception) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST, "token", "push token 값이 올바르지 않습니다.");
		}
	}
}
