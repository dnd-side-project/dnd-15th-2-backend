package com.dnd.qello.notification.view;

import java.time.Instant;

/**
 * 알림함 전체 지우기 결과. {@code dismissedAt}은 서버가 정한 기준 시각이며 그 시각 이전에 생성된 줄만 지웠다 — 이후에
 * 도착한 알림은 새 알림으로 남는다.
 */
public record NotificationDismissal(int dismissedCount, Instant dismissedAt) {

	public NotificationDismissal {
		if (dismissedCount < 0) {
			throw new IllegalArgumentException("dismissedCount must not be negative");
		}
		if (dismissedAt == null) {
			throw new IllegalArgumentException("dismissedAt must not be null");
		}
	}
}
