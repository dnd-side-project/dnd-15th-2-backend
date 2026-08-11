package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// release 승격·rollback 감사 이력 한 건. 운영자가 명시적으로 호출한 승격·rollback만
// 기록한다(INV-REL-001, INV-REL-008 — 승인 없는 자동 교체 경로가 없다는 근거).
public record ReleasePromotionHistoryEntry(Long id, long releaseId, ReleasePromotionAction action,
	Long previousActiveReleaseId, long operatorUserId, Instant occurredAt) {

	public ReleasePromotionHistoryEntry {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (releaseId <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "releaseId", "releaseId는 양수여야 합니다");
		}
		if (action == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "action");
		}
		if (previousActiveReleaseId != null && previousActiveReleaseId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "previousActiveReleaseId", "previousActiveReleaseId는 양수여야 합니다");
		}
		if (operatorUserId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "operatorUserId", "operatorUserId는 양수여야 합니다");
		}
		if (occurredAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "occurredAt");
		}
	}

	public static ReleasePromotionHistoryEntry of(long releaseId, ReleasePromotionAction action,
		Long previousActiveReleaseId, long operatorUserId, Instant occurredAt) {
		return new ReleasePromotionHistoryEntry(null, releaseId, action, previousActiveReleaseId,
			operatorUserId, occurredAt);
	}
}
