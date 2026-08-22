package com.dnd.qello.notification.error;

import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;
import com.dnd.qello.common.error.ErrorCode;

// notification 기능의 오류 코드. BC 약어는 NOT.
//
// 코드 형식은 NOT-{CATEGORY}-{SEQ}, 한 번 배포한 코드 값은 변경 금지.
// 값 단위 검증은 코드를 늘리지 않고 예외의 field와 reason으로 구분.
// push token과 payload 내용은 메시지에 포함 금지.
public enum NotificationErrorCode implements ErrorCode {

	// 수신자, 알림, 이벤트의 식별자가 양수가 아님
	INVALID_ID(HttpStatus.BAD_REQUEST, "NOT-VAL-001", ErrorCategory.VAL, "알림 식별자가 올바르지 않습니다."),

	// 유형, 상태, 시각처럼 반드시 있어야 하는 값의 누락
	REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, "NOT-VAL-002", ErrorCategory.VAL, "알림 필수 값이 없습니다."),

	// 중복 제거 키의 공백 또는 허용 길이 초과
	INVALID_TEXT(HttpStatus.BAD_REQUEST, "NOT-VAL-003", ErrorCategory.VAL, "알림 문자열 값이 올바르지 않습니다."),

	// outbox payload의 JSON object 형식 위반
	INVALID_PAYLOAD(HttpStatus.BAD_REQUEST, "NOT-VAL-004", ErrorCategory.VAL, "이벤트 payload 형식이 올바르지 않습니다."),

	// 시도 횟수처럼 범위가 정해진 값의 허용 범위 이탈
	INVALID_VALUE_RANGE(HttpStatus.BAD_REQUEST, "NOT-VAL-005", ErrorCategory.VAL, "알림 값이 허용 범위를 벗어났습니다."),

	// 알림함 목록 limit이 허용 범위(1~50)를 벗어남
	INVALID_LIMIT(HttpStatus.BAD_REQUEST, "NOT-VAL-006", ErrorCategory.VAL, "limit 값이 올바르지 않습니다."),

	// cursor 두 파라미터 중 한쪽만 지정됨
	INVALID_CURSOR(HttpStatus.BAD_REQUEST, "NOT-VAL-007", ErrorCategory.VAL, "cursor 파라미터가 올바르지 않습니다."),

	// 전역/종류별/quiet 설정 snapshot이나 quiet 값 자체가 계약을 만족하지 않음
	INVALID_PREFERENCE(HttpStatus.BAD_REQUEST, "NOT-VAL-008", ErrorCategory.VAL, "알림 설정 값이 올바르지 않습니다."),

	// 알림 대상이 게시글과 답변 중 최대 하나라는 규칙 위반
	INVALID_NOTIFICATION_TARGET(HttpStatus.BAD_REQUEST, "NOT-DOM-001", ErrorCategory.DOM, "알림 대상이 올바르지 않습니다."),

	// 알림, 전달, outbox의 상태와 시각 불일치
	INVALID_NOTIFICATION_STATE(HttpStatus.BAD_REQUEST, "NOT-DOM-002", ErrorCategory.DOM, "알림 상태와 값이 맞지 않습니다."),

	// 현재 상태에서 허용되지 않는 전이 시도. 재시도로 해결 불가
	INVALID_NOTIFICATION_STATUS(HttpStatus.CONFLICT, "NOT-DOM-003", ErrorCategory.DOM, "현재 알림 상태로는 요청을 처리할 수 없습니다."),

	// 알림이 없거나 남의 알림. 존재 여부를 노출하지 않으므로 두 경우를 구분하지 않는다
	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT-DOM-004", ErrorCategory.DOM, "알림을 찾을 수 없습니다."),

	// 알림함 대상 계정이 존재하지 않음
	ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT-APP-001", ErrorCategory.APP, "계정을 찾을 수 없습니다."),

	// 알림함 대상 계정이 USER가 아니거나 ACTIVE가 아님
	ACCOUNT_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "NOT-APP-002", ErrorCategory.APP, "알림함을 사용할 수 없는 계정입니다."),

	// 같은 중복 제거 키의 알림 또는 이벤트 중복. DB 유일성 제약에서 감지
	DUPLICATED_EVENT(HttpStatus.CONFLICT, "NOT-INFRA-001", ErrorCategory.INFRA, "이미 처리된 알림입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final ErrorCategory category;
	private final String message;

	NotificationErrorCode(HttpStatus httpStatus, String code, ErrorCategory category, String message) {
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
