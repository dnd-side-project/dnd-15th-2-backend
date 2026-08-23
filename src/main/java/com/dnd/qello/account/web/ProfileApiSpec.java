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
		description = """
			인증된 사용자의 닉네임과 프로필 이미지 정보를 조회합니다.

			앱 액세스 토큰이 필요하며, 현재 계정이 존재해야 합니다.

			성공하면 프로필 이미지 조회 URL과 만료 시각, 기본 이미지 사용 여부를 함께 반환합니다.

			프로필 이미지가 없거나 참조한 이미지가 더 이상 READY 상태가 아니면 기본 이미지 URL을
			반환합니다. 외부 저장소에서 조회 URL을 만들 수 없으면 조회에 실패할 수 있습니다.

			조회 URL은 일정 시간이 지나면 만료됩니다. 버킷 이름과 내부 storage key는 반환하지
			않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필을 조회했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "프로필을 조회할 계정을 찾을 수 없습니다. (ACC-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "외부 저장소를 일시적으로 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping
	ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "프로필 이미지 변경",
		description = """
			인증된 사용자의 프로필 이미지로 업로드한 미디어를 지정합니다.

			앱 액세스 토큰이 필요합니다. 요청한 미디어는 현재 사용자 본인의 것이어야 하고,
			업로드 확인이 끝난 상태여야 합니다.

			성공하면 변경된 프로필과 새 이미지 조회 URL을 반환합니다.

			미디어 식별자가 없거나 양수가 아니면 요청을 처리하지 않습니다. 미디어가 없거나 다른
			사용자의 것이면 같은 404로 응답하고, 업로드 확인이 끝나지 않았거나 사용할 수 없는
			상태이면 409로 응답합니다.

			이 요청은 계정의 프로필 이미지 참조만 바꾸며, 업로드한 미디어 자체를 삭제하지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필 이미지를 변경했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "mediaId가 없거나 양수가 아닙니다. (CMN-VAL-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용할 수 있는 미디어를 찾을 수 없습니다. (ANS-DOM-007)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "업로드 확인이 끝난 미디어만 프로필로 지정할 수 있습니다. (ANS-DOM-006)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping(value = "/image", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ProfileResponse>> changeProfileImage(
		@RequestBody @Valid ProfileImageChangeRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "프로필 이미지 삭제",
		description = """
			인증된 사용자의 프로필 이미지 설정을 해제하고 기본 이미지 상태로 되돌립니다.

			앱 액세스 토큰이 필요하며, 현재 계정이 존재해야 합니다.

			성공하면 기본 이미지가 적용된 프로필을 반환합니다.

			프로필 이미지 설정을 해제할 계정을 찾을 수 없으면 요청을 처리하지 않습니다.

			계정에서 이미지 참조만 제거하며, 업로드한 미디어와 저장소 객체는 삭제하지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필 이미지를 삭제했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "프로필을 변경할 계정을 찾을 수 없습니다. (ACC-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping("/image")
	ResponseEntity<ApiResponse<ProfileResponse>> removeProfileImage(
		@Parameter(hidden = true) Authentication authentication);
}
