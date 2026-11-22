package com.dnd.qello.auth.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dnd.qello.auth.domain.OperatorAccessRequest;
import com.dnd.qello.common.web.ClientAddress;

// 접근 기록에 필요한 요청 정보와 현재 운영자 id를 HTTP 요청·SecurityContext에서 꺼낸다(#375).
// 로그인 컨트롤러와 기록 필터가 같은 규칙을 쓰도록 한곳에 둔다.
final class OperatorAccessContext {

	private static final String OPERATOR_AUTHORITY = "ROLE_OPERATOR";

	private OperatorAccessContext() {
	}

	// getRequestURI는 query string을 포함하지 않는다. 헤더·쿠키·본문은 읽지 않는다.
	static OperatorAccessRequest request(HttpServletRequest request) {
		return new OperatorAccessRequest(ClientAddress.of(request), request.getMethod(), request.getRequestURI());
	}

	// ROLE_OPERATOR로 인증된 요청이면 운영자 id, 아니면 null. principal 이름은 로그인 때 넣은 계정 id다
	// (OperatorLoginController).
	static Long currentOperatorId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return null;
		}
		boolean operator = authentication.getAuthorities().stream()
				.anyMatch(authority -> OPERATOR_AUTHORITY.equals(authority.getAuthority()));
		if (!operator) {
			return null;
		}
		try {
			long operatorUserId = Long.parseLong(authentication.getName());
			return operatorUserId > 0 ? operatorUserId : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}
}
