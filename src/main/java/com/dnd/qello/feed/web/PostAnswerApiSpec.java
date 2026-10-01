package com.dnd.qello.feed.web;

import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.feed.web.response.AnswerListingResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 질문글 단위 답변 목록의 HTTP 계약이다. 질문자와 수신 자격자가 같은 엔드포인트를 쓰므로 `내가 보낸 질문` 태그에서 분리했다.
 */
@Tag(name = "답변 목록", description = "질문글에 공개된 답변 목록 조회. 질문자와 수신자가 함께 사용합니다")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface PostAnswerApiSpec {

	@Operation(summary = "질문글의 답변 목록 조회", description = "질문글에 공개된 답변을 최신순으로 보여줍니다. 내가 보낸 질문과 내가 받은 질문 양쪽에서 같은 API를 씁니다."
			+ "\n앱 로그인이 필요합니다(Authorization 헤더에 앱 액세스 토큰)."
			+ "\ncursorPublishedAt과 cursorAnswerId는 둘 다 지정하거나 둘 다 생략해야 합니다.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "답변 목록을 반환합니다. 자격이 없으면 빈 목록입니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "limit 또는 cursor 파라미터가 올바르지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 이 기능을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/posts/{postId}/answers")
	ResponseEntity<ApiResponse<AnswerListingResponse>> answers(
			@Parameter(description = "질문글 식별자", example = "101") @PathVariable long postId,
			@Parameter(description = "페이지네이션 커서: 이전 페이지 마지막 답변의 공개 시각. cursorAnswerId와 함께 지정하거나 함께 생략합니다") @RequestParam(required = false) Instant cursorPublishedAt,
			@Parameter(description = "페이지네이션 커서: 이전 페이지 마지막 답변의 식별자. cursorPublishedAt과 함께 지정하거나 함께 생략합니다") @RequestParam(required = false) Long cursorAnswerId,
			@Parameter(description = "한 번에 가져올 최대 개수. 1~50, 기본 20") @RequestParam(defaultValue = "20") int limit,
			@Parameter(hidden = true) Authentication authentication);
}
