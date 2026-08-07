package com.dnd.qello.auth.error;

import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;
import com.dnd.qello.common.error.ErrorCode;

// 인증·인가 기능의 오류 코드.
//
// 로그인 실패는 원인을 구분해 노출하지 않는다. 존재하지 않는 login_id와 잘못된 비밀번호가
// 다른 코드로 나가면 계정 열거에 쓰인다.
public enum AuthErrorCode implements ErrorCode {

	// login_id 형식 위반. 공백, 길이 초과, 소문자가 아닌 문자
	INVALID_LOGIN_ID(HttpStatus.BAD_REQUEST, "AUT-VAL-001", ErrorCategory.VAL, "로그인 식별자가 올바르지 않습니다."),

	// 자격증명 필수 값 누락
	REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, "AUT-VAL-002", ErrorCategory.VAL, "인증 필수 값이 없습니다."),

	// installation_id 형식 위반. 공백, 64자 초과
	INVALID_INSTALLATION_ID(HttpStatus.BAD_REQUEST, "AUT-VAL-003", ErrorCategory.VAL, "기기 식별자가 올바르지 않습니다."),

	// 잠금 상태와 잠금 해제 시각이 맞지 않는 등 자격증명 자체의 불변식 위반
	INVALID_CREDENTIAL_STATE(HttpStatus.BAD_REQUEST, "AUT-DOM-001", ErrorCategory.DOM, "자격증명 상태가 올바르지 않습니다."),

	// 로그인 실패. 계정 없음과 비밀번호 불일치를 같은 코드로 응답한다
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUT-APP-001", ErrorCategory.APP, "로그인 정보가 올바르지 않습니다."),

	// 연속 실패로 잠긴 자격증명. 잠금이 풀리면 해소된다
	CREDENTIAL_LOCKED(HttpStatus.LOCKED, "AUT-APP-002", ErrorCategory.APP, "잠긴 계정입니다. 잠시 후 다시 시도해 주세요."),

	// 계정이 ACTIVE가 아니어서 로그인할 수 없음
	ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "AUT-APP-003", ErrorCategory.APP, "사용할 수 없는 계정입니다."),

	// 자격증명을 찾을 수 없음. 로그인 경로가 아닌 관리 경로에서만 노출한다
	CREDENTIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "AUT-APP-004", ErrorCategory.APP, "자격증명을 찾을 수 없습니다."),

	// ACTIVE 상태인 installation_id로 재등록 시도. 기기를 잃어버린 경우가 아니면 재등록이 아니라
	// 토큰 재발급(POST /api/v1/auth/token)을 호출해야 한다
	DEVICE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUT-APP-005", ErrorCategory.APP, "이미 등록된 기기입니다."),

	// 토큰 재발급 시 device_secret이 일치하지 않거나 자격증명이 REVOKED 상태. 재등록이 필요하다
	DEVICE_CREDENTIAL_INVALID(HttpStatus.UNAUTHORIZED, "AUT-APP-006", ErrorCategory.APP, "기기 자격증명이 유효하지 않습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final ErrorCategory category;
	private final String message;

	AuthErrorCode(HttpStatus httpStatus, String code, ErrorCategory category, String message) {
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
