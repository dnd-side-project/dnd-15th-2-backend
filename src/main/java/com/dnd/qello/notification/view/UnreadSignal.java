package com.dnd.qello.notification.view;

import java.time.Instant;

/**
 * 지도 홈의 알림 점과 카운터. {@code hasUnseen}은 {@code seenAt} 기준선으로
 * 사라지고, {@code unreadCount}는 톱하지 않은 줄의 개수를 그대로 센다 — 두 값의
 * 기준이 다르다(§8.3).
 */
public record UnreadSignal(boolean hasUnseen, long unreadCount, Instant seenAt) {

	public UnreadSignal {
		if (unreadCount < 0) {
			throw new IllegalArgumentException("unreadCount must not be negative");
		}
	}
}
