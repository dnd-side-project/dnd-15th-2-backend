package com.dnd.qello.common.error;

import org.springframework.stereotype.Component;

import com.dnd.qello.common.web.response.ApiErrorDetail;
import com.dnd.qello.common.web.response.ApiErrorResponse;

// 오류를 응답 본문으로 옮기는 변환기.
//
// 예외의 내부 메시지가 아니라 오류 코드의 안전한 메시지만 응답에 포함.
@Component
public class ApiErrorResponseFactory {

	public ApiErrorResponse from(DomainException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		return from(errorCode, exception.getField(), exception.getReason());
	}

	public ApiErrorResponse from(ErrorCode errorCode, String field, String reason) {
		String safeReason = reason != null ? reason : errorCode.message();
		return ApiErrorResponse.error(
			errorCode.message(),
			new ApiErrorDetail(errorCode.code(), field, safeReason)
		);
	}
}
