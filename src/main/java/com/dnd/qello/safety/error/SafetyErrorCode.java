package com.dnd.qello.safety.error;

import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;
import com.dnd.qello.common.error.ErrorCode;

// safety 기능의 오류 코드. BC 약어는 SAF.
//
// 코드 형식은 SAF-{CATEGORY}-{SEQ}, 한 번 배포한 코드 값은 변경 금지.
// 값 단위 검증은 코드를 늘리지 않고 예외의 field와 reason으로 구분.
// 신고 내용과 신고자·피신고자 정보는 메시지에 포함 금지.
public enum SafetyErrorCode implements ErrorCode {

	// 신고, 검토, 차단 대상의 식별자가 양수가 아님
	INVALID_ID(HttpStatus.BAD_REQUEST, "SAF-VAL-001", ErrorCategory.VAL, "신고 식별자가 올바르지 않습니다."),

	// 상태, 시각, 판정처럼 반드시 있어야 하는 값의 누락
	REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, "SAF-VAL-002", ErrorCategory.VAL, "신고 필수 값이 없습니다."),

	// 신고 대상이 사용자, 게시글, 답변 중 정확히 하나가 아님
	INVALID_REPORT_TARGET(HttpStatus.BAD_REQUEST, "SAF-VAL-003", ErrorCategory.VAL, "신고 대상이 올바르지 않습니다."),

	// 신고 사유 코드의 공백 또는 허용 길이 초과
	INVALID_REASON_CODE(HttpStatus.BAD_REQUEST, "SAF-VAL-004", ErrorCategory.VAL, "신고 사유 코드가 올바르지 않습니다."),

	// 종결 시각이 접수 시각보다 앞선 순서 역전
	INVALID_TIME_ORDER(HttpStatus.BAD_REQUEST, "SAF-VAL-005", ErrorCategory.VAL, "신고 시각 순서가 올바르지 않습니다."),

	// OTHER 사유의 설명 누락 또는 설명 허용 길이 초과
	INVALID_REPORT_DETAIL(HttpStatus.BAD_REQUEST, "SAF-VAL-006", ErrorCategory.VAL, "신고 설명이 올바르지 않습니다."),

	// reason과 subReason 조합이 허용되지 않음
	INVALID_REPORT_SUB_REASON(HttpStatus.BAD_REQUEST, "SAF-VAL-007", ErrorCategory.VAL, "신고 하위 사유가 올바르지 않습니다."),

	// 증거 스냅샷의 편집 횟수가 음수
	INVALID_SNAPSHOT_EDIT_COUNT(HttpStatus.BAD_REQUEST, "SAF-VAL-008", ErrorCategory.VAL, "스냅샷 편집 횟수가 올바르지 않습니다."),

	// 자기 자신에 대한 차단 시도
	SELF_BLOCK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "SAF-DOM-001", ErrorCategory.DOM, "자기 자신을 차단할 수 없습니다."),

	// 종결 상태가 아닌 값으로의 신고 종결 시도
	INVALID_REPORT_STATUS(HttpStatus.BAD_REQUEST, "SAF-DOM-002", ErrorCategory.DOM, "신고 종결 상태가 올바르지 않습니다."),

	// 자기 자신이 작성한 콘텐츠 또는 자기 자신에 대한 신고 시도
	SELF_REPORT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "SAF-DOM-003", ErrorCategory.DOM, "자기 자신을 신고할 수 없습니다."),

	// 이미 다른 사건에 연결된 신고를 다른 사건에 재연결 시도
	REPORT_ALREADY_LINKED_TO_CASE(HttpStatus.BAD_REQUEST, "SAF-DOM-004", ErrorCategory.DOM, "이미 다른 사건에 연결된 신고입니다."),

	// 이미 종결된 사건에 대한 재검토·재종결 시도
	REPORT_CASE_ALREADY_RESOLVED(HttpStatus.BAD_REQUEST, "SAF-DOM-005", ErrorCategory.DOM, "이미 종결된 사건입니다."),

	// 해제할 활성 차단 부재. 이미 해제됐거나 존재하지 않는 상태
	ACTIVE_BLOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "SAF-APP-001", ErrorCategory.APP, "활성 차단을 찾을 수 없습니다."),

	// 신고 대상이 존재하지 않거나 신고자가 열람할 수 없음. 존재 비노출을 위해 둘을 구분하지 않음
	REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "SAF-APP-002", ErrorCategory.APP, "신고 대상을 찾을 수 없습니다."),

	// 요청한 신고를 찾을 수 없거나 본인 소유가 아님. 존재 비노출을 위해 둘을 구분하지 않음
	REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "SAF-APP-003", ErrorCategory.APP, "신고를 찾을 수 없습니다."),

	// 신고자당 rate limit 초과
	REPORT_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "SAF-APP-004", ErrorCategory.APP, "신고 요청이 너무 많습니다."),

	// 같은 대상에 대한 처리 중 신고 중복. DB 유일성 제약에서 감지
	DUPLICATED_OPEN_REPORT(HttpStatus.CONFLICT, "SAF-INFRA-001", ErrorCategory.INFRA, "이미 접수된 신고가 있습니다."),

	// 사건 병합 재시도가 반복적으로 실패함. 재시도 후보
	CASE_MERGE_CONFLICT(HttpStatus.CONFLICT, "SAF-INFRA-002", ErrorCategory.INFRA, "신고 처리가 일시적으로 지연되고 있습니다."),

	// 결과 알림 outbox payload 직렬화 실패(#155). payload가 고정된 형태라 사실상 발생하지 않는다
	PAYLOAD_SERIALIZATION_FAILED(
		HttpStatus.INTERNAL_SERVER_ERROR, "SAF-INFRA-003", ErrorCategory.INFRA, "신고 처리 결과를 저장하지 못했습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final ErrorCategory category;
	private final String message;

	SafetyErrorCode(HttpStatus httpStatus, String code, ErrorCategory category, String message) {
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
