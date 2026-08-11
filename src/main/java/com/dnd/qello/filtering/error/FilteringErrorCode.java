package com.dnd.qello.filtering.error;

import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;
import com.dnd.qello.common.error.ErrorCode;

// filtering 기능의 오류 코드. BC 약어는 FLT.
//
// 코드 형식은 FLT-{CATEGORY}-{SEQ}, 한 번 배포한 코드 값은 변경 금지.
public enum FilteringErrorCode implements ErrorCode {

	// 식별자, target reference, 필수 값의 누락 또는 형식 오류
	REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, "FLT-VAL-001", ErrorCategory.VAL, "필터링 필수 값이 없습니다."),

	// attemptGeneration이 1 미만이거나 target 식별자가 양수가 아님
	INVALID_VALUE_RANGE(HttpStatus.BAD_REQUEST, "FLT-VAL-002", ErrorCategory.VAL, "필터링 값의 범위가 올바르지 않습니다."),

	// 현재 job 상태에서 허용되지 않는 전이 시도. 재시도로 해결 불가
	INVALID_JOB_STATUS(HttpStatus.CONFLICT, "FLT-DOM-001", ErrorCategory.DOM, "현재 job 상태로는 요청을 처리할 수 없습니다."),

	// 이미 수동 결정으로 종결된 job에 자동 결과를 적용하려는 시도(INV-MAN-004)
	ALREADY_MANUALLY_RESOLVED(HttpStatus.CONFLICT, "FLT-DOM-002", ErrorCategory.DOM, "이미 수동으로 종결된 job입니다."),

	// job의 현재 attempt generation과 다른 generation의 결과 적용 시도(INV-GEN-004, INV-GEN-005)
	STALE_ATTEMPT_GENERATION(HttpStatus.CONFLICT, "FLT-DOM-003", ErrorCategory.DOM, "오래된 attempt generation의 결과입니다."),

	// 같은 대상·release로 이미 존재하는 manual review case 또는 appeal case
	DUPLICATE_CASE(HttpStatus.CONFLICT, "FLT-INFRA-001", ErrorCategory.INFRA, "이미 존재하는 case입니다."),

	// 같은 멱등키로 이미 등록된 job 존재. DB 유일성 제약에서 감지
	DUPLICATED_JOB(HttpStatus.CONFLICT, "FLT-INFRA-002", ErrorCategory.INFRA, "이미 접수된 job입니다."),

	// registry 참조 문자열(normalizationRef 등)의 공백 또는 허용 길이 초과
	INVALID_TEXT(HttpStatus.BAD_REQUEST, "FLT-VAL-003", ErrorCategory.VAL, "필터링 문자열 값이 올바르지 않습니다."),

	// registry 참조 값으로 "latest" alias를 쓰려는 시도(INV-REL-001)
	LATEST_ALIAS_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "FLT-VAL-004", ErrorCategory.VAL, "\"latest\" alias는 사용할 수 없습니다."),

	// 현재 release 상태에서 허용되지 않는 전이 시도. 재시도로 해결 불가
	INVALID_RELEASE_STATUS(HttpStatus.CONFLICT, "FLT-DOM-004", ErrorCategory.DOM, "현재 release 상태로는 요청을 처리할 수 없습니다."),

	// 요청한 release를 찾을 수 없음
	RELEASE_NOT_FOUND(HttpStatus.NOT_FOUND, "FLT-DOM-005", ErrorCategory.DOM, "release를 찾을 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final ErrorCategory category;
	private final String message;

	FilteringErrorCode(HttpStatus httpStatus, String code, ErrorCategory category, String message) {
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
