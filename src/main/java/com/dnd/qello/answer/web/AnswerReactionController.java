package com.dnd.qello.answer.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.feed.service.FeedInteractionApplicationService;
import com.dnd.qello.feed.web.response.ReactionResponse;

import lombok.RequiredArgsConstructor;

/** 인증 subject만 application 경계에 전달하는 답변 공감 HTTP 어댑터다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/direction")
public class AnswerReactionController implements AnswerReactionApiSpec {

	private final FeedInteractionApplicationService applicationService;
	private final ApiResponseFactory responseFactory;

	@Override
	public ResponseEntity<ApiResponse<ReactionResponse>> react(long answerId, Authentication authentication) {
		long reactorId = AuthenticatedUserId.require(authentication);
		long reactionCount = applicationService.reactToAnswer(answerId, reactorId);
		return ResponseEntity.ok(responseFactory.success(new ReactionResponse(true, reactionCount)));
	}

	@Override
	public ResponseEntity<ApiResponse<ReactionResponse>> cancel(long answerId, Authentication authentication) {
		long reactorId = AuthenticatedUserId.require(authentication);
		long reactionCount = applicationService.cancelAnswerReaction(answerId, reactorId);
		return ResponseEntity.ok(responseFactory.success(new ReactionResponse(false, reactionCount)));
	}
}
