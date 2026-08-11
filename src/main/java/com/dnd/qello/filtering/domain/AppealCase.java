package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// 작성자 이의제기의 정체성. 6개월 만료, UPHOLD_HIDDEN/OVERTURN_HIDDEN 결과, 통지
// 시각은 appeal 운영 기능이 컬럼을 추가해 구현한다. 이 객체는 "동일 대상·HIDDEN
// decision에 활성 appeal 하나"라는 유일성 불변식(INV-APL-002)만 표현한다.
public record AppealCase(Long id, FilterTargetType targetType, long targetId, long filterDecisionId,
	Instant createdAt) {

	public AppealCase {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (targetType == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "targetType");
		}
		if (targetId <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "targetId", "targetId는 양수여야 합니다");
		}
		if (filterDecisionId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "filterDecisionId", "filterDecisionId는 양수여야 합니다");
		}
		if (createdAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "createdAt");
		}
	}

	public static AppealCase file(FilterTargetType targetType, long targetId, long filterDecisionId, Instant now) {
		return new AppealCase(null, targetType, targetId, filterDecisionId, now);
	}

	public static AppealCase restore(Long id, FilterTargetType targetType, long targetId, long filterDecisionId,
		Instant createdAt) {
		return new AppealCase(id, targetType, targetId, filterDecisionId, createdAt);
	}
}
