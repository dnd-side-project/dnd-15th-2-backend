package com.dnd.qello.filtering.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.moderation.AppealCaseService;

// 이의제기 검토 API. SecurityConfiguration의
// backofficeSecurityFilterChain(/admin/**, hasRole("OPERATOR"))이 인가를 맡는다.
//
// 경로와 문서 애노테이션은 AppealCaseApiSpec에 있다.
@RestController
@RequestMapping("/admin/filtering/appeal-cases")
public class AppealCaseController implements AppealCaseApiSpec {

	private final AppealCaseService appealCaseService;
	private final ApiResponseFactory responseFactory;

	public AppealCaseController(AppealCaseService appealCaseService, ApiResponseFactory responseFactory) {
		this.appealCaseService = appealCaseService;
		this.responseFactory = responseFactory;
	}

	@Override
	public ResponseEntity<ApiResponse<List<AppealCaseResponse>>> findQueue(int limit) {
		List<AppealCaseResponse> queue = appealCaseService.findQueue(limit).stream()
			.map(AppealCaseResponse::from)
			.toList();
		return ResponseEntity.ok(responseFactory.success(queue));
	}

	@Override
	public ResponseEntity<ApiResponse<AppealCaseResponse>> decide(
		long appealCaseId, AppealDecisionRequest request, Authentication authentication
	) {
		AppealCase resolved = appealCaseService.decide(appealCaseId, request.decision(),
			operatorUserId(authentication), request.reason().toDomain());
		return ResponseEntity.ok(responseFactory.success(AppealCaseResponse.from(resolved)));
	}

	@Override
	public ResponseEntity<ApiResponse<AppealCaseResponse>> extendExpiry(
		long appealCaseId, ExtendAppealExpiryRequest request, Authentication authentication
	) {
		AppealCase extended = appealCaseService.extendExpiry(appealCaseId, request.expiresAt(),
			operatorUserId(authentication), request.reason().toDomain());
		return ResponseEntity.ok(responseFactory.success(AppealCaseResponse.from(extended)));
	}

	// OperatorLoginController가 로그인 시 String.valueOf(userId)를 principal 이름으로 심는다.
	private long operatorUserId(Authentication authentication) {
		return Long.parseLong(authentication.getName());
	}
}
