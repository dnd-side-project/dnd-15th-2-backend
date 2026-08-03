package com.dnd.qello.safety.domain;

import java.time.Instant;

public record ModerationReview(Long id, long reportId, long reviewerId, ModerationDecision decision,
	String actionType, String internalNote, Instant reviewedAt) {

	public ModerationReview {
		if (id != null && id <= 0) throw new IllegalArgumentException("id는 양수여야 합니다");
		if (reportId <= 0 || reviewerId <= 0) throw new IllegalArgumentException("ID는 양수여야 합니다");
		if (decision == null || actionType == null || actionType.isBlank() || reviewedAt == null) {
			throw new IllegalArgumentException("검토 필수 값이 유효하지 않습니다");
		}
	}
}
