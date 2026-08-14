package com.dnd.qello.direction.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.direction.web.request.SubmitDirectionPostRequest;
import com.dnd.qello.direction.web.response.DirectionPostSubmissionResponse;
import com.dnd.qello.direction.web.response.DirectionPreviewResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "방향 질문글", description = "방향별 후보 미리보기와 비동기 질문글 제출")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface DirectionPostApiSpec {

	@Operation(
		summary = "방향별 후보 수 미리보기",
		description = "인증 사용자의 현재 presence와 서버 정책으로 모든 활성 방향 구간의 참고 후보 수를 반환합니다. 사용자 ID와 정확 위치는 반환하지 않습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "미리보기를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 위치 또는 방향 정책을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/preview")
	ResponseEntity<ApiResponse<DirectionPreviewResponse>> preview(
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "방향 질문글 비동기 제출",
		description = "텍스트만, JPEG/PNG 이미지 한 장만 또는 둘을 조합해 제출합니다. 수신자 확정은 비동기 worker가 수행합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "질문글 제출을 접수했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "본문, 미디어 또는 멱등키가 정책에 맞지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "미디어 소유권 또는 계정 자격이 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문 또는 방향 구획을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "멱등키가 다른 요청에 재사용됐거나 현재 정책을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/posts", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<DirectionPostSubmissionResponse>> submit(
		@Parameter(name = "Idempotency-Key", required = true, description = "동일 요청 재시도를 위한 1~200자 멱등 키")
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
		@RequestBody @Valid SubmitDirectionPostRequest request,
		@Parameter(hidden = true) Authentication authentication);
}
