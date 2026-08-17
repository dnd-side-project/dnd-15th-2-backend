package com.dnd.qello.filtering.web;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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

// AppealController의 문서 계약. 작성자 본인만 호출할 수 있다.
@Tag(name = "콘텐츠 이의제기", description = "비공개 처리된 자신의 답변에 대한 이의제기 접수와 조회 (#112)")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface AppealApiSpec {

	@Operation(
		summary = "이의제기 접수",
		description = """
			BLOCK 판정으로 비공개 처리된 자신의 답변에 대해 이의를 제기합니다.
			접수는 콘텐츠의 공개 상태를 바꾸지 않습니다 — 검토가 끝날 때까지 비공개로 남습니다.

			접수 기간은 판정 시각으로부터 6개월입니다. 판정 시각을 신뢰할 수 없는 경우에는
			거절하지 않고 접수하며, 그 사실을 acceptanceReasonCode=WINDOW_UNVERIFIABLE로 남깁니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "이의제기를 접수했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "400",
			description = "이의제기를 지원하지 않는 대상 유형입니다. (FLT-VAL-005)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "앱 액세스 토큰이 유효하지 않습니다.",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "본인이 작성한 콘텐츠가 아닙니다. (FLT-DOM-015)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "filter decision을 찾을 수 없습니다. (FLT-DOM-017)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "이미 접수됐거나(FLT-INFRA-001), 접수 기간이 지났거나(FLT-DOM-013), 비공개 대상이 아닙니다(FLT-DOM-014).",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<AppealCaseResponse>> file(
		@RequestBody @Valid FileAppealRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "내 이의제기 목록 조회",
		description = "본인이 접수한 이의제기를 최신순으로 반환합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "앱 액세스 토큰이 유효하지 않습니다.",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping
	ResponseEntity<ApiResponse<List<AppealCaseResponse>>> findMine(
		@Parameter(hidden = true) Authentication authentication);
}
