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
		summary = "방향별로 받을 사람 수 미리보기",
		description = """
			지금 내 위치를 기준으로 방향마다 질문을 받을 수 있는 사람이 몇 명인지 보여줍니다.

			앱 로그인이 필요하고, 위치를 먼저 갱신해 둬야 합니다.

			질문을 보내기 전에 어느 방향으로 보낼지 고르는 데 씁니다. 여기서 세어 준 수는 \
			참고값이며 실제로 받는 사람 수와 다를 수 있습니다.

			상대의 계정 식별자나 정확한 위치는 돌려주지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "방향별 후보 수를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 방향 기능을 사용할 수 없습니다. (DIR-APP-007)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정 또는 현재 활성 방향 구획 체계를 찾을 수 없습니다. (DIR-APP-006, DIR-DOM-006)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "저장된 위치가 없거나 너무 오래됐습니다. (DIR-APP-003, DIR-APP-004)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/preview")
	ResponseEntity<ApiResponse<DirectionPreviewResponse>> preview(
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "방향 질문글 보내기",
		description = """
			고른 방향에 있는 사람들에게 질문글을 보냅니다.

			앱 로그인이 필요하고, 위치를 먼저 갱신해 둬야 합니다. 글만, 이미지 한 장만, \
			또는 둘 다 보낼 수 있습니다.

			접수만 하고 바로 끝나는 요청입니다. 누가 받을지는 서버가 나중에 정하므로 \
			이 응답에는 수신자가 들어 있지 않습니다.

			같은 요청을 다시 보낼 때를 위해 Idempotency-Key 헤더가 필요합니다. 같은 키로 \
			같은 내용을 다시 보내면 질문글이 두 개 만들어지지 않고 처음 결과를 그대로 돌려줍니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "질문글 제출을 접수했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "본문, 미디어 또는 Idempotency-Key가 정책에 맞지 않습니다. (DIR-VAL-002, DIR-VAL-003, DIR-VAL-008)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 소유가 아닌 미디어를 첨부했거나 방향 기능을 쓸 수 없는 계정입니다. (DIR-APP-007)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문, 방향 구획 체계 또는 인증 사용자 계정을 찾을 수 없습니다. (DIR-DOM-006, DIR-APP-006)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "같은 Idempotency-Key를 다른 요청에 썼거나 저장된 위치가 없거나 너무 오래됐습니다. (DIR-APP-005, DIR-APP-003, DIR-APP-004)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/posts", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<DirectionPostSubmissionResponse>> submit(
		@Parameter(name = "Idempotency-Key", required = true, description = "동일 요청 재시도를 위한 1~200자 멱등 키")
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
		@RequestBody @Valid SubmitDirectionPostRequest request,
		@Parameter(hidden = true) Authentication authentication);
}
