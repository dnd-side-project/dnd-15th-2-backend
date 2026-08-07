package com.dnd.qello.auth.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;

import io.swagger.v3.oas.annotations.Parameter;

// CSRF 토큰 발급.
//
// 로그인 POST에도 CSRF를 적용하기로 했으므로(login CSRF 방어) 클라이언트가 로그인 전에
// 토큰을 얻을 경로가 필요하다. 이 GET이 없으면 /admin/** 전체가 인증 필요라 토큰을 받을
// 방법이 없어 로그인 자체가 불가능해진다.
//
// 토큰은 인증 상태와 무관한 값이라 노출해도 안전하다. 방어의 근거는 공격자가 피해자의
// 브라우저에서 이 응답을 읽을 수 없다는 same-origin 정책이다.
@RestController
@RequestMapping("/admin")
public class CsrfTokenController {

	private final ApiResponseFactory responseFactory;

	public CsrfTokenController(ApiResponseFactory responseFactory) {
		this.responseFactory = responseFactory;
	}

	// csrfToken은 Spring Security가 주입한다. 요청 파라미터가 아니므로 문서에서 감춘다.
	// 감추지 않으면 프레임워크 내부 타입이 스펙 스키마로 새어 나간다.
	@GetMapping("/csrf")
	public ResponseEntity<ApiResponse<CsrfTokenResponse>> issue(
		@Parameter(hidden = true) CsrfToken csrfToken
	) {
		return ResponseEntity.ok(responseFactory.success(
			new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken())));
	}

}
