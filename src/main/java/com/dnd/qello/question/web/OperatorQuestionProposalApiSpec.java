package com.dnd.qello.question.web;

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
import com.dnd.qello.question.web.request.ApproveQuestionProposalRequest;
import com.dnd.qello.question.web.request.RejectQuestionProposalRequest;
import com.dnd.qello.question.web.response.ApprovedQuestionResponse;
import com.dnd.qello.question.web.response.QuestionProposalResponse;
import com.dnd.qello.question.web.response.QuestionProposalReviewResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

// OperatorQuestionProposalController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "질문 제안 검토", description = "운영자의 질문 제안 검수 시작, 승인, 반려")
@SecurityRequirement(name = OpenApiConfiguration.OPERATOR_SESSION_SCHEME)
public interface OperatorQuestionProposalApiSpec {

	@Operation(summary = "제안 검수 시작", description = """
			제출된 질문 제안(SUBMITTED)의 검수를 시작해 UNDER_REVIEW 상태로 바꿉니다.

			운영자 로그인과 CSRF 토큰이 필요합니다.

			검수를 시작한 제안만 승인하거나 반려할 수 있습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검수를 시작했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 만료되었습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문 제안을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "제안이 SUBMITTED 상태가 아니어서 검수를 시작할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/proposals/{proposalId}/review")
	ResponseEntity<ApiResponse<QuestionProposalResponse>> startReview(
			@Parameter(description = "검수를 시작할 질문 제안 식별자입니다.") @PathVariable long proposalId);

	@Operation(summary = "제안 승인", description = """
			검수 중인 질문 제안을 승인하고 질문으로 등록합니다.

			운영자 로그인과 CSRF 토큰이 필요합니다. 답변 형식(answerFormat)과 활성 시작 시각(activeFrom)을 함께 보내야 합니다.

			활성 시작 시각이 미래면 그 시각부터 질문으로 쓰입니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "승인 질문을 생성했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "답변 형식·활성 시작 시각이 없거나 활성 기간의 순서가 올바르지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 만료되었습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문 제안을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "제안이 UNDER_REVIEW 상태가 아니어서 승인할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/proposals/{proposalId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ApprovedQuestionResponse>> approve(
			@Parameter(description = "승인할 질문 제안 식별자입니다.") @PathVariable long proposalId,
			@RequestBody @Valid ApproveQuestionProposalRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "제안 반려", description = """
			검수 중인 질문 제안을 반려하고 반려 사유를 기록합니다.

			운영자 로그인과 CSRF 토큰이 필요합니다.

			반려 사유는 제안한 사용자에게 보일 수 있습니다. 반려한 제안은 다시 검수 상태로 되돌릴 수 없습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "반려 판정을 기록했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "반려 사유가 비어 있거나 2,000자를 초과했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 만료되었습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문 제안을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "제안이 UNDER_REVIEW 상태가 아니어서 반려할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/proposals/{proposalId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<QuestionProposalReviewResponse>> reject(
			@Parameter(description = "반려할 질문 제안 식별자입니다.") @PathVariable long proposalId,
			@RequestBody @Valid RejectQuestionProposalRequest request,
			@Parameter(hidden = true) Authentication authentication);
}
