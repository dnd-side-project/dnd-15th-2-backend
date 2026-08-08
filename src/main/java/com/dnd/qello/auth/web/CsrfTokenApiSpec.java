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
			다음 상태 변경 요청에 실어 보낼 CSRF 토큰과 그 헤더 이름을 돌려준다.

			인증이 필요 없다. 로그인 POST도 CSRF 보호 대상이라 로그인 전에 토큰을 받을 경로가 있어야 한다.

			토큰은 인증 상태와 무관한 값이라 노출해도 안전하다. 방어의 근거는 공격자가 피해자의
			브라우저에서 이 응답을 읽을 수 없다는 same-origin 정책이다.""")
	@GetMapping("/csrf")
	ResponseEntity<ApiResponse<CsrfTokenResponse>> issue(@Parameter(hidden = true) CsrfToken csrfToken);

}
