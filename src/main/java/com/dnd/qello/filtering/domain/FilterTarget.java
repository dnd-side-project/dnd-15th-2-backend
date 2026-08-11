package com.dnd.qello.filtering.domain;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// 호출자 도메인(answer, account)의 대상을 가리키는 opaque 참조. 이 시스템은
// targetId·targetVersion의 의미를 해석하지 않고 그대로 저장·비교만 한다.
//
// targetVersion은 현재 항상 0이다. 편집 기능이 결정되기 전까지는 어떤 코드도
// 0이 아닌 값을 만들지 않는다.
public record FilterTarget(FilterTargetType targetType, long targetId, long targetVersion) {

	public FilterTarget {
		if (targetType == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "targetType");
		}
		if (targetId <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "targetId", "targetId는 양수여야 합니다");
		}
		if (targetVersion < 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "targetVersion", "targetVersion은 음수일 수 없습니다");
		}
	}

	public static FilterTarget of(FilterTargetType targetType, long targetId) {
		return new FilterTarget(targetType, targetId, 0L);
	}
}
