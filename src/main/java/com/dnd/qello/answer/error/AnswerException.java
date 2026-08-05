package com.dnd.qello.answer.error;

import com.dnd.qello.common.error.DomainException;

// answer 기능이 던지는 유일한 예외. 개별 실패는 AnswerErrorCode로 구분.
public class AnswerException extends DomainException {

	public AnswerException(AnswerErrorCode errorCode) {
		super(errorCode);
	}

	public AnswerException(AnswerErrorCode errorCode, String field) {
		super(errorCode, field, null);
	}

	public AnswerException(AnswerErrorCode errorCode, String field, String reason) {
		super(errorCode, field, reason);
	}

	public AnswerException(AnswerErrorCode errorCode, String field, String reason, Throwable cause) {
		super(errorCode, field, reason, null, cause);
	}
}
