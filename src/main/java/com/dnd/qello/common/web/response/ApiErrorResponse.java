package com.dnd.qello.common.web.response;

import java.time.Instant;

// 모든 오류 응답의 본문 형식. 상태 코드와 무관하게 이 형식만 사용.
public record ApiErrorResponse(
	String status,
	String message,
	ApiErrorDetail errorDetail,
	Instant timestamp
) {

	private static final String ERROR_STATUS = "error";

	public static ApiErrorResponse error(String message, ApiErrorDetail errorDetail) {
		return new ApiErrorResponse(ERROR_STATUS, message, errorDetail, Instant.now());
	}
}
