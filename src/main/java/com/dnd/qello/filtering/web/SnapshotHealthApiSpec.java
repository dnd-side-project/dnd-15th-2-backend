package com.dnd.qello.filtering.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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

// SnapshotHealthController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "필터링 모델 상태",
	description = "모델 snapshot 장애 상태와 운영자 확정")
@SecurityRequirement(name = OpenApiConfiguration.OPERATOR_SESSION_SCHEME)
public interface SnapshotHealthApiSpec {

	@Operation(
		summary = "모델 snapshot을 영구 장애로 확정하기 (→ PERMANENT_CONFIRMED)",
		description = """
			운영자가 반복적인 장애가 확인된 모델 snapshot을 영구 장애 상태로 확정합니다.

			운영자 세션과 CSRF 토큰이 필요하며, 변경 사유를 함께 보내야 합니다. PERMANENT_SUSPECTED
			상태의 snapshot만 확정할 수 있습니다.

			확정에 성공하면 PERMANENT_CONFIRMED 상태와 확정 시각·운영자 식별자를 반환합니다.

			snapshot이 아직 의심 상태가 아니면 확정할 수 없습니다.

			자동 probe는 이 상태로 확정하지 않으며, 영구 장애 확정은 이 운영자 API에서만 수행합니다.
			확정 이후 자동 경로가 상태를 되돌리지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200", description = "PERMANENT_CONFIRMED로 전이했습니다."),
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
			responseCode = "409",
			description = "PERMANENT_SUSPECTED 상태가 아니어서 확정할 수 없습니다. (FLT-DOM-007)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{modelSnapshot}/confirm-permanent")
	ResponseEntity<ApiResponse<SnapshotHealthResponse>> confirmPermanent(
		@Parameter(description = "OpenAI moderation model snapshot 식별자") @PathVariable String modelSnapshot,
		@RequestBody @Valid OperatorReasonRequest request,
		@Parameter(hidden = true) Authentication authentication);
}
