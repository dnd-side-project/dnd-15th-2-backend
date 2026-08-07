package com.dnd.qello.common.web.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

// 모든 오류 응답의 본문 형식. 상태 코드와 무관하게 이 형식만 사용.
public record ApiErrorResponse(
	ApiStatus status,
	String message,
	ApiErrorDetail errorDetail,
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	Instant timestamp
) {

	public static ApiErrorResponse error(String message, ApiErrorDetail errorDetail, Instant timestamp) {
		return new ApiErrorResponse(ApiStatus.ERROR, message, errorDetail, timestamp);
	}
}
