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

	@Operation(summary = "수신함 목록 조회", description = "만료 전 미답변·답변 완료 질문을 함께 조회하며 카드 status로 구분합니다. 페이지네이션 없이 매칭 시각·항목 ID 내림차순으로 전체 항목을 반환합니다. 방향 칩은 전체 미답변 항목만 집계합니다.\n앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수신함 목록을 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 수신함을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/inbox")
	ResponseEntity<ApiResponse<InboxListingResponse>> list(
			@Parameter(description = "특정 방향의 질문글을 조회할 때 사용합니다 (N, NE, E, SE, S, SW, W, NW). 생략하면 전체 방향을 조회합니다. chips는 이 값과 무관하게 전체 미답변 항목만 집계합니다") @RequestParam(required = false) String directionSegmentKey,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "수신함 상세 조회", description = "질문을 상세 조회합니다.\n앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수신 질문 상세를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 수신함을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없거나 수신 자격이 있는 항목을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "동시 상태 변경으로 상세 열람을 적용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/inbox/{postRecipientId}")
	ResponseEntity<ApiResponse<InboxDetailResponse>> detail(
			@Parameter(description = "수신함 항목 식별자", example = "101") @PathVariable long postRecipientId,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "받은 질문 넘김 대기 요청", description = "받은 질문을 넘기겠다고 대기 하는 요청(유예시간 존재)\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)\n일정 시간 지나면 넘김 확정됨")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "넘김 요청 상태를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 수신함을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없거나 변경할 수신함 항목을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 상태에서는 넘김을 요청할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/inbox/{postRecipientId}/skip")
	ResponseEntity<ApiResponse<InboxCommandResponse>> skip(
			@Parameter(description = "수신함 항목 식별자", example = "101") @PathVariable long postRecipientId,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "받은 질문 넘김 대기 요청 취소", description = "질문 넘김 대기 유예 시간 전에 대기 요청을 취소\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "복원된 수신 상태를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 수신함을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없거나 변경할 수신함 항목을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "되돌리기 유예가 끝났거나 현재 상태에서는 되돌릴 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping("/inbox/{postRecipientId}/skip")
	ResponseEntity<ApiResponse<InboxCommandResponse>> revertSkip(
			@Parameter(description = "수신함 항목 식별자", example = "101") @PathVariable long postRecipientId,
			@Parameter(hidden = true) Authentication authentication);
}
