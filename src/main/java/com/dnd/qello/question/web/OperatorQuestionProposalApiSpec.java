package com.dnd.qello.question.web;

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
import jakarta.validation.Valid;

// OperatorQuestionProposalController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "질문 제안 검토", description = "사용자 제안을 검수 시작, 승인, 반려하는 운영자 전용 API")
@SecurityRequirement(name = OpenApiConfiguration.OPERATOR_SESSION_SCHEME)
public interface OperatorQuestionProposalApiSpec {

	@Operation(
		summary = "제안 검수 시작",
		description = "SUBMITTED 제안을 UNDER_REVIEW로 전이합니다. 승인·반려 전 반드시 거쳐야 합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검수를 시작했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제안을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "SUBMITTED 상태가 아니어서 검수를 시작할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/proposals/{proposalId}/review")
	ResponseEntity<ApiResponse<QuestionProposalResponse>> startReview(
		@Parameter(description = "질문 제안 id") @PathVariable long proposalId);

	@Operation(
		summary = "제안 승인",
		description = "UNDER_REVIEW 제안을 승인해 승인 질문 풀에 활성 질문으로 추가합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "승인 질문을 생성했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "answerFormat 또는 활성 기간이 정책에 맞지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제안을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "UNDER_REVIEW 상태가 아니어서 승인할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/proposals/{proposalId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ApprovedQuestionResponse>> approve(
		@Parameter(description = "질문 제안 id") @PathVariable long proposalId,
		@RequestBody @Valid ApproveQuestionProposalRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "제안 반려",
		description = "UNDER_REVIEW 제안을 반려하고 사유를 append-only 이력으로 남깁니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "반려 판정을 기록했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "reason이 비어 있습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제안을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "UNDER_REVIEW 상태가 아니어서 반려할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/proposals/{proposalId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<QuestionProposalReviewResponse>> reject(
		@Parameter(description = "질문 제안 id") @PathVariable long proposalId,
		@RequestBody @Valid RejectQuestionProposalRequest request,
		@Parameter(hidden = true) Authentication authentication);
}
