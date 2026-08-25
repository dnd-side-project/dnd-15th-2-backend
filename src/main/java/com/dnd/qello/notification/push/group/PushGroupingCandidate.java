package com.dnd.qello.notification.push.group;

import java.time.Instant;

import com.dnd.qello.notification.domain.NotificationType;

/** group에 아직 속하지 않은 notification 한 건의 편입 입력이다. */
public record PushGroupingCandidate(
	long notificationId,
	long recipientId,
	NotificationType type,
	Instant createdAt,
	Long recommendationCycleId
) {

	public PushGroupingCandidate {
		if (notificationId <= 0 || recipientId <= 0) {
			throw new IllegalArgumentException("grouping candidate 식별자는 양수여야 합니다");
		}
		if (type == null || createdAt == null) {
			throw new IllegalArgumentException("grouping candidate 유형과 시각은 필수입니다");
		}
		if (recommendationCycleId != null && recommendationCycleId <= 0) {
			throw new IllegalArgumentException("recommendation cycle ID는 양수여야 합니다");
		}
	}
}
