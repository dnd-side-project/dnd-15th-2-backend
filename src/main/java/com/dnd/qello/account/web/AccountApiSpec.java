package com.dnd.qello.account.web;

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
import jakarta.validation.Valid;

// AccountController의 문서 계약. 인증된 본인의 닉네임만 변경할 수 있다.
@Tag(name = "계정", description = "인증된 사용자 자신의 계정 프로필 (#168)")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface AccountApiSpec {

	@Operation(
		summary = "닉네임 변경",
		description = """
			인증된 본인의 닉네임을 변경합니다. 대소문자를 무시한 중복 닉네임(자기 자신이
			이미 쓰고 있는 값 포함)은 거절됩니다. production moderation gate가 켜져 있으면
			OpenAI moderation 판정을 통과한 뒤에만 반영됩니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "닉네임을 변경했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "400",
			description = "요청 값이 비었거나(CMN-VAL-002), 닉네임이 moderation 정책을 위반했습니다(ACC-DOM-005).",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "앱 액세스 토큰이 유효하지 않습니다.",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "이미 사용 중인 닉네임입니다. 자기 자신의 현재 닉네임도 포함됩니다. (ACC-APP-002)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "503",
			description = "닉네임 moderation 판정을 할 수 없습니다. (ACC-INFRA-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PatchMapping(path = "/api/v1/users/me/nickname", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<NicknameResponse>> changeNickname(
		@RequestBody @Valid ChangeNicknameRequest request,
		@Parameter(hidden = true) Authentication authentication);
}
