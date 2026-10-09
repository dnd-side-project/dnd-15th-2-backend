package com.dnd.qello.account.web;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

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

// AccountController의 문서 계약. 인증된 본인의 닉네임만 변경할 수 있다.
@Tag(name = "계정", description = "내 닉네임 변경, 탈퇴 요청과 철회")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface AccountApiSpec {

	@Operation(summary = "닉네임 변경", description = """
			내 닉네임을 변경합니다. 앞뒤 공백은 지우고 저장합니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			이미 사용 중인 닉네임은 쓸 수 없고 지금 내 닉네임과 같아도 409를 반환합니다.

			닉네임을 바꾼 뒤 일정 기간 안에는 다시 바꿀 수 없습니다. 가입할 때 정한 닉네임은 바로 바꿀 수 있습니다. 실패한 요청을 포함해 하루 변경 요청 수에도 한도가 있습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "닉네임을 변경했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않거나, 정규화한 닉네임이 비었거나, 닉네임이 길이 제한 또는 유해성 검사 기준을 통과하지 못했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "변경할 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임입니다. 자기 자신의 현재 닉네임도 포함됩니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "닉네임 변경 주기가 지나지 않았거나 변경 요청 한도를 넘었습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "닉네임 검사를 할 수 없습니다. 잠시 후 다시 시도해 주세요.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PatchMapping(path = "/api/v1/users/me/nickname", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<NicknameResponse>> changeNickname(
			@RequestBody @Valid ChangeNicknameRequest request,
			@Parameter(hidden = true) Authentication authentication);
}
