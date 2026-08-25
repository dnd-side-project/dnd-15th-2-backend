package com.dnd.qello.notification.push;

import java.util.Map;

/** FCM data payload의 공개 allowlist. 이 record에 내부 식별자나 콘텐츠를 추가하지 않는다. */
public record PushPayload(String type, String count, String hasRemainingTime) {

	public PushPayload {
		if (type == null || type.isBlank() || count == null || count.isBlank()
			|| hasRemainingTime == null || hasRemainingTime.isBlank()) {
			throw new IllegalArgumentException("push payload 필드는 비어 있을 수 없습니다");
		}
		try {
			if (Integer.parseInt(count) <= 0) {
				throw new IllegalArgumentException("push payload count는 양의 정수여야 합니다");
			}
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("push payload count는 양의 정수여야 합니다", exception);
		}
		if (!"true".equals(hasRemainingTime) && !"false".equals(hasRemainingTime)) {
			throw new IllegalArgumentException("hasRemainingTime은 boolean 문자열이어야 합니다");
		}
	}

	public Map<String, String> asData() {
		return Map.of(
			"type", type,
			"count", count,
			"hasRemainingTime", hasRemainingTime);
	}
}
