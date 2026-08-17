package com.dnd.qello.answer.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.service.AnswerSubmissionApplicationService;
import com.dnd.qello.answer.web.request.SubmitAnswerRequest;
import com.dnd.qello.answer.web.response.AnswerSubmissionResponse;
import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;

import lombok.RequiredArgsConstructor;

/**
 * 답변 제출의 HTTP 경계(GitHub #125). 인증 사용자와 client intent를 application service에
 * 전달하고, 자격 검증·저장·moderation 접수는 application 계층에 위임한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/direction/inbox")
public class AnswerSubmissionController implements AnswerSubmissionApiSpec {

	private final AnswerSubmissionApplicationService applicationService;
	private final ApiResponseFactory responseFactory;

	@Override
	public ResponseEntity<ApiResponse<AnswerSubmissionResponse>> submit(
		long postRecipientId, String idempotencyKey, SubmitAnswerRequest request, Authentication authentication
	) {
		validateIdempotencyKey(idempotencyKey);
		long authorId = AuthenticatedUserId.require(authentication);
		Answer answer = applicationService.submit(
			authorId, idempotencyKey, postRecipientId, request.bodyText(), request.mediaIds());
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(responseFactory.success(AnswerSubmissionResponse.from(answer)));
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null) {
			throw new AnswerException(AnswerErrorCode.REQUIRED_VALUE_MISSING, "idempotencyKey", "Idempotency-Key는 필수입니다");
		}
		if (idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
			throw new AnswerException(AnswerErrorCode.INVALID_TEXT, "idempotencyKey", "Idempotency-Key는 1~200자여야 합니다");
		}
	}
}
