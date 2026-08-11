package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// 수동 검토 case의 정체성. 우선순위·band·FIFO·aging·reviewer 배정은 수동 검토 운영
// 기능이 컬럼을 추가해 구현한다. 이 객체는 "동일 대상·release에 case 하나"라는
// 유일성 불변식(INV-MAN-001)만 표현한다 — 실제 강제는 DB unique index가 한다.
public record ManualReviewCase(Long id, FilterTarget target, long filterReleaseId, Instant createdAt) {

	public ManualReviewCase {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (target == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "target");
		}
		if (filterReleaseId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "filterReleaseId", "filterReleaseId는 양수여야 합니다");
		}
		if (createdAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "createdAt");
		}
	}

	public static ManualReviewCase open(FilterTarget target, long filterReleaseId, Instant now) {
		return new ManualReviewCase(null, target, filterReleaseId, now);
	}

	public static ManualReviewCase restore(Long id, FilterTarget target, long filterReleaseId, Instant createdAt) {
		return new ManualReviewCase(id, target, filterReleaseId, createdAt);
	}
}
