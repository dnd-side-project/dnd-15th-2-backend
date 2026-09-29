package com.dnd.qello.filtering.web;

import jakarta.validation.Valid;

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

// SnapshotHealthController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "필터링 모델 상태", description = "검사 모델 장애 상태와 운영자 확정")
@SecurityRequirement(name = OpenApiConfiguration.OPERATOR_SESSION_SCHEME)
public interface SnapshotHealthApiSpec {

	@Operation(summary = "검사 모델을 영구 장애로 확정하기 (→ PERMANENT_CONFIRMED)", description = """
			영구 장애가 의심되는(PERMANENT_SUSPECTED) 검사 모델을 영구 장애로 확정합니다.

			운영자 로그인과 CSRF 토큰이 필요하고 사유(reasonCode, reasonText)를 함께 보내야 합니다.

			영구 장애 확정은 이 API로만 할 수 있고 확정한 뒤에는 자동으로 되돌아가지 않습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "PERMANENT_CONFIRMED로 전이했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "PERMANENT_SUSPECTED 상태가 아니어서 확정할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{modelSnapshot}/confirm-permanent")
	ResponseEntity<ApiResponse<SnapshotHealthResponse>> confirmPermanent(
			@Parameter(description = "영구 장애로 확정할 검사 모델 식별자입니다.") @PathVariable String modelSnapshot,
			@RequestBody @Valid OperatorReasonRequest request,
			@Parameter(hidden = true) Authentication authentication);
}
