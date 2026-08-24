package com.dnd.qello.auth.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;

import com.dnd.qello.common.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

// CsrfTokenController의 문서 계약. 분리 근거는 OperatorLoginApiSpec에 있다.
@Tag(name = "백오피스 인증", description = "운영자 세션 로그인과 로그아웃")
public interface CsrfTokenApiSpec {

	// csrfToken은 Spring Security가 주입한다. 요청 파라미터가 아니므로 문서에서 감춘다.
	// 감추지 않으면 프레임워크 내부 타입이 스펙 스키마로 새어 나간다.
	@Operation(
		summary = "CSRF 토큰 발급",
		description = """
			운영자 로그인과 다른 상태 변경 요청에 넣을 CSRF 토큰과 헤더 이름을 조회합니다.

			인증 없이 호출할 수 있습니다. 로그인 POST도 CSRF 보호 대상이므로 로그인 전에 이 경로를
			호출해야 합니다.

			성공하면 headerName에 토큰을 보낼 요청 헤더 이름을, token에 사용할 토큰을 담아 반환합니다.

			이 토큰은 운영자 세션을 만들거나 권한을 부여하지 않습니다.

			브라우저에서 읽을 수 없는 다른 사이트의 요청에는 이 응답을 그대로 사용할 수 없습니다.""")
	@GetMapping("/csrf")
	ResponseEntity<ApiResponse<CsrfTokenResponse>> issue(@Parameter(hidden = true) CsrfToken csrfToken);

}
