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
import com.dnd.qello.feed.web.response.AnswerListingResponse;
import com.dnd.qello.feed.web.response.SentPostDetailResponse;
import com.dnd.qello.feed.web.response.SentPostListingResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "내가 보낸 질문", description = "질문자 자신이 보낸 질문글 목록·상세와 그 답변 목록 조회")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface SentPostApiSpec {

	@Operation(
		summary = "내가 보낸 질문 목록 조회",
		description = "인증 사용자가 보낸 질문글을 최신순으로 조회합니다. cursorSubmittedAt과 cursorPostId는 "
			+ "둘 다 지정하거나 둘 다 생략해야 합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "질문글 목록을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "limit 또는 cursor 파라미터가 올바르지 않습니다. (FED-VAL-001, FED-VAL-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 이 기능을 사용할 수 없습니다. (FED-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다. (FED-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/posts")
	ResponseEntity<ApiResponse<SentPostListingResponse>> list(
		@Parameter(description = "만료 여부로 좁히는 필터. IN_PROGRESS는 아직 만료되지 않은 질문글, EXPIRED는 만료된 질문글입니다")
		@RequestParam(defaultValue = "ALL") SentPostFilter filter,
		@Parameter(description = "페이지네이션 커서: 이전 페이지 마지막 항목의 제출 시각. cursorPostId와 함께 지정하거나 함께 생략합니다")
		@RequestParam(required = false) Instant cursorSubmittedAt,
		@Parameter(description = "페이지네이션 커서: 이전 페이지 마지막 항목의 질문글 식별자. cursorSubmittedAt과 함께 지정하거나 함께 생략합니다")
		@RequestParam(required = false) Long cursorPostId,
		@Parameter(description = "한 번에 가져올 최대 개수. 1~50, 기본 20")
		@RequestParam(defaultValue = "20") int limit,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "내가 보낸 질문 상세 조회",
		description = "인증 사용자가 보낸 질문글 하나를 조회합니다. 남의 질문글이거나 존재하지 않으면 404입니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "질문글 상세를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 이 기능을 사용할 수 없습니다. (FED-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없거나 질문글을 찾을 수 없습니다. (FED-APP-001, FED-DOM-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/posts/{postId}")
	ResponseEntity<ApiResponse<SentPostDetailResponse>> detail(
		@Parameter(description = "질문글 식별자", example = "101") @PathVariable long postId,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "답변 목록 조회",
		description = "그 질문글의 공개된 답변을 최신순으로 조회합니다. 질문글 작성자와 그 질문글의 수신 자격자만 "
			+ "내용을 받습니다. 자격이 없는 뷰어는 403이 아니라 빈 목록을 받습니다 — 질문글 존재 여부를 흘리지 않기 위함입니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "답변 목록을 반환합니다. 자격이 없으면 빈 목록입니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "limit 또는 cursor 파라미터가 올바르지 않습니다. (FED-VAL-001, FED-VAL-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 이 기능을 사용할 수 없습니다. (FED-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다. (FED-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/posts/{postId}/answers")
	ResponseEntity<ApiResponse<AnswerListingResponse>> answers(
		@Parameter(description = "질문글 식별자", example = "101") @PathVariable long postId,
		@Parameter(description = "페이지네이션 커서: 이전 페이지 마지막 답변의 공개 시각. cursorAnswerId와 함께 지정하거나 함께 생략합니다")
		@RequestParam(required = false) Instant cursorPublishedAt,
		@Parameter(description = "페이지네이션 커서: 이전 페이지 마지막 답변의 식별자. cursorPublishedAt과 함께 지정하거나 함께 생략합니다")
		@RequestParam(required = false) Long cursorAnswerId,
		@Parameter(description = "한 번에 가져올 최대 개수. 1~50, 기본 20")
		@RequestParam(defaultValue = "20") int limit,
		@Parameter(hidden = true) Authentication authentication);
}
