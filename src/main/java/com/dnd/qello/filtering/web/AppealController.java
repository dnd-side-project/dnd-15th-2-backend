package com.dnd.qello.filtering.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.moderation.AppealCaseService;

// 작성자용 이의제기 API. SecurityConfiguration의 appApiSecurityFilterChain
// (/api/**, JWT)이 인증을 맡고, 작성자 본인 여부는 AppealCaseService가
// AppealTargetOwnershipChecker로 확인한다.
//
// 경로와 문서 애노테이션은 AppealApiSpec에 있다.
@RestController
@RequestMapping("/api/v1/filtering/appeals")
public class AppealController implements AppealApiSpec {

	private final AppealCaseService appealCaseService;
	private final ApiResponseFactory responseFactory;

	public AppealController(AppealCaseService appealCaseService, ApiResponseFactory responseFactory) {
		this.appealCaseService = appealCaseService;
		this.responseFactory = responseFactory;
	}

	@Override
	public ResponseEntity<ApiResponse<AppealCaseResponse>> file(
		FileAppealRequest request, Authentication authentication
	) {
		AppealCase filed = appealCaseService.file(request.targetType(), request.targetId(),
			request.filterDecisionId(), AuthenticatedUserId.require(authentication));
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(responseFactory.success(AppealCaseResponse.from(filed)));
	}

	@Override
	public ResponseEntity<ApiResponse<List<AppealCaseResponse>>> findMine(Authentication authentication) {
		List<AppealCaseResponse> appeals = appealCaseService
			.findMine(AuthenticatedUserId.require(authentication)).stream()
			.map(AppealCaseResponse::from)
			.toList();
		return ResponseEntity.ok(responseFactory.success(appeals));
	}
}
