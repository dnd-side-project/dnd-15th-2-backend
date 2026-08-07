package com.dnd.qello.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.auth.security.DeviceSecret;
import com.dnd.qello.auth.service.DeviceRegistrationResult;
import com.dnd.qello.auth.service.DeviceRegistrationService;
import com.dnd.qello.auth.service.DeviceTokenService;
import com.dnd.qello.auth.token.IssuedAccessToken;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;

import jakarta.validation.Valid;

// 앱 기기 등록과 토큰 재발급.
//
// 두 경로 모두 SecurityConfiguration의 appApiSecurityFilterChain에서 인증 없이
// 열려 있다. 등록·재발급 자체가 인증 수단을 얻는 과정이라 그 전에는 인증할 수 없다.
@RestController
@RequestMapping("/api/v1/auth")
public class DeviceAuthController {

	private final DeviceRegistrationService registrationService;
	private final DeviceTokenService tokenService;
	private final ApiResponseFactory responseFactory;

	public DeviceAuthController(
		DeviceRegistrationService registrationService,
		DeviceTokenService tokenService,
		ApiResponseFactory responseFactory
	) {
		this.registrationService = registrationService;
		this.tokenService = tokenService;
		this.responseFactory = responseFactory;
	}

	@PostMapping("/devices")
	public ResponseEntity<ApiResponse<DeviceRegistrationResponse>> register(
		@RequestBody @Valid DeviceRegistrationRequest request
	) {
		DeviceRegistrationResult result = registrationService.register(
			request.installationId(),
			request.platform(),
			request.coarseRegionCode(),
			request.locale(),
			request.timezone(),
			request.nickname()
		);

		DeviceRegistrationResponse body = new DeviceRegistrationResponse(
			result.userId(),
			result.deviceSecret().value(),
			result.accessToken().value(),
			result.accessToken().expiresInSeconds()
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(responseFactory.success(body));
	}

	@PostMapping("/token")
	public ResponseEntity<ApiResponse<DeviceTokenResponse>> reissue(
		@RequestBody @Valid DeviceTokenRequest request
	) {
		IssuedAccessToken issuedToken = tokenService.reissue(
			request.installationId(), new DeviceSecret(request.deviceSecret()));

		return ResponseEntity.ok(responseFactory.success(
			new DeviceTokenResponse(issuedToken.value(), issuedToken.expiresInSeconds())));
	}

}
