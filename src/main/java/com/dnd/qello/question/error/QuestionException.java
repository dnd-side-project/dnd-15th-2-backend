package com.dnd.qello.question.error;

import com.dnd.qello.common.error.DomainException;

// question 기능이 던지는 유일한 예외. 개별 실패는 QuestionErrorCode로 구분.
public class QuestionException extends DomainException {

	public QuestionException(QuestionErrorCode errorCode) {
		super(errorCode);
	}

	public QuestionException(QuestionErrorCode errorCode, String field) {
		super(errorCode, field, null);
	}

	public QuestionException(QuestionErrorCode errorCode, String field, String reason) {
		super(errorCode, field, reason);
	}

	public QuestionException(QuestionErrorCode errorCode, String field, String reason, Throwable cause) {
		super(errorCode, field, reason, null, cause);
	}
}
