package com.dnd.qello.filtering.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

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
@Tag(name = "필터링 snapshot health",
	description = "OpenAI moderation snapshot 단위 health 상태와 PERMANENT_CONFIRMED 운영자 승인")
@SecurityRequirement(name = "operatorSession")
public interface SnapshotHealthApiSpec {

	@Operation(
		summary = "snapshot PERMANENT_CONFIRMED 승인",
		description = """
			PERMANENT_SUSPECTED 상태의 snapshot을 운영자가 명시적으로 영구 장애로 확정한다.
			이 승인 없이는 어떤 자동 경로도 PERMANENT_CONFIRMED에 도달하거나 emergency
			migration을 실행할 수 없다(INV-HLT-005).""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200", description = "PERMANENT_CONFIRMED로 전이했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "PERMANENT_SUSPECTED 상태가 아니어서 승인할 수 없습니다. (FLT-DOM-007)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{modelSnapshot}/confirm-permanent")
	ResponseEntity<ApiResponse<SnapshotHealthResponse>> confirmPermanent(
		@Parameter(description = "OpenAI moderation model snapshot 식별자") @PathVariable String modelSnapshot,
		Authentication authentication);
}
