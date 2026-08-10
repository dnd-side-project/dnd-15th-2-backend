package com.dnd.qello.feed.error;

import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;
import com.dnd.qello.common.error.ErrorCode;

// feed 기능의 오류 코드. BC 약어는 FED.
//
// 코드 형식은 FED-{CATEGORY}-{SEQ}, 한 번 배포한 코드 값은 변경 금지.
// 값 단위 검증은 코드를 늘리지 않고 예외의 field와 reason으로 구분.
public enum FeedErrorCode implements ErrorCode {

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
