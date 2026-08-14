package com.dnd.qello.answer.web;

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
import jakarta.validation.Valid;

@Tag(name = "미디어", description = "질문글 이미지 업로드 예약과 완료 확인")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface MediaAssetApiSpec {

	@Operation(
		summary = "이미지 업로드 예약",
		description = "인증 사용자 소유의 JPEG/JPG 또는 PNG 한 건에 대한 presigned PUT URL을 발급합니다. storage key는 반환하지 않습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "업로드 예약을 발급했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "MIME type 또는 파일 크기가 정책에 맞지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/upload-requests", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<MediaUploadResponse>> issueUploadUrl(
		@RequestBody @Valid MediaUploadRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "이미지 업로드 확인",
		description = "본인 소유 업로드 객체의 실제 MIME type과 크기를 확인합니다. 이미 확정된 상태는 멱등하게 반환합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업로드 상태를 확인했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "미디어를 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "외부 저장소를 일시적으로 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{mediaId}/confirm")
	ResponseEntity<ApiResponse<MediaConfirmResponse>> confirm(
		@Parameter(description = "미디어 식별자", example = "42") @PathVariable long mediaId,
		@Parameter(hidden = true) Authentication authentication);
}
