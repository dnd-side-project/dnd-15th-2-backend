package com.dnd.qello.question.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.service.QuestionProposalApplicationService;
import com.dnd.qello.question.web.request.SubmitQuestionProposalRequest;
import com.dnd.qello.question.web.response.QuestionProposalResponse;

/**
 * 질문 제안의 HTTP 경계. 인증 사용자 식별만 여기서 하고, 계정 자격과 상태
 * 전이는 {@link QuestionProposalApplicationService}에 위임한다.
 */
@RestController
@RequestMapping("/api/v1/questions")
public class QuestionProposalController implements QuestionProposalApiSpec {

	private final QuestionProposalApplicationService applicationService;
	private final ApiResponseFactory responseFactory;

	public QuestionProposalController(
		QuestionProposalApplicationService applicationService,
		ApiResponseFactory responseFactory
	) {
		this.applicationService = applicationService;
		this.responseFactory = responseFactory;
	}

	@Override
	public ResponseEntity<ApiResponse<QuestionProposalResponse>> submit(
		SubmitQuestionProposalRequest request, Authentication authentication) {
		QuestionProposal proposal = applicationService.submit(
			AuthenticatedUserId.require(authentication), request.proposedText());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(responseFactory.success(QuestionProposalResponse.from(proposal)));
	}

	@Override
	public ResponseEntity<ApiResponse<List<QuestionProposalResponse>>> findMine(Authentication authentication) {
		List<QuestionProposalResponse> proposals = applicationService.findMine(
				AuthenticatedUserId.require(authentication)).stream()
			.map(QuestionProposalResponse::from)
			.toList();
		return ResponseEntity.ok(responseFactory.success(proposals));
	}
}
