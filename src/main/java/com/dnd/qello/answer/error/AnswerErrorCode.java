package com.dnd.qello.answer.error;

import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;
import com.dnd.qello.common.error.ErrorCode;

// answer 기능의 오류 코드. BC 약어는 ANS.
//
// 코드 형식은 ANS-{CATEGORY}-{SEQ}, 한 번 배포한 코드 값은 변경 금지.
// 값 단위 검증은 코드를 늘리지 않고 예외의 field와 reason으로 구분.
// 답변 본문은 사용자 콘텐츠이므로 메시지에 포함 금지.
public enum AnswerErrorCode implements ErrorCode {

	// 답변, 작성자, 수신자의 식별자가 양수가 아님
	INVALID_ID(HttpStatus.BAD_REQUEST, "ANS-VAL-001", ErrorCategory.VAL, "답변 식별자가 올바르지 않습니다."),

	// 상태, 시각처럼 반드시 있어야 하는 값의 누락
	REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, "ANS-VAL-002", ErrorCategory.VAL, "답변 필수 값이 없습니다."),

	// 멱등키, 지역 코드, 거리 구간의 공백 또는 허용 길이 초과
	INVALID_TEXT(HttpStatus.BAD_REQUEST, "ANS-VAL-003", ErrorCategory.VAL, "답변 문자열 값이 올바르지 않습니다."),

	// 발신자 기준 방위각의 [0, 360) 범위 이탈
	INVALID_BEARING(HttpStatus.BAD_REQUEST, "ANS-VAL-004", ErrorCategory.VAL, "방위각이 올바르지 않습니다."),

	// 미디어 첨부 대상이 게시글과 답변 중 정확히 하나가 아니거나 식별자 오류
	INVALID_MEDIA_TARGET(HttpStatus.BAD_REQUEST, "ANS-VAL-005", ErrorCategory.VAL, "미디어 첨부 대상이 올바르지 않습니다."),

	// 답변 상태와 게시·삭제 시각의 불일치
	INVALID_ANSWER_STATE(HttpStatus.BAD_REQUEST, "ANS-DOM-001", ErrorCategory.DOM, "답변 상태와 값이 맞지 않습니다."),

	// 현재 답변 상태에서 허용되지 않는 전이 시도. 재시도로 해결 불가
	INVALID_ANSWER_STATUS(HttpStatus.CONFLICT, "ANS-DOM-002", ErrorCategory.DOM, "현재 답변 상태로는 요청을 처리할 수 없습니다."),

	// 안전 검사 미통과 답변의 공개 시도
	SAFETY_CHECK_NOT_PASSED(HttpStatus.CONFLICT, "ANS-DOM-003", ErrorCategory.DOM, "안전 검사를 통과한 답변만 공개할 수 있습니다."),

	// 같은 멱등키로 이미 등록된 답변 존재. DB 유일성 제약에서 감지
	DUPLICATED_ANSWER(HttpStatus.CONFLICT, "ANS-INFRA-001", ErrorCategory.INFRA, "이미 등록된 답변입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final ErrorCategory category;
	private final String message;

	AnswerErrorCode(HttpStatus httpStatus, String code, ErrorCategory category, String message) {
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
