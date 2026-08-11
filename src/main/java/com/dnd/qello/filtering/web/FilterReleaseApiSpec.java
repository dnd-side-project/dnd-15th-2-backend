package com.dnd.qello.filtering.web;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// FilterReleaseController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "필터링 release registry", description = "moderation release candidate 생성, 평가 단계 전환, 승격과 rollback")
@SecurityRequirement(name = "operatorSession")
public interface FilterReleaseApiSpec {

	@Operation(
		summary = "release candidate 생성",
		description = """
			정규화 규칙·로컬 사전·category mapping·model snapshot 참조를 묶어 새 candidate release를 만든다.

			이 시점에는 사용자 상태나 판정에 아무 영향을 주지 않는다. 승격 전까지는 비권위다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "201",
			description = "candidate release가 생성됐습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "400",
			description = "참조 값이 비어 있거나 \"latest\" alias입니다. (FLT-VAL-001, FLT-VAL-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping
	ResponseEntity<ApiResponse<FilterReleaseResponse>> create(@RequestBody @Valid CreateFilterReleaseRequest request);

	@Operation(summary = "release 목록 조회")
	@GetMapping
	ResponseEntity<ApiResponse<List<FilterReleaseResponse>>> findAll();

	@Operation(summary = "release 단건 조회")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "release를 찾을 수 없습니다. (FLT-DOM-005)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/{releaseId}")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> find(@Parameter(description = "release id") @PathVariable long releaseId);

	@Operation(
		summary = "offline evaluation 완료 처리",
		description = "합격 기준 판단은 이 시스템 밖에서 이뤄진다. 이 endpoint는 그 결과를 등록만 한다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OFFLINE_EVALUATED로 전이했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "CANDIDATE 상태가 아니어서 전이할 수 없습니다. (FLT-DOM-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/offline-evaluation")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> markOfflineEvaluated(@PathVariable long releaseId);

	@Operation(
		summary = "shadow 지정",
		description = "비권위 shadow 단계로 전이한다. 사용자 상태와 닉네임 동기 용량에 영향을 주지 않는다(INV-REL-007).")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SHADOW로 전이했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "OFFLINE_EVALUATED 상태가 아니어서 전이할 수 없습니다. (FLT-DOM-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/shadow")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> designateShadow(@PathVariable long releaseId);

	@Operation(
		summary = "canary 지정",
		description = "비권위 canary 단계로 전이한다. 사용자 상태와 닉네임 동기 용량에 영향을 주지 않는다(INV-REL-007).")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "CANARY로 전이했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "SHADOW 상태가 아니어서 전이할 수 없습니다. (FLT-DOM-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/canary")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> designateCanary(@PathVariable long releaseId);

	@Operation(
		summary = "release 승격",
		description = """
			CANARY를 통과한 candidate를 명시적으로 승격한다. 기존에 PROMOTED인 release가 있으면
			이 요청과 같은 트랜잭션에서 ROLLED_BACK으로 내린다. 이 endpoint를 호출하지 않으면
			어떤 release도 자동으로 승격되지 않는다(INV-REL-001, INV-REL-008).""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "PROMOTED로 전이했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "CANARY 상태가 아니어서 승격할 수 없습니다. (FLT-DOM-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/promote")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> promote(
		@PathVariable long releaseId, Authentication authentication);

	@Operation(
		summary = "release rollback",
		description = "이전에 PROMOTED였다가 ROLLED_BACK으로 내려간 release를 다시 승격한다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "다시 PROMOTED로 전이했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "ROLLED_BACK 상태가 아니어서 rollback 대상이 될 수 없습니다. (FLT-DOM-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/rollback")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> rollback(
		@PathVariable long releaseId, Authentication authentication);

}
