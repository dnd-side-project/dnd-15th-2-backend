package com.dnd.qello.auth.web;

import java.time.Clock;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

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
import com.dnd.qello.auth.domain.OperatorAccessLogEntry;
import com.dnd.qello.auth.domain.OperatorAccessRequest;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorAccessLogRecorder;
import com.dnd.qello.auth.service.OperatorLoginService;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;

import lombok.RequiredArgsConstructor;

// 백오피스 로그인과 로그아웃.
//
// 운영자 계정 생성 엔드포인트는 두지 않는다. 자체 가입 경로는 그 자체가 공격면이며
// 운영자는 시드 migration으로만 만든다.
//
// 로그인 판정 결과는 OperatorLoginService가 접근 기록에 남긴다. 서비스에 닿지 않는 요청 제한 거절과
// 로그아웃은 여기서 남긴다(#375). 경로와 문서 애노테이션은 OperatorLoginApiSpec에 있다.
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class OperatorLoginController implements OperatorLoginApiSpec {

	private static final String OPERATOR_AUTHORITY = "ROLE_OPERATOR";

	private final OperatorLoginService operatorLoginService;
	private final AuthRequestRateLimiter rateLimiter;
	private final OperatorAccessLogRecorder accessLogRecorder;
	private final ApiResponseFactory responseFactory;
	private final Clock clock;
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	@Override
	public ResponseEntity<ApiResponse<OperatorSessionResponse>> login(
			OperatorLoginRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		OperatorAccessRequest accessRequest = OperatorAccessContext.request(httpRequest);
		checkRateLimit(httpRequest, accessRequest);
		long userId = operatorLoginService.login(
				LoginId.of(request.loginId()), new RawPassword(request.password()), accessRequest);

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
	// 기록은 무효화 뒤에 한다. 기록이 실패해도 로그아웃은 그대로 끝나야 한다(D4).
	@Override
	public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
		Long operatorUserId = OperatorAccessContext.currentOperatorId();
		HttpSession session = httpRequest.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		if (operatorUserId != null) {
			accessLogRecorder.recordIndependently(OperatorAccessLogEntry.logout(
					operatorUserId, OperatorAccessContext.request(httpRequest), clock.instant()));
		}
		return ResponseEntity.ok(responseFactory.success());
	}

	// 계정 단위 잠금(423)과 별개로 IP 단위로도 막는다. 잠금만으로는 여러 계정을
	// 번갈아 시도하는 대입을 막지 못한다(#315). 거절된 요청은 계정을 조회하지 않으므로
	// 운영자 id 없이 기록한다.
	private void checkRateLimit(HttpServletRequest httpRequest, OperatorAccessRequest accessRequest) {
		try {
			rateLimiter.checkOperatorLogin(httpRequest);
		} catch (AuthException exception) {
			// 기록 단계가 실패해도 429를 그대로 돌려준다(D4).
			try {
				accessLogRecorder.recordIndependently(OperatorAccessLogEntry.loginFailed(
						null, AuthErrorCode.RATE_LIMIT_EXCEEDED, accessRequest, clock.instant()));
			} catch (RuntimeException recordingFailure) {
				exception.addSuppressed(recordingFailure);
			}
			throw exception;
		}
	}
}
