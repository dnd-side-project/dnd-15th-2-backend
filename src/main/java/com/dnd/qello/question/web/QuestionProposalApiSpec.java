package com.dnd.qello.question.web;

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
import com.dnd.qello.question.web.request.SubmitQuestionProposalRequest;
import com.dnd.qello.question.web.response.QuestionProposalResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "질문 제안", description = "사용자가 질문 문구를 제안하고 자신의 제안 이력을 조회")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface QuestionProposalApiSpec {

	@Operation(
		summary = "질문 제안 제출",
		description = """
		사용자가 질문 문구를 제안하고 즉시 제출합니다. 임시저장은 만들지 않습니다.

		앱 액세스 토큰이 필요합니다. 토큰의 계정은 ACTIVE 상태의 USER여야 하며, 제안 문구는
		요청 본문으로 보냅니다.

		성공하면 SUBMITTED 상태의 제안과 제출 시각을 반환합니다. 운영자 검수가 끝나야 승인 또는
		반려 상태로 바뀝니다.

		문구가 비어 있거나 2,000자를 초과하면 제출하지 않습니다. 계정을 찾을 수 없거나 질문
		제안을 사용할 수 없는 계정이면 거절합니다.

		이 호출만으로 질문이 앱에 배정되지는 않습니다. 운영자가 검수하고 승인해야 승인 질문
		풀에 추가됩니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "제안을 제출했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "제안 문구가 비어 있거나 2,000자를 초과했습니다. (CMN-VAL-001, QUE-VAL-002, QUE-VAL-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 없거나 유효하지 않습니다. (CMN-VAL-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 질문을 제안할 수 없습니다. (QUE-APP-004)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문을 제안할 사용자 계정을 찾을 수 없습니다. (QUE-APP-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/proposals", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<QuestionProposalResponse>> submit(
		@RequestBody @Valid SubmitQuestionProposalRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "내가 제안한 질문 목록 조회",
		description = """
		인증 사용자가 제출한 질문 제안 목록을 최신 생성 순으로 조회합니다.

		앱 액세스 토큰이 필요합니다. 토큰의 계정은 ACTIVE 상태의 USER여야 합니다.

		성공하면 본인이 제출한 제안을 createdAt 기준 내림차순으로 반환합니다. 제안이 없으면
		빈 목록을 반환합니다.

		계정을 찾을 수 없거나 질문 제안을 사용할 수 없는 계정이면 목록을 조회할 수 없습니다.

		다른 사용자의 제안은 포함하지 않습니다. 각 항목의 decisionReason은 반려된 제안에서만
		값이 있을 수 있습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제안 목록을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 없거나 유효하지 않습니다. (CMN-VAL-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 질문 제안을 사용할 수 없습니다. (QUE-APP-004)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문을 제안할 사용자 계정을 찾을 수 없습니다. (QUE-APP-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/proposals/me")
	ResponseEntity<ApiResponse<List<QuestionProposalResponse>>> findMine(
		@Parameter(hidden = true) Authentication authentication);
}
