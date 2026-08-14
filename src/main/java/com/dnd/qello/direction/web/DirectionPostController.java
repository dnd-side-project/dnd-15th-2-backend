package com.dnd.qello.direction.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.direction.service.DirectionPostApplicationService;
import com.dnd.qello.direction.service.DirectionPostApplicationService.SubmitCommand;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.service.DirectionPreviewResult;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.web.request.SubmitDirectionPostRequest;
import com.dnd.qello.direction.web.response.DirectionPostSubmissionResponse;
import com.dnd.qello.direction.web.response.DirectionPreviewResponse;

import lombok.RequiredArgsConstructor;

/**
 * 방향 질문글의 HTTP 경계. 인증 사용자와 client intent를 application service에
 * 전달하고, 정책·위치·시각 계산은 application 계층에 위임한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/direction")
public class DirectionPostController implements DirectionPostApiSpec {

	private final DirectionPostApplicationService applicationService;
	private final ApiResponseFactory responseFactory;

	@Override
	public ResponseEntity<ApiResponse<DirectionPreviewResponse>> preview(Authentication authentication) {
		DirectionPreviewResult result = applicationService.preview(authenticatedUserId(authentication));
		return ResponseEntity.ok(responseFactory.success(DirectionPreviewResponse.from(result)));
	}

	@Override
	public ResponseEntity<ApiResponse<DirectionPostSubmissionResponse>> submit(
		String idempotencyKey, SubmitDirectionPostRequest request, Authentication authentication) {
		validateIdempotencyKey(idempotencyKey);
		long userId = authenticatedUserId(authentication);
		DirectionPostService.SendResult result = applicationService.submit(userId, idempotencyKey,
			new SubmitCommand(request.approvedQuestionId(), request.schemeId(), request.segmentKey(),
				request.bodyText(), request.mediaIds()));
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(responseFactory.success(DirectionPostSubmissionResponse.from(result)));
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null) {
			throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, "idempotencyKey",
				"Idempotency-Key는 필수입니다");
		}
		if (idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
			throw new DirectionException(DirectionErrorCode.INVALID_TEXT, "idempotencyKey",
				"Idempotency-Key는 1~200자여야 합니다");
		}
	}

	private long authenticatedUserId(Authentication authentication) {
		return AuthenticatedUserId.require(authentication);
	}
}
