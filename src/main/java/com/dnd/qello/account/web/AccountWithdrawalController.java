package com.dnd.qello.account.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.account.service.AccountWithdrawalService;
import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;

import lombok.RequiredArgsConstructor;

// 인증된 사용자 본인의 탈퇴 요청·철회 API(#337). 경로와 문서 애노테이션은 AccountWithdrawalApiSpec에 있다.
@RestController
@RequiredArgsConstructor
public class AccountWithdrawalController implements AccountWithdrawalApiSpec {

	private final AccountWithdrawalService withdrawalService;
	private final ApiResponseFactory responseFactory;

	@Override
	public ResponseEntity<ApiResponse<AccountWithdrawalResponse>> requestWithdrawal(Authentication authentication) {
		return ResponseEntity.ok(responseFactory.success(AccountWithdrawalResponse.from(
				withdrawalService.request(AuthenticatedUserId.require(authentication)))));
	}

	@Override
	public ResponseEntity<ApiResponse<AccountWithdrawalResponse>> cancelWithdrawal(Authentication authentication) {
		return ResponseEntity.ok(responseFactory.success(AccountWithdrawalResponse.from(
				withdrawalService.cancel(AuthenticatedUserId.require(authentication)))));
	}
}
