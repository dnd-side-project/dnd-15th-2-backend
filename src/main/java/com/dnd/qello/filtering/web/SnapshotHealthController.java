package com.dnd.qello.filtering.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.filtering.domain.SnapshotHealth;
import com.dnd.qello.filtering.service.SnapshotHealthService;

// snapshot health 운영자 승인 API. SecurityConfiguration의
// backofficeSecurityFilterChain(/admin/**, hasRole("OPERATOR"))이 인가를 맡는다.
//
// 경로와 문서 애노테이션은 SnapshotHealthApiSpec에 있다.
@RestController
@RequestMapping("/admin/filtering/snapshot-health")
public class SnapshotHealthController implements SnapshotHealthApiSpec {

	private final SnapshotHealthService snapshotHealthService;
	private final ApiResponseFactory responseFactory;

	public SnapshotHealthController(SnapshotHealthService snapshotHealthService, ApiResponseFactory responseFactory) {
		this.snapshotHealthService = snapshotHealthService;
		this.responseFactory = responseFactory;
	}

	@Override
	public ResponseEntity<ApiResponse<SnapshotHealthResponse>> confirmPermanent(
		String modelSnapshot, OperatorReasonRequest request, Authentication authentication
	) {
		SnapshotHealth confirmed = snapshotHealthService.confirmPermanent(
			modelSnapshot, operatorUserId(authentication), request.toDomain());
		return ResponseEntity.ok(responseFactory.success(SnapshotHealthResponse.from(confirmed)));
	}

	// OperatorLoginController가 로그인 시 String.valueOf(userId)를 principal 이름으로 심는다.
	private long operatorUserId(Authentication authentication) {
		return Long.parseLong(authentication.getName());
	}
}
