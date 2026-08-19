package com.dnd.qello.feed.web;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.feed.service.FeedInteractionApplicationService;
import com.dnd.qello.feed.view.SentPostFilter;
import com.dnd.qello.feed.web.response.AnswerListingResponse;
import com.dnd.qello.feed.web.response.SentPostDetailResponse;
import com.dnd.qello.feed.web.response.SentPostListingResponse;

import lombok.RequiredArgsConstructor;

/** 인증 subject만 application 경계에 전달하는 `내가 보낸 질문` HTTP 어댑터다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/direction")
public class SentPostController implements SentPostApiSpec {

	private final FeedInteractionApplicationService applicationService;
	private final ApiResponseFactory responseFactory;

	@Override
	public ResponseEntity<ApiResponse<SentPostListingResponse>> list(
		SentPostFilter filter, Instant cursorSubmittedAt, Long cursorPostId, int limit, Authentication authentication) {
		long senderId = AuthenticatedUserId.require(authentication);
		var cards = applicationService.listSentPosts(senderId, filter, cursorSubmittedAt, cursorPostId, limit);
		return ResponseEntity.ok(responseFactory.success(SentPostListingResponse.from(cards, limit)));
	}

	@Override
	public ResponseEntity<ApiResponse<SentPostDetailResponse>> detail(long postId, Authentication authentication) {
		long senderId = AuthenticatedUserId.require(authentication);
		return ResponseEntity.ok(responseFactory.success(
			SentPostDetailResponse.from(applicationService.sentPostDetail(senderId, postId))));
	}

	@Override
	public ResponseEntity<ApiResponse<AnswerListingResponse>> answers(
		long postId, Instant cursorPublishedAt, Long cursorAnswerId, int limit, Authentication authentication) {
		long viewerId = AuthenticatedUserId.require(authentication);
		var cards = applicationService.answers(viewerId, postId, cursorPublishedAt, cursorAnswerId, limit);
		return ResponseEntity.ok(responseFactory.success(AnswerListingResponse.from(cards, limit)));
	}
}
