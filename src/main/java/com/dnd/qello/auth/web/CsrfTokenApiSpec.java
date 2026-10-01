package com.dnd.qello.auth.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;

import com.dnd.qello.common.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

// CsrfTokenController의 문서 계약. 분리 근거는 OperatorLoginApiSpec에 있다.
@Tag(name = "백오피스 인증", description = "운영자 로그인과 로그아웃")
public interface CsrfTokenApiSpec {

	// csrfToken은 Spring Security가 주입한다. 요청 파라미터가 아니므로 문서에서 감춘다.
	// 감추지 않으면 프레임워크 내부 타입이 스펙 스키마로 새어 나간다.
	@Operation(summary = "CSRF 토큰 발급", description = """
			운영자 로그인 요청과 상태를 바꾸는 백오피스 요청에 넣을 CSRF 토큰을 발급합니다.

			로그인 없이 호출할 수 있습니다.

			응답의 token 값을 headerName에 적힌 요청 헤더에 담아 보냅니다.""")
	@GetMapping("/csrf")
	ResponseEntity<ApiResponse<CsrfTokenResponse>> issue(@Parameter(hidden = true) CsrfToken csrfToken);

}
