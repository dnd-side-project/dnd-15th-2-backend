package com.dnd.qello.question.error;

import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;
import com.dnd.qello.common.error.ErrorCode;

// question 기능의 오류 코드. BC 약어는 QUE.
//
// 코드 형식은 QUE-{CATEGORY}-{SEQ}, 한 번 배포한 코드 값은 변경 금지.
// 값 단위 검증은 코드를 늘리지 않고 예외의 field와 reason으로 구분.
// 질문 본문이나 제안자 정보는 메시지에 포함 금지.
public enum QuestionErrorCode implements ErrorCode {

	// 질문, 제안, 배정의 식별자가 양수가 아님
	INVALID_ID(HttpStatus.BAD_REQUEST, "QUE-VAL-001", ErrorCategory.VAL, "질문 식별자가 올바르지 않습니다."),

	// 상태, 시각, 본문처럼 반드시 있어야 하는 값의 누락
	REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, "QUE-VAL-002", ErrorCategory.VAL, "질문 필수 값이 없습니다."),

	// 질문 본문, 사유, cycleKey의 허용 길이 초과
	TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "QUE-VAL-003", ErrorCategory.VAL, "질문 값이 허용 길이를 초과했습니다."),

	// 시작과 종료 시각의 순서 역전 또는 기준 시각보다 앞선 값
	INVALID_TIME_ORDER(HttpStatus.BAD_REQUEST, "QUE-VAL-004", ErrorCategory.VAL, "질문 시각 순서가 올바르지 않습니다."),

	// displayOrder처럼 범위가 정해진 값의 허용 범위 이탈
	INVALID_VALUE_RANGE(HttpStatus.BAD_REQUEST, "QUE-VAL-005", ErrorCategory.VAL, "질문 값이 허용 범위를 벗어났습니다."),

	// 제안 상태와 제출 시각, 판정 사유의 불일치
	INVALID_PROPOSAL_STATE(HttpStatus.BAD_REQUEST, "QUE-DOM-001", ErrorCategory.DOM, "질문 제안 상태와 값이 맞지 않습니다."),

	// 현재 제안 상태에서 허용되지 않는 전이 시도. 재시도로 해결 불가
	INVALID_PROPOSAL_STATUS(HttpStatus.CONFLICT, "QUE-DOM-002", ErrorCategory.DOM, "현재 제안 상태로는 요청을 처리할 수 없습니다."),

	// 승인 질문의 상태와 출처, 승인 정보의 불일치
	INVALID_QUESTION_STATE(HttpStatus.BAD_REQUEST, "QUE-DOM-003", ErrorCategory.DOM, "승인 질문 상태와 값이 맞지 않습니다."),

	// createdAt과 updatedAt의 순서 역전
	INVALID_AUDIT_TIMESTAMPS(HttpStatus.BAD_REQUEST, "QUE-DOM-004", ErrorCategory.DOM, "질문 생성·수정 시각이 올바르지 않습니다."),

	// 배정 시각에 활성 상태가 아닌 질문. 질문 풀 변경 시 해소
	QUESTION_NOT_ASSIGNABLE(HttpStatus.CONFLICT, "QUE-APP-001", ErrorCategory.APP, "배정 시각에 활성인 질문이 아닙니다."),

	// 요청한 식별자의 질문 제안이 없음
	PROPOSAL_NOT_FOUND(HttpStatus.NOT_FOUND, "QUE-APP-002", ErrorCategory.APP, "질문 제안을 찾을 수 없습니다."),

	// 질문을 제안할 계정을 찾을 수 없음
	PROPOSER_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "QUE-APP-003", ErrorCategory.APP, "질문을 제안할 계정을 찾을 수 없습니다."),

	// 계정 역할 또는 상태상 질문 제안을 사용할 수 없음
	PROPOSER_ACCOUNT_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "QUE-APP-004", ErrorCategory.APP, "현재 계정은 질문을 제안할 수 없습니다."),

	// cycle 저장 후 식별자 부재. 매핑 또는 DB 설정 문제이므로 로그로 추적
	ASSIGNMENT_CYCLE_NOT_PERSISTED(HttpStatus.INTERNAL_SERVER_ERROR, "QUE-INFRA-001", ErrorCategory.INFRA, "질문 배정 주기를 저장하지 못했습니다."),

	// 같은 주기에 같은 질문 또는 순서의 중복 배정. DB 유일성 제약에서 감지
	DUPLICATED_ASSIGNMENT(HttpStatus.CONFLICT, "QUE-INFRA-002", ErrorCategory.INFRA, "이미 배정된 질문입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final ErrorCategory category;
	private final String message;

	QuestionErrorCode(HttpStatus httpStatus, String code, ErrorCategory category, String message) {
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
