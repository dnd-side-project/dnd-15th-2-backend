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

// AppealCaseController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "필터링 이의제기 검토", description = "이의제기 큐 조회, 결정과 접수 기간 연장")
@SecurityRequirement(name = OpenApiConfiguration.OPERATOR_SESSION_SCHEME)
public interface AppealCaseApiSpec {

	@Operation(
		summary = "이의제기 검토 큐 조회",
		description = """
			운영자가 검토할 이의제기 큐를 조회합니다. 아직 종결되지 않은 이의제기만 포함합니다.

			운영자 세션이 필요합니다. 조회 요청은 CSRF 토큰 없이 호출할 수 있으며, limit으로 한 번에
			받을 최대 항목 수를 지정합니다.

			접수 시각이 빠른 이의제기부터 반환합니다. 검토할 항목이 없으면 빈 목록을 반환합니다.

			운영자 세션이 없거나 운영자 권한이 없으면 조회할 수 없습니다.

			이 API는 큐의 상태를 바꾸지 않으며, 결정 적용은 별도의 결정 API에서 수행합니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "이의제기 검토 큐를 조회했습니다."),
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
	ResponseEntity<ApiResponse<List<AppealCaseResponse>>> findQueue(
		@Parameter(description = "한 번에 반환할 최대 이의제기 수입니다.") @RequestParam(defaultValue = "50") int limit);

	@Operation(
		summary = "이의제기 결정 적용",
		description = """
			운영자가 이의제기를 비공개 유지(UPHOLD_HIDDEN) 또는 비공개 취소(OVERTURN_HIDDEN)로
			종결하고 결정 사유를 기록합니다.

			운영자 세션과 CSRF 토큰이 필요하며, 아직 종결되지 않은 이의제기만 결정할 수 있습니다.

			결정에 성공하면 이의제기가 RESOLVED 상태로 반환됩니다. 비공개 취소 결정이고 다른 공개
			금지 사유가 없으면 답변 복원을 요청하는 이벤트를 발행합니다.

			존재하지 않는 이의제기이거나 이미 종결된 이의제기면 결정할 수 없습니다. 다른 공개 금지
			사유가 남아 있으면 결정은 저장하지만 답변 복원 요청은 발행하지 않습니다.

			답변의 공개 상태를 실제로 바꾸는 작업은 이 API가 아니라 복원 요청을 처리하는 답변 기능이
			수행합니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이의제기를 종결했습니다."),
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
			description = "이의제기 건을 찾을 수 없습니다. (FLT-DOM-011)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "이미 종결된 이의제기 건입니다. (FLT-DOM-012)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/{appealCaseId}/decide", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<AppealCaseResponse>> decide(
		@Parameter(description = "결정을 적용할 이의제기 식별자입니다.") @PathVariable long appealCaseId,
		@RequestBody @Valid AppealDecisionRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "이의제기 접수 기간 연장",
		description = """
			운영자가 이의제기의 접수 만료 시각을 뒤로 미룹니다. 연장 사유도 함께 기록합니다.

			운영자 세션과 CSRF 토큰이 필요합니다. 현재 만료 시각을 확인한 뒤 그보다 늦은 시각을
			지정해야 합니다.

			연장에 성공하면 변경된 만료 시각을 포함한 이의제기를 반환합니다.

			존재하지 않는 이의제기이거나 현재 만료 시각과 같거나 이른 시각을 보내면 연장할 수 없습니다.

			이 API는 접수 기간을 줄이지 않고 뒤로 미루기만 합니다. 연장 사유는 운영 기록으로 남습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "접수 기간을 연장했습니다."),
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
			description = "이의제기 건을 찾을 수 없습니다. (FLT-DOM-011)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "접수 기간은 현재보다 늦은 시각으로만 연장할 수 있습니다. (FLT-DOM-016)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/{appealCaseId}/extend", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<AppealCaseResponse>> extendExpiry(
		@Parameter(description = "접수 기간을 연장할 이의제기 식별자입니다.") @PathVariable long appealCaseId,
		@RequestBody @Valid ExtendAppealExpiryRequest request,
		@Parameter(hidden = true) Authentication authentication);
}
