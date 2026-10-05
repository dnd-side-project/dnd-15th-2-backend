package com.dnd.qello.question.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.question.web.request.ChangeProposalNotificationRequest;
import com.dnd.qello.question.web.request.SubmitQuestionProposalRequest;
import com.dnd.qello.question.web.response.QuestionProposalResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "질문 제안", description = "질문 문구 제안, 내 제안 목록 조회, 제안 삭제와 제안별 알림 설정")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface QuestionProposalApiSpec {

	@Operation(summary = "질문 제안 제출", description = """
			질문 문구를 제안합니다. 임시저장 없이 바로 제출됩니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			운영자가 승인해야 실제 질문으로 쓰입니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "제안을 제출했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "제안 문구가 비어 있거나 2,000자를 초과했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 질문을 제안할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문을 제안할 사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/proposals", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<QuestionProposalResponse>> submit(
			@RequestBody @Valid SubmitQuestionProposalRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "내가 제안한 질문 목록 조회", description = """
			내가 제안한 질문 목록을 최근 제출한 순으로 조회합니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			decisionReason은 반려된 제안에만 값이 있을 수 있습니다.
			삭제한 제안은 목록에 나오지 않습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제안 목록을 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 질문 제안을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문을 제안할 사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/proposals/me")
	ResponseEntity<ApiResponse<List<QuestionProposalResponse>>> findMine(
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "내가 제안한 질문 삭제", description = """
			내가 제안한 질문을 목록에서 삭제합니다. 상태와 관계없이 삭제할 수 있습니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			검토 전이거나 검토 중인 제안을 삭제하면 제안이 철회되어 운영자가 더 이상 판정할 수 없습니다.
			이미 삭제한 제안을 다시 삭제해도 204를 반환합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "제안을 삭제했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 질문 제안을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 계정이 없거나, 제안이 없거나 내 제안이 아닙니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping("/proposals/{proposalId}")
	ResponseEntity<ApiResponse<Void>> delete(
			@Parameter(description = "삭제할 질문 제안 식별자입니다.", example = "1") @PathVariable long proposalId,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "제안한 질문 알림 설정", description = """
			내가 제안한 질문 1건의 검토 결과 push를 끄거나 켭니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			꺼도 알림함 기록은 남고 push만 보내지 않습니다. 알림 종류 전체 설정과는 별개로 동작합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경된 제안을 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "muted 값이 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 질문 제안을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 계정이 없거나, 제안이 없거나 내 제안이 아니거나 이미 삭제한 제안입니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping(value = "/proposals/{proposalId}/notification", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<QuestionProposalResponse>> changeNotification(
			@Parameter(description = "알림을 설정할 질문 제안 식별자입니다.", example = "1") @PathVariable long proposalId,
			@RequestBody @Valid ChangeProposalNotificationRequest request,
			@Parameter(hidden = true) Authentication authentication);
}
