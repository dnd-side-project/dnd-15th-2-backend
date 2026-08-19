package com.dnd.qello.account.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.dnd.qello.account.web.request.ProfileImageChangeRequest;
import com.dnd.qello.account.web.response.ProfileResponse;
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

@Tag(name = "프로필", description = "본인 프로필 조회와 프로필 이미지 관리")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface ProfileApiSpec {

	@Operation(
		summary = "본인 프로필 조회",
		description = "만료가 있는 프로필 이미지 조회 URL을 함께 반환합니다. 프로필 이미지를 설정하지 않았거나 참조한 이미지를 더 이상 쓸 수 없으면 기본 이미지 URL을 반환합니다. 버킷 이름과 storage key는 반환하지 않습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필을 조회했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "외부 저장소를 일시적으로 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping
	ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "프로필 이미지 변경",
		description = "본인 소유의 업로드 확인(READY)된 이미지만 프로필로 지정할 수 있습니다. 남의 이미지와 존재하지 않는 이미지는 모두 404로 응답합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필 이미지를 변경했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "mediaId가 올바르지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용할 수 있는 미디어를 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "READY 상태가 아닌 미디어입니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping(value = "/image", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ProfileResponse>> changeProfileImage(
		@RequestBody @Valid ProfileImageChangeRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "프로필 이미지 삭제",
		description = "프로필 이미지 참조만 해제해 기본 이미지 상태로 되돌립니다. 업로드한 이미지 자체는 삭제하지 않습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필 이미지를 삭제했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping("/image")
	ResponseEntity<ApiResponse<ProfileResponse>> removeProfileImage(
		@Parameter(hidden = true) Authentication authentication);
}
