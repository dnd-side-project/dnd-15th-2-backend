package com.dnd.qello.common.error;

import org.springframework.http.HttpStatus;

// 특정 기능에 속하지 않는 오류 코드.
//
// 요청 검증 실패나 처리되지 않은 예외처럼 전역 처리기가 직접 만들어내는 오류만 정의.
// 기능의 비즈니스 실패는 해당 기능 패키지의 ErrorCode에 정의.
public enum CommonErrorCode implements ErrorCode {

	// 요청 본문 또는 파라미터의 검증 실패
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "CMN-VAL-001", ErrorCategory.VAL, "요청 값이 올바르지 않습니다."),

	// 필수 요청 파라미터 누락
	MISSING_FIELD(HttpStatus.BAD_REQUEST, "CMN-VAL-002", ErrorCategory.VAL, "필수 입력값이 누락되었습니다."),

	// 인증되지 않은 요청. 인증 도입 전 응답 형식 고정용
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "CMN-VAL-003", ErrorCategory.VAL, "인증에 실패했습니다."),

	// 인증은 됐으나 요청 수행 권한 없음
	FORBIDDEN(HttpStatus.FORBIDDEN, "CMN-DOM-001", ErrorCategory.DOM, "접근 권한이 없습니다."),

	// 요청한 리소스 없음
	NOT_FOUND(HttpStatus.NOT_FOUND, "CMN-DOM-002", ErrorCategory.DOM, "요청한 리소스를 찾을 수 없습니다."),

	// 유일성 제약 위반 등 현재 상태와의 충돌
	CONFLICT(HttpStatus.CONFLICT, "CMN-DOM-003", ErrorCategory.DOM, "중복 또는 충돌이 발생했습니다."),

	// 처리되지 않은 예외. 원인은 응답이 아니라 로그로 추적
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CMN-INFRA-001", ErrorCategory.INFRA, "서버 내부 오류가 발생했습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final ErrorCategory category;
	private final String message;

	CommonErrorCode(HttpStatus httpStatus, String code, ErrorCategory category, String message) {
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
