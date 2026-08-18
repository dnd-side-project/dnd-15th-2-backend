package com.dnd.qello.account.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.account.service.ProfileService;
import com.dnd.qello.account.web.request.ProfileImageChangeRequest;
import com.dnd.qello.account.web.response.ProfileResponse;
import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;

import lombok.RequiredArgsConstructor;

/**
 * 본인 프로필 web 경계. 대상 계정은 경로나 본문이 아니라 언제나 JWT subject에서 결정한다 —
 * 다른 사용자의 프로필을 지정할 수 있는 입력이 존재하지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me/profile")
public class ProfileController implements ProfileApiSpec {

	private final ProfileService profileService;
	private final ApiResponseFactory responseFactory;

	@Override
	public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(Authentication authentication) {
		ProfileResponse response = ProfileResponse.from(
			profileService.getProfile(AuthenticatedUserId.require(authentication)));
		return ResponseEntity.ok(responseFactory.success(response));
	}

	@Override
	public ResponseEntity<ApiResponse<ProfileResponse>> changeProfileImage(
		ProfileImageChangeRequest request, Authentication authentication) {
		ProfileResponse response = ProfileResponse.from(profileService.changeProfileImage(
			AuthenticatedUserId.require(authentication), request.mediaId()));
		return ResponseEntity.ok(responseFactory.success(response));
	}

	@Override
	public ResponseEntity<ApiResponse<ProfileResponse>> removeProfileImage(Authentication authentication) {
		ProfileResponse response = ProfileResponse.from(
			profileService.removeProfileImage(AuthenticatedUserId.require(authentication)));
		return ResponseEntity.ok(responseFactory.success(response));
	}
}
