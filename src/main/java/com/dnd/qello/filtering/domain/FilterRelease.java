package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// 모더레이션 정책 구성요소를 묶는 식별자. 정규화 규칙·로컬 사전·threshold·model
// snapshot을 실제로 묶는 registry 컬럼과 승격 로직은 release registry 기능이 추가한다.
// 이 객체는 그 registry가 참조할 안정적인 id의 존재만 보장한다.
public record FilterRelease(Long id, Instant createdAt) {

	public FilterRelease {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (createdAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "createdAt");
		}
	}

	public static FilterRelease create(Instant now) {
		return new FilterRelease(null, now);
	}

	public static FilterRelease restore(Long id, Instant createdAt) {
		return new FilterRelease(id, createdAt);
	}
}
