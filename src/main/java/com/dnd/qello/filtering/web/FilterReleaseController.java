package com.dnd.qello.filtering.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;

// moderation release registry 조회·승격 API. SecurityConfiguration의
// backofficeSecurityFilterChain(/admin/**, hasRole("OPERATOR"))이 인가를 맡는다.
//
// 경로와 문서 애노테이션은 FilterReleaseApiSpec에 있다.
@RestController
@RequestMapping("/admin/filtering/releases")
public class FilterReleaseController implements FilterReleaseApiSpec {

	private final FilterReleaseRegistryService registryService;
	private final ApiResponseFactory responseFactory;

	public FilterReleaseController(
		FilterReleaseRegistryService registryService,
		ApiResponseFactory responseFactory
	) {
		this.registryService = registryService;
		this.responseFactory = responseFactory;
	}

	@Override
	public ResponseEntity<ApiResponse<FilterReleaseResponse>> create(CreateFilterReleaseRequest request) {
		FilterRelease created = registryService.createCandidate(
			request.normalizationRef(), request.localRulesetRef(), request.categoryMappingRef(), request.modelSnapshot());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(responseFactory.success(FilterReleaseResponse.from(created)));
	}

	@Override
	public ResponseEntity<ApiResponse<List<FilterReleaseResponse>>> findAll() {
		List<FilterReleaseResponse> releases = registryService.findAll().stream()
			.map(FilterReleaseResponse::from)
			.toList();
		return ResponseEntity.ok(responseFactory.success(releases));
	}

	@Override
	public ResponseEntity<ApiResponse<FilterReleaseResponse>> find(long releaseId) {
		return ResponseEntity.ok(responseFactory.success(FilterReleaseResponse.from(registryService.find(releaseId))));
	}

	@Override
	public ResponseEntity<ApiResponse<FilterReleaseResponse>> markOfflineEvaluated(
		long releaseId, OperatorReasonRequest request, Authentication authentication
	) {
		FilterRelease release = registryService.markOfflineEvaluated(
			releaseId, operatorUserId(authentication), request.toDomain());
		return ResponseEntity.ok(responseFactory.success(FilterReleaseResponse.from(release)));
	}

	@Override
	public ResponseEntity<ApiResponse<FilterReleaseResponse>> designateShadow(
		long releaseId, OperatorReasonRequest request, Authentication authentication
	) {
		FilterRelease release = registryService.designateShadow(
			releaseId, operatorUserId(authentication), request.toDomain());
		return ResponseEntity.ok(responseFactory.success(FilterReleaseResponse.from(release)));
	}

	@Override
	public ResponseEntity<ApiResponse<FilterReleaseResponse>> designateCanary(
		long releaseId, OperatorReasonRequest request, Authentication authentication
	) {
		FilterRelease release = registryService.designateCanary(
			releaseId, operatorUserId(authentication), request.toDomain());
		return ResponseEntity.ok(responseFactory.success(FilterReleaseResponse.from(release)));
	}

	@Override
	public ResponseEntity<ApiResponse<FilterReleaseResponse>> promote(
		long releaseId, OperatorReasonRequest request, Authentication authentication
	) {
		FilterRelease release = registryService.promote(
			releaseId, operatorUserId(authentication), request.toDomain());
		return ResponseEntity.ok(responseFactory.success(FilterReleaseResponse.from(release)));
	}

	@Override
	public ResponseEntity<ApiResponse<FilterReleaseResponse>> rollback(
		long releaseId, OperatorReasonRequest request, Authentication authentication
	) {
		FilterRelease release = registryService.rollback(
			releaseId, operatorUserId(authentication), request.toDomain());
		return ResponseEntity.ok(responseFactory.success(FilterReleaseResponse.from(release)));
	}

	// OperatorLoginController가 로그인 시 String.valueOf(userId)를 principal 이름으로 심는다.
	private long operatorUserId(Authentication authentication) {
		return Long.parseLong(authentication.getName());
	}

}
