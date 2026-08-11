package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// job 상태 변경 감사 이력 한 건. 도메인 계층은 이 값을 만들기만 하고 쓰지 않는다 —
// FilterJob의 상태 전이 메서드를 호출한 서비스가 같은 트랜잭션에서 저장한다.
public record FilterJobStatusHistoryEntry(Long id, long filterJobId, FilterJobStatus fromStatus,
	FilterJobStatus toStatus, String reason, Instant occurredAt) {

	public FilterJobStatusHistoryEntry {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (filterJobId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "filterJobId", "filterJobId는 양수여야 합니다");
		}
		if (toStatus == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "toStatus");
		}
		if (occurredAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "occurredAt");
		}
	}

	public static FilterJobStatusHistoryEntry of(long filterJobId, FilterJobStatus fromStatus,
		FilterJobStatus toStatus, String reason, Instant occurredAt) {
		return new FilterJobStatusHistoryEntry(null, filterJobId, fromStatus, toStatus, reason, occurredAt);
	}
}
