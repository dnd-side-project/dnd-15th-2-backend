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
import com.dnd.qello.auth.service.OperatorLoginResult;
import com.dnd.qello.auth.service.OperatorLoginService;
import com.dnd.qello.auth.service.OperatorPasswordChangeService;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;

import lombok.RequiredArgsConstructor;

// 백오피스 로그인, 로그아웃, 비밀번호 변경.
//
// 운영자 계정 생성 엔드포인트는 두지 않는다. 자체 가입 경로는 그 자체가 공격면이며
// 운영자는 시드 migration으로만 만든다.
//
// 로그인 판정 결과는 OperatorLoginService가 접근 기록에 남긴다. 서비스에 닿지 않는 요청 제한 거절과
// 로그아웃은 여기서 남긴다(#375). 비밀번호 변경은 기록 필터가 API 호출로 남긴다. 경로와 문서 애노테이션은
// OperatorLoginApiSpec에 있다.
//
// 비밀번호를 바꿔야 하는 운영자에게는 ROLE_OPERATOR 대신 ROLE_OPERATOR_PASSWORD_CHANGE를 준다(#376). 이 권한은
// 비밀번호 변경과 로그아웃만 통과한다(SecurityConfiguration).
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class OperatorLoginController implements OperatorLoginApiSpec {

	private final OperatorLoginService operatorLoginService;
	private final OperatorPasswordChangeService passwordChangeService;
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
		OperatorLoginResult result = operatorLoginService.login(
				LoginId.of(request.loginId()), new RawPassword(request.password()), accessRequest);

		// 인증 전에 심어진 세션 ID를 버리고 새로 발급한다. SecurityFilterChain의
		// sessionFixation 설정은 필터가 인증을 수행할 때 동작하므로, 컨트롤러가 직접
		// 인증하는 이 경로에서는 여기서 무효화해야 한다.
		HttpSession existingSession = httpRequest.getSession(false);
		if (existingSession != null) {
			existingSession.invalidate();
		}
		httpRequest.getSession(true);

		String authority = result.passwordChangeRequired()
				? OperatorAccessContext.PASSWORD_CHANGE_AUTHORITY
				: OperatorAccessContext.OPERATOR_AUTHORITY;
		saveAuthentication(result.userId(), authority, httpRequest, httpResponse);

		return ResponseEntity.ok(responseFactory.success(
				new OperatorSessionResponse(result.userId(), result.passwordChangeRequired())));
	}

	// 변경이 커밋된 뒤 세션 ID를 재발급하고 권한을 정상 운영자로 바꾼다(#376). 비밀번호를 바꾼 시점의 세션 ID가
	// 그대로면, 변경 전에 새어 나간 세션 ID가 새 비밀번호의 세션으로 이어진다.
	//
	// changeSessionId는 세션을 새로 만들지 않고 ID만 바꾼다. 생성 시각이 유지되므로 세션 절대 만료의 기준인 로그인
	// 시각도 그대로다(OperatorSessionAbsoluteTimeoutFilter). 세션을 새로 만들면 비밀번호를 바꿀 때마다 수명이
	// 늘어난다.
	@Override
	public ResponseEntity<ApiResponse<Void>> changePassword(
			OperatorPasswordChangeRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		Long operatorUserId = OperatorAccessContext.currentOperatorId();
		if (operatorUserId == null) {
			// SecurityConfiguration이 운영자 세션만 들여보내므로 여기 오지 않는다. 오면 인증 정보가 깨진 것이다.
			throw new AuthException(AuthErrorCode.CREDENTIAL_NOT_FOUND, null, "자격증명을 찾을 수 없습니다");
		}
		passwordChangeService.changePassword(operatorUserId,
				new RawPassword(request.currentPassword()), new RawPassword(request.newPassword()));

		httpRequest.changeSessionId();
		saveAuthentication(operatorUserId, OperatorAccessContext.OPERATOR_AUTHORITY, httpRequest, httpResponse);
		return ResponseEntity.ok(responseFactory.success());
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

	private void saveAuthentication(long userId, String authority, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
				String.valueOf(userId), null, List.of(new SimpleGrantedAuthority(authority)));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);
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
