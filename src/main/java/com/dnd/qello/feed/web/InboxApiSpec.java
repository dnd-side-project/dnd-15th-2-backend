package com.dnd.qello.feed.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.web.response.InboxCommandResponse;
import com.dnd.qello.feed.web.response.InboxDetailResponse;
import com.dnd.qello.feed.web.response.InboxListingResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "방향 수신함", description = "수신 질문 목록·상세 조회와 넘김 상태 변경")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface InboxApiSpec {

	@Operation(
		summary = "수신함 목록 조회",
		description = "인증 사용자의 수신 질문과 카테고리 전체 방향 칩을 조회합니다. 정확 위치와 내부 사용자 식별자는 반환하지 않습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수신함 목록을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 수신함을 사용할 수 없습니다. (FED-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다. (FED-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/inbox")
	ResponseEntity<ApiResponse<InboxListingResponse>> list(
		@RequestParam(defaultValue = "UNANSWERED") InboxCategory category,
		@RequestParam(required = false) String directionSegmentKey,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "수신함 상세 조회",
		description = "인증 사용자가 수신 자격을 가진 질문을 조회하고 최초 열람 상태를 기록합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수신 질문 상세를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 수신함을 사용할 수 없습니다. (FED-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "수신 자격이 있는 항목을 찾을 수 없습니다. (FED-DOM-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "동시 상태 변경으로 상세 열람을 적용할 수 없습니다. (FED-DOM-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/inbox/{postRecipientId}")
	ResponseEntity<ApiResponse<InboxDetailResponse>> detail(
		@Parameter(description = "수신함 항목 식별자", example = "101") @PathVariable long postRecipientId,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "수신 질문 넘김 요청",
		description = "수신 질문의 넘김을 요청하고 서버 정책으로 계산한 되돌리기 마감을 반환합니다. 반복 요청은 최초 마감을 연장하지 않습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "넘김 요청 상태를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 수신함을 사용할 수 없습니다. (FED-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "변경할 수신함 항목을 찾을 수 없습니다. (FED-DOM-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 상태에서는 넘김을 요청할 수 없습니다. (FED-DOM-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/inbox/{postRecipientId}/skip")
	ResponseEntity<ApiResponse<InboxCommandResponse>> skip(
		@Parameter(description = "수신함 항목 식별자", example = "101") @PathVariable long postRecipientId,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "수신 질문 넘김 되돌리기",
		description = "서버가 정한 유예 마감 전에 넘김 요청을 이전 상태로 되돌립니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "복원된 수신 상태를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 수신함을 사용할 수 없습니다. (FED-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "변경할 수신함 항목을 찾을 수 없습니다. (FED-DOM-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "되돌리기 유예가 끝났거나 현재 상태에서는 되돌릴 수 없습니다. (FED-DOM-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping("/inbox/{postRecipientId}/skip")
	ResponseEntity<ApiResponse<InboxCommandResponse>> revertSkip(
		@Parameter(description = "수신함 항목 식별자", example = "101") @PathVariable long postRecipientId,
		@Parameter(hidden = true) Authentication authentication);
}
