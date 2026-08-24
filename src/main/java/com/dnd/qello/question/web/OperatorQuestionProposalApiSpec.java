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
		description = """
		운영자가 제출된 질문 제안을 검수 중인 상태로 전환합니다.

		운영자 세션과 CSRF 토큰이 필요합니다. 검수 대상 제안은 사용자가 먼저 제출한 상태여야
		합니다.

		성공하면 제안 상태를 UNDER_REVIEW로 바꾼 제안 정보를 반환합니다.

		제안 식별자가 없거나 이미 검수·승인·반려된 상태이면 검수를 시작하지 않습니다.

		검수를 시작한 뒤에만 승인 또는 반려 API를 호출할 수 있습니다. 이 API 자체는 승인이나
		반려 판정을 기록하지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검수를 시작했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 만료되었습니다. (CMN-VAL-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문 제안을 찾을 수 없습니다. (QUE-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "제안이 SUBMITTED 상태가 아니어서 검수를 시작할 수 없습니다. (QUE-DOM-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/proposals/{proposalId}/review")
	ResponseEntity<ApiResponse<QuestionProposalResponse>> startReview(
		@Parameter(description = "검수를 시작할 질문 제안 식별자입니다.") @PathVariable long proposalId);

	@Operation(
		summary = "제안 승인",
		description = """
		운영자가 검수 중인 질문 제안을 승인하고 활성 질문 풀에 추가합니다.

		운영자 세션과 CSRF 토큰이 필요합니다. 승인할 제안은 먼저 검수를 시작한 상태여야 하며,
		허용할 답변 형식과 활성 시작 시각을 함께 보내야 합니다.

		성공하면 승인 질문 식별자·문구·답변 형식·활성 기간과 승인 정보를 반환합니다.

		제안을 찾을 수 없거나 검수 중인 상태가 아니면 승인하지 않습니다. 활성 종료 시각을
		보냈다면 시작 시각보다 늦어야 합니다.

		승인하면 제안 상태와 승인 질문이 함께 기록됩니다. 활성 시작 시각이 현재보다 미래라면
		즉시 배정되는 것이 아니라 해당 시각부터 배정 대상이 됩니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "승인 질문을 생성했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "답변 형식·활성 시작 시각이 없거나 활성 기간의 순서가 올바르지 않습니다. (CMN-VAL-001, QUE-VAL-002, QUE-VAL-004)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 만료되었습니다. (CMN-VAL-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문 제안을 찾을 수 없습니다. (QUE-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "제안이 UNDER_REVIEW 상태가 아니어서 승인할 수 없습니다. (QUE-DOM-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/proposals/{proposalId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ApprovedQuestionResponse>> approve(
		@Parameter(description = "승인할 질문 제안 식별자입니다.") @PathVariable long proposalId,
		@RequestBody @Valid ApproveQuestionProposalRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "제안 반려",
		description = """
		운영자가 검수 중인 질문 제안을 반려하고 반려 사유를 판정 이력으로 기록합니다.

		운영자 세션과 CSRF 토큰이 필요합니다. 반려할 제안은 먼저 검수를 시작한 상태여야 하며,
		제안자에게 전달될 수 있는 사유를 요청 본문으로 보냅니다.

		성공하면 반려 판정, 사유, 판정 시각과 운영자 식별자를 반환합니다.

		제안을 찾을 수 없거나 검수 중인 상태가 아니면 반려하지 않습니다. 사유가 비어 있거나
		2,000자를 초과해도 요청을 처리하지 않습니다.

		판정 이력은 수정하지 않고 추가 기록으로 남깁니다. 반려된 제안을 이 API로 다시 검수
		상태로 되돌리는 경로는 없습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "반려 판정을 기록했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "반려 사유가 비어 있거나 2,000자를 초과했습니다. (CMN-VAL-001, QUE-VAL-002, QUE-VAL-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 만료되었습니다. (CMN-VAL-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문 제안을 찾을 수 없습니다. (QUE-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "제안이 UNDER_REVIEW 상태가 아니어서 반려할 수 없습니다. (QUE-DOM-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/proposals/{proposalId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<QuestionProposalReviewResponse>> reject(
		@Parameter(description = "반려할 질문 제안 식별자입니다.") @PathVariable long proposalId,
		@RequestBody @Valid RejectQuestionProposalRequest request,
		@Parameter(hidden = true) Authentication authentication);
}
