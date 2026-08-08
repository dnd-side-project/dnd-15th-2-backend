package com.dnd.qello.auth.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorLoginService;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

// 백오피스 로그인과 로그아웃.
//
// 운영자 계정 생성 엔드포인트는 두지 않는다. 자체 가입 경로는 그 자체가 공격면이며
// 운영자는 시드 migration으로만 만든다.
//
// 경로와 문서 애노테이션은 OperatorLoginApiSpec에 있다.
@RestController
@RequestMapping("/admin")
public class OperatorLoginController implements OperatorLoginApiSpec {

	private static final String OPERATOR_AUTHORITY = "ROLE_OPERATOR";

	private final OperatorLoginService operatorLoginService;
	private final ApiResponseFactory responseFactory;
	private final SecurityContextRepository securityContextRepository =
		new HttpSessionSecurityContextRepository();

	public OperatorLoginController(
		OperatorLoginService operatorLoginService,
		ApiResponseFactory responseFactory
	) {
		this.operatorLoginService = operatorLoginService;
		this.responseFactory = responseFactory;
	}

	@Override
	public ResponseEntity<ApiResponse<OperatorSessionResponse>> login(
		OperatorLoginRequest request,
		HttpServletRequest httpRequest,
		HttpServletResponse httpResponse
	) {
		long userId = operatorLoginService.login(
			LoginId.of(request.loginId()), new RawPassword(request.password()));

		// 인증 전에 심어진 세션 ID를 버리고 새로 발급한다. SecurityFilterChain의
		// sessionFixation 설정은 필터가 인증을 수행할 때 동작하므로, 컨트롤러가 직접
		// 인증하는 이 경로에서는 여기서 무효화해야 한다.
		HttpSession existingSession = httpRequest.getSession(false);
		if (existingSession != null) {
			existingSession.invalidate();
		}
		httpRequest.getSession(true);

		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
			String.valueOf(userId), null, List.of(new SimpleGrantedAuthority(OPERATOR_AUTHORITY)));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);

		return ResponseEntity.ok(responseFactory.success(new OperatorSessionResponse(userId)));
	}

	// 세션 행을 지우면 권한이 즉시 회수된다. 앱 API의 토큰과 달리 유예가 없다.
	@Override
	public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
		HttpSession session = httpRequest.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		return ResponseEntity.ok(responseFactory.success());
	}

}
