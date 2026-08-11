package com.dnd.qello.direction.error;

import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;
import com.dnd.qello.common.error.ErrorCode;

// direction 기능의 오류 코드. BC 약어는 DIR.
//
// 코드 형식은 DIR-{CATEGORY}-{SEQ}, 한 번 배포한 코드 값은 변경 금지.
// 값 단위 검증은 코드를 늘리지 않고 예외의 field와 reason으로 구분.
// 위치는 개인정보이므로 좌표, cell id, 거리 값은 메시지에 포함 금지.
public enum DirectionErrorCode implements ErrorCode {

	// 발신자, scheme, post의 식별자가 양수가 아님
	INVALID_ID(HttpStatus.BAD_REQUEST, "DIR-VAL-001", ErrorCategory.VAL, "방향 식별자가 올바르지 않습니다."),

	// 시각, 상태, segment 목록처럼 반드시 있어야 하는 값의 누락
	REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, "DIR-VAL-002", ErrorCategory.VAL, "방향 필수 값이 없습니다."),

	// 지역 코드, segment key, 멱등키의 공백 또는 허용 길이 초과
	INVALID_TEXT(HttpStatus.BAD_REQUEST, "DIR-VAL-003", ErrorCategory.VAL, "방향 문자열 값이 올바르지 않습니다."),

	// 방위각의 [0, 360) 범위 이탈 또는 비유한 값
	INVALID_BEARING(HttpStatus.BAD_REQUEST, "DIR-VAL-004", ErrorCategory.VAL, "방위각이 올바르지 않습니다."),

	// 음수인 최소 거리 또는 최소 거리 이하인 최대 거리
	INVALID_DISTANCE_RANGE(HttpStatus.BAD_REQUEST, "DIR-VAL-005", ErrorCategory.VAL, "거리 범위가 올바르지 않습니다."),

	// 위도 또는 경도의 허용 범위 이탈, 한쪽만 지정된 좌표
	INVALID_COORDINATE(HttpStatus.BAD_REQUEST, "DIR-VAL-006", ErrorCategory.VAL, "위치 좌표가 올바르지 않습니다."),

	// 만료 시각이 기준 시각보다 앞서는 등의 시각 순서 역전
	INVALID_TIME_ORDER(HttpStatus.BAD_REQUEST, "DIR-VAL-007", ErrorCategory.VAL, "방향 시각 순서가 올바르지 않습니다."),

	// 정렬 순서, 정확도, 수신 건수처럼 범위가 정해진 값의 허용 범위 이탈
	INVALID_VALUE_RANGE(HttpStatus.BAD_REQUEST, "DIR-VAL-008", ErrorCategory.VAL, "방향 값이 허용 범위를 벗어났습니다."),

	// scheme 유형과 segment 구성의 불일치, sector의 360도 미충족
	INVALID_SCHEME_CONFIGURATION(HttpStatus.BAD_REQUEST, "DIR-DOM-001", ErrorCategory.DOM, "방향 구획 구성이 올바르지 않습니다."),

	// 주어진 방위각을 포함하는 sector 부재. scheme 구성 문제
	SEGMENT_NOT_FOUND(HttpStatus.BAD_REQUEST, "DIR-DOM-002", ErrorCategory.DOM, "방위각을 포함하는 구획이 없습니다."),

	// post 상태와 게시·삭제 시각의 불일치
	INVALID_POST_STATE(HttpStatus.BAD_REQUEST, "DIR-DOM-003", ErrorCategory.DOM, "게시글 상태와 값이 맞지 않습니다."),

	// 수신자 상태와 발견·열람·종료 시각의 불일치
	INVALID_RECIPIENT_STATE(HttpStatus.BAD_REQUEST, "DIR-DOM-004", ErrorCategory.DOM, "수신자 상태와 값이 맞지 않습니다."),

	// 좌표와 대략 위치 셀이 모두 없어 대상 범위 산출 불가
	LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "DIR-DOM-005", ErrorCategory.DOM, "위치 정보가 필요합니다."),

	// 요청한 방향 구획 체계 부재
	SCHEME_NOT_FOUND(HttpStatus.NOT_FOUND, "DIR-DOM-006", ErrorCategory.DOM, "방향 구획 체계를 찾을 수 없습니다."),

	// 수신 자격 없는 사용자의 질문글 공감. post_reaction의 복합 FK에서 감지. 재시도로 해결 불가
	INELIGIBLE_REACTOR(HttpStatus.FORBIDDEN, "DIR-DOM-007", ErrorCategory.DOM, "질문글에 공감할 수 있는 수신자가 아닙니다."),

	// 존재하지 않거나 본인 소유가 아닌 수신 항목. 두 경우를 구분하지 않는다.
	RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DIR-DOM-008", ErrorCategory.DOM, "수신 항목을 찾을 수 없습니다."),

	// 존재하지 않거나 본인이 보내지 않은 질문글. 두 경우를 구분하지 않는다.
	POST_NOT_FOUND(HttpStatus.NOT_FOUND, "DIR-DOM-009", ErrorCategory.DOM, "질문글을 찾을 수 없습니다."),

	// 전송 시각에 활성 상태가 아닌 질문. 질문 풀 변경 시 해소
	QUESTION_NOT_ACTIVE(HttpStatus.CONFLICT, "DIR-APP-001", ErrorCategory.APP, "전송 시각에 활성인 질문이 아닙니다."),

	// 정확 위치가 없는 presence로 인한 수신 후보 계산 불가. 위치 갱신 시 해소
	PRESENCE_LOCATION_MISSING(HttpStatus.CONFLICT, "DIR-APP-002", ErrorCategory.APP, "위치 정보가 없어 수신 후보를 계산할 수 없습니다."),

	// presence 만료 또는 수신 미허용 상태. 위치 갱신 시 해소
	PRESENCE_NOT_CURRENT(HttpStatus.CONFLICT, "DIR-APP-003", ErrorCategory.APP, "현재 위치 정보가 유효하지 않습니다."),

	// 발신자의 presence 기록 부재. 위치 등록 시 해소
	PRESENCE_NOT_FOUND(HttpStatus.CONFLICT, "DIR-APP-004", ErrorCategory.APP, "발신자의 위치 정보가 없습니다."),

	// 같은 멱등키로 이미 전송된 게시글 존재. DB 유일성 제약에서 감지
	DUPLICATED_POST(HttpStatus.CONFLICT, "DIR-INFRA-001", ErrorCategory.INFRA, "이미 전송된 게시글입니다."),

	// 같은 게시글의 동일 수신자 중복. DB 유일성 제약에서 감지
	DUPLICATED_RECIPIENT(HttpStatus.CONFLICT, "DIR-INFRA-002", ErrorCategory.INFRA, "이미 등록된 수신자입니다."),

	// 같은 sender와 멱등키에 다른 사용자 의도 fingerprint를 재사용한 요청
	IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "DIR-APP-005", ErrorCategory.APP,
		"같은 멱등키로 다른 요청을 재사용할 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final ErrorCategory category;
	private final String message;

	DirectionErrorCode(HttpStatus httpStatus, String code, ErrorCategory category, String message) {
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
