package com.dnd.qello.feed.error;

import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;
import com.dnd.qello.common.error.ErrorCode;

// feed 기능의 오류 코드. BC 약어는 FED.
//
// 코드 형식은 FED-{CATEGORY}-{SEQ}, 한 번 배포한 코드 값은 변경 금지.
// 값 단위 검증은 코드를 늘리지 않고 예외의 field와 reason으로 구분.
public enum FeedErrorCode implements ErrorCode {

	// 방향 칩 segmentKey, displayName의 공백 또는 누락
	INVALID_TEXT(HttpStatus.BAD_REQUEST, "FED-VAL-001", ErrorCategory.VAL, "방향 칩 문자열 값이 올바르지 않습니다."),

	// 방향 칩 sortOrder, count의 음수
	INVALID_VALUE_RANGE(HttpStatus.BAD_REQUEST, "FED-VAL-002", ErrorCategory.VAL, "방향 칩 값이 허용 범위를 벗어났습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final ErrorCategory category;
	private final String message;

	FeedErrorCode(HttpStatus httpStatus, String code, ErrorCategory category, String message) {
		this.httpStatus = httpStatus;
		this.code = code;
		this.category = category;
		this.message = message;
	}

	@Override
	public HttpStatus httpStatus() {
		return httpStatus;
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public ErrorCategory category() {
		return category;
	}

	@Override
	public String message() {
		return message;
	}
}
