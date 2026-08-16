package com.dnd.qello.question.web;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalReview;
import com.dnd.qello.question.service.QuestionReviewService;
import com.dnd.qello.question.web.request.ApproveQuestionProposalRequest;
import com.dnd.qello.question.web.request.RejectQuestionProposalRequest;
import com.dnd.qello.question.web.response.ApprovedQuestionResponse;
import com.dnd.qello.question.web.response.QuestionProposalResponse;
import com.dnd.qello.question.web.response.QuestionProposalReviewResponse;

// 질문 제안 검수 API. SecurityConfiguration의 backofficeSecurityFilterChain
// (/admin/**, hasRole("OPERATOR"))이 인가를 맡는다.
//
// 경로와 문서 애노테이션은 OperatorQuestionProposalApiSpec에 있다.
@RestController
@RequestMapping("/admin/questions")
public class OperatorQuestionProposalController implements OperatorQuestionProposalApiSpec {

	private final QuestionReviewService reviewService;
	private final ApiResponseFactory responseFactory;
	private final Clock clock;

	public OperatorQuestionProposalController(
		QuestionReviewService reviewService,
		ApiResponseFactory responseFactory,
		Clock clock
	) {
		this.reviewService = reviewService;
		this.responseFactory = responseFactory;
		this.clock = clock;
	}

	@Override
	public ResponseEntity<ApiResponse<QuestionProposalResponse>> startReview(long proposalId) {
		QuestionProposal proposal = reviewService.startReview(proposalId);
		return ResponseEntity.ok(responseFactory.success(QuestionProposalResponse.from(proposal)));
	}

	@Override
	public ResponseEntity<ApiResponse<ApprovedQuestionResponse>> approve(
		long proposalId, ApproveQuestionProposalRequest request, Authentication authentication) {
		Instant now = clock.instant();
		ApprovedQuestion approved = reviewService.approve(
			proposalId, operatorUserId(authentication), request.answerFormat(),
			request.activeFrom(), request.activeUntil(), now);
		return ResponseEntity.ok(responseFactory.success(ApprovedQuestionResponse.from(approved)));
	}

	@Override
	public ResponseEntity<ApiResponse<QuestionProposalReviewResponse>> reject(
		long proposalId, RejectQuestionProposalRequest request, Authentication authentication) {
		QuestionProposalReview review = reviewService.reject(
			proposalId, operatorUserId(authentication), request.reason(), clock.instant());
		return ResponseEntity.ok(responseFactory.success(QuestionProposalReviewResponse.from(review)));
	}

	// OperatorLoginController가 로그인 시 String.valueOf(userId)를 principal 이름으로 심는다.
	private long operatorUserId(Authentication authentication) {
		return Long.parseLong(authentication.getName());
	}
}
