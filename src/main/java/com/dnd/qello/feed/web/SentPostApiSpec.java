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
import com.dnd.qello.feed.view.SentPostFilter;
import com.dnd.qello.feed.web.response.SentPostDetailResponse;
import com.dnd.qello.feed.web.response.SentPostListingResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "내가 보낸 질문", description = "질문자 자신이 보낸 질문글 목록·상세 조회")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface SentPostApiSpec {

	@Operation(summary = "사용자(내)가 보낸 질문 목록 조회", description = "사용자(내)가 보낸 질문글을 최신순으로 조회\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)"
			+ "\ncursorSubmittedAt과 cursorPostId는 둘 다 지정하거나 둘 다 생략해야 함")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "질문글 목록을 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "limit 또는 cursor 파라미터가 올바르지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 이 기능을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/posts")
	ResponseEntity<ApiResponse<SentPostListingResponse>> list(
			@Parameter(description = "만료 여부로 좁히는 필터. IN_PROGRESS는 아직 만료되지 않은 질문글, EXPIRED는 만료된 질문글입니다") @RequestParam(defaultValue = "ALL") SentPostFilter filter,
			@Parameter(description = "페이지네이션 커서: 이전 페이지 마지막 항목의 제출 시각. cursorPostId와 함께 지정하거나 함께 생략합니다") @RequestParam(required = false) Instant cursorSubmittedAt,
			@Parameter(description = "페이지네이션 커서: 이전 페이지 마지막 항목의 질문글 식별자. cursorSubmittedAt과 함께 지정하거나 함께 생략합니다") @RequestParam(required = false) Long cursorPostId,
			@Parameter(description = "한 번에 가져올 최대 개수. 1~50, 기본 20") @RequestParam(defaultValue = "20") int limit,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "사용자(내)가 보낸 질문 상세 조회", description = "사용자(내)가 보낸 질문 상세 조회\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "질문글 상세를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 이 기능을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없거나 질문글을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/posts/{postId}")
	ResponseEntity<ApiResponse<SentPostDetailResponse>> detail(
			@Parameter(description = "질문글 식별자", example = "101") @PathVariable long postId,
			@Parameter(hidden = true) Authentication authentication);
}
