package com.dnd.qello.account.error;

import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;
import com.dnd.qello.common.error.ErrorCode;

// account 기능의 오류 코드. BC 약어는 ACC.
//
// 코드 형식은 ACC-{CATEGORY}-{SEQ}, 한 번 배포한 코드 값은 변경 금지.
// 값 단위 검증은 코드를 늘리지 않고 예외의 field와 reason으로 구분.
// 닉네임처럼 사용자를 식별할 수 있는 값은 메시지에 포함 금지.
public enum AccountErrorCode implements ErrorCode {

	// Account 생성·복원 시 id가 양수가 아님
	INVALID_ID(HttpStatus.BAD_REQUEST, "ACC-VAL-001", ErrorCategory.VAL, "계정 식별자가 올바르지 않습니다."),

	// role, status, 지역 코드처럼 계정에 반드시 있어야 하는 값의 누락
	REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, "ACC-VAL-002", ErrorCategory.VAL, "계정 필수 값이 없습니다."),

	// 지역 코드, locale, timezone, 닉네임의 허용 길이 초과
	TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "ACC-VAL-003", ErrorCategory.VAL, "계정 값이 허용 길이를 초과했습니다."),

	// 국가 코드가 ISO alpha-2 대문자 형식이 아님
	INVALID_COUNTRY_CODE(HttpStatus.BAD_REQUEST, "ACC-VAL-005", ErrorCategory.VAL, "국가 코드가 올바르지 않습니다."),

	// timezone이 IANA 지역 ID가 아님
	INVALID_TIMEZONE(HttpStatus.BAD_REQUEST, "ACC-VAL-004", ErrorCategory.VAL, "timezone은 유효한 IANA 지역 ID여야 합니다."),

	// createdAt과 updatedAt이 함께 존재하지 않거나 순서 역전
	// auditing이 저장 계층으로 옮겨간 뒤로는 사용하지 않는다. 과거 로그와의 의미를 지키기 위해 남겨 둔다.
	INVALID_AUDIT_TIMESTAMPS(HttpStatus.BAD_REQUEST, "ACC-DOM-001", ErrorCategory.DOM, "계정 생성·수정 시각이 올바르지 않습니다."),

	// DELETED 상태와 deletedAt의 불일치
	INVALID_DELETION_STATE(HttpStatus.BAD_REQUEST, "ACC-DOM-002", ErrorCategory.DOM, "탈퇴 상태와 탈퇴 시각이 일치하지 않습니다."),

	// 사용 중단(#72). 자격증명이 operator_credential로 분리되면서 Account는 비밀번호를
	// 알지 못하게 됐고, role과 자격증명의 조합은 (user_id, role) 복합 FK가 DB에서 거절한다.
	// 배포된 코드 값이므로 삭제하지 않는다(docs/error-codes.md 4절).
	INVALID_PASSWORD_HASH_STATE(HttpStatus.BAD_REQUEST, "ACC-DOM-003", ErrorCategory.DOM, "계정 권한과 비밀번호 설정이 맞지 않습니다."),

	// 현재 상태에서 허용되지 않는 차단, 차단 해제 또는 탈퇴 요청
	INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "ACC-DOM-004", ErrorCategory.DOM, "현재 계정 상태로는 요청을 처리할 수 없습니다."),

	// 수정 대상 계정이 존재하지 않음
	ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACC-APP-001", ErrorCategory.APP, "계정을 찾을 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final ErrorCategory category;
	private final String message;

	AccountErrorCode(HttpStatus httpStatus, String code, ErrorCategory category, String message) {
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
