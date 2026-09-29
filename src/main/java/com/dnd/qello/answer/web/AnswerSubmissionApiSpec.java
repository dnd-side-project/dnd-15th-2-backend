package com.dnd.qello.answer.web;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.dnd.qello.answer.web.request.SubmitAnswerRequest;
import com.dnd.qello.answer.web.response.AnswerSubmissionResponse;
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

@Tag(name = "답변", description = "수신함 항목에 대한 답변 제출")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface AnswerSubmissionApiSpec {

	@Operation(summary = "질문글 답변 보내기", description = "사용자(내)가 받은 질문에 답변 보냄\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)"
			+ "\n새 요청, 재시도를 구분하기 위한 Idempotency-Key 헤더 필요"
			+ "\n수신자만 답변 보내기 가능, 한 번만 답할 수 있음")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "답변 제출을 접수했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "본문, 미디어 또는 Idempotency-Key가 정책에 맞지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 소유가 아닌 미디어를 첨부했거나 답변할 수 없는 계정입니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "답변할 수 있는 수신 항목이 없거나 인증 사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "같은 Idempotency-Key를 다른 요청에 썼거나 이미 이 질문에 답변했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "안전 검사 접수 정책이 아직 준비되지 않았습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/{postRecipientId}/answers", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<AnswerSubmissionResponse>> submit(
			@Parameter(description = "수신 항목 식별자") @PathVariable long postRecipientId,
			@Parameter(name = "Idempotency-Key", required = true, description = "동일 요청 재시도를 위한 1~200자 멱등 키") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody @Valid SubmitAnswerRequest request,
			@Parameter(hidden = true) Authentication authentication);
}
