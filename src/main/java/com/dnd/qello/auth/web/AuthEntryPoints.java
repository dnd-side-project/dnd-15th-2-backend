package com.dnd.qello.auth.web;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.CommonErrorCode;
import com.dnd.qello.common.error.ErrorCode;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

// 필터 단계에서 끝나는 인증·인가 실패의 응답을 만든다.
//
// 이 실패들은 controller에 닿지 않으므로 GlobalExceptionHandler를 거치지 않는다.
// 여기서 같은 형식을 쓰지 않으면 401·403만 응답 계약을 벗어난다.
@Component
public class AuthEntryPoints {

	private final ApiErrorResponseFactory errorResponseFactory;
	private final ObjectMapper objectMapper;

	public AuthEntryPoints(ApiErrorResponseFactory errorResponseFactory, ObjectMapper objectMapper) {
		this.errorResponseFactory = errorResponseFactory;
		this.objectMapper = objectMapper;
	}

	public AuthenticationEntryPoint unauthorized() {
		return (request, response, exception) ->
			write(response, CommonErrorCode.UNAUTHORIZED);
	}

	public AccessDeniedHandler forbidden() {
		return (request, response, exception) ->
			write(response, CommonErrorCode.FORBIDDEN);
	}

	// 예외의 메시지를 응답에 담지 않는다. 인증 실패 원인이 그대로 새어 나간다.
	private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		ApiErrorResponse body = errorResponseFactory.from(errorCode, null, errorCode.message());
		response.setStatus(errorCode.httpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), body);
	}

}
