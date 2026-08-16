package com.dnd.qello.feed.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.feed.service.InboxApplicationService;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.web.response.InboxCommandResponse;
import com.dnd.qello.feed.web.response.InboxDetailResponse;
import com.dnd.qello.feed.web.response.InboxListingResponse;

import lombok.RequiredArgsConstructor;

/** 인증 subject만 application 경계에 전달하는 방향 수신함 HTTP 어댑터다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/direction")
public class InboxController implements InboxApiSpec {

	private final InboxApplicationService applicationService;
	private final ApiResponseFactory responseFactory;

	@Override
	public ResponseEntity<ApiResponse<InboxListingResponse>> list(
		InboxCategory category, String directionSegmentKey, Authentication authentication) {
		long recipientId = AuthenticatedUserId.require(authentication);
		return ResponseEntity.ok(responseFactory.success(
			InboxListingResponse.from(applicationService.list(recipientId, category, directionSegmentKey))));
	}

	@Override
	public ResponseEntity<ApiResponse<InboxDetailResponse>> detail(
		long postRecipientId, Authentication authentication) {
		long recipientId = AuthenticatedUserId.require(authentication);
		return ResponseEntity.ok(responseFactory.success(
			InboxDetailResponse.from(applicationService.detail(recipientId, postRecipientId))));
	}

	@Override
	public ResponseEntity<ApiResponse<InboxCommandResponse>> skip(
		long postRecipientId, Authentication authentication) {
		long recipientId = AuthenticatedUserId.require(authentication);
		PostRecipient recipient = applicationService.skip(recipientId, postRecipientId);
		return ResponseEntity.ok(responseFactory.success(
			InboxCommandResponse.from(recipient, applicationService.revertibleUntil(recipient))));
	}

	@Override
	public ResponseEntity<ApiResponse<InboxCommandResponse>> revertSkip(
		long postRecipientId, Authentication authentication) {
		long recipientId = AuthenticatedUserId.require(authentication);
		PostRecipient recipient = applicationService.revertSkip(recipientId, postRecipientId);
		return ResponseEntity.ok(responseFactory.success(InboxCommandResponse.from(recipient, null)));
	}
}
