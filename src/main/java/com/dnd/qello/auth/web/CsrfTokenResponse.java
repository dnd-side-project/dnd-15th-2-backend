package com.dnd.qello.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;

// 클라이언트가 다음 상태 변경 요청에 실어 보낼 CSRF 토큰과 그 헤더 이름.
@Schema(description = "CSRF 토큰과 그 토큰을 실어 보낼 헤더 이름")
public record CsrfTokenResponse(
	@Schema(description = "토큰을 실어 보낼 요청 헤더 이름")
	String headerName,

	@Schema(description = "다음 상태 변경 요청에 실어 보낼 CSRF 토큰")
	String token
) {
}
