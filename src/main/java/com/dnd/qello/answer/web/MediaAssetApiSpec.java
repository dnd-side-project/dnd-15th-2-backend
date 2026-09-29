package com.dnd.qello.answer.web;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.dnd.qello.answer.web.request.MediaUploadRequest;
import com.dnd.qello.answer.web.response.MediaConfirmResponse;
import com.dnd.qello.answer.web.response.MediaUploadResponse;
import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "미디어", description = "질문글 이미지 업로드 예약과 완료 확인")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface MediaAssetApiSpec {

	@Operation(summary = "이미지 올릴 임시 주소 받기", description = "이미지를 올릴 임시 주소 발급\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)"
			+ "\nJPEG또는 PNG 한 장만 올릴 수 있음")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "업로드 예약을 발급했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이미지 형식이나 파일 크기가 정책에 맞지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/upload-requests", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<MediaUploadResponse>> issueUploadUrl(
			@RequestBody @Valid MediaUploadRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "이미지 업로드 확인", description = "올린 이미지가 실제로 저장됐는지 확인하고 첨부할 수 있는 상태로 변경\n"
			+ "앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)\n본인이 올린 이미지만 확인 가능")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업로드 상태를 확인했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "미디어 식별자가 올바르지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그런 이미지가 없거나 본인이 올린 이미지가 아닙니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "이미지 저장소에 연결할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{mediaId}/confirm")
	ResponseEntity<ApiResponse<MediaConfirmResponse>> confirm(
			@Parameter(description = "미디어 식별자", example = "42") @PathVariable long mediaId,
			@Parameter(hidden = true) Authentication authentication);
}
