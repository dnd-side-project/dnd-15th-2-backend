package com.dnd.qello.account.web;

import jakarta.validation.Valid;

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

@Tag(name = "프로필", description = "본인 프로필 조회와 프로필 이미지 관리")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface ProfileApiSpec {

	@Operation(summary = "본인 프로필 조회", description = """
			내 닉네임과 프로필 이미지를 조회합니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			설정한 이미지가 없으면 기본 이미지 주소를 반환합니다. 이미지 주소는 만료 시각이 지나면 쓸 수 없으니 다시 조회해야 합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필을 조회했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "프로필을 조회할 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "외부 저장소를 일시적으로 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping
	ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "프로필 이미지 변경", description = """
			업로드를 마친 내 이미지를 프로필 이미지로 지정합니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			업로드 확인이 끝난 이미지만 지정할 수 있습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필 이미지를 변경했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "mediaId가 없거나 양수가 아닙니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용할 수 있는 미디어를 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "업로드 확인이 끝난 미디어만 프로필로 지정할 수 있습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping(value = "/image", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ProfileResponse>> changeProfileImage(
			@RequestBody @Valid ProfileImageChangeRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "프로필 이미지 삭제", description = """
			프로필 이미지를 기본 이미지로 되돌립니다. 업로드한 이미지 파일은 지우지 않습니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필 이미지를 삭제했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "프로필을 변경할 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping("/image")
	ResponseEntity<ApiResponse<ProfileResponse>> removeProfileImage(
			@Parameter(hidden = true) Authentication authentication);
}
