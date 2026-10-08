package com.dnd.qello.notification.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 알림함 조회 설정. {@code retention}보다 오래된 알림은 목록과 배지에서 빠질 뿐 행은 지우지 않는다 — 진입
 * 판정({@code /target})은 ID 조회라 이 기간과 무관하다.
 */
@ConfigurationProperties(prefix = "qello.notification.inbox")
public record NotificationInboxProperties(Duration retention) {

	private static final Duration DEFAULT_RETENTION = Duration.ofDays(30);

	public NotificationInboxProperties {
		if (retention == null) {
			retention = DEFAULT_RETENTION;
		}
		// 0 이하가 들어오면 하한이 조회 시각 이상이 되어 모든 알림이 숨겨진다. 기동 단계에서 막는다.
		if (retention.isZero() || retention.isNegative()) {
			throw new IllegalArgumentException("retention은 양수여야 합니다");
		}
	}
}
