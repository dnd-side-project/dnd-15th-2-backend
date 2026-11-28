package com.dnd.qello.auth.web;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dnd.qello.auth.domain.OperatorAccessRequest;
import com.dnd.qello.common.web.ClientAddress;

// 접근 기록에 필요한 요청 정보와 현재 운영자 id를 HTTP 요청·SecurityContext에서 꺼낸다(#375).
// 로그인 컨트롤러와 기록 필터가 같은 규칙을 쓰도록 한곳에 둔다.
//
// 운영자 세션의 권한은 둘이다(#376). 정상 운영자는 ROLE_OPERATOR, 비밀번호를 바꿔야 하는 운영자는
// ROLE_OPERATOR_PASSWORD_CHANGE다. 둘 다 운영자 세션이라 접근 기록 대상이다.
//
// 권한 이름은 여기 한곳에만 둔다. 로그인이 주는 권한과 SecurityConfiguration이 통과시키는 권한이 어긋나면 제한 세션이
// 비밀번호 변경·로그아웃까지 막혀 빠져나올 수 없다.
public final class OperatorAccessContext {

	public static final String OPERATOR_ROLE = "OPERATOR";
	public static final String PASSWORD_CHANGE_ROLE = "OPERATOR_PASSWORD_CHANGE";

	static final String OPERATOR_AUTHORITY = "ROLE_" + OPERATOR_ROLE;
	static final String PASSWORD_CHANGE_AUTHORITY = "ROLE_" + PASSWORD_CHANGE_ROLE;

	private static final Set<String> OPERATOR_SESSION_AUTHORITIES = Set.of(
			OPERATOR_AUTHORITY, PASSWORD_CHANGE_AUTHORITY);

	private OperatorAccessContext() {
	}

	// getRequestURI는 query string을 포함하지 않는다. 헤더·쿠키·본문은 읽지 않는다.
	static OperatorAccessRequest request(HttpServletRequest request) {
		return new OperatorAccessRequest(ClientAddress.of(request), request.getMethod(), request.getRequestURI());
	}

	// 운영자 세션 권한으로 인증된 요청이면 운영자 id, 아니면 null. principal 이름은 로그인 때 넣은 계정 id다
	// (OperatorLoginController).
	static Long currentOperatorId() {
		Authentication authentication = authenticated();
		if (authentication == null || !hasAnyAuthority(authentication, OPERATOR_SESSION_AUTHORITIES)) {
			return null;
		}
		try {
			long operatorUserId = Long.parseLong(authentication.getName());
			return operatorUserId > 0 ? operatorUserId : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	// 비밀번호를 바꿔야 하는 운영자 세션인지. 접근 거부 응답의 오류 코드를 고를 때 쓴다(AuthEntryPoints).
	static boolean isPasswordChangeSession() {
		Authentication authentication = authenticated();
		return authentication != null && hasAnyAuthority(authentication, Set.of(PASSWORD_CHANGE_AUTHORITY));
	}

	private static Authentication authenticated() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return null;
		}
		return authentication;
	}

	private static boolean hasAnyAuthority(Authentication authentication, Set<String> authorities) {
		return authentication.getAuthorities().stream()
				.anyMatch(authority -> authorities.contains(authority.getAuthority()));
	}
}
