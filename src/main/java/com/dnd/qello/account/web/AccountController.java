package com.dnd.qello.account.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.service.NicknameRegistrationService;
import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;

// 인증된 사용자 본인의 계정 프로필 API. 경로와 문서 애노테이션은 AccountApiSpec에 있다.
@RestController
public class AccountController implements AccountApiSpec {

	private final NicknameRegistrationService nicknameRegistrationService;
	private final ApiResponseFactory responseFactory;

	public AccountController(
		NicknameRegistrationService nicknameRegistrationService,
		ApiResponseFactory responseFactory
	) {
		this.nicknameRegistrationService = nicknameRegistrationService;
		this.responseFactory = responseFactory;
	}

	@Override
	public ResponseEntity<ApiResponse<NicknameResponse>> changeNickname(
		ChangeNicknameRequest request, Authentication authentication
	) {
		Account updated = nicknameRegistrationService.changeNickname(
			AuthenticatedUserId.require(authentication), request.nickname());
		return ResponseEntity.ok(responseFactory.success(NicknameResponse.from(updated)));
	}
}
