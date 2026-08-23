package com.dnd.qello.filtering.web;

import java.util.List;

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
import jakarta.validation.Valid;

// ManualReviewCaseController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "필터링 수동 검토", description = "검토 큐 조회와 결정")
@SecurityRequirement(name = OpenApiConfiguration.OPERATOR_SESSION_SCHEME)
public interface ManualReviewCaseApiSpec {

	@Operation(
		summary = "검토자 큐 조회",
		description = """
			아직 종결되지 않은 수동 검토 건을 우선순위 큐로 조회합니다.

			운영자 세션이 필요합니다. agingThresholdSeconds로 STANDARD 건을 HIGH 우선순위로 볼 대기
			시간을 지정하고, limit으로 반환할 최대 건수를 지정합니다.

			높은 우선순위 건을 먼저 반환하고, 같은 우선순위에서는 오래 열린 건부터 반환합니다. 대상이
			없으면 빈 목록을 반환합니다.

			운영자 세션이 없거나 운영자 권한이 없으면 조회할 수 없습니다.

			대기 시간에 따른 우선순위는 조회할 때만 계산하며 저장된 case의 band를 바꾸지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "수동 검토 큐를 조회했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "운영자 세션이 없거나 유효하지 않습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "운영자 권한이 없으면 조회할 수 없습니다. (CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping
	ResponseEntity<ApiResponse<List<ManualReviewCaseResponse>>> findQueue(
		@Parameter(description = "STANDARD 건을 높은 우선순위로 볼 대기 시간(초)입니다.") @RequestParam long agingThresholdSeconds,
		@Parameter(description = "한 번에 반환할 최대 검토 건수입니다.") @RequestParam(defaultValue = "50") int limit);

	@Operation(
		summary = "수동 검토 결정 적용",
		description = """
			운영자가 수동 검토 건을 ALLOW 또는 BLOCK으로 종결하고 결정 사유를 기록합니다.

			운영자 세션과 CSRF 토큰이 필요합니다. 아직 종결되지 않은 검토 건을 대상으로 결정합니다.

			성공하면 RESOLVED 상태와 결정 결과를 포함한 검토 건을 반환합니다.

			검토 건이 없거나 이미 종결된 검토 건이면 결정할 수 없습니다. 자동 결과가 먼저 도착한
			경우에는 자동 결과를 유지하고 운영자 결정으로 덮어쓰지 않습니다.

			이 API는 검토 건과 연결된 필터 작업의 최종 결과를 기록합니다. 자동 결과가 이미 확정된
			경우에는 그 결과를 유지합니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "case가 종료됐습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "운영자 세션이 없거나 유효하지 않습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "수동 검토 건을 찾을 수 없습니다. (FLT-DOM-010)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "현재 수동 검토 건 상태에서는 결정할 수 없습니다. (FLT-DOM-009)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{caseId}/decide")
	ResponseEntity<ApiResponse<ManualReviewCaseResponse>> decide(
		@Parameter(description = "결정을 적용할 수동 검토 건 식별자입니다.") @PathVariable long caseId,
		@RequestBody @Valid ManualReviewDecisionRequest request,
		Authentication authentication);
}
