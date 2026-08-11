package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// pipeline attempt 하나의 원시 결과. filter_job.resolvedVerdict가 "현재 권위 있는
// 요약"이라면 이 레코드는 append-only 원장이다. 한 번 만들어지면 값이 바뀌지 않는다.
public record FilterDecision(Long id, long filterJobId, int attemptGeneration, FilterVerdict verdict,
	long requestedReleaseId, String actualModel, Instant decidedAt) {

	public FilterDecision {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (filterJobId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "filterJobId", "filterJobId는 양수여야 합니다");
		}
		if (attemptGeneration < 1) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "attemptGeneration", "attemptGeneration은 1 이상이어야 합니다");
		}
		if (verdict == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "verdict");
		}
		if (requestedReleaseId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "requestedReleaseId", "requestedReleaseId는 양수여야 합니다");
		}
		if (decidedAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "decidedAt");
		}
	}

	// actualModel은 공급자 응답이 실제로 보고한 model이다. requestedReleaseId가 가리키는
	// release와 다를 수 있어 별도로 기록한다(INV-REL-005). 아직 알 수 없으면 null.
	public static FilterDecision of(long filterJobId, int attemptGeneration, FilterVerdict verdict,
		long requestedReleaseId, String actualModel, Instant decidedAt) {
		return new FilterDecision(null, filterJobId, attemptGeneration, verdict, requestedReleaseId,
			actualModel, decidedAt);
	}

	public static FilterDecision restore(Long id, long filterJobId, int attemptGeneration, FilterVerdict verdict,
		long requestedReleaseId, String actualModel, Instant decidedAt) {
		return new FilterDecision(id, filterJobId, attemptGeneration, verdict, requestedReleaseId,
			actualModel, decidedAt);
	}
}
