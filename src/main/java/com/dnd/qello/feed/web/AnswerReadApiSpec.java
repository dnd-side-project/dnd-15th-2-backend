package com.dnd.qello.feed.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.feed.web.response.AnswersReadResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * `새로운 답변 N개` 배지 해제. 두 전이 모두 GREATEST(현재값, at)로만 전진하므로
 * 반복 호출과 순서 역전이 읽음 지점을 과거로 되돌리지 않는다 — 그래서 PUT이다.
 */
@Tag(name = "답변 읽음 처리", description = "질문자·수신자의 답변 열람 시각을 기록해 미읽음 배지를 해제한다")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface AnswerReadApiSpec {

	@Operation(
		summary = "질문자의 답변 읽음 처리",
		description = "인증 사용자가 보낸 질문글의 답변 열람 시각을 서버 시각으로 기록합니다. 반복 호출은 이미 "
			+ "기록된 시각보다 이전으로 되돌리지 않습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "갱신된 읽음 시각을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 이 기능을 사용할 수 없습니다. (FED-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문글을 찾을 수 없습니다. (DIR-DOM-009)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/posts/{postId}/answers/read")
	ResponseEntity<ApiResponse<AnswersReadResponse>> markSenderAnswersRead(
		@Parameter(description = "질문글 식별자", example = "101") @PathVariable long postId,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "수신자의 답변 읽음 처리",
		description = "인증 사용자가 받은 질문의 답변 열람 시각을 서버 시각으로 기록합니다. 반복 호출은 이미 "
			+ "기록된 시각보다 이전으로 되돌리지 않습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "갱신된 읽음 시각을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 이 기능을 사용할 수 없습니다. (FED-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "수신 항목을 찾을 수 없습니다. (DIR-DOM-008)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/inbox/{postRecipientId}/answers/read")
	ResponseEntity<ApiResponse<AnswersReadResponse>> markRecipientAnswersRead(
		@Parameter(description = "수신함 항목 식별자", example = "101") @PathVariable long postRecipientId,
		@Parameter(hidden = true) Authentication authentication);
}
