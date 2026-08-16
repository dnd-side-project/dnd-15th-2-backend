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
		description = "제안을 생성과 동시에 제출 상태로 전이합니다. 임시저장 단계는 없습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "제안을 제출했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "제안 문구가 정책에 맞지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 질문을 제안할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/proposals", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<QuestionProposalResponse>> submit(
		@RequestBody @Valid SubmitQuestionProposalRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "내가 제안한 질문 목록 조회",
		description = "인증 사용자가 제출한 모든 제안을 최신 제출 순으로 반환합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제안 목록을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/proposals/me")
	ResponseEntity<ApiResponse<List<QuestionProposalResponse>>> findMine(
		@Parameter(hidden = true) Authentication authentication);
}
