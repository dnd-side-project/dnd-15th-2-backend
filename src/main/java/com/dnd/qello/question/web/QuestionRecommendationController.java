package com.dnd.qello.question.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.question.service.QuestionRecommendationService;
import com.dnd.qello.question.web.response.RecommendedQuestionResponse;

import lombok.RequiredArgsConstructor;

/**
 * 추천 질문 조회의 HTTP 경계. 계정 자격과 질문 선택은 {@link QuestionRecommendationService}에 위임한다.
 */
@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionRecommendationController implements QuestionRecommendationApiSpec {

	private final QuestionRecommendationService recommendationService;
	private final ApiResponseFactory responseFactory;

	@Override
	public ResponseEntity<ApiResponse<List<RecommendedQuestionResponse>>> findRecommendations(
			Authentication authentication) {
		List<RecommendedQuestionResponse> questions = recommendationService.findRecommendations(
				AuthenticatedUserId.require(authentication)).stream()
				.map(RecommendedQuestionResponse::from)
				.toList();
		return ResponseEntity.ok(responseFactory.success(questions));
	}
}
