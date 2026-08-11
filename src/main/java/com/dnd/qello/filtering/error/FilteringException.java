package com.dnd.qello.filtering.error;

import com.dnd.qello.common.error.DomainException;

// filtering 기능이 던지는 유일한 예외. 개별 실패는 FilteringErrorCode로 구분.
public class FilteringException extends DomainException {

	public FilteringException(FilteringErrorCode errorCode) {
		super(errorCode);
	}

	public FilteringException(FilteringErrorCode errorCode, String field) {
		super(errorCode, field, null);
	}

	public FilteringException(FilteringErrorCode errorCode, String field, String reason) {
		super(errorCode, field, reason);
	}
}
