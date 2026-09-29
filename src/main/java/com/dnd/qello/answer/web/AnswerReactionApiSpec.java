package com.dnd.qello.answer.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.feed.web.response.ReactionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 답변 공감. 누르기(PUT)와 취소(DELETE)로 나눠 재시도로 도착한 중복 요청이 최종 상태를 뒤집지 않게 한다.
 */
@Tag(name = "답변 공감", description = "그 질문글을 볼 수 있는 사용자의 답변 공감 남김·취소")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface AnswerReactionApiSpec {

	@Operation(summary = "답변 공감 남기기", description = "질문글 작성자, 해당 질문글 수신자(볼 수 있는 사람)가 답변에 공감할 수 있음(자기 답변에는 공감X)"
			+ "\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "공감 상태와 공감 수를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "답변 식별자가 올바르지 않거나 그런 답변이 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "그 질문글을 볼 수 있는 사람만 답변에 공감할 수 있습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/answers/{answerId}/reaction")
	ResponseEntity<ApiResponse<ReactionResponse>> react(
			@Parameter(description = "답변 식별자", example = "201") @PathVariable long answerId,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "답변 공감 취소", description = "질문글 작성자, 해당 질문글 수신자(볼 수 있는 사람)가 답변에 취소\n"
			+ "앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "공감 상태와 공감 수를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping("/answers/{answerId}/reaction")
	ResponseEntity<ApiResponse<ReactionResponse>> cancel(
			@Parameter(description = "답변 식별자", example = "201") @PathVariable long answerId,
			@Parameter(hidden = true) Authentication authentication);
}
