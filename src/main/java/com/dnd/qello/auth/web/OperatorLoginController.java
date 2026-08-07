package com.dnd.qello.auth.web;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorLoginService;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

// 백오피스 로그인과 로그아웃.
//
// 운영자 계정 생성 엔드포인트는 두지 않는다. 자체 가입 경로는 그 자체가 공격면이며
// 운영자는 시드 migration으로만 만든다.
//
// swagger의 @ApiResponse는 이 저장소의 응답 래퍼(ApiResponse)와 이름이 겹친다.
// 래퍼가 반환 타입으로 훨씬 자주 쓰이므로 그쪽을 import하고 애노테이션은 정규화한다.
@Tag(name = "백오피스 인증", description = "운영자 세션 로그인과 로그아웃")
@RestController
@RequestMapping("/admin")
public class OperatorLoginController {

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

	@Operation(
		summary = "운영자 로그인",
		description = """
			자격증명을 검증하고 세션 쿠키를 발급한다.

			실패 원인은 구분해 알리지 않는다. 존재하지 않는 loginId와 잘못된 비밀번호가 같은 401로 나간다.
			계정 열거를 막기 위한 것이다.

			CSRF 보호 대상이므로 GET /admin/csrf로 받은 토큰을 함께 보내야 한다.""")
	@ApiResponses({
		// content를 비워 두면 springdoc이 반환 타입으로 채운다. 200을 아예 적지 않으면
		// 선언한 오류 응답만 남고 성공 응답이 통째로 빠진다.
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "로그인에 성공했습니다. 세션 쿠키가 발급됩니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "로그인 정보가 올바르지 않습니다. (AUT-APP-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "사용할 수 없는 계정이거나 CSRF 토큰이 유효하지 않습니다. (AUT-APP-003, CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "423",
			description = "연속 실패로 잠긴 계정입니다. 잠금이 풀리면 해소됩니다. (AUT-APP-002)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<OperatorSessionResponse>> login(
		@RequestBody @Valid OperatorLoginRequest request,
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
	@Operation(
		summary = "운영자 로그아웃",
		description = """
			세션을 무효화한다. 권한은 즉시 회수되며 유예 시간이 없다.

			CSRF 보호 대상이므로 GET /admin/csrf로 받은 토큰을 함께 보내야 한다.""")
	@SecurityRequirement(name = "operatorSession")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "로그아웃했습니다. 세션이 무효화됩니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "세션이 없거나 만료되었습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
		HttpSession session = httpRequest.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		return ResponseEntity.ok(responseFactory.success());
	}

}
