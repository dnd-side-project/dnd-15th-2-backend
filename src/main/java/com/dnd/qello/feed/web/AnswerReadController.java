package com.dnd.qello.feed.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.feed.service.FeedInteractionApplicationService;
import com.dnd.qello.feed.web.response.AnswersReadResponse;

import lombok.RequiredArgsConstructor;

/** 인증 subject만 application 경계에 전달하는 답변 읽음 처리 HTTP 어댑터다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/direction")
public class AnswerReadController implements AnswerReadApiSpec {

	private final FeedInteractionApplicationService applicationService;
	private final ApiResponseFactory responseFactory;

	@Override
	public ResponseEntity<ApiResponse<AnswersReadResponse>> markSenderAnswersRead(
		long postId, Authentication authentication) {
		long senderId = AuthenticatedUserId.require(authentication);
		return ResponseEntity.ok(responseFactory.success(
			new AnswersReadResponse(applicationService.markSenderAnswersRead(senderId, postId))));
	}

	@Override
	public ResponseEntity<ApiResponse<AnswersReadResponse>> markRecipientAnswersRead(
		long postRecipientId, Authentication authentication) {
		long recipientId = AuthenticatedUserId.require(authentication);
		return ResponseEntity.ok(responseFactory.success(
			new AnswersReadResponse(applicationService.markRecipientAnswersRead(recipientId, postRecipientId))));
	}
}
