package com.dnd.qello.feed.error;

import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;
import com.dnd.qello.common.error.ErrorCode;

// feed 기능의 오류 코드. BC 약어는 FED.
//
// 코드 형식은 FED-{CATEGORY}-{SEQ}, 한 번 배포한 코드 값은 변경 금지.
// 값 단위 검증은 코드를 늘리지 않고 예외의 field와 reason으로 구분.
public enum FeedErrorCode implements ErrorCode {

	// 존재하지 않음, 타인 소유와 현재 열람 자격 상실을 구분하지 않아 수신 항목의 존재를 숨긴다.
	INBOX_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "FED-DOM-001", ErrorCategory.DOM, "수신함 항목을 찾을 수 없습니다."),

	// 유예 마감 이후 되돌리기처럼 대상은 유효하지만 요청한 상태 전이를 더는 적용할 수 없다.
	INBOX_TRANSITION_CONFLICT(HttpStatus.CONFLICT, "FED-DOM-002", ErrorCategory.DOM, "수신함 상태를 변경할 수 없습니다."),

	// 인증 subject에 대응하는 계정이 없다.
	INBOX_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "FED-APP-001", ErrorCategory.APP, "계정을 찾을 수 없습니다."),

	// 수신함은 ACTIVE USER 계정만 사용할 수 있다.
	INBOX_ACCOUNT_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "FED-APP-002", ErrorCategory.APP, "수신함을 사용할 수 없는 계정입니다."),

	// 방향 칩 segmentKey, displayName의 공백 또는 누락.
	// 두 값 모두 클라이언트 입력이 아니라 SQL 조회 결과(JdbcInboxQueryRepository)에서
	// 채워지므로, 위반은 direction_segment 데이터나 행 매핑의 결함을 뜻한다.
	INVALID_TEXT(HttpStatus.INTERNAL_SERVER_ERROR, "FED-INFRA-001", ErrorCategory.INFRA, "방향 칩 데이터를 생성하지 못했습니다."),

	// 방향 칩 sortOrder, count의 음수. INVALID_TEXT와 같은 이유로 서버 측 결함이다.
	INVALID_VALUE_RANGE(HttpStatus.INTERNAL_SERVER_ERROR, "FED-INFRA-002", ErrorCategory.INFRA, "방향 칩 데이터를 생성하지 못했습니다."),

	// 거리 스냅샷이 내부 불변식인 음수 범위를 벗어났다.
	INVALID_DISTANCE(HttpStatus.INTERNAL_SERVER_ERROR, "FED-INFRA-003", ErrorCategory.INFRA, "거리 표시 정책을 적용하지 못했습니다.");

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
