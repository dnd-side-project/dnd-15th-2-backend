package com.dnd.qello.notification.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** D-2 secret-backed FCM 전송 설정. 실제 값은 SSM SecureString을 통해서만 주입한다. */
@ConfigurationProperties(prefix = "qello.notification.push.fcm")
public record PushProperties(
	String projectId,
	String credentialJson,
	Duration connectTimeout,
	Duration readTimeout
) {

	public PushProperties {
		if (projectId == null || projectId.isBlank()) {
			throw new IllegalArgumentException("FCM projectId는 비어 있을 수 없습니다");
		}
		if (credentialJson == null || credentialJson.isBlank()) {
			throw new IllegalArgumentException("FCM credentialJson은 비어 있을 수 없습니다");
		}
		if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
			throw new IllegalArgumentException("FCM connectTimeout은 양수여야 합니다");
		}
		if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
			throw new IllegalArgumentException("FCM readTimeout은 양수여야 합니다");
		}
	}

	@Override
	public String toString() {
		return "PushProperties[REDACTED]";
	}

}
