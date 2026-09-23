package com.dnd.qello.direction.web;

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
 * 질문글 공감. 누르기(PUT)와 취소(DELETE)로 나눠 재시도로 도착한 중복 요청이 최종 상태를 뒤집지 않게 한다 — 단일 toggle
 * 호출과 달리 몇 번 도착해도 결과가 같다.
 */
@Tag(name = "질문글 공감", description = "수신 자격이 있는 사용자의 질문글 공감 남김·취소")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface PostReactionApiSpec {

	@Operation(summary = "내가 받은 질문글 공감 남기기", description = "사용자(내)가 받은 질문글에 공감\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "공감 상태와 공감 수를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "수신 자격이 없는 사용자는 공감할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/posts/{postId}/reaction")
	ResponseEntity<ApiResponse<ReactionResponse>> react(
			@Parameter(description = "질문글 식별자", example = "101") @PathVariable long postId,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "내가 받은 질문글 공감 취소하기", description = "사용자(내)가 받은 질문글에 공감\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "공감 상태와 공감 수를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping("/posts/{postId}/reaction")
	ResponseEntity<ApiResponse<ReactionResponse>> cancel(
			@Parameter(description = "질문글 식별자", example = "101") @PathVariable long postId,
			@Parameter(hidden = true) Authentication authentication);
}
