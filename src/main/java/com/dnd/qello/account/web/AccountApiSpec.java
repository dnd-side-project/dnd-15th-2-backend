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
@Tag(name = "계정", description = "인증된 사용자의 닉네임 변경")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface AccountApiSpec {

	@Operation(
		summary = "닉네임 변경",
		description = """
			인증된 사용자의 닉네임을 변경합니다. 앞뒤 공백은 제거한 뒤 저장합니다.

			앱 액세스 토큰이 필요합니다. 현재 계정이 존재해야 하며, 이미 사용 중인 닉네임은
			자기 자신의 현재 닉네임을 포함해 사용할 수 없습니다.

			변경에 성공하면 새 닉네임을 반환합니다.

			닉네임이 비어 있거나 길이 제한을 넘거나 유해성 검사를 통과하지 못하면 변경하지
			않습니다. 닉네임 검증 서비스를 사용할 수 없을 때는 일시적인 오류로 응답합니다.

			중복 확인과 저장 사이에 다른 요청이 먼저 같은 닉네임을 저장하면 변경되지 않을 수
			있습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "닉네임을 변경했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "400",
			description = "요청 값이 올바르지 않거나 닉네임이 길이 제한 또는 유해성 검사 기준을 통과하지 못했습니다. (CMN-VAL-001, ACC-VAL-003, ACC-DOM-005)",
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
			responseCode = "404",
			description = "변경할 계정을 찾을 수 없습니다. (ACC-APP-001)",
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
