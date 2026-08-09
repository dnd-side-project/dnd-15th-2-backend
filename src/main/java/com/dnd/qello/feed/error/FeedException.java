package com.dnd.qello.feed.error;

import com.dnd.qello.common.error.DomainException;

// feed 기능이 던지는 유일한 예외. 개별 실패는 FeedErrorCode로 구분.
public class FeedException extends DomainException {

	public FeedException(FeedErrorCode errorCode) {
		super(errorCode);
	}

	public FeedException(FeedErrorCode errorCode, String field) {
		super(errorCode, field, null);
	}

	public FeedException(FeedErrorCode errorCode, String field, String reason) {
		super(errorCode, field, reason);
	}

	public FeedException(FeedErrorCode errorCode, String field, String reason, Throwable cause) {
		super(errorCode, field, reason, null, cause);
	}
}
