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
			BLOCK 판정으로 비공개 처리된 답변에 이의제기를 접수합니다. 요청 본문으로 대상 유형,
			답변 식별자와 필터 판정 식별자를 함께 지정합니다.

			앱 액세스 토큰이 필요하며, 지정한 답변의 작성자만 접수할 수 있습니다. 현재 대상 유형은
			ANSWER만 지원합니다.

			접수에 성공하면 이의제기가 OPEN 상태로 저장되어 반환됩니다. 답변은 검토가 끝날 때까지
			비공개 상태로 유지됩니다.

			지원하지 않는 대상 유형, 다른 사용자의 답변, 존재하지 않는 필터 판정, BLOCK이 아닌 판정,
			이미 접수한 대상 또는 접수 기간이 지난 판정이면 접수할 수 없습니다.

			접수 기간은 판정 시각부터 6개월입니다. 판정 시각을 확인할 수 없으면 접수를 거절하지 않고
			acceptanceReasonCode에 WINDOW_UNVERIFIABLE을 기록합니다.""")
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
			description = "앱 액세스 토큰이 없거나 유효하지 않습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "본인이 작성한 답변에만 이의제기를 접수할 수 있습니다. (FLT-DOM-015)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "지정한 필터 판정을 찾을 수 없습니다. (FLT-DOM-017)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "이미 접수했거나(FLT-INFRA-001), 접수 기간이 지났거나(FLT-DOM-013), 비공개 대상이 아닙니다(FLT-DOM-014).",
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
		description = """
			본인이 접수한 이의제기 목록을 조회합니다.

			앱 액세스 토큰이 필요하며, 인증된 사용자가 접수한 이의제기만 반환합니다.

			목록은 최근 접수한 이의제기부터 반환합니다. 접수한 항목이 없으면 빈 목록을 반환합니다.

			토큰이 없거나 유효하지 않으면 조회할 수 없습니다.

			이 API는 이의제기 상태를 바꾸지 않고 목록만 조회합니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "앱 액세스 토큰이 없거나 유효하지 않습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping
	ResponseEntity<ApiResponse<List<AppealCaseResponse>>> findMine(
		@Parameter(hidden = true) Authentication authentication);
}
