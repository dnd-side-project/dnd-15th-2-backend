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
import com.dnd.qello.feed.web.response.AnswerListingResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/direction")
public class PostAnswerController implements PostAnswerApiSpec {

	private final FeedInteractionApplicationService applicationService;
	private final ApiResponseFactory responseFactory;

	@Override
	public ResponseEntity<ApiResponse<AnswerListingResponse>> answers(
			long postId, Instant cursorPublishedAt, Long cursorAnswerId, int limit, Authentication authentication) {
		long viewerId = AuthenticatedUserId.require(authentication);
		var cards = applicationService.answers(viewerId, postId, cursorPublishedAt, cursorAnswerId, limit);
		return ResponseEntity.ok(responseFactory.success(AnswerListingResponse.from(cards, limit)));
	}
}
