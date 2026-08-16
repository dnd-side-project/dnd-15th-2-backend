package com.dnd.qello.filtering.web;

import java.time.Duration;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.moderation.ManualReviewDecisionService;

// 수동 검토 case 큐 조회·결정 API. SecurityConfiguration의
// backofficeSecurityFilterChain(/admin/**, hasRole("OPERATOR"))이 인가를 맡는다.
//
// 경로와 문서 애노테이션은 ManualReviewCaseApiSpec에 있다.
@RestController
@RequestMapping("/admin/filtering/manual-review-cases")
public class ManualReviewCaseController implements ManualReviewCaseApiSpec {

	private final ManualReviewDecisionService manualReviewDecisionService;
	private final ApiResponseFactory responseFactory;

	public ManualReviewCaseController(
		ManualReviewDecisionService manualReviewDecisionService, ApiResponseFactory responseFactory
	) {
		this.manualReviewDecisionService = manualReviewDecisionService;
		this.responseFactory = responseFactory;
	}

	@Override
	public ResponseEntity<ApiResponse<List<ManualReviewCaseResponse>>> findQueue(long agingThresholdSeconds, int limit) {
		List<ManualReviewCaseResponse> queue = manualReviewDecisionService
			.findQueue(Duration.ofSeconds(agingThresholdSeconds), limit)
			.stream()
			.map(ManualReviewCaseResponse::from)
			.toList();
		return ResponseEntity.ok(responseFactory.success(queue));
	}

	@Override
	public ResponseEntity<ApiResponse<ManualReviewCaseResponse>> decide(
		long caseId, ManualReviewDecisionRequest request, Authentication authentication
	) {
		ManualReviewCase resolved =
			manualReviewDecisionService.decide(caseId, request.verdict(), operatorUserId(authentication));
		return ResponseEntity.ok(responseFactory.success(ManualReviewCaseResponse.from(resolved)));
	}

	// OperatorLoginController가 로그인 시 String.valueOf(userId)를 principal 이름으로 심는다.
	private long operatorUserId(Authentication authentication) {
		return Long.parseLong(authentication.getName());
	}
}
