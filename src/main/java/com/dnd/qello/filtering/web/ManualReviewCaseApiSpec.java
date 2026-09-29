package com.dnd.qello.filtering.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

// ManualReviewCaseController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "필터링 수동 검토", description = "검토 큐 조회와 결정")
@SecurityRequirement(name = OpenApiConfiguration.OPERATOR_SESSION_SCHEME)
public interface ManualReviewCaseApiSpec {

	@Operation(summary = "검토자 큐 조회", description = """
			아직 종결되지 않은 수동 검토 건을 우선순위가 높은 순으로 조회합니다. 우선순위가 같으면 오래 기다린 건이 먼저 옵니다.

			운영자 로그인이 필요합니다.

			agingThresholdSeconds(초)보다 오래 기다린 STANDARD 건은 HIGH로 올려 정렬합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수동 검토 큐를 조회했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없으면 조회할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping
	ResponseEntity<ApiResponse<List<ManualReviewCaseResponse>>> findQueue(
			@Parameter(description = "STANDARD 건을 높은 우선순위로 볼 대기 시간(초)입니다.") @RequestParam long agingThresholdSeconds,
			@Parameter(description = "한 번에 반환할 최대 검토 건수입니다.") @RequestParam(defaultValue = "50") int limit);

	@Operation(summary = "수동 검토 결정 적용", description = """
			수동 검토 건을 ALLOW 또는 BLOCK으로 종결합니다.

			운영자 로그인과 CSRF 토큰이 필요하고 결정 사유(reason)를 함께 보내야 합니다.

			자동 판정 결과가 먼저 나온 건이면 운영자 결정 대신 그 결과로 종결합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수동 검토 건을 종결했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "수동 검토 건을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 수동 검토 건 상태에서는 결정할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{caseId}/decide")
	ResponseEntity<ApiResponse<ManualReviewCaseResponse>> decide(
			@Parameter(description = "결정을 적용할 수동 검토 건 식별자입니다.") @PathVariable long caseId,
			@RequestBody @Valid ManualReviewDecisionRequest request,
			Authentication authentication);
}
